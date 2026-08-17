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
import { lagosDateKey, lagosDayIndex, lagosWeekday } from './lagosTime';
import { needsDigestForSegments, segmentChain } from './segmentDetector';

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
      const digestEmpty = wantOrders && needsDigestForSegments(counts)
        ? isDigestEmpty(digestDetector(await io.loadOrders(r.uid), now))
        : false;

      const segments = segmentChain({
        customerCount: counts.customerCount,
        orderCount: counts.orderCount,
        teamCount: counts.teamCount,
        hasReferralLink: r.hasReferralLink,
        digestEmpty,
        tier: r.tier,
      });

      const campaign = selectCampaign(
        config,
        { segments, sendCount: state.sendCount, campaignCounts: state.campaignCounts },
        now,
      );
      if (!campaign) { result.skippedNoCampaign++; continue; }

      const tokens = await io.loadPushTokens(r.uid);
      if (tokens.length === 0) { result.skippedNoTokens++; continue; }

      const { successCount, invalidTokens } = await io.sendPush(tokens, campaign);
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
