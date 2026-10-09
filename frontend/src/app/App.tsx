import { FormEvent, useState } from "react";
import { Link, Navigate, Route, Routes, useNavigate } from "react-router-dom";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const response = await fetch(`${API_URL}/auth/login`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      if (!response.ok) throw new Error("Invalid email or password.");
      const tokens = await response.json();
      sessionStorage.setItem("wastecollect.tokens", JSON.stringify(tokens));
      navigate("/account");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to sign in.");
    } finally { setBusy(false); }
  }
  return <main className="panel">
    <p className="eyebrow">Secure access</p>
    <h1>Sign in</h1>
    <form className="auth-form" onSubmit={submit}>
      <label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
      <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
      {error && <p role="alert">{error}</p>}
      <button className="button primary" disabled={busy}>{busy ? "Signing in..." : "Sign in"}</button>
    </form>
  </main>;
}

function Account() {
  const tokens = sessionStorage.getItem("wastecollect.tokens");
  if (!tokens) return <Navigate to="/login" replace />;
  return <main className="panel"><p className="eyebrow">Resident workspace</p><h1>Request a pickup</h1>
    <p className="lead">Choose a waste category, service zone, and preferred date.</p>
    <PickupForm />
    <button className="button secondary" onClick={() => { sessionStorage.removeItem("wastecollect.tokens"); window.location.href = "/"; }}>Sign out</button>
  </main>;
}

function PickupForm() {
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage("");
    const form = new FormData(event.currentTarget);
    const tokens = JSON.parse(sessionStorage.getItem("wastecollect.tokens") ?? "{}");
    try {
      const response = await fetch(`${API_URL}/requests`, {
        method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${tokens.accessToken}` },
        body: JSON.stringify({ zoneId: form.get("zoneId"), categoryId: form.get("categoryId"), address: form.get("address"),
          quantity: Number(form.get("quantity")), unit: form.get("unit"), preferredDate: form.get("preferredDate"), notes: form.get("notes") }),
      });
      if (!response.ok) throw new Error("Unable to create pickup request.");
      const request = await response.json();
      setMessage(`Request ${request.publicCode} created successfully.`);
      event.currentTarget.reset();
    } catch (exception) { setMessage(exception instanceof Error ? exception.message : "Unable to create request."); }
    finally { setBusy(false); }
  }
  return <form className="auth-form" onSubmit={submit}>
    <label>Service zone<select name="zoneId" required><option value="">Select a zone</option><option value="00000000-0000-0000-0000-000000000001">Central Zone</option><option value="00000000-0000-0000-0000-000000000002">North Zone</option></select></label>
    <label>Waste category<select name="categoryId" required><option value="">Select a category</option><option value="00000000-0000-0000-0000-000000000001">General waste</option><option value="00000000-0000-0000-0000-000000000002">Recyclables</option><option value="00000000-0000-0000-0000-000000000003">Organic waste</option></select></label>
    <label>Address<input name="address" maxLength={300} required /></label>
    <label>Quantity<input name="quantity" type="number" min="0.01" step="0.01" required /></label>
    <label>Unit<select name="unit" required><option value="BAG">Bag</option><option value="KG">Kilogram</option></select></label>
    <label>Preferred date<input name="preferredDate" type="date" min={new Date().toISOString().slice(0, 10)} required /></label>
    <label>Notes<textarea name="notes" maxLength={1000} /></label>
    {message && <p role="status">{message}</p>}
    <button className="button primary" disabled={busy}>{busy ? "Submitting..." : "Request pickup"}</button>
  </form>;
}

function Home() {
  return (
    <main className="hero">
      <p className="eyebrow">Waste collection management</p>
      <h1>Cleaner collections, coordinated simply.</h1>
      <p className="lead">
        WasteCollect connects residents, administrators, and collectors through one
        reliable workflow.
      </p>
      <div className="actions">
        <Link className="button primary" to="/how-it-works">How it works</Link>
        <Link className="button secondary" to="/login">Sign in</Link>
      </div>
    </main>
  );
}

function Placeholder({ title, description }: { title: string; description: string }) {
  return (
    <main className="panel">
      <p className="eyebrow">Foundation route</p>
      <h1>{title}</h1>
      <p className="lead">{description}</p>
    </main>
  );
}

export default function App() {
  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/">WasteCollect</Link>
        <nav aria-label="Primary navigation">
          <Link to="/how-it-works">How it works</Link>
          <Link to="/waste-information">Waste information</Link>
          <Link to="/login">Login</Link>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/how-it-works" element={<Placeholder title="How it works" description="Residents request pickups, administrators organize collections, and collectors record outcomes." />} />
        <Route path="/waste-information" element={<Placeholder title="Waste information" description="Category guidance will be connected to the waste catalog in a later vertical slice." />} />
        <Route path="/login" element={<Login />} />
        <Route path="/account" element={<Account />} />
        <Route path="*" element={<Placeholder title="Page not found" description="The requested page does not exist." />} />
      </Routes>
    </div>
  );
}
