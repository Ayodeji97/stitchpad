import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { createHash } from 'crypto';
import { buildPasswordResetEmail } from './passwordResetEmailTemplate';
import { sendResendEmail } from '../email/resendClient';

const REGION = 'europe-west1';
const SUBJECT = 'Reset your StitchPad password';
// Server-side rate limit, keyed by email (the caller is unauthenticated — these
// users have forgotten their password, so there's no uid to key on). Mirrors the
// verification email's 60s cooldown: stops anyone from spamming the reset
// endpoint to burn Resend quota / sender reputation, or to harass an inbox.
const THROTTLE_MS = 60_000;

export interface SendPasswordResetResult {
  // false when the email isn't registered — returned WITHOUT an error so the
  // client can't distinguish "sent" from "no such account" (enumeration guard).
  sent: boolean;
}

/**
 * Test seam over Firebase Auth + the email provider. Production wires this to
 * admin.auth() and Resend; tests inject fakes so the handler runs offline.
 */
export interface PasswordResetEmailIO {
  /** Returns the account's display name, or null if no account has this email. */
  getUserByEmail(email: string): Promise<{ displayName?: string } | null>;
  generateLink(email: string): Promise<string>;
  sendEmail(params: { to: string; displayName?: string; resetLink: string }): Promise<void>;
  /**
   * Atomically reserves a send for this email key, returning false if one
   * happened within the throttle window. The server-side rate limit.
   */
  reserveSend(emailKey: string): Promise<boolean>;
  /** Releases a reservation so a failed delivery doesn't block retries. */
  releaseSend(emailKey: string): Promise<void>;
}

function normalizeEmail(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const email = raw.trim().toLowerCase();
  // Deliberately permissive: just enough to reject obvious junk before we hit
  // Firebase Auth. Real validation is Firebase's job.
  if (email.length === 0 || email.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return null;
  }
  return email;
}

// Hash the email so the throttle doc id isn't a plaintext address sitting in a
// root collection (these docs aren't under users/{uid}, so they're not swept by
// onAuthUserDeleted — keep them opaque and let a TTL policy on `expireAt` reap them).
function emailKeyOf(email: string): string {
  return createHash('sha256').update(email).digest('hex');
}

/**
 * Pure handler for testing. Production wraps this in functions.https.onCall.
 * Intentionally UNauthenticated — password reset is for users who are locked
 * out. Sends a branded reset email via the injected IO, but never reveals
 * whether the address is registered.
 */
export async function sendPasswordResetEmailHandler(
  data: unknown,
  io: PasswordResetEmailIO,
): Promise<SendPasswordResetResult> {
  const email = normalizeEmail((data as { email?: unknown } | null | undefined)?.email);
  if (!email) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_email');
  }

  const key = emailKeyOf(email);
  const allowed = await io.reserveSend(key);
  if (!allowed) {
    throw new functions.https.HttpsError('resource-exhausted', 'password_reset_throttled');
  }

  try {
    const user = await io.getUserByEmail(email);
    if (!user) {
      // No account: succeed silently without sending. The reservation stays so
      // repeated probes get throttled identically to real addresses (no timing
      // or rate-limit signal to enumerate from).
      return { sent: false };
    }
    const resetLink = await io.generateLink(email);
    await io.sendEmail({ to: email, displayName: user.displayName, resetLink });
    return { sent: true };
  } catch (err) {
    // Release the reservation so a genuine delivery failure doesn't lock the
    // user out of retrying for the full throttle window.
    await io.releaseSend(key).catch(() => undefined);
    functions.logger.error('password reset email send failed', {
      error: err instanceof Error ? err.message : String(err),
    });
    throw new functions.https.HttpsError('unavailable', 'email_send_failed');
  }
}

async function sendViaResend(
  apiKey: string,
  params: { to: string; displayName?: string; resetLink: string },
): Promise<void> {
  const { html, text } = buildPasswordResetEmail({
    displayName: params.displayName,
    resetLink: params.resetLink,
  });
  await sendResendEmail(apiKey, { to: params.to, subject: SUBJECT, html, text });
}

function productionIO(apiKey: string): PasswordResetEmailIO {
  const db = admin.firestore();
  const throttleRef = (key: string) => db.collection('mailThrottle').doc(key);
  return {
    async getUserByEmail(email) {
      try {
        const user = await admin.auth().getUserByEmail(email);
        return { displayName: user.displayName };
      } catch (err) {
        if ((err as { code?: string }).code === 'auth/user-not-found') {
          return null;
        }
        throw err;
      }
    },
    generateLink(email) {
      // No ActionCodeSettings yet → Firebase's default hosted reset page. When
      // the app deep-link infra lands, pass ActionCodeSettings here to open the
      // app instead (the email template needs no change).
      return admin.auth().generatePasswordResetLink(email);
    },
    sendEmail(params) {
      return sendViaResend(apiKey, params);
    },
    reserveSend(key) {
      const ref = throttleRef(key);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const now = Date.now();
        const lastSent: number = (snap.exists && snap.data()?.passwordResetLastSentMillis) || 0;
        if (now - lastSent < THROTTLE_MS) {
          return false;
        }
        tx.set(
          ref,
          {
            passwordResetLastSentMillis: now,
            // For a Firestore TTL policy to reap these opaque throttle docs.
            expireAt: admin.firestore.Timestamp.fromMillis(now + THROTTLE_MS),
          },
          { merge: true },
        );
        return true;
      });
    },
    async releaseSend(key) {
      await throttleRef(key).delete();
    },
  };
}

export const sendPasswordResetEmail = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (data) => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      functions.logger.error('RESEND_API_KEY secret is not configured');
      throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');
    }
    return sendPasswordResetEmailHandler(data, productionIO(apiKey));
  });
