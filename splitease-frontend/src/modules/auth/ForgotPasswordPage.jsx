import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { expiresIn, formatClock, maskEmail, resendIn, useNow } from "./verification";

const CODE_LENGTH = 6;

/**
 * "I forgot my password", in two steps on one page: the address, then the code with
 * the new password. Both steps talk to the public /auth endpoints because there is no
 * session to speak of - the code is the only credential that matters.
 *
 * The server answers step 1 identically for an address with an account and one
 * without, so the wording here promises nothing: "if an account exists ...".
 */
export default function ForgotPasswordPage() {
  const { forgotPassword, resetPassword } = useAuth();
  const navigate = useNavigate();
  const [stage, setStage] = useState("email");
  const [email, setEmail] = useState("");
  const [issued, setIssued] = useState(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const now = useNow();

  const secondsLeft = expiresIn(issued, now);
  const cooldownLeft = resendIn(issued, now);
  const expired = secondsLeft === 0;

  /** Step 1, and (with resend) the "send a new code" button on step 2. */
  async function requestCode(event, { resend = false } = {}) {
    if (event) event.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const payload = await forgotPassword(email.trim());
      setIssued(payload);
      setCode("");
      setStage("reset");
      if (resend) setNotice(`A new code was requested for ${payload.maskedEmail}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleReset(event) {
    event.preventDefault();
    setError("");
    setNotice("");
    if (password !== confirm) {
      setError("The two passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      // Success signs the user in, so the guard on /groups opens - the address counts
      // as verified, because proving the mailbox is what the code just did.
      await resetPassword({ email: email.trim(), code, newPassword: password });
      navigate("/groups", { replace: true });
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
          <h1>{stage === "email" ? "Reset your password" : "Choose a new password"}</h1>
        </div>

        {stage === "email" ? (
          <>
            <p className="verify-lead">
              Enter the email you signed up with and we&apos;ll send a 6-digit code to confirm it&apos;s you.
            </p>
            <form className="verify-form" onSubmit={requestCode}>
              <div className="field">
                <label htmlFor="email">Email</label>
                <input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <button className="btn" type="submit" disabled={busy}>
                {busy ? "Sending…" : "Send code"}
              </button>
            </form>
            {error && <p className="error-text">{error}</p>}
            <p className="switch">
              Remembered it? <Link to="/login">Log in</Link>
            </p>
          </>
        ) : (
          <>
            <p className="verify-lead">
              If an account exists for <b>{issued?.maskedEmail || maskEmail(email.trim())}</b>, we sent a 6-digit code
              to it. Enter the code together with your new password.
              {secondsLeft > 0 && (
                <>
                  {" "}
                  It expires in <span className="num">{formatClock(secondsLeft)}</span>.
                </>
              )}
            </p>

            {issued?.devCode && (
              <p className="verify-hint">
                While <code>app.otp.expose-code</code> is on, the code is returned here too: <b>{issued.devCode}</b> — it
                is also in the backend log.
              </p>
            )}

            {expired && <p className="error-text">That code expired. Request a new one.</p>}

            <form className="verify-form" onSubmit={handleReset}>
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
              <div className="field">
                <label htmlFor="new-password">New password</label>
                <input
                  id="new-password"
                  type="password"
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="confirm-password">Confirm new password</label>
                <input
                  id="confirm-password"
                  type="password"
                  required
                  minLength={8}
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                />
              </div>
              <button className="btn" type="submit" disabled={busy || code.length !== CODE_LENGTH}>
                {busy ? "Saving…" : "Set new password"}
              </button>
              <button
                className="btn secondary"
                type="button"
                onClick={() => requestCode(null, { resend: true })}
                disabled={busy || cooldownLeft > 0}
              >
                {cooldownLeft > 0 ? `Resend in ${cooldownLeft}s` : "Send a new code"}
              </button>
            </form>

            {error && <p className="error-text">{error}</p>}
            {notice && <p className="verify-ok">{notice}</p>}

            <p className="switch">
              Wrong address?{" "}
              <button
                type="button"
                onClick={() => {
                  setStage("email");
                  setIssued(null);
                  setError("");
                  setNotice("");
                }}
              >
                Start over
              </button>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
