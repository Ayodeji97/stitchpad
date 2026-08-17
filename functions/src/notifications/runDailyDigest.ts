import * as functions from 'firebase-functions/v1';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { buildDigestEmail } from './digestEmailTemplate';
import { lagosDateKey } from './lagosTime';
import { pushSummary } from './pushSummary';
import { staffDigests } from './staffDigest';
import { DigestIO, DigestRunResult } from './types';

/** Pure run loop. Production wraps this with productionDigestIO; tests inject fakes. */
export async function runDailyDigest(io: DigestIO, now: number): Promise<DigestRunResult> {
  const recipients = await io.listRecipients();
  const todayKey = lagosDateKey(now);
  const result: DigestRunResult = {
    considered: recipients.length, sent: 0, staffPushed: 0, suppressedEmpty: 0,
    skippedDisabled: 0, skippedAlreadySent: 0, skippedNotAllowed: 0, failed: 0,
  };

  for (const r of recipients) {
    try {
      const orders = await io.loadOrders(r.uid);
      const model = digestDetector(orders, now);
      await io.writeNotifications(r.uid, model);   // ALWAYS — in-app inbox is ungated

      // PUSH (Android slice 3) — gated independently of email. Its OWN try/catch so a
      // push failure (FCM down, token load, stamp) never blocks the email digest below.
      try {
        if (
          r.pushEnabled &&
          io.isAllowed(r.uid, r.email) &&
          !isDigestEmpty(model) &&
          (await io.getLastPushDate(r.uid)) !== todayKey
        ) {
          const tokens = await io.loadPushTokens(r.uid);
          if (tokens.length > 0) {
            const { successCount, invalidTokens } = await io.sendPush(tokens, pushSummary(model));
            if (invalidTokens.length > 0) {
              await io.deletePushTokens(r.uid, invalidTokens);
            }
            if (successCount > 0) {
              await io.setLastPushDate(r.uid, todayKey);
            }
          }
        }
      } catch (pushErr) {
        functions.logger.error('daily digest: push failed (email unaffected)', {
          uid: r.uid,
          error: pushErr instanceof Error ? pushErr.message : String(pushErr),
        });
      }

      // STAFF DIGESTS — the person actually sewing has never been told about their
      // own deadlines. Reuses the orders already loaded above, so it costs no extra
      // read of the workshop. Its own try/catch: a staff push must never cost the
      // owner their digest, which is the more important message of the two.
      // Honour the SAME rollout gate as the owner. This block runs before the owner's
      // gate below, so without it an emergency STAGING flip would stop owner digests
      // while staff pushes carried on — defeating the one-line rollback.
      try {
        const staffUids = io.isAllowed(r.uid, r.email) ? await io.listStaffUids(r.uid) : [];
        for (const d of staffDigests(orders, staffUids, now)) {
          if (!(await io.isStaffPushEnabled(d.staffUid))) continue;
          if ((await io.getLastPushDate(d.staffUid)) === todayKey) continue;
          const tokens = await io.loadPushTokens(d.staffUid);
          if (tokens.length === 0) continue;
          const { successCount, invalidTokens } = await io.sendPush(
            tokens,
            // NOT the owner's 'to_collect' target: firestore.rules denies staff the money
            // surface, so that tap dead-ends on a blank screen — and it aims the money
            // screen at people who must never see it.
            { ...pushSummary(d.model), target: 'dashboard' },
          );
          if (invalidTokens.length > 0) await io.deletePushTokens(d.staffUid, invalidTokens);
          if (successCount > 0) {
            await io.setLastPushDate(d.staffUid, todayKey);
            result.staffPushed++;
          }
        }
      } catch (staffErr) {
        functions.logger.error('daily digest: staff digests failed (owner unaffected)', {
          uid: r.uid,
          error: staffErr instanceof Error ? staffErr.message : String(staffErr),
        });
      }

      if (!r.digestEnabled) { result.skippedDisabled++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }
      if ((await io.getLastSentDate(r.uid)) === todayKey) { result.skippedAlreadySent++; continue; }
      if (isDigestEmpty(model)) { result.suppressedEmpty++; continue; }

      const { subject, html, text } = buildDigestEmail(model, r.name);
      // Stamp AFTER a successful send (at-least-once): if setLastSentDate throws
      // after the email went out, the next run may re-send — preferred over
      // stamping first and losing the email on a transient Resend failure.
      await io.sendEmail({ to: r.email, subject, html, text });
      await io.setLastSentDate(r.uid, todayKey);
      result.sent++;
    } catch (err) {
      result.failed++;
      functions.logger.error('daily digest: recipient failed', {
        uid: r.uid, error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  functions.logger.info('daily digest run complete', { ...result });
  return result;
}
