import { createContext, useContext, useState, useCallback } from "react";
import { authService } from "./authService";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(authService.getCurrentUser());
  const [isAuthenticated, setIsAuthenticated] = useState(authService.isAuthenticated());
  // The confirmation step: what we know about the code that was just issued, and the
  // address it was sent to. Both live in localStorage so a reload keeps the countdown
  // and the mailbox.
  const [verification, setVerification] = useState(authService.getPendingVerification());
  const [pendingEmail, setPendingEmail] = useState(authService.getPendingEmail());

  /**
   * Only a verified account can log in. If the server refuses with EMAIL_NOT_VERIFIED,
   * remember the address and let the caller route to the confirmation step - no session
   * was created.
   */
  const login = useCallback(async (credentials) => {
    try {
      const data = await authService.login(credentials);
      setUser(data?.user || null);
      setIsAuthenticated(true);
      setVerification(null);
      setPendingEmail(null);
      return data;
    } catch (err) {
      if (err.code === "EMAIL_NOT_VERIFIED") {
        authService.savePendingEmail(credentials.email);
        setPendingEmail(credentials.email);
      }
      throw err;
    }
  }, []);

  /**
   * Creating an account does not sign you in: the account exists and a code is on its
   * way, but the dashboard stays shut until that code is confirmed.
   */
  const register = useCallback(async (payload) => {
    const data = await authService.register(payload);
    setVerification(data?.verification || null);
    setPendingEmail(payload?.email || null);
    return data;
  }, []);

  /**
   * Forgot password, step 1: asks for a code and hands the payload back to the page.
   * Nothing is cached - the payload belongs to that one form.
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
    setPendingEmail(null);
    return data;
  }, []);

  /** A correct 6-digit code verifies the mailbox and, at last, signs the user in. */
  const verifyEmail = useCallback(async ({ email, code }) => {
    const verified = await authService.verifyEmail({ email, code });
    setUser(verified);
    setIsAuthenticated(true);
    setVerification(null);
    setPendingEmail(null);
    return verified;
  }, []);

  const resendVerification = useCallback(async (email) => {
    const issued = await authService.resendVerification(email);
    setVerification(issued);
    setPendingEmail(email);
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
    setPendingEmail(null);
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
    pendingEmail,
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
