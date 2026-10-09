import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link, NavLink, Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import AdminOperations, { AdminNav } from "../features/admin/AdminOperations";
import CollectorWorkspace, { CollectorGroup } from "../features/collector/CollectorWorkspace";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";
const TOKEN_KEY = "wastecollect.tokens";
const ROLE_KEY = "wastecollect.role";

type Tokens = { accessToken: string; refreshToken: string };
type UserProfile = { id: string; email: string; displayName: string; role: string; status: string };
type Zone = { id: string; code: string; name: string };
type Category = { id: string; code: string; name: string; allowedUnit: string };
type Pickup = {
  id: string;
  publicCode: string;
  zoneName: string;
  categoryName: string;
  quantity: number;
  unit: string;
  preferredDate: string;
  status: string;
  address?: string;
  notes?: string | null;
};
type HistoryItem = { previousStatus: string | null; nextStatus: string; reason: string; createdAt: string };
type Candidate = { id: string; publicCode: string; categoryName: string; address: string; quantity: number; unit: string };
type GroupSuggestion = { zoneId: string; zoneName: string; preferredDate: string; requests: Candidate[] };
type CollectionGroup = GroupSuggestion & { id: string; publicCode: string; status: string };

function readTokens(): Tokens | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(TOKEN_KEY) ?? "null") as Partial<Tokens> | null;
    return value && typeof value.accessToken === "string" && typeof value.refreshToken === "string"
      ? { accessToken: value.accessToken, refreshToken: value.refreshToken }
      : null;
  } catch {
    sessionStorage.removeItem(TOKEN_KEY);
    return null;
  }
}

async function revokeSession(onLogout: () => void, navigate: ReturnType<typeof useNavigate>) {
  const tokens = readTokens();
  try {
    if (tokens?.refreshToken) {
      await fetch(`${API_URL}/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
        signal: AbortSignal.timeout(10000),
      });
    }
  } catch {
    // Clear the local session even when the service cannot be reached.
  } finally {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(ROLE_KEY);
    onLogout();
    navigate("/login", { replace: true });
  }
}

async function apiError(response: Response, fallback: string) {
  try {
    const payload = (await response.json()) as { message?: string };
    return payload.message || fallback;
  } catch {
    return fallback;
  }
}

function localToday() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function Login({ onLogin }: { onLogin: (role: string) => void }) {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`${API_URL}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), password }),
      });
      if (!response.ok) throw new Error(await apiError(response, "Invalid email or password."));
      const tokens = (await response.json()) as Tokens;
      if (!tokens.accessToken || !tokens.refreshToken) throw new Error("The server returned an invalid sign-in response.");
      sessionStorage.setItem(TOKEN_KEY, JSON.stringify(tokens));
      const profileResponse = await fetch(`${API_URL}/users/me`, { headers: { Authorization: `Bearer ${tokens.accessToken}` } });
      if (!profileResponse.ok) throw new Error("Unable to load your account.");
      const profile = (await profileResponse.json()) as UserProfile;
      sessionStorage.setItem(ROLE_KEY, profile.role);
      onLogin(profile.role);
      navigate(profile.role === "ADMIN" ? "/admin" : profile.role === "COLLECTOR" ? "/collector" : "/account", { replace: true });
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "";
      setError(message === "Failed to fetch"
        ? "The service is unreachable. Make sure the backend is running, then try again."
        : message || "Unable to sign in. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-intro" aria-labelledby="login-title">
        <p className="eyebrow">Resident portal</p>
        <h1 id="login-title">Welcome back.</h1>
        <p className="lead">Sign in to request a pickup and keep track of your collection schedule.</p>
        <div className="trust-note"><span aria-hidden="true">✓</span> Secure, password-protected access</div>
      </section>
      <section className="auth-card" aria-label="Sign in form">
        <div>
          <p className="eyebrow">Secure access</p>
          <h2>Sign in</h2>
        </div>
        <form className="form-stack" onSubmit={submit}>
          <label htmlFor="login-email">Email address</label>
          <input id="login-email" name="email" type="email" autoComplete="email" inputMode="email"
            value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" required autoFocus />
          <label htmlFor="login-password">Password</label>
          <input id="login-password" name="password" type="password" autoComplete="current-password"
            value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" required />
          {error && <p className="form-message error" role="alert">{error}</p>}
          <button className="button primary full" type="submit" disabled={busy}>
            {busy ? <><span className="spinner" aria-hidden="true" /> Signing in…</> : "Sign in"}
          </button>
        </form>
        <Link className="back-link" to="/">← Back to home</Link>
      </section>
    </main>
  );
}

