/**
 * Shared leaf helpers for the password-reset enqueuer + worker. Kept in their
 * own module so neither function has to import the other.
 */

/**
 * Trims + lowercases an email and rejects obvious junk. Deliberately permissive
 * — real validation is Firebase Auth's job; this just keeps malformed input out
 * of the throttle/queue. Returns null if the value isn't a usable email.
 */
export function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const email = raw.trim().toLowerCase();
  if (email.length === 0 || email.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return null;
  }
  return email;
}
