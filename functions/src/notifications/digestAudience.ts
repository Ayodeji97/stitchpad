/**
 * Who gets the digest EMAIL, and on which day (decided 2026-11-23,
 * docs/handoff/2026-11-23-notifications-first-email-paid-only.md).
 *
 * Push and the in-app inbox stay daily for everyone. The email is weekly and goes
 * only to paid owners, so it fits the Resend free plan (100/day, 3,000/month): the
 * old daily email to every owner needed ~190/day and overran the quota on 21 Nov 2026.
 */
import { DAY_MS, lagosDayIndex } from './lagosTime';

/** `config/app.digestEmailAudience`. Missing or unknown values mean 'paid'. */
export type DigestEmailAudience = 'paid' | 'none' | 'all';

export function parseDigestEmailAudience(value: unknown): DigestEmailAudience {
  return value === 'none' || value === 'all' ? value : 'paid';
}

/** Minimum days between two digest emails to the same owner. */
export const MIN_DAYS_BETWEEN_EMAILS = 6;

/** Firestore Timestamp, Date, or epoch millis → millis. Anything else → null. */
function toMillis(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (value instanceof Date) return value.getTime();
  if (value && typeof (value as { toMillis?: unknown }).toMillis === 'function') {
    return (value as { toMillis: () => number }).toMillis();
  }
  return null;
}

/**
 * A paid owner bought a plan through Paystack or Apple, and that plan is still
 * running. A launch grant (`grantSource: launch_free`, no end date) is NOT paid,
 * and neither is a paid plan whose end date has passed.
 */
export function isPaidSubscriber(user: Record<string, unknown> | undefined, now: number): boolean {
  if (!user) return false;
  const source = user.subscriptionSource;
  if (source !== 'paystack' && source !== 'apple') return false;
  if (user.subscriptionStatus !== 'active') return false;
  const endsAt = toMillis(user.subscriptionEndsAt);
  return endsAt !== null && endsAt > now;
}

/**
 * The owner's fixed email weekday (0=Sunday … 6=Saturday, same numbering as
 * lagosWeekday). FNV-1a over the uid: stable across runs and deploys, and spreads
 * payers over the week so no single day hits the 100/day cap.
 */
export function digestEmailWeekday(uid: string): number {
  let hash = 0x811c9dc5;
  for (let i = 0; i < uid.length; i++) {
    hash ^= uid.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash % 7;
}

/** Whole Lagos days from a 'YYYY-MM-DD' stamp to `now`. null for a missing/bad stamp. */
export function daysSinceDateKey(dateKey: string | null, now: number): number | null {
  if (!dateKey) return null;
  const parsed = Date.parse(`${dateKey}T00:00:00Z`);
  if (Number.isNaN(parsed)) return null;
  return lagosDayIndex(now) - Math.floor(parsed / DAY_MS);
}
