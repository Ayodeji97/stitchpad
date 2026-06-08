import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { getFunctions } from 'firebase-admin/functions';
import { createHash } from 'crypto';
import { normalizeEmail } from './passwordResetShared';

const REGION = 'europe-west1';
// Fully-qualified target so the enqueue resolves the queue in the right region
// (the worker is deployed to europe-west1, not the admin SDK's default location).
const WORKER_RESOURCE = `locations/${REGION}/functions/processPasswordResetEmail`;
// Server-side rate limit, keyed by email (the caller is unauthenticated — these
// users have forgotten their password, so there's no uid to key on). Stops anyone
// from spamming the reset endpoint to burn Resend quota / sender reputation, or to
// harass an inbox. Also bounds how often we enqueue work.
const THROTTLE_MS = 60_000;

export interface SendPasswordResetResult {
  // A constant acknowledgement. Deliberately carries NO signal about whether the
  // address was registered or an email actually went out. The callable does the
  // SAME work for every email — normalize, throttle, enqueue — and the real
  // send/no-send decision happens later in the worker, off the response path. So
  // neither the payload NOR the response timing can be used to enumerate accounts.
  ok: true;
}

const ACK: SendPasswordResetResult = { ok: true };

/**
 * Test seam. Production wires this to the throttle (Firestore) and the Cloud
 * Tasks queue; tests inject fakes so the handler runs offline.
 */
export interface PasswordResetEnqueuerIO {
  /**
   * Atomically reserves a send for this email key, returning false if one
   * happened within the throttle window. The server-side rate limit.
   */
  reserveSend(emailKey: string): Promise<boolean>;
  /** Releases a reservation so a failed enqueue doesn't block retries. */
  releaseSend(emailKey: string): Promise<void>;
  /** Hands the actual send off to the worker queue. */
  enqueue(email: string): Promise<void>;
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
 * out. Constant-time by construction: it never checks whether the account
 * exists, never generates a link, and never sends — it just throttles and
 * enqueues, identically for every email. The worker does the existence-dependent
 * work where its timing can't be observed by the caller.
 */
export async function sendPasswordResetEmailHandler(
  data: unknown,
  io: PasswordResetEnqueuerIO,
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
    await io.enqueue(email);
    return ACK;
  } catch (err) {
    // Enqueue failed before any work happened (e.g. IAM/quota) — release the
    // reservation so the user can retry immediately instead of waiting out 60s.
    await io.releaseSend(key).catch(() => undefined);
    functions.logger.error('password reset enqueue failed', {
      error: err instanceof Error ? err.message : String(err),
    });
    throw new functions.https.HttpsError('unavailable', 'email_send_failed');
  }
}

function productionIO(): PasswordResetEnqueuerIO {
  const db = admin.firestore();
  const throttleRef = (key: string) => db.collection('mailThrottle').doc(key);
  return {
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
    async enqueue(email) {
      await getFunctions().taskQueue(WORKER_RESOURCE).enqueue({ email });
    },
  };
}

export const sendPasswordResetEmail = functions
  .region(REGION)
  .https.onCall(async (data) => {
    return sendPasswordResetEmailHandler(data, productionIO());
  });
