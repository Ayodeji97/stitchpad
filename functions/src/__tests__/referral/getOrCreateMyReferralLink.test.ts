import * as functions from 'firebase-functions/v1';
import { getOrCreateMyReferralLinkHandler } from '../../referral/getOrCreateMyReferralLink';

// Minimal in-memory Firestore double: doc get/set + a single-shot transaction
// with read-before-write. Mirrors the inline fake used by
// createMarketer.test.ts / recordAttribution.test.ts (no shared helper exists
// yet for the referral test suite).
function makeDb(initial: Record<string, any> = {}) {
  const store = new Map<string, any>(Object.entries(initial));
  const docRef = (path: string): any => ({
    path,
    get: async () => ({ exists: store.has(path), data: () => store.get(path) }),
    set: async (data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(path) ?? {};
      store.set(path, opts?.merge ? { ...prev, ...data } : data);
    },
  });
  const tx = {
    get: async (ref: any) => ({ exists: store.has(ref.path), data: () => store.get(ref.path) }),
    set: (ref: any, data: any, opts?: { merge?: boolean }) => {
      const prev = store.get(ref.path) ?? {};
      store.set(ref.path, opts?.merge ? { ...prev, ...data } : data);
    },
  };
  const db: any = {
    doc: (path: string) => docRef(path),
    runTransaction: async (fn: any) => fn(tx),
  };
  return { store, db };
}

const ctx = (uid?: string) => ({ auth: uid ? { uid, token: {} } : undefined }) as unknown as functions.https.CallableContext;

describe('getOrCreateMyReferralLinkHandler', () => {
  it('mints a payout-disabled user-referrer and stores the code on the user doc', async () => {
    const { db } = makeDb({ 'users/u1': { displayName: 'Ada', businessName: 'Ada Styles', email: 'ada@x.com' } });
    let n = 0;
    const deps = {
      db,
      now: () => new Date('2026-08-03T00:00:00Z'),
      randomCode: () => `CODE${n++}`,
      randomId: () => 'rid',
    };

    const res = await getOrCreateMyReferralLinkHandler({}, ctx('u1'), deps);

    expect(res.code).toBe('CODE0');
    expect(res.url).toBe('https://link.getstitchpad.com/r/CODE0');
    expect(res.playUrl).toBe('https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DCODE0');

    const codeDoc = await db.doc('referralCodes/CODE0').get();
    expect(codeDoc.exists).toBe(true);
    const marketer = await db.doc(`marketers/${codeDoc.data().marketerId}`).get();
    expect(marketer.data()).toMatchObject({
      type: 'user',
      program: 'founding_tailors',
      payoutRatePerUser: 0,
      referrerUid: 'u1',
      name: 'Ada Styles',
    });

    const userDoc = await db.doc('users/u1').get();
    expect(userDoc.data().referralCode).toBe('CODE0');
  });

  it('falls back to displayName then "Tailor" when businessName is missing', async () => {
    const { db } = makeDb({ 'users/u2': { displayName: 'Bola', email: 'bola@x.com' } });
    const deps = {
      db,
      now: () => new Date('2026-08-03T00:00:00Z'),
      randomCode: () => 'CODEB1',
      randomId: () => 'rid2',
    };

    await getOrCreateMyReferralLinkHandler({}, ctx('u2'), deps);

    const codeDoc = await db.doc('referralCodes/CODEB1').get();
    const marketer = await db.doc(`marketers/${codeDoc.data().marketerId}`).get();
    expect(marketer.data()).toMatchObject({ name: 'Bola' });
  });

  it('is idempotent: a second call returns the same code and does not mint again', async () => {
    const { db, store } = makeDb({ 'users/u1': { displayName: 'Ada', email: 'ada@x.com', referralCode: 'CODE0' } });
    const deps = {
      db,
      now: () => new Date(),
      randomCode: () => 'SHOULD_NOT_BE_USED',
      randomId: () => 'rid',
    };

    const res = await getOrCreateMyReferralLinkHandler({}, ctx('u1'), deps);

    expect(res.code).toBe('CODE0');
    // No marketer/code doc was minted for the never-used candidate code.
    expect(store.has('referralCodes/SHOULD_NOT_BE_USED')).toBe(false);
  });

  it('re-reads and returns the winner code when a concurrent call already minted (race)', async () => {
    const { db, store } = makeDb({ 'users/u1': { displayName: 'Ada', email: 'ada@x.com' } });
    const origRunTx = db.runTransaction;
    // Simulate a concurrent mint winning between the pre-transaction read and
    // the transaction actually running.
    db.runTransaction = async (fn: any) => {
      store.set('users/u1', { displayName: 'Ada', email: 'ada@x.com', referralCode: 'WINNER1' });
      return origRunTx(fn);
    };
    const deps = {
      db,
      now: () => new Date(),
      randomCode: () => 'LOSER99',
      randomId: () => 'rid',
    };

    const res = await getOrCreateMyReferralLinkHandler({}, ctx('u1'), deps);

    expect(res.code).toBe('WINNER1');
    expect(store.has('referralCodes/LOSER99')).toBe(false);
  });

  it('rejects an unauthenticated caller', async () => {
    const { db } = makeDb({});
    const deps = { db, now: () => new Date(), randomCode: () => 'X', randomId: () => 'rid' };
    await expect(getOrCreateMyReferralLinkHandler({}, ctx(undefined), deps)).rejects.toMatchObject({ code: 'unauthenticated' });
  });
});
