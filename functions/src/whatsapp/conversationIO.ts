import * as admin from 'firebase-admin';
import { ConversationDoc } from './types';

/** Read/write seam over the `whatsappConversations/{waId}` state doc. */
export interface ConversationIO {
  /** Returns the conversation, or a fresh BOT/terms-pending default if absent. */
  get(waId: string): Promise<ConversationDoc>;
  /** Merges partial updates into the doc. */
  update(waId: string, updates: Partial<ConversationDoc>): Promise<void>;
}

const DEFAULT: ConversationDoc = { state: 'BOT', termsAccepted: false };

export function productionConversationIO(db: admin.firestore.Firestore): ConversationIO {
  const ref = (waId: string) => db.collection('whatsappConversations').doc(waId);
  return {
    async get(waId) {
      const snap = await ref(waId).get();
      if (!snap.exists) return { ...DEFAULT };
      const d = snap.data() as Partial<ConversationDoc> | undefined;
      return {
        state: d?.state ?? 'BOT',
        termsAccepted: d?.termsAccepted ?? false,
        language: d?.language,
      };
    },
    async update(waId, updates) {
      await ref(waId).set(updates, { merge: true });
    },
  };
}
