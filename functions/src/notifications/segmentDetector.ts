/**
 * Maps a tailor to the ONE thing they have not discovered yet, so the engagement
 * push can talk about their actual next step instead of shouting generic
 * marketing at everyone.
 *
 * Pure: no IO, no clock. The caller gathers {@link UserSignals} (three cheap
 * Firestore .count() aggregations plus the user doc) and this decides.
 */

/** Orders that mark a shop as busy enough to plausibly need staff. */
export const BUSY_ORDER_THRESHOLD = 10;

export type Segment =
  /** Signed up, never added a customer. The very first step. */
  | 'no_customer'
  /** Has customers but has never written an order. */
  | 'no_order'
  /** Real order volume, still working solo — team feature discovery. */
  | 'busy_no_team'
  /** Active tailor who has never pulled their referral link. */
  | 'no_referral'
  /** Working, but nothing due/overdue/owed today — the digest sends them nothing. */
  | 'quiet'
  /** Everyone. The fallback bucket announcements target. */
  | 'all';

/** Every segment, in the order {@link detectSegment} tests them. */
export const SEGMENTS: readonly Segment[] = [
  'no_customer',
  'no_order',
  'busy_no_team',
  'no_referral',
  'quiet',
  'all',
] as const;

export type Tier = 'free' | 'pro' | 'atelier';

export interface UserSignals {
  customerCount: number;
  orderCount: number;
  teamCount: number;
  /** True once the tailor has generated a Founding Tailors referral link. */
  hasReferralLink: boolean;
  /** digestDetector found nothing actionable today — only computed when needed. */
  digestEmpty: boolean;
  /**
   * Carried so copy can differ between an honest upgrade prompt (free) and pure
   * feature discovery (paid) for the same segment. The detector itself does not
   * branch on tier — campaigns do.
   */
  tier: Tier;
}

/**
 * First unmet milestone wins.
 *
 * Order is the whole design. A tailor with no customers must never be told about
 * the team feature — that is noise, and it is the fastest way to teach someone
 * that our notifications are not worth reading. Each rung assumes the ones above
 * it are satisfied, so the conditions below stay deliberately narrow.
 */
export function detectSegment(s: UserSignals): Segment {
  if (s.customerCount <= 0) return 'no_customer';
  if (s.orderCount <= 0) return 'no_order';
  if (s.orderCount >= BUSY_ORDER_THRESHOLD && s.teamCount <= 0) return 'busy_no_team';
  if (!s.hasReferralLink) return 'no_referral';
  if (s.digestEmpty) return 'quiet';
  return 'all';
}

/**
 * True when the segment can only be decided by reading the tailor's orders in
 * full (via digestDetector). Lets the run loop skip a per-user orders read
 * entirely when no live campaign targets `quiet` — the difference between an
 * O(users) and an O(users × orders) run.
 */
export function segmentNeedsDigest(segment: Segment): boolean {
  return segment === 'quiet';
}
