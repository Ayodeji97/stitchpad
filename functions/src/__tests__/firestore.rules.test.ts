import { readFileSync } from 'fs';
import { resolve } from 'path';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
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

// A signed-in STAFF member of `workshopUid`, i.e. carrying the custom claims the
// approve-staff Cloud Function will set: role='staff', workshopUid=<owner uid>.
function staffDb(staffUid: string, workshopUid: string) {
  return testEnv.authenticatedContext(staffUid, { role: 'staff', workshopUid }).firestore();
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

describe('gifts collection', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'gifts/CODE123'), { status: 'paid', code: 'CODE123', tier: 'pro' });
      await setDoc(doc(admin, 'giftLinkTokens/TOK'), { uid: 'alice' });
    });
  });

  it('rejects any client read of a gift (bearer code must not leak)', async () => {
    await assertFails(getDoc(doc(db('alice'), 'gifts/CODE123')));
    await assertFails(getDoc(doc(db('bob'), 'gifts/CODE123')));
  });

  it('rejects any client write of a gift (self-grant / claim tampering)', async () => {
    await assertFails(setDoc(doc(db('alice'), 'gifts/NEW'), { status: 'paid', tier: 'atelier' }));
    await assertFails(updateDoc(doc(db('alice'), 'gifts/CODE123'), { status: 'claimed' }));
  });

  it('rejects any client read or write of the giftLinkTokens reverse index', async () => {
    await assertFails(getDoc(doc(db('alice'), 'giftLinkTokens/TOK')));
    await assertFails(setDoc(doc(db('alice'), 'giftLinkTokens/MINE'), { uid: 'alice' }));
  });
});

describe('referral collections (Admin-SDK only)', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'marketers/m1'), {
        name: 'Ada', type: 'affiliate', code: 'ADA12345', payoutRatePerUser: 200000, status: 'active',
      });
      await setDoc(doc(admin, 'referralCodes/ADA12345'), { marketerId: 'm1' });
      await setDoc(doc(admin, 'referrals/bob'), { marketerId: 'm1', milestone: 'attributed', payoutState: 'none' });
      await setDoc(doc(admin, 'referralDevices/hash1'), { referredUid: 'bob', marketerId: 'm1' });
    });
  });

  it('rejects reading a marketer (payout terms must not leak)', async () => {
    await assertFails(getDoc(doc(db('alice'), 'marketers/m1')));
  });

  it('rejects writing a marketer (self-enroll / rate tampering)', async () => {
    await assertFails(setDoc(doc(db('alice'), 'marketers/mine'), { name: 'Me', payoutRatePerUser: 999999 }));
    await assertFails(updateDoc(doc(db('alice'), 'marketers/m1'), { payoutRatePerUser: 999999 }));
  });

  it('rejects reading or writing the referralCodes reverse index', async () => {
    await assertFails(getDoc(doc(db('alice'), 'referralCodes/ADA12345')));
    await assertFails(setDoc(doc(db('alice'), 'referralCodes/MINE'), { marketerId: 'm1' }));
  });

  it('rejects reading a referral or self-marking it qualified/paid', async () => {
    await assertFails(getDoc(doc(db('alice'), 'referrals/bob')));
    await assertFails(setDoc(doc(db('alice'), 'referrals/alice'), { marketerId: 'm1', milestone: 'qualified' }));
    await assertFails(updateDoc(doc(db('bob'), 'referrals/bob'), { payoutState: 'paid' }));
  });

  it('rejects reading or forging the referralDevices dedupe index', async () => {
    await assertFails(getDoc(doc(db('alice'), 'referralDevices/hash1')));
    await assertFails(setDoc(doc(db('alice'), 'referralDevices/hash2'), { referredUid: 'alice', marketerId: 'm1' }));
  });
});

describe('referredBy field hardening', () => {
  it('rejects planting referredBy at user-doc creation (self-attribution)', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, referredBy: 'm1' }),
    );
  });

  it('rejects adding or changing referredBy via update', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), { ...SEED_DEFAULTS, referredBy: 'm1' });
    });
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referredBy: 'm2' }));
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referredBy: deleteField() }));
  });

  it('rejects adding referredBy to a doc that never had it', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), SEED_DEFAULTS);
    });
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referredBy: 'm1' }));
  });
});

