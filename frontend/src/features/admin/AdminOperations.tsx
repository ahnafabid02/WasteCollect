import { FormEvent, useCallback, useEffect, useState } from "react";
import { NavLink, Navigate, useLocation } from "react-router-dom";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";
type Item = { id: string; publicCode: string; status: string; zoneName: string; preferredDate: string; residentName?: string; categoryName?: string; address?: string; memberCount?: number; scheduledStart?: string; scheduledEnd?: string; collectorId?: string; collectorName?: string };
type Page<T> = { items: T[]; total: number; page: number; size: number };
type Collector = { id: string; displayName: string; email: string; status: string; activeAssignments: number };
type Settings = { maxGroupRequests: number; minimumNoticeHours: number; serviceTimezone: string };
type Audit = { id: string; action: string; entityType: string; entityId: string; details: string; createdAt: string; actorName: string };
type Dashboard = { requests: { status: string; total: number }[]; groups: { status: string; total: number }[]; activeCollectors: number; unassignedGroups: number };
type Assignment = { id: string; collectorName: string; startsAt: string; endsAt: string; closedAt: string | null; reason: string };
type RequestDetails = Item & { quantity: number; unit: string; notes: string | null; history: { nextStatus: string; reason: string; createdAt: string; actorName: string }[] };

function localDateTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

function token() {
  try { return (JSON.parse(sessionStorage.getItem("wastecollect.tokens") ?? "null") as { accessToken?: string } | null)?.accessToken; }
  catch { return undefined; }
}

async function api<T>(path: string, method = "GET", data?: unknown, signal?: AbortSignal): Promise<T> {
  return request<T>(`${API}/admin${path}`, method, data, signal, true);
}

async function request<T>(url: string, method = "GET", data?: unknown, signal?: AbortSignal, authenticated = false): Promise<T> {
  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) abort();
  signal?.addEventListener("abort", abort, { once: true });
  const timeout = setTimeout(abort, 15000);
  try {
    const response = await fetch(url, {
      method, headers: { ...(authenticated ? { Authorization: `Bearer ${token()}` } : {}), ...(data ? { "Content-Type": "application/json" } : {}) },
      body: data ? JSON.stringify(data) : undefined, signal: controller.signal,
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({})) as { message?: string; details?: { field: string; reason: string }[] };
      throw new Error(response.status === 401 || response.status === 403 ? "Your session expired or you do not have administrator access. Sign in again." : payload.details?.map(item => `${item.field}: ${item.reason}`).join("; ") || payload.message || "Unable to complete the operation.");
    }
    return response.status === 204 ? undefined as T : await response.json() as T;
  } finally {
    clearTimeout(timeout); signal?.removeEventListener("abort", abort);
  }
}

export function AdminNav() {
  return <nav className="admin-nav" aria-label="Administrator navigation">
    <NavLink to="/admin" end>Overview</NavLink><NavLink to="/admin/requests">Requests</NavLink>
    <NavLink to="/admin/groups">Grouping</NavLink><NavLink to="/admin/scheduling">Scheduling</NavLink>
    <NavLink to="/admin/collectors">Collectors</NavLink><NavLink to="/admin/audit">Audit history</NavLink>
    <NavLink to="/admin/settings">Settings</NavLink>
  </nav>;
}

function Pagination({ page, total, onPage }: { page: number; total: number; onPage: (page: number) => void }) {
  return <div className="pagination"><button className="button secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {page + 1} · {total} records</span><button className="button secondary" disabled={(page + 1) * 20 >= total} onClick={() => onPage(page + 1)}>Next</button></div>;
}

