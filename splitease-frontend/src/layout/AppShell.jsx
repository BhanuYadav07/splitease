import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../modules/auth/AuthContext";

export default function AppShell({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          Split<span>Ease</span>
        </div>
        <nav>
          <div className="module-label">GROUP</div>
          <NavLink to="/groups" end className={({ isActive }) => (isActive ? "active" : "")}>
            Your groups
          </NavLink>

          <div className="module-label">USER</div>
          <NavLink to="/profile" className={({ isActive }) => (isActive ? "active" : "")}>
            Profile
          </NavLink>
        </nav>
        <div className="user-box">
          <div style={{ marginBottom: 6 }}>{user?.name || "Signed in"}</div>
          <button className="logout-btn" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