describe('referralCode field hardening', () => {
  it('rejects planting a referralCode at user-doc creation (createUserProfile runs before any mint)', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, referralCode: 'FAKE1234' }),
    );
  });

  it('allows a normal profile edit that never touches referralCode (guard does not break edits)', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), { ...SEED_DEFAULTS, referralCode: 'REAL1234' });
    });
    await assertSucceeds(
      updateDoc(doc(db('alice'), 'users/alice'), { displayName: 'Alice B' }),
    );
  });

  it('rejects changing or deleting a server-minted referralCode via update', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), { ...SEED_DEFAULTS, referralCode: 'REAL1234' });
    });
    // Change it to an arbitrary code (share a different link).
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referralCode: 'HACK9999' }));
    // Delete it (share a dead link + force re-mint / stop minting).
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referralCode: deleteField() }));
    // A full replacement-set that omits referralCode is ALSO a delete of it — must fail.
    await assertFails(setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, displayName: 'Alice' }));
  });

  it('rejects adding referralCode to a doc that never had it (planting via update)', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), SEED_DEFAULTS);
    });
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { referralCode: 'FAKE1234' }));
  });
});

describe('giftLinkToken field hardening', () => {
  it('rejects planting a giftLinkToken at user-doc creation', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, giftLinkToken: 'HACK' }),
    );
  });

  it('rejects adding or changing giftLinkToken via update', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), { ...SEED_DEFAULTS, giftLinkToken: 'SERVERTOK' });
    });
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { giftLinkToken: 'HACK' }));
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { giftLinkToken: deleteField() }));
  });
});

describe('subscription fallback (queued gift segment) hardening', () => {
  it('rejects planting fallback fields at user-doc creation', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), { ...SEED_DEFAULTS, subscriptionFallbackTier: 'pro' }),
    );
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice'), {
        ...SEED_DEFAULTS,
        subscriptionFallbackEndsAt: Timestamp.fromDate(new Date('2050-01-01T00:00:00Z')),
      }),
    );
  });

  it('rejects adding or changing fallback fields via update', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice'), {
        ...SEED_DEFAULTS,
        subscriptionTier: 'atelier',
        subscriptionFallbackTier: 'pro',
        subscriptionFallbackEndsAt: Timestamp.fromDate(new Date('2027-01-01T00:00:00Z')),
      });
    });
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionFallbackTier: 'atelier' }));
    await assertFails(updateDoc(doc(db('alice'), 'users/alice'), { subscriptionFallbackEndsAt: deleteField() }));
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

describe('activity docs — serverCreatedAt / createdAt', () => {
  const now = Date.now();
  it('allows create with serverCreatedAt == request.time and past createdAt', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c1'), {
        createdAt: now - 60_000,
        serverCreatedAt: serverTimestamp(),
      }),
    );
  });
  it('rejects create with a client-literal serverCreatedAt', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c2'), {
        createdAt: now,
        serverCreatedAt: Timestamp.fromMillis(now),
      }),
    );
  });
  it('rejects create with a future createdAt beyond skew', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c3'), {
        createdAt: now + 3_600_000, // 1h ahead > 5m skew
        serverCreatedAt: serverTimestamp(),
      }),
    );
  });
  it('allows a create with no serverCreatedAt (old binary) and past createdAt', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c4'), { createdAt: now - 60_000 }),
    );
  });
  it('allows an edit that leaves serverCreatedAt unchanged', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c5'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c5'), { name: 'Ada' }, { merge: true }),
    );
  });
  it('rejects an update that rewrites serverCreatedAt to a forged value', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c6'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c6'),
        { serverCreatedAt: Timestamp.fromMillis(now - 5 * 86_400_000) }, { merge: true }),
    );
  });
  it('applies the same rules to orders and measurements', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o1'),
        { createdAt: now - 60_000, serverCreatedAt: serverTimestamp() }),
    );
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m1'),
        { createdAt: now, serverCreatedAt: Timestamp.fromMillis(now) }),
    );
  });
});

describe('activity docs — serverCreatedAt immutability (C1 fix)', () => {
  const now = Date.now();

  // Regression for the Critical bypass: the old rule keyed off
  // request.resource.data (AFTER state), so `updateDoc({serverCreatedAt:
  // deleteField()})` dropped the field from the AFTER state and was ALLOWED —
  // opening the door to a follow-up serverTimestamp() "first stamp" that
  // rewrote an already-stamped value. The fixed rule keys off resource.data
  // (BEFORE state) first, so once stamped, the field must stay present AND
  // unchanged — a delete is a removal, which fails both conjuncts.
  it('C1 regression: denies delete-then-restamp bypass on an already-stamped doc (customers)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c7'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/customers/c7'), { serverCreatedAt: deleteField() }),
    );
  });

  it('C1 regression: denies delete-then-restamp bypass on an already-stamped doc (orders)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o2'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/orders/o2'), { serverCreatedAt: deleteField() }),
    );
  });

  it('C1 regression: denies delete-then-restamp bypass on an already-stamped doc (measurements)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m2'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m2'), { serverCreatedAt: deleteField() }),
    );
  });

  it('denies directly overwriting an existing serverCreatedAt on orders (I1 coverage)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o3'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/orders/o3'), {
        serverCreatedAt: Timestamp.fromMillis(now - 5 * 86_400_000),
      }),
    );
  });

  it('denies directly overwriting an existing serverCreatedAt on measurements (I1 coverage)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m3'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m3'), {
        serverCreatedAt: Timestamp.fromMillis(now - 5 * 86_400_000),
      }),
    );
  });

  it('M2: allows the legitimate first-stamp via update on a doc created with no serverCreatedAt', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c8'), { createdAt: now - 60_000 });
    await assertSucceeds(
      updateDoc(doc(db('alice'), 'users/alice/customers/c8'), { serverCreatedAt: serverTimestamp() }),
    );
  });

  it('M2: allows a normal field edit (no serverCreatedAt in the write) on a still-unstamped doc', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c9'), { createdAt: now - 60_000 });
    await assertSucceeds(
      updateDoc(doc(db('alice'), 'users/alice/customers/c9'), { name: 'Ada' }),
    );
  });
});

