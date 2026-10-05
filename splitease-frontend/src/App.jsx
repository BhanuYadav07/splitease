import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./modules/auth/AuthContext";
import ProtectedRoute from "./modules/auth/ProtectedRoute";
import LoginPage from "./modules/auth/LoginPage";
import RegisterPage from "./modules/auth/RegisterPage";
import ForgotPasswordPage from "./modules/auth/ForgotPasswordPage";
import VerifyEmailPage from "./modules/auth/VerifyEmailPage";
import ProfilePage from "./modules/user/ProfilePage";
import GroupsPage from "./modules/group/GroupsPage";
import GroupDetailPage from "./modules/group/GroupDetailPage";
import AppShell from "./layout/AppShell";

/**
 * Where a signed-in visitor belongs. An account that has not confirmed its email
 * yet only opens the verification step - sign-up is a gate, not a banner on top
 * of the dashboard, so finding the dashboard takes a correct 6-digit code.
 */
function homePath(isAuthenticated, user) {
  if (!isAuthenticated) return "/login";
  return user?.emailVerified ? "/groups" : "/verify-email";
}

export default function App() {
  const { isAuthenticated, user } = useAuth();
  const home = homePath(isAuthenticated, user);

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to={home} replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to={home} replace /> : <RegisterPage />}
      />
      <Route
        path="/forgot-password"
        element={isAuthenticated ? <Navigate to={home} replace /> : <ForgotPasswordPage />}
      />
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route
        path="/groups"
        element={
          <ProtectedRoute>
            <AppShell>
              <GroupsPage />
            </AppShell>
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:groupId"
        element={
          <ProtectedRoute>
            <AppShell>
              <GroupDetailPage />
            </AppShell>
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <AppShell>
              <ProfilePage />
            </AppShell>
          </ProtectedRoute>
        }
      />

      <Route path="/" element={<Navigate to={home} replace />} />
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  );
}
