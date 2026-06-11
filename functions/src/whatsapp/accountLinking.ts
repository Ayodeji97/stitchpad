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

/**
 * The stored formats a number might appear as, so a single Firestore lookup can
 * match however the tailor saved it. We ONLY expand to the Nigerian variants
 * (+234 / 234 / 0-prefixed / bare national) when the sender is actually a 234
 * E.164 number — otherwise a foreign sender whose last 10 digits happen to match
 * a Nigerian user's number could be linked to that account. Non-Nigerian senders
 * are matched on their exact E.164 form only.
 */
export function phoneCandidates(waId: string): string[] {
  const digits = waId.replace(/\D/g, '');
  if (digits.startsWith('234') && digits.length === 13) {
    const nat = digits.slice(3);
    return [`+234${nat}`, `234${nat}`, `0${nat}`, nat];
  }
  return [`+${digits}`, digits];
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

/**
 * Given the matching uids from each queried field, returns the single linked uid
 * or null. Links ONLY when exactly one distinct user matches across all fields —
 * if the number hits one user's whatsappNumber and a different user's phone, that
 * is ambiguous and we must not disclose either account.
 */
export function resolveUniqueUid(uidGroups: string[][]): string | null {
  const uids = new Set<string>();
  for (const group of uidGroups) {
    for (const id of group) uids.add(id);
  }
  return uids.size === 1 ? [...uids][0] : null;
}

export function productionAccountLinkIO(db: admin.firestore.Firestore): AccountLinkIO {
  const users = db.collection('users');
  return {
    async findUidByNumber(waId) {
      const candidates = phoneCandidates(waId);
      // Firestore 'in' caps at 30 values; we pass 4. Collect matches across BOTH
      // whatsappNumber and phone, then link only if exactly one distinct user
      // matched (a cross-field collision between two users is ambiguous).
      const groups: string[][] = [];
      for (const field of ['whatsappNumber', 'phone']) {
        const snap = await users.where(field, 'in', candidates).get();
        groups.push(snap.docs.map((d) => d.id));
      }
      return resolveUniqueUid(groups);
    },
    async getTier(uid) {
      const snap = await users.doc(uid).get();
      if (!snap.exists) return null;
      const d = snap.data() as RawUserTierDoc | undefined;
      return d?.subscriptionTier ?? d?.tier ?? null;
    },
  };
}
