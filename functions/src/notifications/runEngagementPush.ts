/**
 * Pure run loop for the twice-weekly engagement push. Production wiring lives in
 * engagementPush.ts; tests inject fakes through {@link EngagementIO}.
 *
 * Mirrors runDailyDigest's shape on purpose — same injected-IO seam, same
 * per-recipient try/catch so one bad user cannot abort the run, same counted
 * result logged at the end.
 */
import * as functions from 'firebase-functions/v1';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { parseEngagementConfig } from './engagementConfig';
import { isEngagementDay, needsOrders, selectCampaign } from './engagementSelector';
import { EngagementIO, EngagementRunResult } from './engagementTypes';
import { OrderScanDoc } from './types';
import { renderTemplate } from './campaignTemplate';
import { DAY_MS, lagosDateKey, lagosDayIndex, lagosWeekday } from './lagosTime';
import { needsDigestForSegments, segmentChain } from './segmentDetector';

/**
 * Whole days since the most recently CREATED order, or null when there are none.
 *
 * createdAt, not updatedAt: dormancy is about whether new work is being logged. A
 * tailor who edits an old order has not started anything new, and updatedAt is also
 * bumped by our own server writes, which would mask the very state we are detecting.
 */
export function daysSinceNewestOrder(orders: { createdAt?: number }[], now: number): number | null {
  const newest = orders.reduce<number | null>((max, o) => {
    const t = typeof o.createdAt === 'number' ? o.createdAt : null;
    return t !== null && (max === null || t > max) ? t : max;
  }, null);
  if (newest === null) return null;
  return Math.max(0, Math.floor((now - newest) / DAY_MS));
}

/**
 * Delivered orders with no cost recorded.
 *
 * Costs live in /private/money and are joined on by loadOrders. An empty costs array on
 * a finished order means the tailor cannot see what they actually made on it — the
 * profit figure is simply absent, which is invisible until someone goes looking.
 *
 * Only DELIVERED counts: an order still in progress may legitimately have costs still
 * to come, and nagging about it would be wrong.
 */
export function countDeliveredWithoutCosts(orders: OrderScanDoc[]): number {
  return orders.filter((o) =>
    o.status === 'DELIVERED' && o.archivedAt == null && (o.costs?.length ?? 0) === 0).length;
}

function emptyResult(): EngagementRunResult {
  return {
    considered: 0,
    sent: 0,
    skippedDisabled: 0,
    skippedNotEngagementDay: 0,
    skippedOptedOut: 0,
    skippedNotAllowed: 0,
    skippedPushedToday: 0,
    skippedCadence: 0,
    skippedNoValidCampaigns: 0,
    skippedNoCampaign: 0,
    skippedNoTokens: 0,
    failed: 0,
  };
}

export async function runEngagementPush(io: EngagementIO, now: number): Promise<EngagementRunResult> {
  const result = emptyResult();
  const config = parseEngagementConfig(await io.loadConfig());

  // Global short-circuits BEFORE listing recipients: a disabled config or a
  // non-send day must cost one document read, not a full users scan.
  if (!config.enabled) {
    result.skippedDisabled = 1;
    functions.logger.info('engagement push: disabled by config');
    return result;
  }
  // Re-checked here and not only in cron, so a mis-set schedule or a manual
  // invoke still cannot turn a twice-weekly nudge into a daily one.
  if (!isEngagementDay(config, now)) {
    result.skippedNotEngagementDay = 1;
    // Deliberately logs BOTH sides. config.daysOfWeek can only narrow the deployed
    // cron, never move it, so an operator who sets days the cron never fires on gets
    // silence — this line is what makes that diagnosable instead of mysterious.
    functions.logger.info('engagement push: not a configured send day', {
      lagosWeekday: lagosWeekday(now),
      configuredDays: config.daysOfWeek,
    });
    return result;
  }
  if (config.campaigns.length === 0) {
    result.skippedNoValidCampaigns = 1;
    functions.logger.warn('engagement push: no valid campaigns — check config/engagementPush');
    return result;
  }

  const todayKey = lagosDateKey(now);
  const todayIndex = lagosDayIndex(now);
  const wantOrders = needsOrders(config, now);

  const recipients = await io.listRecipients();
  result.considered = recipients.length;

  for (const r of recipients) {
    try {
      if (!r.announcementsEnabled) { result.skippedOptedOut++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }

      const state = await io.loadState(r.uid);

      // The load-bearing brake. The digest runs at 07:00 and stamps lastPushDate;
      // this job runs at 10:00, so a user who already heard from us today is left
      // alone. Two notifications in one morning is how an app gets muted.
      if (state.lastPushDate === todayKey) { result.skippedPushedToday++; continue; }

      if (
        state.lastSentDayIndex !== null &&
        todayIndex - state.lastSentDayIndex < config.minDaysBetween
      ) {
        result.skippedCadence++;
        continue;
      }

      const counts = await io.loadCounts(r.uid);

      // The orders read is the one expensive call in this loop, so it is decided
      // PER USER, not once for the run: only a tailor who has cleared every
      // activation rung can land in `quiet`, so anyone still short of that is
      // already settled by the two count queries above. Skipping them is the
      // difference between scanning every order of every user and scanning the
      // orders of the activated minority.
      let digestEmpty = false;
      let daysSinceLastOrder: number | null = null;
      let deliveredWithoutCosts: number | null = null;
      if (wantOrders && needsDigestForSegments(counts)) {
        const orders = await io.loadOrders(r.uid);
        digestEmpty = isDigestEmpty(digestDetector(orders, now));
        daysSinceLastOrder = daysSinceNewestOrder(orders, now);
        deliveredWithoutCosts = countDeliveredWithoutCosts(orders);
      }

      const segments = segmentChain({
        customerCount: counts.customerCount,
        orderCount: counts.orderCount,
        teamCount: counts.teamCount,
        hasReferralLink: r.hasReferralLink,
        digestEmpty,
        daysSinceLastOrder,
        welcomeDaysLeft: r.welcomeDaysLeft,
        deliveredWithoutCosts,
        tier: r.tier,
      });

      const templateVars = {
        businessName: r.businessName,
        points: r.points,
        customerCount: counts.customerCount,
        orderCount: counts.orderCount,
      };

      const campaign = selectCampaign(
        config,
        { segments, sendCount: state.sendCount, campaignCounts: state.campaignCounts },
        now,
      );
      if (!campaign) { result.skippedNoCampaign++; continue; }

      const tokens = await io.loadPushTokens(r.uid);
      if (tokens.length === 0) { result.skippedNoTokens++; continue; }

      // Personalise last, so selection stays pure and testable on the raw copy.
      const rendered = {
        ...campaign,
        title: renderTemplate(campaign.title, templateVars),
        body: renderTemplate(campaign.body, templateVars),
      };

      const { successCount, invalidTokens } = await io.sendPush(tokens, rendered);
      if (invalidTokens.length > 0) await io.deletePushTokens(r.uid, invalidTokens);

      // Record only on a real delivery. Stamping a failed send would burn the
      // user's cadence window and their per-campaign cap for a push they never got.
      if (successCount > 0) {
        await io.recordSent(r.uid, campaign.id, todayKey, todayIndex);
        result.sent++;
      } else {
        result.failed++;
      }
    } catch (err) {
      result.failed++;
      functions.logger.error('engagement push: recipient failed', {
        uid: r.uid,
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  functions.logger.info('engagement push run complete', { ...result });
  return result;
}
