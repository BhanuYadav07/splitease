import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  // Sign-up is gated: until the code emailed at the end of registration is
  // entered, the only page an account can reach is the confirmation step.
  if (!user?.emailVerified) {
    return <Navigate to="/verify-email" replace />;
  }
  return children;
}
