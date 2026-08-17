/**
 * Chooses which campaign (if any) a given tailor should receive right now.
 *
 * Pure: no IO, no clock beyond the `now` passed in. The run loop supplies the
 * user's segment and send history; this decides.
 */
import { EngagementCampaign, EngagementConfig } from './engagementConfig';
import { lagosWeekday } from './lagosTime';
import { Segment } from './segmentDetector';

/** True when today (Lagos) is one of the configured send days. */
export function isEngagementDay(config: EngagementConfig, now: number): boolean {
  return config.daysOfWeek.includes(lagosWeekday(now));
}

/** Inside the campaign's optional live window. Bounds are inclusive. */
function isInWindow(c: EngagementCampaign, now: number): boolean {
  if (c.startAt !== null && now < c.startAt) return false;
  if (c.endAt !== null && now > c.endAt) return false;
  return true;
}

/**
 * Campaigns that are live right now, ignoring the user. Used to decide up front
 * whether ANY per-user work is worth doing this run.
 */
export function liveCampaigns(config: EngagementConfig, now: number): EngagementCampaign[] {
  if (!config.enabled) return [];
  return config.campaigns.filter((c) => isInWindow(c, now));
}

/**
 * Whether this run needs each tailor's full order list.
 *
 * Only the `quiet` segment requires running digestDetector, and that is the one
 * expensive read in the loop. When no live campaign targets `quiet`, skipping it
 * turns an O(users × orders) run into an O(users) one.
 */
export function needsOrders(config: EngagementConfig, now: number): boolean {
  return liveCampaigns(config, now).some((c) => c.segment === 'quiet');
}

export interface SelectionContext {
  segment: Segment;
  /** How many engagement pushes this user has ever received — drives rotation. */
  sendCount: number;
  /** Per-campaign lifetime send tally for this user, keyed by campaign id. */
  campaignCounts: Record<string, number>;
}

/**
 * The campaign to send, or null when nothing applies.
 *
 * Eligibility: live window, segment match, and under the per-user cap. Then:
 *
 *   - Any candidate with `priority > 0` wins outright, highest first. This is the
 *     announcement override — "we shipped staff accounts" should interrupt the
 *     normal rotation for its window, not queue politely behind it.
 *   - Otherwise rotate deterministically: candidates sorted by id, indexed by the
 *     user's total send count. Deterministic beats random here because it is
 *     testable without seeding and it guarantees a user actually alternates
 *     rather than drawing the same message twice running.
 *
 * Ties on priority fall back to id order, so the choice is never arbitrary.
 */
export function selectCampaign(
  config: EngagementConfig,
  ctx: SelectionContext,
  now: number,
): EngagementCampaign | null {
  const candidates = liveCampaigns(config, now)
    .filter((c) => c.segment === ctx.segment)
    .filter((c) => c.maxSendsPerUser === 0 || (ctx.campaignCounts[c.id] ?? 0) < c.maxSendsPerUser)
    .sort((a, b) => a.id.localeCompare(b.id));

  if (candidates.length === 0) return null;

  const prioritised = candidates.filter((c) => c.priority > 0);
  if (prioritised.length > 0) {
    return prioritised.reduce((best, c) => (c.priority > best.priority ? c : best));
  }

  // sendCount is a non-negative integer from our own state doc, but guard anyway
  // so a corrupt value can't index out of bounds and return undefined.
  const offset = Math.abs(Math.trunc(ctx.sendCount)) % candidates.length;
  return candidates[offset];
}
