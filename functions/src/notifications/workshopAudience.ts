/**
 * Who to notify inside a workshop, and how to reach their devices.
 *
 * Until now nothing could notify a STAFF member at all, and the reason is structural:
 * a staff member's push tokens live under their own auth uid
 * (`users/{staffUid}/notificationTokens`), while the work they do lives under the
 * OWNER's tree (`users/{ownerUid}/orders/...`). Nothing bridged the two, so every
 * notification the app sends is implicitly owner-only.
 *
 * This module is that bridge. Given an owner uid it resolves the active staff on the
 * roster and can collect tokens across several users at once.
 */
import * as functions from 'firebase-functions/v1';
import type { Firestore } from 'firebase-admin/firestore';
import { loadPushTokens } from './fcm';

/** A workshop's notifiable people. */
export interface WorkshopAudience {
  ownerUid: string;
  /** Auth uids of ACTIVE staff. Pending and revoked memberships are excluded. */
  staffUids: string[];
}

/**
 * Active staff for one workshop.
 *
 * Membership docs live at `users/{ownerUid}/memberships/{staffAuthUid}` — the doc id
 * IS the staff auth uid. Filtered in memory rather than with `where('status','==',...)`
 * because a roster is a handful of documents and the seat cap keeps it that way;
 * a server-side filter would buy nothing and cost an index.
 */
export async function loadWorkshopAudience(
  db: Firestore,
  ownerUid: string,
): Promise<WorkshopAudience> {
  const staffUids: string[] = [];
  try {
    const snap = await db.collection('users').doc(ownerUid).collection('memberships').get();
    for (const doc of snap.docs) {
      if (doc.data()?.status === 'active') staffUids.push(doc.id);
    }
  } catch (err) {
    // Degrade to owner-only rather than failing the whole notification: the owner
    // still hears about their own workshop.
    functions.logger.warn('loadWorkshopAudience failed — notifying owner only', {
      ownerUid,
      error: err instanceof Error ? err.message : String(err),
    });
  }
  return { ownerUid, staffUids };
}

/** A device token paired with the user it belongs to, so pruning stays per-user. */
export interface OwnedToken {
  uid: string;
  token: string;
}

/**
 * Every registered device across the given users.
 *
 * Tokens stay tagged with their owner because pruning a dead token means deleting it
 * from THAT user's subcollection — a flat token list would lose the information
 * needed to clean up.
 */
export async function loadTokensForUsers(db: Firestore, uids: string[]): Promise<OwnedToken[]> {
  const perUser = await Promise.all(uids.map(async (uid) => {
    const tokens = await loadPushTokens(db, uid).catch(() => [] as string[]);
    return tokens.map((token) => ({ uid, token }));
  }));
  return perUser.flat();
}

/** Groups dead tokens back by owner so each user's subcollection is pruned correctly. */
export function groupTokensByUid(tokens: OwnedToken[], dead: string[]): Map<string, string[]> {
  const deadSet = new Set(dead);
  const byUid = new Map<string, string[]>();
  for (const t of tokens) {
    if (!deadSet.has(t.token)) continue;
    const list = byUid.get(t.uid) ?? [];
    list.push(t.token);
    byUid.set(t.uid, list);
  }
  return byUid;
}
