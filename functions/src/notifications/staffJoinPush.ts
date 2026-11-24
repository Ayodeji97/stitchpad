/**
 * Push the owner when someone asks to join their workshop.
 *
 * redeemStaffInvite already wrote an in-app STAFF_PENDING inbox doc, but nothing
 * reached the owner's phone, so a join request could sit unseen until the owner
 * happened to open Team. Called once per successful redeem (a callable is not retried,
 * and a re-redeem of the same code fails with invite_not_open), so no extra dedupe.
 *
 * Tap target `team` opens the Team screen on 1.4.0+; older builds don't know the
 * target and simply open the app (PushTargetParser returns null for unknown targets).
 */
import type { Firestore } from 'firebase-admin/firestore';
import { serverLanguage } from '../localization/language';
import { deletePushTokens, loadPushTokens, PushPayload, PushResult, sendMulticast } from './fcm';
import { isActivityPushEnabled } from './orderActivity';

export interface JoinRequest {
  ownerUid: string;
  staffUid: string;
  staffName: string;
  staffEmail: string;
}

export function joinRequestPushCopy(req: JoinRequest, locale?: string): { title: string; body: string } {
  const fr = serverLanguage(locale) === 'fr';
  const who = req.staffName.trim() || req.staffEmail.trim() || (fr ? 'Quelqu’un' : 'Someone');
  return fr
    ? { title: 'Nouvelle demande d’adhésion', body: `${who} demande à rejoindre votre atelier. Touchez pour répondre.` }
    : { title: 'New join request', body: `${who} asked to join your workshop. Tap to review.` };
}

export interface JoinPushIO {
  loadOwner(uid: string): Promise<Record<string, unknown> | null>;
  loadTokens(uid: string): Promise<string[]>;
  send(tokens: string[], payload: PushPayload): Promise<PushResult>;
  deleteTokens(uid: string, tokens: string[]): Promise<void>;
}

export type JoinPushOutcome = 'sent' | 'push_disabled' | 'no_tokens';

export async function runJoinRequestPush(req: JoinRequest, io: JoinPushIO): Promise<JoinPushOutcome> {
  const owner = await io.loadOwner(req.ownerUid);
  if (!isActivityPushEnabled(owner)) return 'push_disabled';
  const tokens = await io.loadTokens(req.ownerUid);
  if (tokens.length === 0) return 'no_tokens';
  const locale = typeof owner?.locale === 'string' ? owner.locale : undefined;
  const tag = `stitchpad_join_${req.staffUid}`;
  const { invalidTokens } = await io.send(tokens, {
    ...joinRequestPushCopy(req, locale),
    data: { target: 'team' },
    androidTag: tag,
    apnsCollapseId: tag,
  });
  if (invalidTokens.length > 0) await io.deleteTokens(req.ownerUid, invalidTokens);
  return 'sent';
}

export function productionJoinPushIO(db: Firestore): JoinPushIO {
  return {
    loadOwner: async (uid) => {
      const snap = await db.collection('users').doc(uid).get();
      return snap.exists ? (snap.data() ?? {}) : null;
    },
    loadTokens: (uid) => loadPushTokens(db, uid),
    send: (tokens, payload) => sendMulticast(tokens, payload, 'join-request push'),
    deleteTokens: (uid, tokens) => deletePushTokens(db, uid, tokens),
  };
}
