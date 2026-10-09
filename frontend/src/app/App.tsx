import { Link, Route, Routes } from "react-router-dom";

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
        <Route path="/login" element={<Placeholder title="Sign in" description="Authentication is introduced in milestone M2." />} />
        <Route path="*" element={<Placeholder title="Page not found" description="The requested page does not exist." />} />
      </Routes>
    </div>
  );
}
