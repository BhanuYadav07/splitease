import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { expiresIn, formatClock, maskEmail, resendIn, useNow } from "./verification";

const CODE_LENGTH = 6;

/**
 * The sign-up gate, and the door for anyone whose address is not confirmed yet.
 *
 * There is deliberately no session required here: an account becomes usable only when
 * the code mailed to it is entered, so this page has to work for a brand-new sign-up
 * and for a login attempt the server refused as unverified. The address travels with
 * the code (POST /otp/verify) - it is public because the code itself is the proof.
 *
 * Nothing here mirrors the server's pause (3 wrong codes or passwords stop all mail to
 * the account for 15 minutes): the refusal arrives as a 400 whose message is shown
 * below, so the rule lives in exactly one place - OtpServiceImpl.
 */
export default function VerifyEmailPage() {
  const { user, isAuthenticated, verification, pendingEmail, verifyEmail, resendVerification, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const now = useNow();

  // Where the code would go: the signed-in account's address, or the one we remembered
  // from a sign-up / refused login. With neither there is nothing to confirm.
  const email = user?.email || pendingEmail || "";

  if (isAuthenticated && user?.emailVerified) return <Navigate to="/groups" replace />;
  if (!email) return <Navigate to="/login" replace />;

  const address = verification?.maskedEmail || maskEmail(email);
  const emailChanged = Boolean(location.state?.emailChanged);
  const secondsLeft = expiresIn(verification, now);
  const cooldownLeft = resendIn(verification, now);
  const expired = secondsLeft === 0;

  // Three states deserve different words: a code is out (register or resend), the
  // address just changed, or we simply have an address to send to.
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
      await verifyEmail({ email, code }); // verifies the mailbox and returns a session
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
      const issued = await resendVerification(email);
      setCode("");
      setNotice(`A new code is on its way to ${issued.maskedEmail}. It expires in 5 minutes.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function handleSwitchAccount(event) {
    event.preventDefault();
    logout();
    navigate("/login", { replace: true });
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
          <Link to="/login" onClick={handleSwitchAccount}>
            Log in with another account
          </Link>
        </p>
      </div>
    </div>
  );
}
