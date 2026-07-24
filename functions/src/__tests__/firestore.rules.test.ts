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