// The rest of this suite only ever edits via updateDoc()/merge writes, which
// masked a shipping bug: FirebaseOrderRepository.updateOrder and
// FirebaseMeasurementRepository.updateMeasurement used full-document REPLACEMENT
// writes (`docRef.set(dto)`, no merge). A replacement drops every field absent
// from the DTO, and the DTOs have no `serverCreatedAt`, so every edit to a
// new-binary doc was rejected with permission-denied.
//
// The rule is right and stays: allowing a replacement to drop the stamp would
// reopen the C1 recycling attack (drop stamp + reset `createdAt` to today +
// re-stamp = a fresh "activity day" from an old doc, dodging the customer cap).
// The CLIENT was fixed to use `merge = true` instead. These pin both halves so
// nobody "fixes" the permission-denied by loosening the rule.
describe('activity docs — replacement vs merge edits (app update path)', () => {
  const now = Date.now();

  it('allows a replacement-set edit of an order created WITHOUT serverCreatedAt (old binary)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_repl_legacy'), { createdAt: now - 60_000 });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_repl_legacy'),
        { createdAt: now - 60_000, status: 'IN_PROGRESS' }),
    );
  });

  it('DENIES a replacement-set edit that drops the stamp on an order (why the client uses merge)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_repl_stamped'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/orders/o_repl_stamped'),
        { createdAt: now - 60_000, status: 'IN_PROGRESS' }),
    );
  });

  it('DENIES a replacement-set edit that drops the stamp on a measurement', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_repl'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_repl'),
        { createdAt: now - 60_000, chest: 42 }),
    );
  });

  // The fixed client path: set(dto, merge = true). GitLive encodes defaults, so
  // the DTO's full field set is still written (clearing still works) while
  // serverCreatedAt — absent from the DTO — survives untouched.
  it('allows a MERGE-set edit of a stamped order (fixed updateOrder path)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_merge_stamped'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_merge_stamped'),
        { createdAt: now - 60_000, status: 'IN_PROGRESS', notes: null },
        { merge: true }),
    );
  });

  it('allows a MERGE-set edit of a stamped measurement (fixed updateMeasurement path)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_merge'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_merge'),
        { createdAt: now - 60_000, chest: 42 }, { merge: true }),
    );
  });

  it('a merge edit leaves serverCreatedAt byte-identical', async () => {
    const ref = doc(db('alice'), 'users/alice/orders/o_merge_stable');
    await setDoc(ref, { createdAt: now - 60_000, serverCreatedAt: serverTimestamp() });
    const before = (await getDoc(ref)).data()?.serverCreatedAt;
    await assertSucceeds(setDoc(ref, { status: 'DONE' }, { merge: true }));
    const after = (await getDoc(ref)).data()?.serverCreatedAt;
    expect(after).toEqual(before);
  });
});

