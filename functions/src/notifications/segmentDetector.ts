/**
 * Works out which segments apply to a tailor, so the engagement push can talk
 * about their actual next step instead of shouting generic marketing at everyone.
 *
 * Pure: no IO, no clock. The caller gathers signals (cheap Firestore .count()
 * aggregations plus the user doc) and this decides.
 */

/** Orders that mark a shop as busy enough to plausibly need staff. */
export const BUSY_ORDER_THRESHOLD = 10;

/**
 * Days without a new order before a tailor counts as dormant.
 *
 * 21 is deliberately past "quiet spell" and short of "gone". The daily digest goes
 * SILENT for these tailors by design — nothing is due, so nothing is sent — which
 * means the people closest to churning are the ones we say least to.
 */
export const DORMANT_DAYS = 21;

/** Days left in the First Month window that trigger the ending nudge. */
export const WELCOME_ENDING_DAYS = 3;

export type Segment =
  /** Signed up, never added a customer. The very first step. */
  | 'no_customer'
  /** Has customers but has never written an order. */
  | 'no_order'
  /** Real order volume, still working solo — team feature discovery. */
  | 'busy_no_team'
  /** Free tier, First Month window about to expire — the cap is about to drop. */
  | 'welcome_ending'
  /** Was working, but has logged no new order in DORMANT_DAYS. The churn cohort. */
  | 'dormant'
  /** Delivered work with no cost recorded — profit cannot be computed for it. */
  | 'no_costs'
  /** Working, but nothing due/overdue/owed today — the digest sends them nothing. */
  | 'quiet'
  /** Activated tailor who has never pulled their referral link. */
  | 'no_referral'
  /** Everyone. The catch-all announcements target, and the end of every chain. */
  | 'all';

export const SEGMENTS: readonly Segment[] = [
  'no_customer',
  'no_order',
  'busy_no_team',
  'welcome_ending',
  'dormant',
  'no_costs',
  'quiet',
  'no_referral',
  'all',
] as const;

export type Tier = 'free' | 'pro' | 'atelier';

/**
 * The part of a tailor's state that three cheap `.count()` aggregations answer.
 * Split out from {@link UserSignals} on purpose: {@link activationSegment} runs on
 * these alone, which lets the caller skip the expensive per-user orders read for
 * anyone whose segment is already settled.
 */
export interface ActivationSignals {
  customerCount: number;
  orderCount: number;
  /** ACTIVE, NON-OWNER roster rows only. */
  teamCount: number;
}

export interface UserSignals extends ActivationSignals {
  /** True once the tailor has generated a Founding Tailors referral link. */
  hasReferralLink: boolean;
  /** digestDetector found nothing actionable today — only computed when needed. */
  digestEmpty: boolean;
  /**
   * Whole days since the most recent order was CREATED, or null when unknown
   * (no orders, or the orders read was skipped this run).
   */
  daysSinceLastOrder: number | null;
  /**
   * Whole days until the free First Month window expires, or null when it does not
   * apply — paid tier, window already over, or never granted.
   */
  welcomeDaysLeft: number | null;
  /**
   * Delivered orders carrying no cost entry, so their profit is unknowable. null when
   * the orders read was skipped this run.
   */
  deliveredWithoutCosts: number | null;
  /**
   * Carried so copy can differ between an honest upgrade prompt (free) and pure
   * feature discovery (paid) for the same segment. Nothing here branches on tier —
   * campaigns do.
   */
  tier: Tier;
}

/**
 * The first unmet ACTIVATION milestone, or null when the tailor has cleared them
 * all. Decidable from counts alone — no orders read required.
 *
 * Order is the whole design. A tailor with no customers must never be told about
 * staff accounts: that is noise, and it is the fastest way to teach someone our
 * notifications are not worth reading.
 */
export function activationSegment(s: ActivationSignals): Segment | null {
  if (s.customerCount <= 0) return 'no_customer';
  if (s.orderCount <= 0) return 'no_order';
  if (s.orderCount >= BUSY_ORDER_THRESHOLD && s.teamCount <= 0) return 'busy_no_team';
  return null;
}

/**
 * True when deciding this tailor's segments still needs their full order list
 * (`digestEmpty` and `daysSinceLastOrder` both come from it). Only tailors who have
 * cleared every activation rung can reach `quiet` or `dormant`, so everyone else
 * skips the orders read entirely.
 *
 * This is the per-user cost lever: without it, one live `quiet` campaign would
 * scan every order of every user, including the majority whose segment was
 * already settled by two count queries.
 */
export function needsDigestForSegments(s: ActivationSignals): boolean {
  return activationSegment(s) === null;
}

/**
 * Every segment that applies to this tailor, MOST SPECIFIC FIRST, always ending
 * in `all`.
 *
 * A chain rather than a single segment for two reasons, both of which were real
 * bugs when this returned one value:
 *
 *   1. An `all` campaign — a release announcement — has to be able to reach a
 *      tailor who also sits in `no_customer`. With exact-match selection it
 *      reached only fully-activated tailors on a busy day, i.e. almost nobody.
 *   2. Once a tailor exhausted `maxSendsPerUser` for their one segment they went
 *      permanently silent, even with live campaigns that applied to them. Since
 *      most tailors never mint a referral link, that silenced most of the base
 *      after two sends.
 *
 * `quiet` deliberately outranks `no_referral`: "nothing due today" is about the
 * tailor's own work, while "invite a friend" is about ours. Neither applies until
 * the activation rungs are cleared — asking someone with zero customers to
 * recruit is exactly the noise this ladder exists to prevent.
 */
export function segmentChain(s: UserSignals): Segment[] {
  const chain: Segment[] = [];
  const activation = activationSegment(s);
  if (activation) {
    // Someone who has not added a customer yet has no use for "your First Month is
    // ending" or "it's been a while" — the basics come first, always.
    chain.push(activation);
  } else {
    // Time-boxed and therefore first: this one expires, the others keep.
    if (s.welcomeDaysLeft !== null && s.welcomeDaysLeft <= WELCOME_ENDING_DAYS) {
      chain.push('welcome_ending');
    }
    // Dormant supersedes quiet — same tailor, but a much sharper signal that earns
    // sharper copy. Quiet stays in the chain behind it as a fallback once the
    // dormant campaigns are spent.
    if (s.daysSinceLastOrder !== null && s.daysSinceLastOrder >= DORMANT_DAYS) {
      chain.push('dormant');
    }
    if (s.deliveredWithoutCosts !== null && s.deliveredWithoutCosts > 0) {
      chain.push('no_costs');
    }
    if (s.digestEmpty) chain.push('quiet');
    if (!s.hasReferralLink) chain.push('no_referral');
  }
  chain.push('all');
  return chain;
}
