/**
 * One-click unsubscribe for the daily email.
 *
 * Required, not optional: from 2026-08-20 the daily email goes to every account
 * rather than only those with work due, and Gmail/Yahoo bulk-sender rules require a
 * working `List-Unsubscribe` + `List-Unsubscribe-Post` pair on that volume. Without
 * it the send reputation of send.getstitchpad.com degrades and takes the
 * verification and password-reset mail down with it.
 *
 * The opt-out flag lives in TOP-LEVEL `emailPrefs/{uid}`, deliberately not on
 * `users/{uid}`: 41 of 154 accounts have no users doc at all (they never finished
 * workshop setup), and those are precisely the people most likely to want out.
 */
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v1';
import { createHmac, timingSafeEqual } from 'crypto';

const REGION = 'europe-west1';

/** Truncated HMAC over the uid. 32 hex chars = 128 bits, far past guessing range. */
export function unsubscribeToken(uid: string, secret: string): string {
  return createHmac('sha256', secret).update(uid).digest('hex').slice(0, 32);
}

export function buildUnsubscribeUrl(uid: string, secret: string, baseUrl: string): string {
  return `${baseUrl}?u=${encodeURIComponent(uid)}&t=${unsubscribeToken(uid, secret)}`;
}

/** Constant-time compare that cannot throw on a length mismatch. */
export function tokenMatches(expected: string, provided: string): boolean {
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

function page(title: string, body: string): string {
  return `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" /><title>${title}</title></head>
<body style="margin:0;padding:48px 16px;background:#FAF6EC;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
<div style="max-width:420px;margin:0 auto;background:#FFFFFF;border:1px solid #E5E3DF;border-radius:14px;padding:32px;">
<div style="font-size:18px;font-weight:800;color:#2C3E7C;margin-bottom:18px;">StitchPad</div>
${body}</div></body></html>`;
}

export const emailUnsubscribe = functions
  .region(REGION)
  .runWith({ secrets: ['EMAIL_UNSUB_SECRET'] })
  .https.onRequest(async (req, res) => {
    const secret = process.env.EMAIL_UNSUB_SECRET;
    if (!secret) {
      functions.logger.error('emailUnsubscribe: EMAIL_UNSUB_SECRET not configured');
      res.status(500).send(page('Error', '<p>Something went wrong. Please reply to any StitchPad email and we will remove you by hand.</p>'));
      return;
    }

    const uid = String(req.query.u ?? req.body?.u ?? '');
    const token = String(req.query.t ?? req.body?.t ?? '');
    if (!uid || !token || !tokenMatches(unsubscribeToken(uid, secret), token)) {
      res.status(400).send(page('Invalid link', '<p>This unsubscribe link is not valid. Reply to any StitchPad email and we will remove you by hand.</p>'));
      return;
    }

    // GET only CONFIRMS. Email security scanners and inbox previewers prefetch links,
    // so performing the opt-out on GET would silently unsubscribe active tailors who
    // never clicked anything. Gmail's one-click path POSTs, which is handled below.
    if (req.method === 'GET') {
      res.status(200).send(page('Unsubscribe', `
<p style="font-size:15px;line-height:1.6;color:#252320;">Stop receiving the daily StitchPad email?</p>
<form method="POST">
<input type="hidden" name="u" value="${uid.replace(/"/g, '&quot;')}" />
<input type="hidden" name="t" value="${token.replace(/"/g, '&quot;')}" />
<button type="submit" style="margin-top:14px;background:#2C3E7C;color:#FFFFFF;border:0;border-radius:10px;padding:13px 26px;font-size:15px;font-weight:700;cursor:pointer;">Yes, unsubscribe me</button>
</form>
<p style="margin-top:20px;font-size:13px;color:#57534C;">Your account and everything you have saved stay exactly as they are.</p>`));
      return;
    }

    try {
      await admin.firestore().collection('emailPrefs').doc(uid).set(
        { optOut: true, optOutAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true },
      );
    } catch (err) {
      functions.logger.error('emailUnsubscribe: write failed', {
        uid, error: err instanceof Error ? err.message : String(err),
      });
      res.status(500).send(page('Error', '<p>We could not save that. Please reply to any StitchPad email and we will remove you by hand.</p>'));
      return;
    }

    functions.logger.info('emailUnsubscribe: opted out', { uid });
    res.status(200).send(page('Unsubscribed', `
<p style="font-size:15px;line-height:1.6;color:#252320;">Done — you will not get the daily StitchPad email again.</p>
<p style="margin-top:16px;font-size:13px;color:#57534C;">Your account and your saved work are untouched. You can turn the daily summary back on any time under Settings → Preferences.</p>`));
  });
