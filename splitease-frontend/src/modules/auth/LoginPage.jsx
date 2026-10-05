import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await login(form);
      // An account that never finished sign-up goes back to the confirmation
      // step - the dashboard would only bounce it there anyway.
      navigate(data?.user?.emailVerified ? "/groups" : "/verify-email");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <h1>
          Split<span style={{ color: "var(--accent)" }}>Ease</span>
        </h1>
        <p style={{ color: "var(--ink-soft)", marginTop: 0, marginBottom: 24, fontSize: 14 }}>
          Log in to settle up with your groups.
        </p>
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              required
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button className="btn" type="submit" disabled={loading} style={{ width: "100%" }}>
            {loading ? "Logging in…" : "Log in"}
          </button>
        </form>
        <p className="switch">
          Forgot your password? <Link to="/forgot-password">Send yourself a code</Link>
        </p>
        <p className="switch">
          New here? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
