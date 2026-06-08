import * as functions from 'firebase-functions/v1';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { buildDigestEmail } from './digestEmailTemplate';
import { lagosDateKey } from './lagosTime';
import { DigestIO, DigestRunResult } from './types';

/** Pure run loop. Production wraps this with productionDigestIO; tests inject fakes. */
export async function runDailyDigest(io: DigestIO, now: number): Promise<DigestRunResult> {
  const recipients = await io.listRecipients();
  const todayKey = lagosDateKey(now);
  const result: DigestRunResult = {
    considered: recipients.length, sent: 0, suppressedEmpty: 0,
    skippedDisabled: 0, skippedAlreadySent: 0, skippedNotAllowed: 0, failed: 0,
  };

  for (const r of recipients) {
    try {
      const model = digestDetector(await io.loadOrders(r.uid), now);
      await io.writeNotifications(r.uid, model);   // ALWAYS — in-app inbox is ungated

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
