import * as crypto from 'crypto';
import * as admin from 'firebase-admin';

/**
 * Idempotency seam. Meta retries deliveries aggressively, so every inbound
 * message id is recorded once; the marker is released if the reply fails so a
 * retry can re-process it (mirrors sendVerificationEmail's reserve/release).
 */
export interface DedupIO {
  /** True the FIRST time a (waId, messageId) is seen; false on any repeat. */
  markProcessed(waId: string, messageId: string): Promise<boolean>;
  /** Undoes a markProcessed when the reply failed, so a Meta retry re-runs it. */
  release(waId: string, messageId: string): Promise<void>;
}

/**
 * Maps an opaque WhatsApp message id to a Firestore-safe document id. WAMIDs
 * are base64-flavored and can contain `/`, which is illegal in a Firestore path
 * segment and would throw. A sha256 hex digest is deterministic (so the dedup
 * still catches retries) and always path-safe; the original id is kept in the
 * document body.
 */
export function messageDocId(messageId: string): string {
  return crypto.createHash('sha256').update(messageId).digest('hex');
}

/**
 * Firestore-backed dedup. `.create()` throws ALREADY_EXISTS (gRPC code 6) on
 * the second delivery, so a `true` return means "first time, go process it".
 * The doc lives in the conversation's transcript subcollection.
 */
export function productionDedupIO(db: admin.firestore.Firestore): DedupIO {
  const messageRef = (waId: string, messageId: string) =>
    db.collection('whatsappConversations').doc(waId).collection('messages').doc(messageDocId(messageId));
  return {
    async markProcessed(waId, messageId) {
      // KNOWN LIMITATION (deferred to Slice 3): single-state marker. If Meta
      // retries WHILE the first attempt is still inside the reply, the retry
      // sees the doc and skips; if the first attempt then fails and releases,
      // the message can be dropped. A proper pending/completed state + TTL
      // arrives with the Slice 3 conversation state machine. The race window is
      // the sub-second send and Meta retries are not sub-second, so acceptable.
      try {
        await messageRef(waId, messageId).create({ messageId, direction: 'inbound', receivedAt: Date.now() });
        return true;
      } catch (err) {
        // gRPC ALREADY_EXISTS = 6 — a retry of a message we already handled.
        // admin.firestore.GrpcStatus is a type-only export at runtime, so we
        // compare the numeric code directly (same trick as dailyDigest).
        if ((err as { code?: number }).code === 6) {
          return false;
        }
        throw err;
      }
    },
    async release(waId, messageId) {
      await messageRef(waId, messageId).delete();
    },
  };
}