function Account({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate();
  const tokens = readTokens();
  const accessToken = tokens?.accessToken;
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [zones, setZones] = useState<Zone[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [requests, setRequests] = useState<Pickup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const logout = useCallback(() => revokeSession(onLogout, navigate), [navigate, onLogout]);

  const loadWorkspace = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError("");
    try {
      const authHeaders = { Authorization: `Bearer ${accessToken}` };
      const [profileResponse, zonesResponse, categoriesResponse, requestsResponse] = await Promise.all([
        fetch(`${API_URL}/users/me`, { headers: authHeaders }),
        fetch(`${API_URL}/zones`),
        fetch(`${API_URL}/waste-categories`),
        fetch(`${API_URL}/requests/my`, { headers: authHeaders }),
      ]);
      if (profileResponse.status === 401 || profileResponse.status === 403) {
        void logout();
        return;
      }
      if (![profileResponse, zonesResponse, categoriesResponse, requestsResponse].every((response) => response.ok)) {
        throw new Error("We could not load your workspace. Please refresh and try again.");
      }
      const [nextProfile, nextZones, nextCategories, nextRequests] = await Promise.all([
        profileResponse.json(), zonesResponse.json(), categoriesResponse.json(), requestsResponse.json(),
      ]);
      setProfile(nextProfile);
      setZones(nextZones);
      setCategories(nextCategories);
      setRequests(nextRequests);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to load your workspace.");
    } finally {
      setLoading(false);
    }
  }, [logout, accessToken]);

  useEffect(() => { void loadWorkspace(); }, [loadWorkspace]);

  if (!tokens) return <Navigate to="/login" replace />;

  return (
    <main className="workspace">
      <section className="workspace-heading">
        <div>
          <p className="eyebrow">Resident workspace</p>
          <h1>{profile ? `Hello, ${profile.displayName}.` : "Your pickups"}</h1>
          <p className="lead">Schedule a collection and review your recent requests.</p>
        </div>
        <button className="button ghost" type="button" onClick={() => void logout()}>Sign out</button>
      </section>
      {error && <p className="form-message error page-message" role="alert">{error}</p>}
      {loading ? <div className="loading-card" role="status"><span className="spinner dark" /> Loading your workspace…</div> : (
        <div className="workspace-grid">
          <section className="content-card">
            <div className="section-heading"><span className="step">01</span><div><h2>Request a pickup</h2><p>Tell us what needs collecting.</p></div></div>
            <PickupForm zones={zones} categories={categories} token={tokens.accessToken} onCreated={loadWorkspace} />
          </section>
          <section className="content-card requests-card">
            <div className="section-heading"><span className="step">02</span><div><h2>Recent requests</h2><p>Your latest collection activity.</p></div></div>
            {requests.length === 0 ? (
              <div className="empty-state"><span aria-hidden="true">♻</span><p>No pickup requests yet.</p><small>Your first request will appear here.</small></div>
            ) : (
              <ul className="request-list">
                {requests.map((request) => <li key={request.id}>
                  <Link to={`/requests/${request.id}`}><strong>{request.categoryName}</strong><span>{request.zoneName} · {request.quantity} {request.unit.toLowerCase()}</span></Link>
                  <div className="request-meta"><span className={`status ${request.status.toLowerCase()}`}>{request.status}</span><time>{request.preferredDate}</time></div>
                </li>)}
              </ul>
            )}
          </section>
        </div>
      )}
    </main>
  );
}

function RequestDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const tokens = readTokens();
  const accessToken = tokens?.accessToken;
  const [request, setRequest] = useState<Pickup | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    if (!accessToken || !id) return;
    const headers = { Authorization: `Bearer ${accessToken}` };
    try {
      const [requestResponse, historyResponse] = await Promise.all([fetch(`${API_URL}/requests/${id}`, { headers }), fetch(`${API_URL}/requests/${id}/history`, { headers })]);
      if (!requestResponse.ok || !historyResponse.ok) throw new Error(await apiError(requestResponse, "Unable to load request."));
      setRequest(await requestResponse.json()); setHistory(await historyResponse.json());
    } catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to load request."); }
  }, [accessToken, id]);
  useEffect(() => { void load(); }, [load]);
  if (!tokens) return <Navigate to="/login" replace />;
  async function cancel() {
    if (!request || !accessToken) return; setBusy(true); setError("");
    try {
      const response = await fetch(`${API_URL}/requests/${request.id}/cancel`, { method: "PATCH", headers: { Authorization: `Bearer ${accessToken}` } });
      if (!response.ok) throw new Error(await apiError(response, "Unable to cancel request."));
      await load();
    } catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to cancel request."); }
    finally { setBusy(false); }
  }
  return <main className="info-page"><button className="back-link detail-back" onClick={() => navigate("/account")}>← Back to workspace</button>{error && <p className="form-message error">{error}</p>}{!request ? <div className="loading-card"><span className="spinner dark" /> Loading request…</div> : <><p className="eyebrow">Pickup request</p><h1>{request.publicCode}</h1><div className="detail-grid"><section className="content-card"><h2>Collection details</h2><dl><div><dt>Status</dt><dd><span className="status">{request.status}</span></dd></div><div><dt>Waste</dt><dd>{request.categoryName} · {request.quantity} {request.unit.toLowerCase()}</dd></div><div><dt>Zone and date</dt><dd>{request.zoneName} · {request.preferredDate}</dd></div><div><dt>Address</dt><dd>{request.address}</dd></div>{request.notes && <div><dt>Notes</dt><dd>{request.notes}</dd></div>}</dl>{request.status === "PENDING" && <button className="button danger" disabled={busy} onClick={() => void cancel()}>{busy ? "Cancelling…" : "Cancel request"}</button>}</section><section className="content-card"><h2>Status history</h2><ol className="timeline">{history.map((item,index) => <li key={`${item.createdAt}-${index}`}><span /><div><strong>{item.nextStatus}</strong><p>{item.reason}</p><time>{new Date(item.createdAt).toLocaleString()}</time></div></li>)}</ol></section></div></>}</main>;
}

function Register() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch(`${API_URL}/auth/register`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ displayName: form.get("displayName"), email: form.get("email"), password: form.get("password") }) });
      if (!response.ok) throw new Error(await apiError(response, "Unable to create account."));
      navigate("/login", { replace: true });
    } catch (exception) { setError(exception instanceof Error ? exception.message : "Unable to create account."); }
    finally { setBusy(false); }
  }
  return <main className="auth-layout"><section className="auth-intro"><p className="eyebrow">Join WasteCollect</p><h1>Create your resident account.</h1><p className="lead">Request collections and follow their progress from one place.</p></section><section className="auth-card"><div><p className="eyebrow">Resident registration</p><h2>Create account</h2></div><form className="form-stack" onSubmit={submit}><label htmlFor="register-name">Display name</label><input id="register-name" name="displayName" autoComplete="name" maxLength={120} required /><label htmlFor="register-email">Email address</label><input id="register-email" name="email" type="email" autoComplete="email" required /><label htmlFor="register-password">Password</label><input id="register-password" name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required /><small className="field-help">Use at least 12 characters.</small>{error && <p className="form-message error" role="alert">{error}</p>}<button className="button primary full" disabled={busy}>{busy ? "Creating…" : "Create account"}</button></form><Link className="back-link" to="/login">Already registered? Sign in</Link></section></main>;
}

