import { EngagementCampaign } from './engagementConfig';
import { Tier } from './segmentDetector';
import { OrderScanDoc } from './types';

/**
 * A candidate for the engagement push. Deliberately thinner than DigestRecipient:
 * this job sends no email, so it needs no verified address and no display name —
 * only enough to resolve the rollout gate and the opt-out.
 */
export interface EngagementRecipient {
  uid: string;
  /** Only used for the allowlist gate; may be blank for legacy docs. */
  email: string;
  /** False only when explicitly opted out of tips/announcements. */
  announcementsEnabled: boolean;
  tier: Tier;
  /**
   * True once the tailor has minted a Founding Tailors link. Lives here rather
   * than on EngagementCounts because it comes from the `referralCode` field on
   * the same user doc that supplies `tier` and the opt-out — no extra read.
   */
  hasReferralLink: boolean;
  /** businessName || displayName || 'Tailor'. Used by {{businessName}}. */
  businessName: string;
  /** Founding Tailors points, resolved once per run from leaderboards/current. */
  points: number;
  /**
   * Whole days until the free First Month window expires; null when it does not
   * apply. From `welcomeBonusAppliedAt` on the user doc — no extra read.
   */
  welcomeDaysLeft: number | null;
}

/** Per-user engagement history, read from `users/{uid}/private/digestState`. */
export interface EngagementUserState {
  /**
   * Lagos date key of the user's most recent push from ANY scheduled job. Shared
   * with the daily digest on purpose — it is what stops a promo stacking on top of
   * a morning summary.
   */
  lastPushDate: string | null;
  /** Lagos day index of the last engagement push, for the cadence gap. */
  lastSentDayIndex: number | null;
  /** Total engagement pushes ever sent to this user — drives campaign rotation. */
  sendCount: number;
  /** Lifetime per-campaign tallies, keyed by campaign id. */
  campaignCounts: Record<string, number>;
}

/** Counts backing the segment ladder. */
export interface EngagementCounts {
  customerCount: number;
  orderCount: number;
  /** ACTIVE, NON-OWNER roster rows only — see loadCounts in engagementPush.ts. */
  teamCount: number;
}

export interface EngagementIO {
  /** Raw `config/engagementPush` data — validated by parseEngagementConfig. */
  loadConfig(): Promise<unknown>;
  listRecipients(): Promise<EngagementRecipient[]>;
  loadState(uid: string): Promise<EngagementUserState>;
  loadCounts(uid: string): Promise<EngagementCounts>;
  /** Only called when a live campaign targets `quiet` or `dormant`. */
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  loadPushTokens(uid: string): Promise<string[]>;
  sendPush(
    tokens: string[],
    campaign: EngagementCampaign,
  ): Promise<{ successCount: number; invalidTokens: string[] }>;
  deletePushTokens(uid: string, tokens: string[]): Promise<void>;
  recordSent(uid: string, campaignId: string, dateKey: string, dayIndex: number): Promise<void>;
  isAllowed(uid: string, email: string): boolean;
}

export interface EngagementRunResult {
  considered: number;
  sent: number;
  skippedDisabled: number;
  skippedNotEngagementDay: number;
  /** Run-level: the config parsed to zero usable campaigns. Distinct from skippedNoCampaign. */
  skippedNoValidCampaigns: number;
  skippedOptedOut: number;
  skippedNotAllowed: number;
  skippedPushedToday: number;
  skippedCadence: number;
  /** Per-user: no live campaign matched any segment in this tailor's chain. */
  skippedNoCampaign: number;
  skippedNoTokens: number;
  failed: number;
}
