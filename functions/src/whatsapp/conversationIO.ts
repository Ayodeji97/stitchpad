import * as admin from 'firebase-admin';
import { ConversationDoc } from './types';

/** Read/write seam over the `whatsappConversations/{waId}` state doc. */
export interface ConversationIO {
  /** Returns the conversation, or a fresh BOT/terms-pending default if absent. */
  get(waId: string): Promise<ConversationDoc>;
  /** Merges partial updates into the doc. */
  update(waId: string, updates: Partial<ConversationDoc>): Promise<void>;
}

/**
 * Maps a raw Firestore doc to a ConversationDoc, applying defaults. Kept pure
 * and exported so every persisted field (including the account-linking state)
 * is guaranteed to survive a read — dropping a field here silently breaks the
 * flows that depend on it across messages.
 */
export function mapConversationDoc(d: Partial<ConversationDoc> | undefined): ConversationDoc {
  return {
    state: d?.state ?? 'BOT',
    termsAccepted: d?.termsAccepted ?? false,
    language: d?.language,
    linkedUid: d?.linkedUid,
    linkingConsent: d?.linkingConsent,
    awaitingLinkConsent: d?.awaitingLinkConsent,
    pendingAccountIntent: d?.pendingAccountIntent,
  };
}

export function productionConversationIO(db: admin.firestore.Firestore): ConversationIO {
  const ref = (waId: string) => db.collection('whatsappConversations').doc(waId);
  return {
    async get(waId) {
      const snap = await ref(waId).get();
      return mapConversationDoc(snap.exists ? (snap.data() as Partial<ConversationDoc> | undefined) : undefined);
    },
    async update(waId, updates) {
      await ref(waId).set(updates, { merge: true });
    },
  };
}
