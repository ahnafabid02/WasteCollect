import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link, NavLink, Navigate, Route, Routes, useNavigate } from "react-router-dom";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";
const TOKEN_KEY = "wastecollect.tokens";

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
};

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

function Login({ onLogin }: { onLogin: () => void }) {
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
      onLogin();
      navigate("/account", { replace: true });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to sign in. Please try again.");
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
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [zones, setZones] = useState<Zone[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [requests, setRequests] = useState<Pickup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const logout = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY);
    onLogout();
    navigate("/login", { replace: true });
  }, [navigate, onLogout]);

  const loadWorkspace = useCallback(async () => {
    if (!tokens) return;
    setLoading(true);
    setError("");
    try {
      const authHeaders = { Authorization: `Bearer ${tokens.accessToken}` };
      const [profileResponse, zonesResponse, categoriesResponse, requestsResponse] = await Promise.all([
        fetch(`${API_URL}/users/me`, { headers: authHeaders }),
        fetch(`${API_URL}/zones`),
        fetch(`${API_URL}/waste-categories`),
        fetch(`${API_URL}/requests/my`, { headers: authHeaders }),
      ]);
      if (profileResponse.status === 401 || profileResponse.status === 403) {
        logout();
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
  }, [logout, tokens]);

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
        <button className="button ghost" type="button" onClick={logout}>Sign out</button>
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
                  <div><strong>{request.categoryName}</strong><span>{request.zoneName} · {request.quantity} {request.unit.toLowerCase()}</span></div>
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
  const [authenticated, setAuthenticated] = useState(() => Boolean(readTokens()));
  return <div className="app-shell">
    <header className="topbar"><Link className="brand" to="/"><span aria-hidden="true">♻</span> WasteCollect</Link><nav aria-label="Primary navigation"><NavLink to="/how-it-works">How it works</NavLink><NavLink to="/waste-information">Waste guide</NavLink><NavLink className="nav-cta" to={authenticated ? "/account" : "/login"}>{authenticated ? "My account" : "Sign in"}</NavLink></nav></header>
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/how-it-works" element={<InfoPage eyebrow="A simple three-step service" title="From request to collection." description="A clear workflow keeps residents informed and collections organized."><article><span>01</span><h2>Submit your request</h2><p>Choose a category, service zone, date, and collection address.</p></article><article><span>02</span><h2>We coordinate</h2><p>Your request enters the collection queue for scheduling.</p></article><article><span>03</span><h2>Waste is collected</h2><p>Track the request from your resident workspace.</p></article></InfoPage>} />
      <Route path="/waste-information" element={<InfoPage eyebrow="Sort smarter" title="Know what goes where." description="Select the matching category when you create a pickup request."><article><span className="category-icon">●</span><h2>General waste</h2><p>Everyday non-recyclable household items. Measured by bag.</p></article><article><span className="category-icon mint">●</span><h2>Recyclables</h2><p>Clean paper, plastic, glass, and metal. Measured by bag.</p></article><article><span className="category-icon gold">●</span><h2>Organic waste</h2><p>Food scraps and compostable material. Measured in kilograms.</p></article></InfoPage>} />
      <Route path="/login" element={authenticated ? <Navigate to="/account" replace /> : <Login onLogin={() => setAuthenticated(true)} />} />
      <Route path="/account" element={<Account onLogout={() => setAuthenticated(false)} />} />
      <Route path="*" element={<main className="not-found"><p className="eyebrow">404</p><h1>That page wandered off.</h1><p className="lead">Let’s get you back to a cleaner route.</p><Link className="button primary" to="/">Return home</Link></main>} />
    </Routes>
    <footer><span>© 2026 WasteCollect</span><span>Cleaner neighborhoods, one pickup at a time.</span></footer>
  </div>;
}