// createdAt is the day-anchor isServerFresh trusts. On a STAMPED doc it must be
// frozen: otherwise one stamped doc, re-dated to a fresh in-window day each
// nightly run, manufactures multiple distinct active days and dodges the
// customer cap. UNSTAMPED (legacy/old-binary) docs keep createdAt mutable —
// they are graded by the Lane A ratchet, and may carry createdAt == 0 (missing
// field) that the client remaps to now on the next write.
describe('activity docs — createdAt frozen once stamped', () => {
  const now = Date.now();

  it('rejects moving createdAt on a stamped order (the re-date attack)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_cad_attack'), {
      createdAt: now - 5 * 86_400_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/orders/o_cad_attack'),
        { createdAt: now }, { merge: true }),
    );
  });

  it('rejects moving createdAt on a stamped measurement', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_cad_attack'), {
      createdAt: now - 5 * 86_400_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m_cad_attack'),
        { createdAt: now }, { merge: true }),
    );
  });

  it('rejects a replacement-set that re-dates createdAt while re-sending the stamp', async () => {
    // Even a full write that faithfully re-sends serverCreatedAt must not move createdAt.
    const ref = doc(db('alice'), 'users/alice/orders/o_cad_repl');
    await setDoc(ref, { createdAt: now - 5 * 86_400_000, serverCreatedAt: serverTimestamp() });
    const sca = (await getDoc(ref)).data()?.serverCreatedAt;
    await assertFails(
      setDoc(ref, { createdAt: now, serverCreatedAt: sca }, { merge: true }),
    );
  });

  it('allows an edit that keeps createdAt unchanged on a stamped doc (honest edit)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_cad_ok'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_cad_ok'),
        { createdAt: now - 60_000, status: 'IN_PROGRESS' }, { merge: true }),
    );
  });

  it('allows an edit that omits createdAt entirely on a stamped doc (field-level update)', async () => {
    // The app's .update("status", ...) style writes never resend createdAt; the
    // merged post-write doc keeps the stored value, so this must pass.
    const ref = doc(db('alice'), 'users/alice/orders/o_cad_omit');
    await setDoc(ref, { createdAt: now - 60_000, serverCreatedAt: serverTimestamp() });
    await assertSucceeds(updateDoc(ref, { status: 'DONE' }));
  });

  it('allows re-dating createdAt on an UNSTAMPED legacy doc (graded by the ratchet)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_cad_legacy'), { createdAt: now - 5 * 86_400_000 });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_cad_legacy'),
        { createdAt: now }, { merge: true }),
    );
  });

  it('allows an unstamped doc with createdAt == 0 to be edited (client remaps to now)', async () => {
    await setDoc(doc(db('alice'), 'users/alice/orders/o_cad_zero'), { createdAt: 0 });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_cad_zero'),
        { createdAt: now, status: 'IN_PROGRESS' }, { merge: true }),
    );
  });
});

