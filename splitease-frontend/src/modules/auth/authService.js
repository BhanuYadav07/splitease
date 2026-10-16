import { api } from "../common/api";

const JWT_KEY = "splitease_jwt";
const USER_KEY = "splitease_user";
const OTP_KEY = "splitease_otp";
const PENDING_EMAIL_KEY = "splitease_pending_email";

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
   * Sign-up answers with the account and the verification payload - but no token.
   * An unconfirmed account is not logged in, so the session only appears once the
   * emailed code is accepted (see `verifyEmail`). The address is remembered so the
   * confirmation step knows who it is waiting on, even after a reload.
   */
  async register({ name, email, password }) {
    const data = await api.post("/auth/register", { name, email, password }, { auth: false });
    this.savePendingVerification(data?.verification);
    this.savePendingEmail(email);
    return data;
  },

  /** Only a verified account gets here - the server refuses an unconfirmed one. */
  async login({ email, password }) {
    const data = await api.post("/auth/login", { email, password }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    return data;
  },

  /**
   * Step 1 of a password reset. Unauthenticated on purpose - there is no session yet -
   * and the server answers the same whether or not the address has an account, so the
   * payload may describe a code that can never match.
   */
  async forgotPassword(email) {
    return api.post("/auth/forgot-password", { email }, { auth: false });
  },

  /**
   * Step 2: the code stands in for the old password, and a real session comes back -
   * same shape as login, so the user lands inside the app rather than on the login
   * screen.
   */
  async resetPassword({ email, code, newPassword }) {
    const data = await api.post("/auth/reset-password", { email, code, newPassword }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    this.clearPendingVerification();
    this.clearPendingEmail();
    return data;
  },

  /**
   * POST /otp/verify - the code is the credential, the address travels with it. This is
   * how an account becomes usable: a good code verifies the mailbox and returns the
   * first real session.
   */
  async verifyEmail({ email, code }) {
    const data = await api.post("/otp/verify", { email, code }, { auth: false });
    persistSession({ token: data?.token, user: data?.user });
    this.clearPendingVerification();
    this.clearPendingEmail();
    return data?.user;
  },

  /** POST /otp/resend - new code, new 5 minute window (throttled by the server). */
  async resendVerification(email) {
    const verification = await api.post("/otp/resend", { email }, { auth: false });
    this.savePendingVerification(verification);
    this.savePendingEmail(email);
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

  savePendingEmail(email) {
    if (email) localStorage.setItem(PENDING_EMAIL_KEY, email.trim());
  },

  getPendingEmail() {
    return localStorage.getItem(PENDING_EMAIL_KEY);
  },

  clearPendingEmail() {
    localStorage.removeItem(PENDING_EMAIL_KEY);
  },

  /** Keeps the cached user - and with it the route guard's idea of where we belong - in sync. */
  saveUser(user) {
    return persistUser(user);
  },

  logout() {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(OTP_KEY);
    localStorage.removeItem(PENDING_EMAIL_KEY);
  },

  getCurrentUser() {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  },

  isAuthenticated() {
    return !!localStorage.getItem(JWT_KEY);
  },
};
