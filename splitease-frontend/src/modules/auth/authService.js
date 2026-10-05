import { api } from "../common/api";

const JWT_KEY = "splitease_jwt";
const USER_KEY = "splitease_user";
const OTP_KEY = "splitease_otp";

function persistSession({ token, user }) {
  if (token) localStorage.setItem(JWT_KEY, token);
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
}

function persistUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

export const authService = {
  /**
   * Sign-up answers with the login shape (201 {token, user, verification}), so the
   * confirmation step that follows has a session to verify and resend with. The
   * dashboard is not part of this call: `verification` describes the 6-digit code
   * that was just emailed, and it stays in localStorage so the countdown survives
   * a page reload.
   */
  async register({ name, email, password }) {
    const data = await api.post("/auth/register", { name, email, password }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    this.savePendingVerification(data?.verification);
    return data;
  },

  async login({ email, password }) {
    const data = await api.post("/auth/login", { email, password }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    return data;
  },

  /**
   * Step 1 of a password reset. Unauthenticated on purpose - there is no session yet -
   * and the server answers the same whether or not the address has an account, so the
   * payload may describe a code that can never match. The caller only uses it to render
   * the step; nothing is stored under `splitease_otp`, which belongs to sign-up.
   */
  async forgotPassword(email) {
    return api.post("/auth/forgot-password", { email }, { auth: false });
  },

  /**
   * Step 2: the code stands in for the old password, and a real session comes back -
   * same shape as login, so the user lands inside the app rather than on the login
   * screen. The address is verified by the same call, which is why the pending sign-up
   * payload (if any) is cleared here too.
   */
  async resetPassword({ email, code, newPassword }) {
    const data = await api.post("/auth/reset-password", { email, code, newPassword }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    this.clearPendingVerification();
    return data;
  },

  /**
   * POST /otp/verify - the address comes from the JWT, only the code is sent.
   * Success clears the pending payload and the cached user gains
   * emailVerified=true, which is what lets the router move on to the dashboard.
   */
  async verifyEmail(code) {
    const user = await api.post("/otp/verify", { code });
    this.clearPendingVerification();
    return persistUser(user);
  },

  /** POST /otp/resend - new code, new 5 minute window. */
  async resendVerification() {
    const verification = await api.post("/otp/resend");
    this.savePendingVerification(verification);
    return verification;
  },

  savePendingVerification(verification) {
    if (!verification) return;
    // Stored verbatim: the countdown needs no extra client timestamp because
    // expiresAt minus expiresInSeconds is exactly when the server issued the code.
    localStorage.setItem(OTP_KEY, JSON.stringify(verification));
  },

  getPendingVerification() {
    const raw = localStorage.getItem(OTP_KEY);
    return raw ? JSON.parse(raw) : null;
  },

  clearPendingVerification() {
    localStorage.removeItem(OTP_KEY);
  },

  /** Keeps the cached user - and with it the route guard's idea of where we belong - in sync. */
  saveUser(user) {
    return persistUser(user);
  },

  logout() {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(OTP_KEY);
  },

  getCurrentUser() {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  },

  isAuthenticated() {
    return !!localStorage.getItem(JWT_KEY);
  },
};