// Slice 8e: base order/customer docs must never (re)gain money/contact fields
// from ANY client write — including offline writes queued by a pre-8d-1 build and
// flushed after the strip (the version floor is a UI gate, not a write block).
// This is the permanent close of the re-contamination window; the post-flip strip
// dry-run monitoring is now a backstop, not the only defence.
//
// These rules must NOT be deployed before the 8c version floor is enforced: a
// pre-8d-1 client writes money/contact on every save and would break wholesale.
describe('Slice 8e: money/contact denied on base-doc client writes', () => {
  const now = Date.now();

  describe('orders — money keys', () => {
    it('DENIES an owner create carrying totalPrice (legacy full-DTO write)', async () => {
      await assertFails(
        setDoc(doc(db('alice'), 'users/alice/orders/o_money_create'), {
          customerName: 'Ada',
          status: 'PENDING',
          createdAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
          totalPrice: 40000,
        }),
      );
    });

    // Every top-level money key is guarded, not just totalPrice — a legacy DTO
    // encodes all of them, but a hand-rolled write could carry only one.
    it('DENIES an owner create carrying any single money key', async () => {
      const keys = ['discount', 'discountReason', 'depositPaid', 'balanceRemaining', 'payments', 'costs'];
      const values: Record<string, unknown> = {
        discount: 500, discountReason: 'loyal', depositPaid: 2000,
        balanceRemaining: 3000, payments: [], costs: [],
      };
      for (const key of keys) {
        await assertFails(
          setDoc(doc(db('alice'), `users/alice/orders/o_money_${key}`), {
            status: 'PENDING', createdAt: now - 60_000, [key]: values[key],
          }),
        );
      }
    });

    it('allows a money-free owner create (the post-8d-1 OrderBaseDto shape)', async () => {
      await assertSucceeds(
        setDoc(doc(db('alice'), 'users/alice/orders/o_money_free'), {
          customerName: 'Ada',
          status: 'PENDING',
          items: [{ id: 'i1', garmentType: 'Agbada' }],
          createdAt: now - 60_000,
          updatedAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
        }),
      );
    });

    it('DENIES an owner update that touches payments', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice/orders/o_money_upd'), {
          status: 'PENDING', createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
        });
      });
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice/orders/o_money_upd'), {
          payments: [{ id: 'p1', amount: 5000 }],
        }),
      );
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice/orders/o_money_upd'), { totalPrice: 40000 }),
      );
    });

    // The key property of the affectedKeys() formulation: legacy money already on
    // a base doc may REMAIN (the strip, an Admin-SDK job, is what removes it) while
    // an honest money-free edit of that same doc still goes through.
    it('allows a money-free owner update of a doc that STILL carries legacy money', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice/orders/o_money_legacy'), {
          status: 'PENDING',
          notes: 'old',
          totalPrice: 40000,
          payments: [{ id: 'p1', amount: 5000 }],
          createdAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
        });
      });
      await assertSucceeds(
        updateDoc(doc(db('alice'), 'users/alice/orders/o_money_legacy'), {
          status: 'IN_PROGRESS', notes: 'new', updatedAt: now,
        }),
      );
      // ...but rewriting the legacy money it still carries is denied.
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice/orders/o_money_legacy'), { totalPrice: 1 }),
      );
    });

    // The merge-set edit path (FirebaseOrderRepository.updateOrder) resends the
    // full OrderBaseDto. Values that are byte-identical are not "affected", so a
    // merge write is only rejected when it actually carries money.
    it('allows the merge-set updateOrder path on a legacy money-bearing doc', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice/orders/o_money_merge'), {
          status: 'PENDING', totalPrice: 40000, createdAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
        });
      });
      await assertSucceeds(
        setDoc(doc(db('alice'), 'users/alice/orders/o_money_merge'),
          { status: 'DONE', updatedAt: now }, { merge: true }),
      );
    });
  });

  describe('customers — contact keys', () => {
    it('DENIES an owner create carrying phone (legacy full-DTO write)', async () => {
      await assertFails(
        setDoc(doc(db('alice'), 'users/alice/customers/c_contact_create'), {
          name: 'Ada',
          createdAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
          phone: '+2348011112222',
        }),
      );
    });

    it('DENIES an owner create carrying email or address', async () => {
      await assertFails(
        setDoc(doc(db('alice'), 'users/alice/customers/c_contact_email'), {
          name: 'Ada', createdAt: now - 60_000, email: 'ada@example.com',
        }),
      );
      await assertFails(
        setDoc(doc(db('alice'), 'users/alice/customers/c_contact_addr'), {
          name: 'Ada', createdAt: now - 60_000, address: '1 Broad St',
        }),
      );
    });

    it('allows a contact-free owner create (the post-8d-1 CustomerBaseDto shape)', async () => {
      await assertSucceeds(
        setDoc(doc(db('alice'), 'users/alice/customers/c_contact_free'), {
          name: 'Ada',
          slotState: 'active',
          createdAt: now - 60_000,
          updatedAt: now - 60_000,
          serverCreatedAt: serverTimestamp(),
        }),
      );
    });

    it('DENIES an owner update that touches phone', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice/customers/c_contact_upd'), {
          name: 'Ada', createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
        });
      });
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice/customers/c_contact_upd'), { phone: '+234' }),
      );
    });

    it('allows a contact-free owner update of a doc that STILL carries legacy contact', async () => {
      await asAdmin(async (admin) => {
        await setDoc(doc(admin, 'users/alice/customers/c_contact_legacy'), {
          name: 'Ada', phone: '+234', email: 'ada@example.com',
          createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
        });
      });
      await assertSucceeds(
        updateDoc(doc(db('alice'), 'users/alice/customers/c_contact_legacy'), {
          name: 'Ada B', updatedAt: now,
        }),
      );
      await assertFails(
        updateDoc(doc(db('alice'), 'users/alice/customers/c_contact_legacy'), { phone: '+999' }),
      );
    });
  });

  // createOrder/createCustomer are TWO writes: set(dto) then
  // set({serverCreatedAt}, merge=true). Both halves must go through — whether the
  // stamp reaches the rules engine as an UPDATE (doc already created) or as a
  // CREATE (see the stamp-only cases below).
  describe('the two-step create + stamp', () => {
    it('allows the legit two-step createOrder: money-free create, then the stamp merge', async () => {
      const ref = doc(db('alice'), 'users/alice/orders/o_two_step');
      await assertSucceeds(
        setDoc(ref, {
          customerName: 'Ada',
          status: 'PENDING',
          createdAt: now - 60_000,
          updatedAt: now - 60_000,
        }),
      );
      await assertSucceeds(
        setDoc(ref, { serverCreatedAt: serverTimestamp() }, { merge: true }),
      );
    });

    it('allows the legit two-step createCustomer: contact-free create, then the stamp merge', async () => {
      const ref = doc(db('alice'), 'users/alice/customers/c_two_step');
      await assertSucceeds(
        setDoc(ref, {
          name: 'Ada',
          slotState: 'active',
          createdAt: now - 60_000,
          updatedAt: now - 60_000,
        }),
      );
      await assertSucceeds(
        setDoc(ref, { serverCreatedAt: serverTimestamp() }, { merge: true }),
      );
    });
  });

  // The stamp op, when the rules engine grades it as a CREATE. The client's
  // create is set(baseDto) + set({serverCreatedAt}, merge=true) issued
  // back-to-back; on the Android/GitLive path the stamp op reaches the rules
  // engine as a create whose payload is only serverCreatedAt (the doc does not
  // exist in the state that op is graded against), and the commit is atomic — so
  // denying this shape denies the whole create. It must be ALLOWED.
  describe('the serverCreatedAt-only stamp write', () => {
    it('allows a stamp-only create on orders (the client stamp op graded as a create)', async () => {
      await assertSucceeds(
        setDoc(doc(db('alice'), 'users/alice/orders/o_stamp_only'), {
          serverCreatedAt: serverTimestamp(),
        }, { merge: true }),
      );
    });

    it('allows a stamp-only create on customers (the client stamp op graded as a create)', async () => {
      await assertSucceeds(
        setDoc(doc(db('alice'), 'users/alice/customers/c_stamp_only'), {
          serverCreatedAt: serverTimestamp(),
        }, { merge: true }),
      );
    });
  });

  // End-to-end shape of the client's create: base doc + stamp in one commit.
  // NOTE on coverage — this pair does NOT reproduce the device failure on its
  // own: the JS SDK folds two writes to the same doc inside a WriteBatch into a
  // single mutation, so the stamp never reaches the rules engine as its own op
  // here and these two passed even with the stamp-only-create guard in place.
  // The guard's actual bite is pinned by the stamp-only create tests above, which
  // were red before it was removed. Keep both: this pair is the app-shaped smoke
  // check, those are the discriminating regression pin.
  describe('the client create+stamp batch (writeBatch, as the app writes)', () => {
    it('allows the createOrder batch: base create + serverCreatedAt stamp in one commit', async () => {
      const aliceDb = db('alice');
      const batch = writeBatch(aliceDb);
      const ref = doc(aliceDb, 'users/alice/orders/batch-create');
      batch.set(ref, {
        customerName: 'Ada',
        status: 'PENDING',
        createdAt: Date.now(),
        updatedAt: Date.now(),
      });
      batch.set(ref, { serverCreatedAt: serverTimestamp() }, { merge: true });
      await assertSucceeds(batch.commit());
    });

    it('allows the createCustomer batch: base create + serverCreatedAt stamp in one commit', async () => {
      const aliceDb = db('alice');
      const batch = writeBatch(aliceDb);
      const ref = doc(aliceDb, 'users/alice/customers/batch-create');
      batch.set(ref, {
        name: 'Ada',
        slotState: 'active',
        createdAt: Date.now(),
        updatedAt: Date.now(),
      });
      batch.set(ref, { serverCreatedAt: serverTimestamp() }, { merge: true });
      await assertSucceeds(batch.commit());
    });
  });

  // The wall itself is unchanged: money/contact still belong in /private, and the
  // owner must still be able to write them there.
  it('the owner can still write money and contact to the /private sub-docs', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o_money_free/private/money'),
        { ownerId: 'alice', totalPrice: 40000, payments: [], costs: [] }),
    );
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c_contact_free/private/contact'),
        { ownerId: 'alice', phone: '+234', email: null, address: null }),
    );
  });
});

