import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { groupService } from "./groupService";

export default function GroupsPage() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);

  function load() {
    setLoading(true);
    groupService
      .listGroups()
      .then(setGroups)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleCreate(e) {
    e.preventDefault();
    setCreating(true);
    try {
      await groupService.createGroup({ name });
      setName("");
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setCreating(false);
    }
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Your groups</h1>
          <p>Every group you split expenses with.</p>
        </div>
        <button className="btn" onClick={() => setShowForm((s) => !s)}>
          {showForm ? "Cancel" : "New group"}
        </button>
      </div>
      {showForm && (
        <div className="card" style={{ marginBottom: 20, maxWidth: 420 }}>
          <form onSubmit={handleCreate}>
            <div className="field">
              <label htmlFor="group-name">Group name</label>
              <input
                id="group-name"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Goa Trip"
              />
            </div>
            <button className="btn" type="submit" disabled={creating}>
              {creating ? "Creating…" : "Create group"}
            </button>
          </form>
        </div>
      )}
      {error && <p className="error-text">{error}</p>}
      {loading ? (
        <p>Loading…</p>
      ) : groups.length === 0 ? (
        <div className="empty-state">
          <p>No groups yet. Create one to start splitting expenses.</p>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 14 }}>
          {groups.map((g) => (
            <Link key={g.id} to={`/groups/${g.id}`} style={{ textDecoration: "none" }}>
              <div className="card">
                <h3 style={{ fontSize: 17 }}>{g.name}</h3>
                <p style={{ color: "var(--ink-soft)", fontSize: 13, margin: 0 }}>
                  {g.memberCount ?? g.members?.length ?? 0} members
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