export default function AdminOperations({ onLogout }: { onLogout: () => Promise<void> }) {
  const location = useLocation();
  const section = location.pathname.split("/")[2] ?? "overview";
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [status, setStatus] = useState("");
  const [zone, setZone] = useState("");
  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState("desc");
  const [zones, setZones] = useState<{ id: string; name: string }[]>([]);
  const [items, setItems] = useState<Page<Item>>({ items: [], total: 0, page: 0, size: 20 });
  const [collectors, setCollectors] = useState<Collector[]>([]);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [audit, setAudit] = useState<Page<Audit>>({ items: [], total: 0, page: 0, size: 20 });
  const [selected, setSelected] = useState<Item | null>(null);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [editorLoading, setEditorLoading] = useState(false);
  const [requestDetails, setRequestDetails] = useState<RequestDetails | null>(null);
  const load = useCallback(async (signal?: AbortSignal) => {
    if (!token() || sessionStorage.getItem("wastecollect.role") !== "ADMIN") return;
    setLoading(true); setError("");
    try {
      if (section === "overview") setDashboard(await api<Dashboard>("/dashboard", "GET", undefined, signal));
      else if (section === "settings") setSettings(await api<Settings>("/settings", "GET", undefined, signal));
      else if (section === "audit") setAudit(await api<Page<Audit>>(`/audit?page=${page}`, "GET", undefined, signal));
      else if (section === "collectors") setCollectors(await api<Collector[]>("/collectors", "GET", undefined, signal));
      else {
        const params = new URLSearchParams({ query, status, page: String(page), sort, direction });
        if (zone) params.set("zoneId", zone);
        const [nextItems, nextCollectors, nextZones] = await Promise.all([
          api<Page<Item>>(`/${section === "requests" ? "requests" : "collections"}?${params}`, "GET", undefined, signal),
          section === "scheduling" ? api<Collector[]>("/collectors", "GET", undefined, signal) : Promise.resolve([]),
          request<{ id: string; name: string }[]>(`${API}/zones`, "GET", undefined, signal),
        ]);
        if (signal?.aborted) return;
        setItems(nextItems); setCollectors(nextCollectors); setZones(nextZones);
      }
    } catch (exception) { if (!signal?.aborted) setError(exception instanceof Error ? exception.message : "Unable to load this page."); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [section, page, query, status, zone, sort, direction]);
  useEffect(() => {
    const controller = new AbortController(); void load(controller.signal);
    return () => controller.abort();
  }, [load, revision]);
  useEffect(() => {
    if (!selected || section !== "scheduling") return;
    const controller = new AbortController(); setEditorLoading(true); setAssignments([]);
    void api<Assignment[]>(`/groups/${selected.id}/assignments`, "GET", undefined, controller.signal)
      .then(value => { if (!controller.signal.aborted) setAssignments(value); })
      .catch(exception => { if (!controller.signal.aborted) setError(exception instanceof Error ? exception.message : "Unable to load assignment history."); })
      .finally(() => { if (!controller.signal.aborted) setEditorLoading(false); });
    return () => controller.abort();
  }, [selected, section]);
  useEffect(() => {
    if (!selected || section !== "requests") return;
    const controller = new AbortController(); setEditorLoading(true); setRequestDetails(null);
    void api<RequestDetails>(`/requests/${selected.id}`, "GET", undefined, controller.signal)
      .then(value => { if (!controller.signal.aborted) setRequestDetails(value); })
      .catch(exception => { if (!controller.signal.aborted) setError(exception instanceof Error ? exception.message : "Unable to load request details."); })
      .finally(() => { if (!controller.signal.aborted) setEditorLoading(false); });
    return () => controller.abort();
  }, [selected, section]);
  if (!token() || sessionStorage.getItem("wastecollect.role") !== "ADMIN") return <Navigate to="/login" replace />;

  async function mutate(path: string, method: string, data: unknown, message: string) {
    if (busy) return false;
    setBusy(true); setError(""); setSuccess("");
    try { await api(path, method, data); setSuccess(message); setSelected(null); setRevision(value => value + 1); return true; }
    catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to save changes."); return false; }
    finally { setBusy(false); }
  }
  async function submitSchedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = new FormData(event.currentTarget);
    await mutate(`/groups/${selected.id}/schedule`, "PATCH", { startsAt: new Date(String(form.get("start"))).toISOString(), endsAt: new Date(String(form.get("end"))).toISOString(), reason: form.get("reason") }, "Collection schedule saved.");
  }
  async function submitAssignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = new FormData(event.currentTarget);
    await mutate(`/groups/${selected.id}/assignment`, "POST", { collectorId: form.get("collector"), reason: form.get("reason") }, "Collector assignment saved.");
  }
  async function cancelGroup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return;
    const form = new FormData(event.currentTarget);
    await mutate(`/groups/${selected.id}/cancel`, "PATCH", { reason: form.get("reason") }, "Group cancelled. Requests have returned to the pending queue.");
  }
  async function createCollector(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement);
    if (await mutate("/users/collectors", "POST", Object.fromEntries(form), "Collector account created.")) formElement.reset();
  }
  async function saveSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    await mutate("/settings", "PATCH", { maxGroupRequests: Number(form.get("maximum")), minimumNoticeHours: Number(form.get("notice")) }, "Settings saved.");
  }
  const title = { overview: "Operations overview", requests: "Pickup requests", scheduling: "Collection scheduling", collectors: "Collector management", audit: "Audit history", settings: "Operational settings" }[section] ?? "Administration";
  return <main className="workspace"><AdminNav /><section className="workspace-heading"><div><p className="eyebrow">Administrator workspace</p><h1>{title}</h1></div><button className="button ghost" onClick={() => void onLogout()}>Sign out</button></section>
    {error && <div className="form-message error" role="alert">{error} <button className="button secondary" onClick={() => void load()}>Retry</button></div>}
    {success && <p className="form-message success" role="status">{success}</p>}
    {["requests", "scheduling"].includes(section) && <form className="admin-filters" onSubmit={event => { event.preventDefault(); setQuery(searchInput); setPage(0); }}>
      <label>Search<input value={searchInput} onChange={event => setSearchInput(event.target.value)} placeholder="Code, zone, address or resident" /></label>
      <label>Status<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}><option value="">All statuses</option>{(section === "requests" ? ["PENDING","GROUPED","SCHEDULED","IN_PROGRESS","COMPLETED","FAILED","CANCELLED"] : ["DRAFT","SCHEDULED","IN_PROGRESS","COMPLETED","CANCELLED"]).map(value => <option key={value}>{value}</option>)}</select></label>
      <label>Zone<select value={zone} onChange={event => { setZone(event.target.value); setPage(0); }}><option value="">All zones</option>{zones.map(value => <option key={value.id} value={value.id}>{value.name}</option>)}</select></label>
      <label>Sort<select value={sort} onChange={event => { setSort(event.target.value); setPage(0); }}><option value="createdAt">Created</option><option value="preferredDate">Preferred date</option><option value="status">Status</option><option value="publicCode">Code</option></select></label>
      <label>Direction<select value={direction} onChange={event => { setDirection(event.target.value); setPage(0); }}><option value="desc">Descending</option><option value="asc">Ascending</option></select></label>
      <button className="button secondary" type="submit">Search</button>
    </form>}
    {loading ? <div className="loading-card" role="status"><span className="spinner dark" /> Loading operations…</div> : <>
      {section === "overview" && dashboard && <div className="metrics-grid"><section className="content-card"><h2>Requests</h2>{dashboard.requests.length ? dashboard.requests.map(item => <p key={item.status}>{item.status} <strong>{item.total}</strong></p>) : <p>No requests yet.</p>}</section><section className="content-card"><h2>Collections</h2>{dashboard.groups.length ? dashboard.groups.map(item => <p key={item.status}>{item.status} <strong>{item.total}</strong></p>) : <p>No groups yet.</p>}</section><section className="content-card"><h2>Staffing</h2><p>Active collectors <strong>{dashboard.activeCollectors}</strong></p><p>Scheduled groups awaiting assignment <strong>{dashboard.unassignedGroups}</strong></p></section></div>}
      {["requests", "scheduling"].includes(section) && <section className="content-card">{!items.items.length ? <p className="empty-state">No matching records. Adjust your filters or create a collection in Grouping.</p> : <div className="table-scroll"><table className="admin-table"><thead><tr><th>{section === "requests" ? "Request" : "Collection"}</th><th>Zone / date</th><th>Status</th><th>{section === "requests" ? "Details" : "Schedule / collector"}</th><th>Actions</th></tr></thead><tbody>{items.items.map(item => <tr key={item.id}><td><strong>{item.publicCode}</strong><small>{item.residentName ?? `${item.memberCount} active request(s)`}</small></td><td>{item.zoneName}<small>{item.preferredDate}</small></td><td><span className="status">{item.status}</span></td><td>{item.categoryName ?? (item.scheduledStart ? new Date(item.scheduledStart).toLocaleString() : "Not scheduled")}<small>{item.address ?? item.collectorName ?? "Unassigned"}</small></td><td>{(section === "requests" || ["DRAFT","SCHEDULED"].includes(item.status)) && <button className="button secondary" onClick={() => setSelected(item)}>{section === "requests" ? "View details" : "Manage"}</button>}</td></tr>)}</tbody></table></div>}<Pagination page={page} total={items.total} onPage={setPage} /></section>}
      {section === "requests" && selected && <section className="content-card operation-editor"><div className="section-heading"><h2>Request {selected.publicCode}</h2><button className="button ghost" onClick={() => setSelected(null)}>Close</button></div>{editorLoading ? <p role="status">Loading request details…</p> : requestDetails && <><p>{requestDetails.residentName} · {requestDetails.categoryName} · {requestDetails.quantity} {requestDetails.unit}</p><p>{requestDetails.address}</p>{requestDetails.notes && <p>{requestDetails.notes}</p>}<h3>Status history</h3>{requestDetails.history.length ? <ol className="timeline">{requestDetails.history.map((value, index) => <li key={`${value.createdAt}-${index}`}><span /><div><strong>{value.nextStatus}</strong><p>{value.reason}</p><small>{value.actorName}</small><time>{new Date(value.createdAt).toLocaleString()}</time></div></li>)}</ol> : <p>No status history recorded.</p>}</>}</section>}
      {section === "scheduling" && selected && <section key={selected.id} className="content-card operation-editor"><div className="section-heading"><h2>Manage {selected.publicCode}</h2><button className="button ghost" onClick={() => setSelected(null)}>Close</button></div><p>Times use your browser’s local timezone. Every change requires a reason.</p><div className="workspace-grid"><form className="form-stack" onSubmit={event => void submitSchedule(event)}><h3>{selected.status === "SCHEDULED" ? "Reschedule" : "Schedule"}</h3><label>Start<input name="start" type="datetime-local" defaultValue={localDateTime(selected.scheduledStart)} required /></label><label>End<input name="end" type="datetime-local" defaultValue={localDateTime(selected.scheduledEnd)} required /></label><label>Reason<input name="reason" required maxLength={500} /></label><button className="button primary" disabled={busy}>Save schedule</button></form>{selected.status === "SCHEDULED" && <form className="form-stack" onSubmit={event => void submitAssignment(event)}><h3>{selected.collectorId ? "Reassign collector" : "Assign collector"}</h3><label>Collector<select name="collector" defaultValue={selected.collectorId ?? ""} required><option value="" disabled>Select an active collector</option>{collectors.filter(value => value.status === "ACTIVE").map(value => <option key={value.id} value={value.id}>{value.displayName} · {value.activeAssignments} assignment(s)</option>)}</select></label><label>Reason<input name="reason" required maxLength={500} /></label><button className="button primary" disabled={busy || !collectors.some(value => value.status === "ACTIVE")}>Save assignment</button></form>}</div><form className="form-stack cancel-form" onSubmit={event => void cancelGroup(event)}><h3>Cancel this group</h3><p>Closes the assignment and returns all active requests to the pending queue.</p><label>Cancellation reason<input name="reason" required maxLength={500} /></label><button className="button danger" disabled={busy}>Cancel group</button></form><h3>Assignment history</h3>{editorLoading ? <p role="status">Loading assignment history…</p> : assignments.length ? <ul className="request-list">{assignments.map(value => <li key={value.id}><div><strong>{value.collectorName}</strong><span>{new Date(value.startsAt).toLocaleString()} – {new Date(value.endsAt).toLocaleString()}</span><span>{value.reason}</span></div><span className="status">{value.closedAt ? "CLOSED" : "ACTIVE"}</span></li>)}</ul> : <p>No assignments recorded yet.</p>}</section>}
      {section === "collectors" && <div className="workspace-grid"><section className="content-card"><h2>Collectors</h2>{!collectors.length ? <p>No collector accounts yet.</p> : <ul className="request-list">{collectors.map(value => <li key={value.id}><div><strong>{value.displayName}</strong><span>{value.email} · {value.activeAssignments} assignment(s)</span><span className="status">{value.status}</span></div><button className="button secondary" disabled={busy} onClick={() => void mutate(`/collectors/${value.id}/status`, "PATCH", { status: value.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE" }, "Collector status saved.")}>{value.status === "ACTIVE" ? "Suspend" : "Activate"}</button></li>)}</ul>}</section><section className="content-card"><h2>Create collector account</h2><form className="form-stack" onSubmit={event => void createCollector(event)}><label>Display name<input name="displayName" required maxLength={120} /></label><label>Email<input name="email" type="email" required /></label><label>Initial password<input name="temporaryPassword" type="password" required minLength={12} maxLength={128} autoComplete="new-password" /></label><button className="button primary" disabled={busy}>Create collector</button></form></section></div>}
      {section === "audit" && <section className="content-card">{!audit.items.length ? <p>No operational changes recorded.</p> : <ol className="timeline">{audit.items.map(value => <li key={value.id}><span /><div><strong>{value.action.replaceAll("_", " ")}</strong><p>{value.details}</p><small>{value.actorName} · {value.entityType} · {value.entityId}</small><time>{new Date(value.createdAt).toLocaleString()}</time></div></li>)}</ol>}<Pagination page={page} total={audit.total} onPage={setPage} /></section>}
      {section === "settings" && settings && <section className="content-card"><form className="form-stack" onSubmit={event => void saveSettings(event)}><label>Maximum requests per group<input name="maximum" type="number" min={1} max={500} required defaultValue={settings.maxGroupRequests} /></label><label>Minimum scheduling notice (hours)<input name="notice" type="number" min={0} max={168} required defaultValue={settings.minimumNoticeHours} /></label><p>Service timezone: {settings.serviceTimezone}. Collector windows cannot overlap; adjacent windows are allowed. Collection windows are limited to 24 hours.</p><button className="button primary" disabled={busy}>Save settings</button></form></section>}
    </>}
  </main>;
}