// Owner + Staff feature: money and customer contact live in owner-only
// /private sub-docs. These rules ship WITH the dual-write (Slice 2) so the
// new owner-client writes aren't default-denied; being isOwner-only they also
// pre-enforce the staff wall (a non-owner — the future staff member — is denied).
describe('owner-only /private sub-docs (money + contact wall)', () => {
  it('owner can write and read the customer private/contact sub-doc', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/private/contact'),
        { phone: '+2348011112222', email: null, address: null }),
    );
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice/customers/c1/private/contact')));
  });

  it('owner can write and read the order private/money sub-doc', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o1/private/money'),
        { totalPrice: 40000, itemPrices: { i1: 1000 } }),
    );
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice/orders/o1/private/money')));
  });

  it('a non-owner is denied reading or writing both private sub-docs', async () => {
    await assertFails(getDoc(doc(db('bob'), 'users/alice/customers/c1/private/contact')));
    await assertFails(
      setDoc(doc(db('bob'), 'users/alice/customers/c1/private/contact'), { phone: 'x' }),
    );
    await assertFails(getDoc(doc(db('bob'), 'users/alice/orders/o1/private/money')));
    await assertFails(
      setDoc(doc(db('bob'), 'users/alice/orders/o1/private/money'), { totalPrice: 1 }),
    );
  });
});

