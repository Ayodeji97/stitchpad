import * as admin from 'firebase-admin';

/**
 * Optional account linking: match an inbound WhatsApp number to a StitchPad
 * user so the bot can answer narrow, read-only, consent-gated questions about
 * their own account (e.g. their plan). No account-mutating actions here — those
 * escalate to a human.
 */

/** The only account-specific question the bot answers in v1. */
export type AccountIntent = 'tier';

const TIER_RE = /\b(my (plan|tier|subscription)|what (plan|tier)|which (plan|tier)|am i on (a )?(free|pro|atelier))\b/i;

/** Detects a question about the user's own account state, or null. */
export function detectAccountIntent(text: string): AccountIntent | null {
  return TIER_RE.test(text) ? 'tier' : null;
}

/** The Nigerian national (10-digit) part of a number, from any format. */
function nationalNumber(raw: string): string {
  const digits = raw.replace(/\D/g, '');
  return digits.length >= 10 ? digits.slice(-10) : digits;
}

/**
 * The common stored formats a Nigerian number might appear as, so a single
 * Firestore lookup can match however the tailor saved their number (E.164 with
 * or without +, or the local 0-prefixed form).
 */
export function phoneCandidates(waId: string): string[] {
  const nat = nationalNumber(waId);
  return [`+234${nat}`, `234${nat}`, `0${nat}`, nat];
}

/** Read seam over the users collection for linking + tier lookup. */
export interface AccountLinkIO {
  /** The uid whose whatsappNumber/phone matches, or null if none / ambiguous. */
  findUidByNumber(waId: string): Promise<string | null>;
  /** The user's subscription tier label, or null if unknown. */
  getTier(uid: string): Promise<string | null>;
}

interface RawUserTierDoc {
  subscriptionTier?: string;
  tier?: string;
}

export function productionAccountLinkIO(db: admin.firestore.Firestore): AccountLinkIO {
  const users = db.collection('users');
  return {
    async findUidByNumber(waId) {
      const candidates = phoneCandidates(waId);
      // Firestore 'in' caps at 30 values; we pass 4. Check whatsappNumber first,
      // then fall back to the reserved `phone` field.
      for (const field of ['whatsappNumber', 'phone']) {
        const snap = await users.where(field, 'in', candidates).get();
        // Exactly one match links; 0 or >1 (ambiguous) does not.
        if (snap.size === 1) return snap.docs[0].id;
        if (snap.size > 1) return null;
      }
      return null;
    },
    async getTier(uid) {
      const snap = await users.doc(uid).get();
      if (!snap.exists) return null;
      const d = snap.data() as RawUserTierDoc | undefined;
      return d?.subscriptionTier ?? d?.tier ?? null;
    },
  };
}
