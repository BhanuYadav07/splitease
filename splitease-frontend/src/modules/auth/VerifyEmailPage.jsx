import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { expiresIn, formatClock, maskEmail, resendIn, useNow } from "./verification";

const CODE_LENGTH = 6;

/**
 * The sign-up gate. Creating an account only gets you this far: the account
 * exists and a 6-digit code is in the mailbox, but nothing else in the app opens
 * until the code is entered. The login screen routes an unverified account here
 * too, and so does ProtectedRoute, so this page is the only door forward.
 *
 * The session (JWT) is what makes verify and resend possible at all - the
 * backend takes the address from the token rather than the body, so nobody can
 * confirm somebody else's mailbox. Nothing here mirrors the server's mail pause
 * (5 wrong codes or passwords stop all mail to the account for 3 days): the
 * refusal arrives as a 400 whose message is shown below, so the rule lives in
 * exactly one place - OtpServiceImpl.
 */
export default function VerifyEmailPage() {
  const { user, isAuthenticated, verification, verifyEmail, resendVerification, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const now = useNow();

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user?.emailVerified) return <Navigate to="/groups" replace />;

  const address = verification?.maskedEmail || maskEmail(user?.email);
  const emailChanged = Boolean(location.state?.emailChanged);
  const secondsLeft = expiresIn(verification, now);
  const cooldownLeft = resendIn(verification, now);
  const expired = secondsLeft === 0;

  // Three states deserve different words: a code is out (register or resend), the
  // address just changed, or a session arrived with no code attached (a plain login
  // of an account that never finished sign-up, or a reload after logging out).
  const lead = !verification ? (
    emailChanged ? (
      <>
        That address is new, so we have to check it. Send a code to <b>{address}</b> to confirm it.
      </>
    ) : (
      <>
        Send a code to <b>{address}</b> to finish setting up your account.
      </>
    )
  ) : (
    <>
      We emailed a 6-digit code to <b>{address}</b>. Enter it to finish setting up your account.
    </>
  );

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await verifyEmail(code); // flips user.emailVerified, so the guard above moves us on
      navigate("/groups", { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleResend() {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const issued = await resendVerification();
      setCode("");
      setNotice(`A new code is on its way to ${issued.maskedEmail}. It expires in 5 minutes.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <div className="verify-head">
          <span className="verify-head-icon" aria-hidden="true">
            ✉
          </span>
          <h1>Confirm your email address</h1>
        </div>
        <p className="verify-lead">
          {lead}
          {secondsLeft > 0 && (
            <>
              {" "}
              It expires in <span className="num">{formatClock(secondsLeft)}</span>.
            </>
          )}
        </p>

        {verification?.devCode && (
          <p className="verify-hint">
            {verification.emailDelivered ? (
              <>
                The code was emailed and, because <code>app.otp.expose-code</code> is on, it is here too:{" "}
                <b>{verification.devCode}</b>.
              </>
            ) : (
              <>
                Nothing left this machine (no mailbox configured, or delivery failed), so here is the code:{" "}
                <b>{verification.devCode}</b> — it is also in the backend log. Set <code>spring.mail.username</code> to
                send real email.
              </>
            )}
          </p>
        )}

        {expired && <p className="error-text">That code expired. Request a new one.</p>}

        <form className="verify-form" onSubmit={handleSubmit}>
          <input
            className="verify-code-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            placeholder={"0".repeat(CODE_LENGTH)}
            maxLength={CODE_LENGTH}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, CODE_LENGTH))}
            aria-label="Verification code"
          />
          <button className="btn" type="submit" disabled={busy || code.length !== CODE_LENGTH}>
            {busy ? "Checking…" : "Verify email"}
          </button>
          <button className="btn secondary" type="button" onClick={handleResend} disabled={busy || cooldownLeft > 0}>
            {cooldownLeft > 0 ? `Resend in ${cooldownLeft}s` : verification ? "Resend code" : "Send code"}
          </button>
        </form>

        {error && <p className="error-text">{error}</p>}
        {notice && <p className="verify-ok">{notice}</p>}

        <p className="switch">
          Wrong account?{" "}
          <Link to="/login" onClick={logout}>
            Log in with another account
          </Link>
        </p>
      </div>
    </div>
  );
}