// Owner + Staff feature: an active member (custom claims role='staff',
// workshopUid=<owner>) may READ the owner's base work and advance an order's
// production status — but never the /private sub-docs, never non-status writes,
// and never another workshop's tree. `chidi` is alice's staff throughout.
describe('active staff member access', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      // Slice 4a: isActiveMember now also requires an ACTIVE membership doc.
      await setDoc(doc(admin, 'users/alice/memberships/chidi'), { status: 'active' });
      await setDoc(doc(admin, 'users/alice/customers/c1'), { name: 'Ada' });
      await setDoc(doc(admin, 'users/alice/customers/c1/private/contact'), { phone: '+234' });
      await setDoc(doc(admin, 'users/alice/customers/c1/measurements/m1'), { gender: 'FEMALE' });
      await setDoc(doc(admin, 'users/alice/orders/o1'), {
        status: 'PENDING',
        customerName: 'Ada',
        serverCreatedAt: serverTimestamp(),
        createdAt: 1000,
      });
      await setDoc(doc(admin, 'users/alice/orders/o1/private/money'), { totalPrice: 40000 });
    });
  });

  it('reads the base customer, measurement, and order', async () => {
    await assertSucceeds(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/c1')));
    await assertSucceeds(
      getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/c1/measurements/m1')),
    );
    await assertSucceeds(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1')));
  });

  it('loses access the instant the membership is revoked, even with a valid claim', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/memberships/chidi'), { status: 'revoked' });
    });
    // Stale claim still says role=staff/workshopUid=alice, but the doc gate denies.
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/c1')));
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1')));
  });

  it('is denied a base doc that still carries sensitive fields (dual-write window)', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/customers/withPhone'), { name: 'Bola', phone: '+234' });
      await setDoc(doc(admin, 'users/alice/orders/withMoney'), { status: 'PENDING', totalPrice: 5000 });
    });
    // The field-absence gate blocks staff until Slice 8 strips these fields.
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/withPhone')));
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/withMoney')));
  });

  // The GET guard must cover the SAME field set the strip script deletes
  // (ORDER_MONEY_FIELDS) — a legacy order carrying only depositPaid /
  // balanceRemaining is still money-bearing and must not reach staff.
  it('is denied an order carrying only the legacy deposit/balance money fields', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/orders/withDeposit'), { status: 'PENDING', depositPaid: 2000 });
      await setDoc(doc(admin, 'users/alice/orders/withBalance'), { status: 'PENDING', balanceRemaining: 3000 });
    });
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/withDeposit')));
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/withBalance')));
  });

  // Regression guard for the money/contact-wall LIST leak (2026-07-30 smoke): the
  // field-absence guard only gates single-doc GETs — Firestore rules are NOT query
  // filters, so a member LIST of the collection is not evaluated per-doc. Slice 8d
  // strips money/contact off the base docs (with a version floor guaranteeing no
  // base doc reaching this rule still carries them), so Slice 8e flips `allow list`
  // open to active members. GET keeps its own field-absence guard as defence-in-depth.
  it('can LIST stripped orders and customers collections (Slice 8e flip)', async () => {
    await asAdmin(async (admin) => {
      // Base docs in the post-8d stripped shape: no money, no contact.
      await setDoc(doc(admin, 'users/alice/orders/o-stripped'), {
        customerName: 'Ada',
        status: 'PENDING',
        items: [{ id: 'i1', garmentType: 'Agbada' }],
        createdAt: 1,
        updatedAt: 1,
      });
      await setDoc(doc(admin, 'users/alice/customers/c-stripped'), {
        name: 'Ada',
        slotState: 'active',
        createdAt: 1,
        updatedAt: 1,
      });
    });
    // NOTE: `allow list` has no per-doc field guard — rules are not query filters.
    // The 8d strip + version floor are the guarantee that no base doc carries
    // money/contact by the time this rule is deployed.
    await assertSucceeds(getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/orders')));
    await assertSucceeds(getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/customers')));
  });

  it('staff of another workshop still cannot LIST', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/orders/o1b'), {
        customerName: 'Ada',
        status: 'PENDING',
        createdAt: 1,
        updatedAt: 1,
      });
    });
    await assertFails(getDocs(collection(staffDb('mallory', 'bob'), 'users/alice/orders')));
    await assertFails(getDocs(collection(staffDb('mallory', 'bob'), 'users/alice/customers')));
  });

  it('owner can still LIST their own orders and customers (no regression)', async () => {
    await assertSucceeds(getDocs(collection(db('alice'), 'users/alice/orders')));
    await assertSucceeds(getDocs(collection(db('alice'), 'users/alice/customers')));
  });

  it('may still LIST measurements (needed to sew — measurement access is unchanged)', async () => {
    await assertSucceeds(
      getDocs(collection(staffDb('chidi', 'alice'), 'users/alice/customers/c1/measurements')),
    );
  });

  it('is denied the /private money and contact sub-docs (the wall)', async () => {
    await assertFails(
      getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/c1/private/contact')),
    );
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1/private/money')));
  });

  it('may advance an order status (status-only update)', async () => {
    await assertSucceeds(
      updateDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1'), {
        status: 'IN_PROGRESS',
        subStatus: 'SEWING',
        updatedAt: 123,
      }),
    );
  });

  it('cannot make a non-status order edit (money/customer/items stay owner-only)', async () => {
    await assertFails(
      updateDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1'), { totalPrice: 999 }),
    );
    await assertFails(
      updateDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1'), { customerName: 'x' }),
    );
  });

  it('cannot create or delete orders or customers', async () => {
    await assertFails(
      setDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o2'), { status: 'PENDING' }),
    );
    await assertFails(deleteDoc(doc(staffDb('chidi', 'alice'), 'users/alice/orders/o1')));
    await assertFails(deleteDoc(doc(staffDb('chidi', 'alice'), 'users/alice/customers/c1')));
  });

  it('cannot read a workshop it is not a member of', async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/bob/orders/ob1'), { status: 'PENDING' });
    });
    // chidi's claim scopes to alice, so bob's tree is off-limits.
    await assertFails(getDoc(doc(staffDb('chidi', 'alice'), 'users/bob/orders/ob1')));
  });
});

