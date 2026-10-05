import { useEffect, useState } from "react";

/**
 * The clock, the countdowns and the address mask shared by the two screens that ask
 * for a 6-digit code: the sign-up confirmation step and the password reset. They live
 * here once so both read the same server fields the same way.
 */

/** Ticking clock so the 5-minute countdown and the resend cooldown move on their own. */
export function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

/** 296 -> "4:56" */
export function formatClock(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

/**
 * When the server issued the current code: its expiry minus the window it started
 * with. Reading the instant off the payload (instead of stamping Date.now() when the
 * response arrives) keeps the cooldown correct after a reload and immune to a slow
 * render.
 */
function issuedAt(verification) {
  if (!verification?.expiresAt) return 0;
  return new Date(verification.expiresAt).getTime() - (verification.expiresInSeconds || 0) * 1000;
}

/** Seconds until the code dies, or null when nothing has been issued. */
export function expiresIn(verification, now) {
  if (!verification?.expiresAt) return null;
  return Math.max(0, Math.round((new Date(verification.expiresAt).getTime() - now) / 1000));
}

/** Seconds the "resend" button must stay disabled: the server's 30-second cooldown. */
export function resendIn(verification, now) {
  if (!verification) return 0;
  return Math.max(
    0,
    Math.ceil((issuedAt(verification) + (verification.resendAvailableInSeconds || 0) * 1000 - now) / 1000)
  );
}

/** tarun@gmail.com -> t***n@gmail.com, for when no freshly issued code names the address. */
export function maskEmail(email) {
  const at = (email || "").indexOf("@");
  if (at <= 0) return "your email address";
  const local = email.slice(0, at);
  const domain = email.slice(at);
  if (local.length <= 2) return `${local[0]}***${domain}`;
  return `${local[0]}***${local[local.length - 1]}${domain}`;
}
