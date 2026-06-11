import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { dupeTokenRefsToPrune } from './tokenOwnership';

const REGION = 'europe-west1';

/**
 * Single-ownership enforcement for FCM tokens. When a token doc is created under a uid,
 * delete the same token under any OTHER uid (stale registrations from failed sign-out /
 * reinstall / account-switch on a shared device) so one tailor's digest can't reach
 * another tailor's device. onCreate (not onWrite): a same-user re-register is an update;
 * the token appearing under a new uid is the create we act on. Deletes don't re-trigger.
 */
export const pruneTokenOwnership = functions
  .region(REGION)
  .firestore.document('users/{uid}/notificationTokens/{token}')
  .onCreate(async (_snap, context) => {
    const ownerUid = context.params.uid as string;
    const token = context.params.token as string;
    const db = admin.firestore();

    const dupes = await db
      .collectionGroup('notificationTokens')
      .where('token', '==', token)
      .get();

    const owners = dupes.docs
      .map((d) => ({ uid: d.ref.parent.parent?.id, ref: d.ref }))
      .filter((d): d is { uid: string; ref: FirebaseFirestore.DocumentReference } => !!d.uid);

    const toDelete = dupeTokenRefsToPrune(ownerUid, owners);
    if (toDelete.length === 0) return;

    const batch = db.batch();
    toDelete.forEach((ref) => batch.delete(ref));
    await batch.commit();

    functions.logger.info('token ownership: pruned stale token docs', {
      tokenPrefix: token.slice(0, 24),
      ownerUid,
      pruned: toDelete.length,
    });
  });