function AdminGroups({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate();
  const tokens = readTokens();
  const accessToken = tokens?.accessToken;
  const [suggestions, setSuggestions] = useState<GroupSuggestion[]>([]);
  const [groups, setGroups] = useState<CollectionGroup[]>([]);
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyKey, setBusyKey] = useState("");
  const logout = useCallback(() => revokeSession(onLogout, navigate), [navigate, onLogout]);
  const load = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true); setMessage("");
    const headers = { Authorization: `Bearer ${accessToken}` };
    try {
      const [suggestionsResponse, groupsResponse] = await Promise.all([fetch(`${API_URL}/admin/groups/suggestions`, { method: "POST", headers }), fetch(`${API_URL}/admin/groups`, { headers })]);
      if (suggestionsResponse.status === 401 || suggestionsResponse.status === 403) { void logout(); return; }
      if (!suggestionsResponse.ok || !groupsResponse.ok) throw new Error("Unable to load grouping workspace.");
      const nextSuggestions = await suggestionsResponse.json() as GroupSuggestion[];
      setSuggestions(nextSuggestions); setGroups(await groupsResponse.json());
      setSelected(Object.fromEntries(nextSuggestions.flatMap(suggestion => suggestion.requests.map(request => [request.id, true]))));
    } catch (exception) { setMessage(exception instanceof Error ? exception.message : "Unable to load grouping workspace."); }
    finally { setLoading(false); }
  }, [accessToken, logout]);
  useEffect(() => { void load(); }, [load]);
  if (!tokens || sessionStorage.getItem(ROLE_KEY) !== "ADMIN") return <Navigate to="/login" replace />;
  async function confirm(suggestion: GroupSuggestion) {
    if (!accessToken) return;
    const requestIds = suggestion.requests.filter(request => selected[request.id]).map(request => request.id);
    if (!requestIds.length) { setMessage("Select at least one request for the group."); return; }
    setBusyKey(`${suggestion.zoneId}:${suggestion.preferredDate}`); setMessage("");
    try {
      const response = await fetch(`${API_URL}/admin/groups`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` }, body: JSON.stringify({ zoneId: suggestion.zoneId, preferredDate: suggestion.preferredDate, requestIds }) });
      if (!response.ok) throw new Error(await apiError(response, "Unable to confirm group."));
      const group = await response.json() as CollectionGroup; setMessage(`${group.publicCode} confirmed with ${group.requests.length} request(s).`); await load();
    } catch (exception) { setMessage(exception instanceof Error ? exception.message : "Unable to confirm group."); }
    finally { setBusyKey(""); }
  }
  return <main className="workspace"><AdminNav /><section className="workspace-heading"><div><p className="eyebrow">Administrator workspace</p><h1>Grouping review</h1><p className="lead">Review zone-and-date suggestions, adjust membership, and confirm atomically.</p></div><button className="button ghost" onClick={() => void logout()}>Sign out</button></section>{message && <p className="form-message success page-message" role="status">{message}</p>}{loading ? <div className="loading-card"><span className="spinner dark" /> Loading grouping candidates…</div> : <div className="workspace-grid"><section className="content-card"><div className="section-heading"><span className="step">01</span><div><h2>Draft suggestions</h2><p>Suggestions do not change stored requests.</p></div></div>{suggestions.length === 0 ? <div className="empty-state"><span>✓</span><p>No eligible requests</p><small>Pending requests will appear by zone and date.</small></div> : <div className="suggestion-list">{suggestions.map(suggestion => { const key=`${suggestion.zoneId}:${suggestion.preferredDate}`; return <article key={key} className="suggestion"><header><div><strong>{suggestion.zoneName}</strong><span>{suggestion.preferredDate}</span></div><span>{suggestion.requests.length} request(s)</span></header>{suggestion.requests.map(request => <label className="candidate" key={request.id}><input type="checkbox" checked={Boolean(selected[request.id])} onChange={event => setSelected(current => ({...current,[request.id]:event.target.checked}))} /><span><strong>{request.publicCode} · {request.categoryName}</strong><small>{request.address} · {request.quantity} {request.unit.toLowerCase()}</small></span></label>)}<button className="button primary full" disabled={busyKey===key} onClick={() => void confirm(suggestion)}>{busyKey===key ? "Confirming…" : "Confirm selected group"}</button></article>})}</div>}</section><section className="content-card"><div className="section-heading"><span className="step">02</span><div><h2>Confirmed groups</h2><p>Committed memberships and status.</p></div></div>{groups.length===0 ? <div className="empty-state"><span>♻</span><p>No confirmed groups yet</p></div> : <ul className="request-list">{groups.map(group => <li key={group.id}><div><strong>{group.publicCode}</strong><span>{group.zoneName} · {group.requests.length} stop(s)</span></div><div className="request-meta"><span className="status">{group.status}</span><time>{group.preferredDate}</time></div></li>)}</ul>}</section></div>}</main>;
}

function PickupForm({ zones, categories, token, onCreated }: { zones: Zone[]; categories: Category[]; token: string; onCreated: () => Promise<void> }) {
  const [categoryId, setCategoryId] = useState("");
  const [message, setMessage] = useState("");
  const [isError, setIsError] = useState(false);
  const [busy, setBusy] = useState(false);
  const selectedCategory = useMemo(() => categories.find((category) => category.id === categoryId), [categories, categoryId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !selectedCategory) return;
    setBusy(true);
    setMessage("");
    setIsError(false);
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const response = await fetch(`${API_URL}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          zoneId: form.get("zoneId"), categoryId, address: form.get("address"),
          quantity: Number(form.get("quantity")), unit: selectedCategory.allowedUnit,
          preferredDate: form.get("preferredDate"), notes: form.get("notes") || null,
        }),
      });
      if (!response.ok) throw new Error(await apiError(response, "Unable to create pickup request."));
      const request = (await response.json()) as Pickup;
      setMessage(`Request ${request.publicCode} was created successfully.`);
      formElement.reset();
      setCategoryId("");
      await onCreated();
    } catch (exception) {
      setIsError(true);
      setMessage(exception instanceof Error ? exception.message : "Unable to create request.");
    } finally {
      setBusy(false);
    }
  }

  return <form className="pickup-form" onSubmit={submit}>
    <div className="field-row">
      <label>Service zone<select name="zoneId" required defaultValue=""><option value="" disabled>Select a zone</option>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>
      <label>Waste category<select name="categoryId" required value={categoryId} onChange={(event) => setCategoryId(event.target.value)}><option value="" disabled>Select a category</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
    </div>
    <label>Collection address<input name="address" autoComplete="street-address" maxLength={300} placeholder="House, road, and area" required /></label>
    <div className="field-row quantity-row">
      <label>Quantity<input name="quantity" type="number" inputMode="decimal" min="0.01" max="99999999.99" step="0.01" placeholder="0.00" required /></label>
      <label>Unit<input value={selectedCategory?.allowedUnit === "KG" ? "Kilogram (kg)" : selectedCategory ? "Bag" : "Select a category first"} readOnly aria-readonly="true" /></label>
    </div>
    <label>Preferred date<input name="preferredDate" type="date" min={localToday()} required /></label>
    <label>Notes <span className="optional">Optional</span><textarea name="notes" maxLength={1000} rows={3} placeholder="Access instructions or anything the collector should know" /></label>
    {message && <p className={`form-message ${isError ? "error" : "success"}`} role={isError ? "alert" : "status"}>{message}</p>}
    <button className="button primary full" type="submit" disabled={busy || zones.length === 0 || categories.length === 0}>{busy ? "Submitting…" : "Request pickup"}</button>
  </form>;
}