// Owner + Staff backend collections: memberships (owner-read + self-read,
// Admin-only writes) and staffInvites (bearer codes, never client-accessible).
describe('staff memberships + invites collections', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/memberships/chidi'), {
        status: 'pending',
        staffAuthUid: 'chidi',
      });
      await setDoc(doc(admin, 'staffInvites/CODE1'), { workshopUid: 'alice', status: 'open' });
    });
  });

  it('owner reads their memberships; a staff member reads only their own doc', async () => {
    await assertSucceeds(getDoc(doc(db('alice'), 'users/alice/memberships/chidi')));
    await assertSucceeds(getDoc(doc(db('chidi'), 'users/alice/memberships/chidi')));
    await assertFails(getDoc(doc(db('bob'), 'users/alice/memberships/chidi')));
  });

  it('denies all client writes to memberships (lifecycle is callable/Admin-only)', async () => {
    await assertFails(setDoc(doc(db('alice'), 'users/alice/memberships/x'), { status: 'active' }));
    await assertFails(
      updateDoc(doc(db('alice'), 'users/alice/memberships/chidi'), { status: 'active' }),
    );
    await assertFails(
      updateDoc(doc(db('chidi'), 'users/alice/memberships/chidi'), { status: 'active' }),
    );
    await assertFails(deleteDoc(doc(db('alice'), 'users/alice/memberships/chidi')));
  });

  it('never lets a client read or write staffInvites (redeem is via callable)', async () => {
    await assertFails(getDoc(doc(db('alice'), 'staffInvites/CODE1')));
    await assertFails(getDoc(doc(db('chidi'), 'staffInvites/CODE1')));
    await assertFails(setDoc(doc(db('mallory'), 'staffInvites/HACK'), { workshopUid: 'mallory' }));
  });
});

// Slice 8a: the owner reads money (orders) + contact (customers) for whole LISTS
// via one collectionGroup("private") query scoped by the `ownerId` field. A bare
// path-scoped rule can't authorize a collection-group query (rules-are-not-filters),
// so the query MUST be `where('ownerId','==', uid)` and the rule scopes on ownerId.
describe('Slice 8a: owner collectionGroup(private) read', () => {
  beforeEach(async () => {
    await asAdmin(async (admin) => {
      await setDoc(doc(admin, 'users/alice/orders/o1/private/money'), {
        ownerId: 'alice', orderId: 'o1', totalPrice: 5000,
      });
      await setDoc(doc(admin, 'users/alice/customers/c1/private/contact'), {
        ownerId: 'alice', customerId: 'c1', phone: '+234',
      });
      // A different owner's private docs must never leak into alice's query.
      await setDoc(doc(admin, 'users/bob/orders/ob1/private/money'), {
        ownerId: 'bob', orderId: 'ob1', totalPrice: 9000,
      });
    });
  });

  it('allows an owner collectionGroup(private) query filtered by their own ownerId', async () => {
    await assertSucceeds(
      getDocs(query(collectionGroup(db('alice'), 'private'), where('ownerId', '==', 'alice'))),
    );
  });

  it('returns ONLY the owner own private docs (money + contact), never another owner', async () => {
    const snap = await getDocs(
      query(collectionGroup(db('alice'), 'private'), where('ownerId', '==', 'alice')),
    );
    const paths = snap.docs.map((d) => d.ref.path);
    expect(paths).toHaveLength(2);
    expect(paths.every((p) => p.startsWith('users/alice/'))).toBe(true);
  });

  it('rejects a collectionGroup(private) query NOT scoped by ownerId', async () => {
    await assertFails(getDocs(collectionGroup(db('alice'), 'private')));
  });

  it('rejects reading another owner private docs by filtering on their ownerId', async () => {
    await assertFails(
      getDocs(query(collectionGroup(db('alice'), 'private'), where('ownerId', '==', 'bob'))),
    );
  });

  it('the collection-group rule does not open a direct staff read of the wall', async () => {
    // mallory is a stranger; the money/contact wall must still deny direct reads.
    await assertFails(getDoc(doc(db('mallory'), 'users/alice/orders/o1/private/money')));
    await assertFails(getDoc(doc(db('mallory'), 'users/alice/customers/c1/private/contact')));
  });
});
