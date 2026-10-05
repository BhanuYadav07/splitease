import { createContext, useContext, useState, useCallback } from "react";
import { authService } from "./authService";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(authService.getCurrentUser());
  const [isAuthenticated, setIsAuthenticated] = useState(authService.isAuthenticated());
  // The confirmation step: what we know about the code that was just issued.
  // It lives in localStorage too, so a reload keeps the countdown and the address.
  const [verification, setVerification] = useState(authService.getPendingVerification());

  const login = useCallback(async (credentials) => {
    const data = await authService.login(credentials);
    setUser(data?.user || null);
    setIsAuthenticated(true);
    return data;
  }, []);

  /**
   * Creating an account returns a session plus the verification payload. The
   * session is only good for confirming the address, so the caller routes to the
   * confirmation step - the dashboard unlocks once that code is accepted.
   */
  const register = useCallback(async (payload) => {
    const data = await authService.register(payload);
    setUser(data?.user || null);
    setIsAuthenticated(true);
    setVerification(data?.verification || null);
    return data;
  }, []);

  /**
   * Forgot password, step 1: asks for a code and hands the payload back to the page.
   * Nothing is cached - the payload belongs to that one form, and the server keeps the
   * code itself, not this component.
   */
  const forgotPassword = useCallback((email) => authService.forgotPassword(email), []);

  /**
   * Forgot password, step 2: the code is accepted, the password is replaced and a
   * session arrives, so this ends like login - with the user inside the app.
   */
  const resetPassword = useCallback(async (payload) => {
    const data = await authService.resetPassword(payload);
    setUser(data?.user || null);
    setIsAuthenticated(true);
    setVerification(null);
    return data;
  }, []);

  /** The right 6-digit code flips user.emailVerified, which opens the rest of the app. */
  const verifyEmail = useCallback(async (code) => {
    const verified = await authService.verifyEmail(code);
    setUser(verified);
    setVerification(null);
    return verified;
  }, []);

  const resendVerification = useCallback(async () => {
    const issued = await authService.resendVerification();
    setVerification(issued);
    return issued;
  }, []);

  /** Used by the profile page: changing the email un-verifies it server-side. */
  const updateUser = useCallback((next) => {
    setUser(authService.saveUser(next));
  }, []);

  const logout = useCallback(() => {
    authService.logout();
    setUser(null);
    setIsAuthenticated(false);
    setVerification(null);
  }, []);

  const value = {
    user,
    setUser,
    updateUser,
    login,
    register,
    forgotPassword,
    resetPassword,
    logout,
    isAuthenticated,
    verification,
    verifyEmail,
    resendVerification,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