function Home() {
  return <main className="home">
    <section className="hero">
      <div className="hero-copy"><p className="eyebrow">Waste collection, simplified</p><h1>Cleaner streets start with a better pickup.</h1><p className="lead">Schedule household waste collection in minutes and keep every request in one reliable place.</p><div className="actions"><Link className="button primary" to="/login">Request a pickup</Link><Link className="button secondary" to="/how-it-works">See how it works</Link></div><div className="hero-proof"><span><strong>3</strong> waste types</span><span><strong>2</strong> service zones</span><span><strong>24/7</strong> requests</span></div></div>
      <div className="hero-visual" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="eco-mark">♻</div><div className="pickup-chip"><span>✓</span><div><strong>Pickup requested</strong><small>We’ll take it from here</small></div></div></div>
    </section>
    <section className="feature-strip" aria-label="Service benefits"><article><span>01</span><h2>Choose</h2><p>Select your zone and waste type.</p></article><article><span>02</span><h2>Schedule</h2><p>Pick a convenient collection date.</p></article><article><span>03</span><h2>Track</h2><p>See each request in your workspace.</p></article></section>
  </main>;
}

function InfoPage({ eyebrow, title, description, children }: { eyebrow: string; title: string; description: string; children: React.ReactNode }) {
  return <main className="info-page"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="lead">{description}</p><div className="info-grid">{children}</div></main>;
}

