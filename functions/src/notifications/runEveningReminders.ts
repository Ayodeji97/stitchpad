/**
 * Pure run loop for the evening reminder push. Production wiring lives in
 * eveningReminders.ts; tests inject fakes through {@link ReminderIO}.
 *
 * Same shape as runDailyDigest / runEngagementPush: one config read, a per-user
 * try/catch so one bad workshop cannot abort the run, one summary log at the end.
 *
 * THE INVARIANT: at most ONE evening push per owner per Lagos day. Everything that
 * applies tonight is folded into that one push; the nudge only goes when nothing
 * else does. It is tracked separately from the morning digest's `lastPushDate`, so
 * the 07:00 digest never suppresses it and it never suppresses tomorrow's digest.
 */
import * as functions from 'firebase-functions/v1';
import { lagosDateKey } from './lagosTime';
import { nudgePush, reminderPush } from './reminderCopy';
import {
  detectReminders, lagosStartOfDay, leadKind, nudgeDecision, planSize, stampSent,
} from './reminderDetector';
import { ReminderIO, ReminderItem, ReminderPushPayload, ReminderRunResult } from './reminderTypes';

export interface RunOptions {
  /**
   * Debug only: ignore the once-per-evening cap so a tester can re-send to
   * themselves. Every other rule (opt-outs, kill switch, per-order caps) holds.
   */
  ignoreDailyCap?: boolean;
}

function emptyResult(): ReminderRunResult {
  return {
    considered: 0, sent: 0, sentDue: 0, sentOwed: 0, sentReady: 0, sentNudge: 0, items: 0,
    skippedKillSwitch: 0, skippedOptedOut: 0, skippedNotAllowed: 0, skippedAlreadyPushedToday: 0,
    skippedNothingToSay: 0, skippedNoTokens: 0, failed: 0,
  };
}

export async function runEveningReminders(io: ReminderIO, now: number, opts: RunOptions = {}): Promise<ReminderRunResult> {
  const result = emptyResult();

  // Emergency brake, read ONCE. A failed read sends nothing: these reminders are
  // a nice-to-have, and an unreadable switch must not be mistaken for "on".
  let enabled = false;
  try {
    enabled = await io.isEnabled();
  } catch (err) {
    functions.logger.error('evening reminders: could not read config/app.reminderPushEnabled; sending nothing', {
      error: err instanceof Error ? err.message : String(err),
    });
  }
  if (!enabled) {
    result.skippedKillSwitch = 1;
    functions.logger.info('evening reminders run complete', { ...result });
    return result;
  }

  const todayKey = lagosDateKey(now);
  const recipients = await io.listRecipients();
  result.considered = recipients.length;

  for (const r of recipients) {
    try {
      // Same inheritance as the digest (resolved in listRecipients), plus #409's
      // workshop-activity switch when present.
      if (!r.pushEnabled || !r.activityPushEnabled) { result.skippedOptedOut++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }

      const state = await io.loadState(r.uid);
      if (!opts.ignoreDailyCap && state.lastEveningPushDate === todayKey) {
        result.skippedAlreadyPushedToday++;
        continue;
      }

      const orders = await io.loadOrders(r.uid);
      const plan = detectReminders(orders, state, now);

      let payload: ReminderPushPayload;
      let items: ReminderItem[] = [];
      let nudged = false;
      if (planSize(plan) > 0) {
        items = [...plan.due, ...plan.owed, ...plan.ready];
        payload = reminderPush(plan, now, r.locale);
        // Inbox first and regardless of tokens, like the digest: the in-app list
        // is the record of what we told them. Deterministic ids → no duplicates.
        await io.writeInbox(r.uid, items);
      } else {
        const decision = nudgeDecision(orders, state, now, todayKey);
        if (decision !== 'eligible' || await io.hasCustomerActivitySince(r.uid, lagosStartOfDay(now))) {
          result.skippedNothingToSay++;
          continue;
        }
        payload = nudgePush(r.locale);
        nudged = true;
      }

      const tokens = await io.loadPushTokens(r.uid);
      if (tokens.length === 0) { result.skippedNoTokens++; continue; }

      const { successCount, invalidTokens } = await io.sendPush(tokens, payload);
      if (invalidTokens.length > 0) await io.deletePushTokens(r.uid, invalidTokens);

      // Stamp only on a real delivery: a failed send must not burn an order's
      // once-only reminder or the evening cap.
      if (successCount === 0) { result.failed++; continue; }
      await io.saveState(r.uid, stampSent(state, items, orders, now, todayKey, nudged));

      result.sent++;
      result.items += items.length;
      if (nudged) result.sentNudge++;
      else {
        const lead = leadKind(plan);
        if (lead === 'due') result.sentDue++;
        else if (lead === 'owed') result.sentOwed++;
        else result.sentReady++;
      }
    } catch (err) {
      result.failed++;
      functions.logger.error('evening reminders: recipient failed', {
        uid: r.uid, error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  functions.logger.info('evening reminders run complete', { ...result });
  return result;
}
