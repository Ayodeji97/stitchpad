import { clawbackReferralOnDelete, REJECT_ACCOUNT_DELETED } from '../../referral/clawback';

// Fake Firestore enforcing read-before-write inside transactions.
function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
  });
  const db: any = {
    doc: (path: string) => docRef(path),
    runTransaction: async (fn: any) => {
      let hasWritten = false;
      const tx = {
        get: async (ref: any) => {
          if (hasWritten) throw new Error('Firestore transactions require all reads before writes');
          return { exists: store.has(ref.path), data: () => store.get(ref.path) };
        },
        set: (ref: any, data: any, opts?: { merge?: boolean }) => {
          hasWritten = true;
          const prev = store.get(ref.path) ?? {};
          store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
        },
      };
      return fn(tx);
    },
  };
  return { store, db };
}

const NOW = () => new Date(Date.UTC(2026, 6, 20, 4, 0, 0));

function seed(referral: Record<string, any> | null, marketer: Record<string, any> = {}) {
  const init: Record<string, any> = {
    'marketers/m1': { pendingAmount: 500_000, confirmedAmount: 500_000, ...marketer },
  };
  if (referral) init['referrals/u1'] = { marketerId: 'm1', payoutAmount: 500_000, ...referral };
  return makeDb(init);
}

describe('clawbackReferralOnDelete', () => {
  it('reverses a pending payout and marks the referral rejected', async () => {
    const { store, db } = seed({ payoutState: 'pending' });
    const outcome = await clawbackReferralOnDelete('u1', db, NOW);
    expect(outcome).toBe('clawed_back');
    expect(store.get('referrals/u1')).toMatchObject({
      payoutState: 'rejected',
      payoutRejectedReason: REJECT_ACCOUNT_DELETED,
    });
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 0, confirmedAmount: 500_000 });
  });

  it('reverses a confirmed (unpaid) payout from confirmedAmount', async () => {
    const { store, db } = seed({ payoutState: 'confirmed' });
    const outcome = await clawbackReferralOnDelete('u1', db, NOW);
    expect(outcome).toBe('clawed_back');
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 500_000, confirmedAmount: 0 });
  });

  it('does NOT claw back an already-paid payout (money already left)', async () => {
    const { store, db } = seed({ payoutState: 'paid' });
    const outcome = await clawbackReferralOnDelete('u1', db, NOW);
    expect(outcome).toBe('none');
    expect(store.get('referrals/u1').payoutState).toBe('paid');
    expect(store.get('marketers/m1')).toMatchObject({ pendingAmount: 500_000, confirmedAmount: 500_000 });
  });

  it('is a no-op for a non-payable referral (none / already rejected)', async () => {
    for (const state of ['none', 'rejected']) {
      const { db } = seed({ payoutState: state });
      expect(await clawbackReferralOnDelete('u1', db, NOW)).toBe('none');
    }
  });

  it('is a no-op when the deleted user was never referred', async () => {
    const { db } = seed(null);
    expect(await clawbackReferralOnDelete('u1', db, NOW)).toBe('none');
  });
});