export default function App() {
  const navigate = useNavigate();
  const [role, setRole] = useState(() => sessionStorage.getItem(ROLE_KEY));
  const handleLogout = useCallback(() => setRole(null), []);
  const adminLogout = useCallback(() => revokeSession(handleLogout, navigate), [handleLogout, navigate]);
  const authenticated = Boolean(readTokens() && role);
  return <div className="app-shell">
    <header className="topbar"><Link className="brand" to="/"><span aria-hidden="true">♻</span> WasteCollect</Link><nav aria-label="Primary navigation"><NavLink to="/how-it-works">How it works</NavLink><NavLink to="/waste-information">Waste guide</NavLink>{!authenticated && <NavLink to="/register">Register</NavLink>}<NavLink className="nav-cta" to={role === "ADMIN" ? "/admin" : role === "COLLECTOR" ? "/collector" : authenticated ? "/account" : "/login"}>{authenticated ? "My workspace" : "Sign in"}</NavLink></nav></header>
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/how-it-works" element={<InfoPage eyebrow="A simple three-step service" title="From request to collection." description="A clear workflow keeps residents informed and collections organized."><article><span>01</span><h2>Submit your request</h2><p>Choose a category, service zone, date, and collection address.</p></article><article><span>02</span><h2>We coordinate</h2><p>Your request enters the collection queue for scheduling.</p></article><article><span>03</span><h2>Waste is collected</h2><p>Track the request from your resident workspace.</p></article></InfoPage>} />
      <Route path="/waste-information" element={<InfoPage eyebrow="Sort smarter" title="Know what goes where." description="Select the matching category when you create a pickup request."><article><span className="category-icon">●</span><h2>General waste</h2><p>Everyday non-recyclable household items. Measured by bag.</p></article><article><span className="category-icon mint">●</span><h2>Recyclables</h2><p>Clean paper, plastic, glass, and metal. Measured by bag.</p></article><article><span className="category-icon gold">●</span><h2>Organic waste</h2><p>Food scraps and compostable material. Measured in kilograms.</p></article></InfoPage>} />
      <Route path="/register" element={authenticated ? <Navigate to={role === "ADMIN" ? "/admin" : role === "COLLECTOR" ? "/collector" : "/account"} replace /> : <Register />} />
      <Route path="/login" element={authenticated ? <Navigate to={role === "ADMIN" ? "/admin" : role === "COLLECTOR" ? "/collector" : "/account"} replace /> : <Login onLogin={nextRole => setRole(nextRole)} />} />
      <Route path="/account" element={<Account onLogout={handleLogout} />} />
      <Route path="/collector" element={role === "COLLECTOR" ? <CollectorWorkspace onLogout={handleLogout} /> : <Navigate to="/login" replace />} />
      <Route path="/collector/groups/:id" element={role === "COLLECTOR" ? <CollectorGroup /> : <Navigate to="/login" replace />} />
      <Route path="/requests/:id" element={<RequestDetail />} />
      <Route path="/admin/groups" element={<AdminGroups onLogout={handleLogout} />} />
      {["/admin", "/admin/requests", "/admin/scheduling", "/admin/collectors", "/admin/audit", "/admin/settings"].map(path => <Route key={path} path={path} element={<AdminOperations key={path} onLogout={adminLogout} />} />)}
      <Route path="*" element={<main className="not-found"><p className="eyebrow">404</p><h1>That page wandered off.</h1><p className="lead">Let’s get you back to a cleaner route.</p><Link className="button primary" to="/">Return home</Link></main>} />
    </Routes>
    <footer><span>© 2026 WasteCollect</span><span>Cleaner neighborhoods, one pickup at a time.</span></footer>
  </div>;
}
