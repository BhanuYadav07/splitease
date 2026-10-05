import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { userService } from "./userService";
import { useAuth } from "../auth/AuthContext";

export default function ProfilePage() {
  const [form, setForm] = useState({ name: "", email: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const { updateUser } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    userService
      .getProfile()
      .then((data) => setForm({ name: data?.name || "", email: data?.email || "" }))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError("");
    setSaved(false);
    try {
      // The response carries emailVerified, which the server resets to false when
      // the address changed. A new address has to be confirmed again, so that case
      // hands the user to the confirmation step instead of a profile page the
      // route guard no longer lets them see.
      const updated = await userService.updateProfile(form);
      updateUser(updated);
      if (updated?.emailVerified === false) {
        navigate("/verify-email", { replace: true, state: { emailChanged: true } });
        return;
      }
      setSaved(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Profile</h1>
          <p>Your account details.</p>
        </div>
      </div>
      <div className="card" style={{ maxWidth: 440 }}>
        {loading ? (
          <p>Loading…</p>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="field">
              <label htmlFor="name">Full name</label>
              <input
                id="name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </div>
            {error && <p className="error-text">{error}</p>}
            {saved && <p style={{ color: "var(--receive)", fontSize: 13 }}>Profile updated.</p>}
            <button className="btn" type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save changes"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
