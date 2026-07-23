import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { normalizeEmail } from './passwordResetShared';
import { buildPasswordResetEmail } from './passwordResetEmailTemplate';
import { rewriteActionLinkHost } from './actionLinkHost';
import { sendResendEmail } from '../email/resendClient';

const REGION = 'europe-west1';
const SUBJECT = 'Reset your StitchPad password';

/**
 * Test seam over Firebase Auth + the email provider. Production wires this to
 * admin.auth() and Resend; tests inject fakes so the handler runs offline.
 */
export interface PasswordResetWorkerIO {
  /** Returns the account's display name, or null if no account has this email. */
  getUserByEmail(email: string): Promise<{ displayName?: string } | null>;
  generateLink(email: string): Promise<string>;
  sendEmail(params: { to: string; displayName?: string; resetLink: string }): Promise<void>;
}

/**
 * Pure handler for testing. Runs off the response path (dispatched by Cloud
 * Tasks from the sendPasswordResetEmail callable), so its timing is invisible to
 * the original caller — this is where the account-existence check lives.
 *
 * Throwing here is intentional: it tells Cloud Tasks to retry (transient Resend /
 * Auth failures). A malformed payload is dropped (returns) so it doesn't retry
 * forever — the enqueuer already validated, this is defense-in-depth.
 */
export async function processPasswordResetEmailHandler(
  data: unknown,
  io: PasswordResetWorkerIO,
): Promise<void> {
  const email = normalizeEmail((data as { email?: unknown } | null | undefined)?.email);
  if (!email) {
    functions.logger.error('password reset task has no valid email; dropping');
    return;
  }

  const user = await io.getUserByEmail(email);
  if (!user) {
    // No account (or deleted between enqueue and dispatch) — nothing to send.
    return;
  }

  const resetLink = rewriteActionLinkHost(await io.generateLink(email));
  await io.sendEmail({ to: email, displayName: user.displayName, resetLink });
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

function productionIO(apiKey: string): PasswordResetWorkerIO {
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
  };
}

export const processPasswordResetEmail = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .tasks.taskQueue({
    retryConfig: { maxAttempts: 5, minBackoffSeconds: 5, maxBackoffSeconds: 60 },
    // Cap concurrent sends so a burst can't exhaust Resend quota / sender reputation.
    rateLimits: { maxConcurrentDispatches: 6 },
  })
  .onDispatch(async (data) => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      // Throw (not drop) — a missing secret is operator error, not a bad task;
      // let Tasks retry so a fixed config recovers queued resets.
      functions.logger.error('RESEND_API_KEY secret is not configured');
      throw new Error('email_not_configured');
    }
    await processPasswordResetEmailHandler(data, productionIO(apiKey));
  });
