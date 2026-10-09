import { FormEvent, useCallback, useEffect, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

type Tokens = { accessToken: string };
type Group = { id: string; publicCode: string; status: string; zoneName: string; preferredDate: string; scheduledStart?: string; scheduledEnd?: string; memberCount: number; completedCount: number };
type Request = { id: string; publicCode: string; status: string; address: string; quantity: number; unit: string; notes?: string; categoryName: string; attempts: number };
type GroupDetail = Group & { requests: Request[]; attempts: { requestId: string; publicCode: string; attemptNumber: number; outcome: string; reason: string; attemptedAt: string }[] };
type Dashboard = { today: string; assignedGroups: Group[]; completedRequests: number; failedRequests: number };

function readToken(): string | null {
  try { return (JSON.parse(sessionStorage.getItem("wastecollect.tokens") ?? "null") as Tokens | null)?.accessToken ?? null; }
  catch { return null; }
}

async function api<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  const response = await fetch(`${API}/collector${path}`, {
    method,
    headers: { Authorization: `******`, ...(body ? { "Content-Type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(payload.message ?? "Unable to complete the collector operation.");
  }
  return response.status === 204 ? undefined as T : await response.json() as T;
}

export default function CollectorWorkspace({ onLogout }: { onLogout: () => void }) {
  const token = readToken();
  const navigate = useNavigate();
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true); setError("");
    try { setDashboard(await api<Dashboard>("/dashboard")); }
    catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to load your worklist."); }
    finally { setLoading(false); }
  }, [token]);
  useEffect(() => { void load(); }, [load]);

  if (!token) return <Navigate to="/login" replace />;
  function logout() { sessionStorage.removeItem("wastecollect.tokens"); sessionStorage.removeItem("wastecollect.role"); onLogout(); navigate("/login", { replace: true }); }
  return <main className="workspace">
    <section className="workspace-heading">
      <div><p className="eyebrow">Collector workspace</p><h1>Today’s worklist.</h1><p className="lead">Only groups currently assigned to you are shown.</p></div>
      <button className="button ghost" onClick={logout}>Sign out</button>
    </section>
    {error && <p className="form-message error page-message" role="alert">{error}</p>}
    {loading ? <div className="loading-card" role="status"><span className="spinner dark" /> Loading your worklist…</div> : dashboard && <><section className="feature-strip collector-metrics">
      <article><span>Assigned groups</span><h2>{dashboard.assignedGroups.length}</h2><p>Scheduled and active collections</p></article>
      <article><span>Completed today</span><h2>{dashboard.completedRequests}</h2><p>Successful pickup attempts</p></article>
      <article><span>Needs retry</span><h2>{dashboard.failedRequests}</h2><p>Failed attempts recorded today</p></article>
    </section><section className="content-card collector-list"><div className="section-heading"><span className="step">01</span><div><h2>Assigned groups</h2><p>Open a group to start work and record each pickup outcome.</p></div></div>
      {dashboard.assignedGroups.length === 0 ? <div className="empty-state"><p>No assigned collections yet.</p><small>Your administrator will assign scheduled work here.</small></div> :
        <ul className="request-list">{dashboard.assignedGroups.map(group => <li key={group.id}><button className="collector-group-link" onClick={() => navigate(`/collector/groups/${group.id}`)}><strong>{group.publicCode}</strong><span>{group.zoneName} · {group.memberCount} pickups</span></button><div className="request-meta"><span className={`status ${group.status.toLowerCase()}`}>{group.status}</span><time>{group.preferredDate}</time></div></li>)}</ul>}
    </section></>}
  </main>;
}

export function CollectorGroup() {
  const token = readToken();
  const { id } = useParams();
  const navigate = useNavigate();
  const [group, setGroup] = useState<GroupDetail | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (id) try { setGroup(await api<GroupDetail>(`/groups/${id}`)); } catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to load this group."); }
  }, [id]);
  useEffect(() => { void load(); }, [load]);
  if (!token) return <Navigate to="/login" replace />;
  async function start() { if (!id) return; setBusy(true); setError(""); try { await api(`/groups/${id}/start`, "POST"); await load(); } catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to start work."); } finally { setBusy(false); } }
  async function submit(event: FormEvent<HTMLFormElement>, request: Request, outcome: "COMPLETED" | "FAILED") {
    event.preventDefault(); if (!id) return; setBusy(true); setError("");
    const form = new FormData(event.currentTarget);
    try { await api(`/groups/${id}/attempts`, "POST", { requestId: request.id, outcome, reason: String(form.get("reason") ?? "").trim(), retryAt: outcome === "FAILED" && form.get("retryAt") ? new Date(String(form.get("retryAt"))).toISOString() : null }); await load(); }
    catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to record this attempt."); } finally { setBusy(false); }
  }
  return <main className="info-page"><button className="back-link detail-back" onClick={() => navigate("/collector")}>← Back to worklist</button>{error && <p className="form-message error" role="alert">{error}</p>}{!group ? <div className="loading-card"><span className="spinner dark" /> Loading group…</div> : <><p className="eyebrow">Assigned collection</p><h1>{group.publicCode}</h1><p className="lead">{group.zoneName} · {group.preferredDate} · {group.status}</p>{group.status === "SCHEDULED" && <button className="button primary" disabled={busy} onClick={() => void start()}>Start work</button>}<section className="collector-pickups">{group.requests.map(request => <article className="content-card" key={request.id}><div className="section-heading"><div><h2>{request.categoryName} · {request.publicCode}</h2><p>{request.address} · {request.quantity} {request.unit.toLowerCase()}</p></div><span className={`status ${request.status.toLowerCase()}`}>{request.status}</span></div>{request.notes && <p>{request.notes}</p>}{request.status === "IN_PROGRESS" && <><form className="collector-attempt" onSubmit={event => void submit(event, request, "COMPLETED")}><label htmlFor={`reason-${request.id}`}>Attempt notes</label><input id={`reason-${request.id}`} name="reason" required maxLength={500} placeholder="e.g. Collected from front gate" /><div className="actions"><button className="button primary" disabled={busy}>Mark collected</button><button className="button secondary" type="button" disabled={busy} onClick={() => { const form = document.getElementById(`failure-${request.id}`) as HTMLFormElement | null; if (form) { form.querySelector<HTMLInputElement>("input[name=reason]")!.value = window.prompt("Why did the pickup fail?", "Resident unavailable") ?? ""; if (form.querySelector<HTMLInputElement>("input[name=reason]")!.value) form.requestSubmit(); } }}>Record failed attempt</button></div></form><form id={`failure-${request.id}`} className="hidden-form" onSubmit={event => void submit(event, request, "FAILED")}><input name="reason" required maxLength={500} defaultValue="Unable to complete pickup" /><input name="retryAt" type="datetime-local" /></form></>}</article>)}</section></>}</main>;
}
