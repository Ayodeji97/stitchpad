import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { buildVerificationEmailHtml } from './verificationEmailTemplate';

const REGION = 'europe-west1';
const FROM = 'StitchPad <noreply@send.getstitchpad.com>';
const REPLY_TO = 'support@getstitchpad.com';
const SUBJECT = 'Verify your email for StitchPad';
const RESEND_ENDPOINT = 'https://api.resend.com/emails';
// Server-side backstop matching the client's 60s resend cooldown, so a client
// calling the callable directly can't bypass the UI throttle and burn Resend
// quota / sender reputation.
const THROTTLE_MS = 60_000;

export interface SendVerificationResult {
  sent: boolean;
  alreadyVerified: boolean;
}

/**
 * Test seam over Firebase Auth + the email provider. Production wires this to
 * admin.auth() and Resend; tests inject fakes so the handler runs offline.
 */
export interface VerificationEmailIO {
  getUser(uid: string): Promise<{ email?: string; displayName?: string; emailVerified: boolean }>;
  generateLink(email: string): Promise<string>;
  sendEmail(params: { to: string; displayName?: string; verifyLink: string }): Promise<void>;
  /**
   * Atomically reserves a send for this uid, returning false if one happened
   * within the throttle window. The server-side rate limit.
   */
  reserveSend(uid: string): Promise<boolean>;
  /** Releases a reservation so a failed delivery doesn't block retries. */
  releaseSend(uid: string): Promise<void>;
}

/**
 * Pure handler for testing. Production wraps this in functions.https.onCall.
 * Requires an authenticated caller; sends a branded verification email via the
 * injected IO unless the account is already verified.
 */
export async function sendVerificationEmailHandler(
  context: functions.https.CallableContext,
  io: VerificationEmailIO,
): Promise<SendVerificationResult> {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }

  const user = await io.getUser(uid);
  if (!user.email) {
    throw new functions.https.HttpsError('failed-precondition', 'no_email_on_account');
  }
  if (user.emailVerified) {
    return { sent: false, alreadyVerified: true };
  }

  const allowed = await io.reserveSend(uid);
  if (!allowed) {
    throw new functions.https.HttpsError('resource-exhausted', 'verification_email_throttled');
  }

  try {
    const verifyLink = await io.generateLink(user.email);
    await io.sendEmail({ to: user.email, displayName: user.displayName, verifyLink });
  } catch (err) {
    // Release the reservation so a genuine delivery failure doesn't lock the
    // user out of retrying for the full throttle window.
    await io.releaseSend(uid).catch(() => undefined);
    functions.logger.error('verification email send failed', {
      uid,
      error: err instanceof Error ? err.message : String(err),
    });
    throw new functions.https.HttpsError('unavailable', 'email_send_failed');
  }

  return { sent: true, alreadyVerified: false };
}

async function sendViaResend(
  apiKey: string,
  params: { to: string; displayName?: string; verifyLink: string },
): Promise<void> {
  const html = buildVerificationEmailHtml({
    displayName: params.displayName,
    verifyLink: params.verifyLink,
  });
  const response = await fetch(RESEND_ENDPOINT, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      from: FROM,
      to: [params.to],
      reply_to: REPLY_TO,
      subject: SUBJECT,
      html,
    }),
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(`Resend responded ${response.status}: ${detail}`);
  }
}

function productionIO(apiKey: string): VerificationEmailIO {
  const db = admin.firestore();
  return {
    async getUser(uid) {
      const user = await admin.auth().getUser(uid);
      return {
        email: user.email,
        displayName: user.displayName,
        emailVerified: user.emailVerified,
      };
    },
    generateLink(email) {
      return admin.auth().generateEmailVerificationLink(email);
    },
    sendEmail(params) {
      return sendViaResend(apiKey, params);
    },
    reserveSend(uid) {
      const ref = db.collection('email_throttle').doc(uid);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const now = Date.now();
        const lastSent: number = (snap.exists && snap.data()?.verificationLastSentMillis) || 0;
        if (now - lastSent < THROTTLE_MS) {
          return false;
        }
        tx.set(ref, { verificationLastSentMillis: now }, { merge: true });
        return true;
      });
    },
    async releaseSend(uid) {
      await db.collection('email_throttle').doc(uid).delete();
    },
  };
}

export const sendVerificationEmail = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (_data, context) => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      functions.logger.error('RESEND_API_KEY secret is not configured');
      throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');
    }
    return sendVerificationEmailHandler(context, productionIO(apiKey));
  });
