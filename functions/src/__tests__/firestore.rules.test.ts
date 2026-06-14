import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';

/**
 * Security-rules tests for firestore.rules. Run with `npm run test:rules`
 * (wraps this in `firebase emulators:exec --only firestore`).
 *
 * These exist because tuning the user-doc rules by hand kept regressing the
 * signup seed path and the server-owned field locks. Encode the behaviours we
 * rely on so future rule edits are verified, not guessed.
 */

const RULES = readFileSync(resolve(__dirname, '../../../firestore.rules'), 'utf8');

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-stitchpad',
    firestore: { rules: RULES, host: '127.0.0.1', port: 8080 },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

/** Seed server-authored state (Admin SDK bypasses rules in production). */
function asAdmin(work: (db: ReturnType<ReturnType<RulesTestEnvironment['authenticatedContext']>['firestore']>) => Promise<void>) {
  return testEnv.withSecurityRulesDisabled((ctx) => work(ctx.firestore() as never));
}

function db(uid: string) {
  return testEnv.authenticatedContext(uid).firestore();
}

const SEED_DEFAULTS = {
  subscriptionTier: 'free',
  subscriptionStatus: 'active',
  subscriptionRenews: false,
  customerCount: 0,
  bonusCoins: 30,
  welcomeBonusAppliedAt: serverTimestamp(),
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
};

describe('users/{uid} creation', () => {
  it('allows the fire-and-forget profile-only create (no billing fields)', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice'), { displayName: 'Alice', updatedAt: serverTimestamp() }),
    );
  });

  it('allows creating with the safe entitlement defaults', async () => {
    await assertSucceeds(setDoc(doc(db('alice'), 'users/alice'), SEED_DEFAULTS));
  });

  it('rejects creating with a paid tier', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, subscriptionTier: 'pro' }),
    );
  });

  it('rejects planting subscriptionEndsAt at creation', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), {
        ...SEED_DEFAULTS,
        subscriptionEndsAt: Timestamp.fromDate(new Date('2050-01-01T00:00:00Z')),
      }),
    );
  });

  it('rejects planting Apple provenance fields at creation', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, subscriptionSource: 'apple' }),
    );
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, appleOriginalTransactionId: 'orig-1' }),
    );
  });

  it('rejects another user creating your doc', async () => {
    await assertFails(setDoc(doc(db('bob'), 'users/alice'), SEED_DEFAULTS));
  });
});

describe('users/{uid} updates', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), { ...SEED_DEFAULTS, displayName: 'Alice' });
    });
  });

  it('allows editing a display field', async () => {
    await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice'), { displayName: 'Alice B' }));
  });

  it('rejects self-upgrading the subscription tier', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionTier: 'pro' }));
  });

  it('rejects changing the welcome bonus', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { bonusCoins: 999 }));
  });
});

describe('users/{uid}/notifications', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/notifications/n1'), { isRead: false, title: 'Order due' });
    });
  });

  it('lets the owner read and mark isRead', async () => {
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice/notifications/n1')));
    await assertSucceeds(updateDoc(doc(db('alice'), 'users/alice/notifications/n1'), { isRead: true }));
  });

  it('rejects editing any field other than isRead', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'users/alice/notifications/n1'), { title: 'tampered' }));
  });

  it('rejects client create and delete', async () => {
    await assertFails(setDoc(doc(db('alice'), 'users/alice/notifications/n2'), { isRead: false }));
    await assertFails(deleteDoc(doc(db('alice'), 'users/alice/notifications/n1')));
  });

  it('rejects reading another user notifications', async () => {
    await assertFails(getDoc(doc(db('bob'), 'users/alice/notifications/n1')));
  });
});

describe('users/{uid}/billingTransactions', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/billingTransactions/ref1'), { status: 'paid', amountKobo: 200000 });
    });
  });

  it('lets the owner read their own transactions', async () => {
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice/billingTransactions/ref1')));
  });

  it('rejects any client write', async () => {
    await assertFails(setDoc(doc(db('alice'), 'users/alice/billingTransactions/ref2'), { status: 'paid' }));
    await assertFails(updateDoc(doc(db('alice'), 'users/alice/billingTransactions/ref1'), { status: 'free_money' }));
  });

  it('rejects reading another user transactions', async () => {
    await assertFails(getDoc(doc(db('bob'), 'users/alice/billingTransactions/ref1')));
  });
});

describe('appleSubscriptions reverse index', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'appleSubscriptions/orig-1'), { uid: 'alice' });
    });
  });

  it('rejects any client read (uid ownership map is not enumerable)', async () => {
    await assertFails(getDoc(doc(db('alice'), 'appleSubscriptions/orig-1')));
  });

  it('rejects any client write (cannot forge ownership)', async () => {
    await assertFails(setDoc(doc(db('alice'), 'appleSubscriptions/orig-2'), { uid: 'alice' }));
    await assertFails(updateDoc(doc(db('alice'), 'appleSubscriptions/orig-1'), { uid: 'mallory' }));
  });
});

describe('server-owned field hardening', () => {
  describe('on an active paid user', () => {
    beforeEach(async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice'), {
          ...SEED_DEFAULTS,
          subscriptionTier: 'pro',
          subscriptionStatus: 'active',
          subscriptionEndsAt: Timestamp.fromDate(new Date('2026-08-01T00:00:00Z')),
        });
      });
    });

    it('blocks deleting subscriptionEndsAt (expiry-query bypass)', async () => {
      await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionEndsAt: deleteField() }));
    });

    it('blocks deleting subscriptionRenews (expiry-query bypass)', async () => {
      await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionRenews: deleteField() }));
    });

    it('blocks deleting subscriptionTier (billing-state corruption)', async () => {
      await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionTier: deleteField() }));
    });

    it('blocks deleting subscriptionStatus (billing-state corruption)', async () => {
      await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionStatus: deleteField() }));
    });
  });

  describe('plant guards', () => {
    it('rejects adding a paid tier to a profile-only doc via update', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice'), { displayName: 'Alice' });
      });
      await assertFails(
        setDoc(doc(db('alice'), 'users/alice'), { subscriptionTier: 'pro' }, { merge: true }),
      );
    });

    it('rejects adding subscriptionEndsAt to a free user via update', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice'), SEED_DEFAULTS);
      });
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice'), {
          subscriptionEndsAt: Timestamp.fromDate(new Date('2050-01-01T00:00:00Z')),
        }),
      );
    });

    it('rejects adding welcomeBonusAppliedAt or bonusCoins via update (no welcome-bonus replay)', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice'), { displayName: 'Alice' });
      });
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice'), { welcomeBonusAppliedAt: serverTimestamp() }),
      );
      await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { bonusCoins: 30 }));
    });

    it('rejects self-marking as an Apple subscriber via update', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice'), SEED_DEFAULTS);
      });
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice'), { subscriptionSource: 'apple' }),
      );
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice'), { appleProductId: 'com.danzucker.stitchpad.pro.monthly' }),
      );
    });
  });
});
