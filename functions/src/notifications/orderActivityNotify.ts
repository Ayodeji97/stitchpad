import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { Firestore } from 'firebase-admin/firestore';
import { deletePushTokens, loadPushTokens, sendMulticast } from './fcm';
import { ActivityIO, isActivityPushEnabled, orderActivityEvents, runOrderActivity } from './orderActivity';

const REGION = 'europe-west1';
const ALREADY_EXISTS = 6;

export function productionActivityIO(db: Firestore): ActivityIO {
  return {
    loadRecipient: async (uid) => {
      const snap = await db.collection('users').doc(uid).get();
      if (!snap.exists) return null;
      const u = snap.data() ?? {};
      return {
        pushEnabled: isActivityPushEnabled(u),
        locale: typeof u.locale === 'string' ? u.locale : undefined,
      };
    },
    isActiveStaff: async (ownerUid, staffUid) => {
      const snap = await db.collection('users').doc(ownerUid).collection('memberships').doc(staffUid).get();
      return snap.exists && snap.data()?.status === 'active';
    },
    loadMemberName: async (ownerUid, memberUid) => {
      const snap = await db.collection('users').doc(ownerUid).collection('team').doc(memberUid).get()
        .catch(() => null);
      const name = snap?.data()?.name;
      return typeof name === 'string' ? name : null;
    },
    createInbox: async (uid, docId, data) => {
      try {
        await db.collection('users').doc(uid).collection('notifications').doc(docId).create(data);
        return 'created';
      } catch (err) {
        if ((err as { code?: number }).code === ALREADY_EXISTS) return 'exists';
        throw err;
      }
    },
    loadTokens: (uid) => loadPushTokens(db, uid),
    send: (tokens, payload) => sendMulticast(tokens, payload, 'order-activity push'),
    deleteTokens: (uid, tokens) => deletePushTokens(db, uid, tokens),
    now: () => Date.now(),
    log: (message, data) => functions.logger.error(message, data),
  };
}

/**
 * Workshop activity alerts (see orderActivity.ts): staff status change → owner,
 * owner assignment → staff. Runs on every order update, so the pure decision runs
 * FIRST and returns before any Firestore read for the vast majority of writes.
 * Never rethrows: a push problem must never surface as a failed order write.
 */
export const onOrderActivity = functions
  .region(REGION)
  .firestore.document('users/{uid}/orders/{orderId}')
  .onUpdate(async (change, context) => {
    const uid = context.params.uid as string;
    const orderId = context.params.orderId as string;
    const before = change.before.data();
    const after = change.after.data();
    if (orderActivityEvents(uid, orderId, before, after).length === 0) return;

    try {
      const results = await runOrderActivity(uid, orderId, before, after, productionActivityIO(admin.firestore()));
      for (const r of results) {
        functions.logger.info('onOrderActivity: event handled', {
          uid, orderId, kind: r.event.kind, recipientUid: r.event.recipientUid, outcome: r.outcome,
        });
      }
    } catch (err) {
      functions.logger.error('onOrderActivity: failed', {
        uid, orderId, error: err instanceof Error ? err.message : String(err),
      });
    }
  });
