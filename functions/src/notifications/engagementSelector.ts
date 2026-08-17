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

/** Segments that can only be decided by reading a tailor's full order list. */
const ORDER_DEPENDENT_SEGMENTS: readonly Segment[] = ['quiet', 'dormant', 'no_costs'];

/**
 * Whether this run needs each tailor's full order list.
 *
 * `quiet` needs digestDetector and `dormant` needs the newest order's createdAt —
 * both come from the same read, which is the one expensive call in the loop. When no
 * live campaign targets either, skipping it turns an O(users × orders) run into an
 * O(users) one.
 */
export function needsOrders(config: EngagementConfig, now: number): boolean {
  return liveCampaigns(config, now).some((c) => ORDER_DEPENDENT_SEGMENTS.includes(c.segment));
}

export interface SelectionContext {
  /** Applicable segments, most specific first, ending in 'all'. See segmentChain. */
  segments: Segment[];
  /** How many engagement pushes this user has ever received — drives rotation. */
  sendCount: number;
  /** Per-campaign lifetime send tally for this user, keyed by campaign id. */
  campaignCounts: Record<string, number>;
}

/**
 * The campaign to send, or null when nothing applies.
 *
 * A campaign is eligible when it is inside its window, targets one of the user's
 * segments, and is under that user's per-campaign cap. Among the eligible set:
 *
 *   - Any candidate with `priority > 0` wins outright, highest first, REGARDLESS of
 *     where its segment sits in the chain. That is what lets a `segment: "all"`
 *     release announcement interrupt a brand-new tailor's `no_customer` copy — with
 *     segment-position taking precedence instead, announcements would only ever
 *     reach fully-activated tailors.
 *   - Otherwise the most specific segment with anything eligible wins, and we rotate
 *     deterministically within it: candidates sorted by id, indexed by the user's
 *     total send count. Deterministic beats random because it is testable without
 *     seeding and guarantees a user alternates rather than drawing the same message
 *     twice running.
 *
 * Walking the chain rather than matching one segment is what stops a tailor going
 * permanently silent once their most specific segment's campaigns are used up.
 *
 * Ties on priority fall back to id order, so the choice is never arbitrary.
 */
export function selectCampaign(
  config: EngagementConfig,
  ctx: SelectionContext,
  now: number,
): EngagementCampaign | null {
  const live = liveCampaigns(config, now)
    .filter((c) => c.maxSendsPerUser === 0 || (ctx.campaignCounts[c.id] ?? 0) < c.maxSendsPerUser);

  const eligibleFor = (segment: Segment) => live
    .filter((c) => c.segment === segment)
    .sort((a, b) => a.id.localeCompare(b.id));

  // Priority override first, across every segment the user belongs to.
  const prioritised = ctx.segments
    .flatMap((segment) => eligibleFor(segment))
    .filter((c) => c.priority > 0)
    .sort((a, b) => (b.priority - a.priority) || a.id.localeCompare(b.id));
  if (prioritised.length > 0) return prioritised[0];

  // Otherwise the most specific segment that still has copy available.
  for (const segment of ctx.segments) {
    const candidates = eligibleFor(segment);
    if (candidates.length === 0) continue;
    // sendCount is a non-negative integer from our own state doc, but guard anyway
    // so a corrupt value can't index out of bounds and return undefined.
    const offset = Math.abs(Math.trunc(ctx.sendCount)) % candidates.length;
    return candidates[offset];
  }
  return null;
}
