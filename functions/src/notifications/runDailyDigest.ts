import * as functions from 'firebase-functions/v1';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { buildDigestEmail } from './digestEmailTemplate';
import { buildNudgeEmail, NudgeKind } from './nudgeEmailTemplate';
import { ResendError } from '../email/resendClient';
import { lagosDateKey } from './lagosTime';
import { pushSummary } from './pushSummary';
import { staffDigests } from './staffDigest';
import { DigestIO, DigestRunResult } from './types';

/**
 * Neutral "open the app" link. Already shipped and verified on both platforms:
 * link.getstitchpad.com serves /.well-known/assetlinks.json and
 * apple-app-site-association listing /r, and `/r` is in the Android intent-filter
 * plus the iOS associated domains.
 *
 * Bare `/r` carries no referral code, so DeepLinkParser.parseReferral returns null
 * and the app simply opens where it normally would — no bogus attribution recorded.
 *
 * If the app is NOT installed the hosted /r page detects the platform and sends the
 * visitor to the right store. That is why this must not be a hardcoded store URL:
 * an iPhone user was being sent to Google Play whenever we had no FCM token to read
 * their platform from (33 of 152 accounts on 2026-08-20).
 *
 * On a build below the 618 force-update floor the app opens onto its own update
 * screen, which uses `config/app.updateUrlAndroid` / `updateUrlIos` — so the store
 * hand-off stays platform-correct there too.
 */
const OPEN_APP_URL = 'https://link.getstitchpad.com/r';

function ctaUrl(): string {
  return OPEN_APP_URL;
}

/** RFC 8058 one-click unsubscribe. Gmail/Yahoo require this at bulk volume. */
function unsubscribeHeaders(url: string): Record<string, string> | undefined {
  if (!url) return undefined;
  return { 'List-Unsubscribe': `<${url}>`, 'List-Unsubscribe-Post': 'List-Unsubscribe=One-Click' };
}

/** Pure run loop. Production wraps this with productionDigestIO; tests inject fakes. */
export async function runDailyDigest(io: DigestIO, now: number): Promise<DigestRunResult> {
  const recipients = await io.listRecipients();
  const todayKey = lagosDateKey(now);
  const result: DigestRunResult = {
    considered: recipients.length, sent: 0, staffPushed: 0, suppressedEmpty: 0,
    nudged: 0, skippedOptedOut: 0, skippedBounced: 0,
    skippedDisabled: 0, skippedUnverified: 0, skippedAlreadySent: 0,
    skippedNotAllowed: 0, failed: 0,
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
      // Explicit one-click unsubscribe outranks every other email rule.
      if (r.emailOptOut) { result.skippedOptedOut++; continue; }
      // Resend already rejected this address permanently; retrying it every morning
      // is what turns a young sending domain into a blocked one.
      if (r.hardBounce) { result.skippedBounced++; continue; }
      // EMAIL-ONLY gate. Everything above this line (inbox, owner push, staff
      // digests) has already run for an unverified owner — deliberately, since
      // none of it depends on the address being reachable.
      if (!r.emailVerified) { result.skippedUnverified++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }
      if ((await io.getLastSentDate(r.uid)) === todayKey) { result.skippedAlreadySent++; continue; }

      // A quiet morning used to `continue` here and send nothing at all, which on the
      // first production morning silenced 81 of 109 owners. The daily touchpoint now
      // always has something to say — but WHAT it says has to match how far in the
      // tailor actually is: "did a job come in today?" is nonsense to someone who has
      // never added a customer.
      const empty = isDigestEmpty(model);
      let kind: NudgeKind | null = null;
      if (empty) {
        if (orders.length > 0) kind = 'quiet';
        else kind = (await io.hasCustomers(r.uid)) ? 'first_order' : 'setup';
      }
      const opts = { ctaUrl: ctaUrl(), unsubscribeUrl: r.unsubscribeUrl };
      const { subject, html, text } = kind
        ? buildNudgeEmail(kind, r.name, opts)
        : buildDigestEmail(model, r.name, opts);

      // Stamp AFTER a successful send (at-least-once): if setLastSentDate throws
      // after the email went out, the next run may re-send — preferred over
      // stamping first and losing the email on a transient Resend failure.
      try {
        await io.sendEmail({
          to: r.email, subject, html, text, headers: unsubscribeHeaders(r.unsubscribeUrl),
        });
      } catch (sendErr) {
        // A permanent 4xx means the address itself is bad — suppress it for good.
        // 5xx / network errors are transient and must stay retryable tomorrow.
        if (sendErr instanceof ResendError && sendErr.status !== undefined
            && sendErr.status >= 400 && sendErr.status < 500) {
          await io.markHardBounce(r.uid);
          functions.logger.warn('daily digest: address hard-bounced, suppressed', {
            uid: r.uid, status: sendErr.status,
          });
        }
        throw sendErr;
      }
      await io.setLastSentDate(r.uid, todayKey);
      if (kind) result.nudged++; else result.sent++;
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
