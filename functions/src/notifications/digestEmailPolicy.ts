import { lagosDateKey, lagosDayIndex, lagosWeekday } from './lagosTime';

export type DigestEmailAudience = 'paid' | 'none' | 'all';

/** Unknown/missing config must never opt free accounts into email. */
export function digestEmailAudience(value: unknown): DigestEmailAudience {
  return value === 'none' || value === 'all' ? value : 'paid';
}

/** Only a live verified payment counts; launch grants and gifts are not paid. */
export function hasPaidDigestAccess(data: Record<string, unknown>, now: number): boolean {
  return (data.subscriptionSource === 'paystack' || data.subscriptionSource === 'apple') &&
    data.subscriptionStatus === 'active' &&
    typeof data.subscriptionEndsAt === 'number' && Number.isFinite(data.subscriptionEndsAt) &&
    data.subscriptionEndsAt > now;
}

/** Stable unsigned DJB2 hash, Sunday=0. Do not change: this assigns each owner's day. */
export function digestEmailWeekday(uid: string): number {
  let hash = 5381;
  for (let i = 0; i < uid.length; i++) hash = (Math.imul(hash, 33) + uid.charCodeAt(i)) >>> 0;
  return hash % 7;
}

export function isWeeklyDigestDue(uid: string, lastSentDate: string | null, now: number): boolean {
  if (digestEmailWeekday(uid) !== lagosWeekday(now)) return false;
  if (!lastSentDate) return true;
  // Stamp precision is a Lagos calendar day. Fail closed on corrupt/future stamps.
  if (!/^\d{4}-\d{2}-\d{2}$/.test(lastSentDate)) return false;
  const last = Date.parse(`${lastSentDate}T00:00:00+01:00`);
  if (!Number.isFinite(last) || lagosDateKey(last) !== lastSentDate) return false;
  return lagosDayIndex(now) - lagosDayIndex(last) >= 6;
}
