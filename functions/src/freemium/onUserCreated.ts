import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  buildLaunchGrantFields,
  shouldGrantLaunchFree,
  UserSubscriptionFields,
} from './launchGrant';

const REGION = 'europe-west1';

export type GrantOutcome = 'granted' | 'skipped-disabled' | 'skipped-managed';

export interface UserCreatedDeps {
  isGrantEnabled: () => Promise<boolean>;
  writeGrant: (uid: string, now: Date) => Promise<void>;
  now: () => Date;
}

/**
 * Pure handler: decides and (optionally) applies the launch grant for one new
 * user doc. Production wraps this in the onCreate trigger below; tests drive it
 * with fakes.
 */
export async function handleUserCreated(
  deps: UserCreatedDeps,
  uid: string,
  data: UserSubscriptionFields | undefined,
): Promise<GrantOutcome> {
  if (!(await deps.isGrantEnabled())) {
    return 'skipped-disabled';
  }
  if (!shouldGrantLaunchFree(data)) {
    return 'skipped-managed';
  }
  await deps.writeGrant(uid, deps.now());
  return 'granted';
}

function productionDeps(): UserCreatedDeps {
  const db = admin.firestore();
  return {
    isGrantEnabled: async () => {
      const snap = await db.doc('config/app').get();
      return snap.get('launchFreeGrantEnabled') === true;
    },
    writeGrant: async (uid, now) => {
      await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
    },
    now: () => new Date(),
  };
}

/**
 * Grants the launch-free entitlement to every newly-created user doc while
 * config/app.launchFreeGrantEnabled === true. Writing back to users/{uid} does
 * NOT re-trigger onCreate, so there is no loop. Turn the promo off in January by
 * setting the flag false (no redeploy needed).
 */
export const grantLaunchFreeOnSignup = functions
  .region(REGION)
  .firestore.document('users/{uid}')
  .onCreate(async (snap, context) => {
    const uid = context.params.uid as string;
    const outcome = await handleUserCreated(
      productionDeps(),
      uid,
      snap.data() as UserSubscriptionFields | undefined,
    );
    functions.logger.info('launch-free grant', { uid, outcome });
  });
