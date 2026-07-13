import * as functions from 'firebase-functions/v1';
import { recordReferralAttributionHandler } from '../../referral/recordAttribution';
import { QUALIFY_WINDOW_DAYS, DAY_MS } from '../../referral/referralConstants';

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

const NOW = new Date('2026-07-02T12:00:00Z');
const deps = (db: any, creationMs: number | null = null) => ({
  db,
  now: () => NOW,
  userCreationTimeMs: async () => creationMs,
});

function ctx(uid: string | null, email?: string): functions.https.CallableContext {
  if (!uid) return {} as functions.https.CallableContext;
  return { auth: { uid, token: email ? { email } : {} } } as unknown as functions.https.CallableContext;
}

// A store pre-seeded with an active affiliate marketer + its code.
function seeded(extra: Record<string, any> = {}) {
  return makeDb({
    'marketers/m1': { type: 'affiliate', referrerUid: null, email: 'ada@example.com', status: 'active' },
    'referralCodes/ABCD1234': { marketerId: 'm1' },
    ...extra,
  });
}

describe('recordReferralAttributionHandler — guards', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = seeded();
    await expect(recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx(null), deps(db)))
      .rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('rejects a missing code', async () => {
    const { db } = seeded();
    await expect(recordReferralAttributionHandler({}, ctx('bob'), deps(db)))
      .rejects.toMatchObject({ code: 'invalid-argument', message: 'missing_code' });
  });

  it('rejects a code containing path separators before building a doc path', async () => {
    const { db } = seeded();
    for (const bad of ['a/b/c', '../../secret', 'code.', 'has space']) {
      await expect(recordReferralAttributionHandler({ code: bad }, ctx('bob'), deps(db)))
        .rejects.toMatchObject({ code: 'invalid-argument' });
    }
  });

  it('ignores a malformed deviceHash rather than failing attribution', async () => {
    const { store, db } = seeded();
    const res = await recordReferralAttributionHandler(
      { code: 'ABCD1234', deviceHash: 'bad/hash' }, ctx('bob'), deps(db),
    );
    expect(res.status).toBe('attributed');
    expect(store.get('referrals/bob').deviceHash).toBeNull();
    // A missing/malformed hash disables device-reuse detection, so we surface it
    // as an ADVISORY flag (not blocking) rather than skipping silently.
    expect(store.get('referrals/bob').flags).toEqual(['missing_device_hash']);
  });

  it('rejects an unknown code', async () => {
    const { db } = seeded();
    await expect(recordReferralAttributionHandler({ code: 'NOPE9999' }, ctx('bob'), deps(db)))
      .rejects.toMatchObject({ code: 'invalid-argument', message: 'referral_code_not_found' });
  });

  it('rejects a disabled marketer (indistinguishable from unknown)', async () => {
    const { db } = makeDb({
      'marketers/m1': { type: 'affiliate', status: 'disabled', email: 'ada@example.com' },
      'referralCodes/ABCD1234': { marketerId: 'm1' },
    });
    await expect(recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db)))
      .rejects.toMatchObject({ code: 'invalid-argument', message: 'referral_code_not_found' });
  });
});

describe('recordReferralAttributionHandler — happy path', () => {
  it('creates the referral, claims the device, stamps referredBy, no flags', async () => {
    const { store, db } = seeded({ 'users/bob': { createdAt: NOW.getTime() } });
    const res = await recordReferralAttributionHandler(
      { code: 'ABCD1234', deviceHash: 'devhash1', source: 'install_referrer' },
      ctx('bob', 'newuser@example.com'),
      deps(db),
    );
    expect(res).toEqual({ status: 'attributed', marketerId: 'm1' });

    const ref = store.get('referrals/bob');
    expect(ref).toMatchObject({
      marketerId: 'm1', code: 'ABCD1234', referrerType: 'affiliate',
      milestone: 'attributed', payoutState: 'none', payoutAmount: 0,
      deviceHash: 'devhash1', attributionSource: 'install_referrer',
      activeDays: 0, activeDayKeys: [], flags: [],
    });
    expect(ref.qualificationWindowEndsAt.toMillis()).toBe(NOW.getTime() + QUALIFY_WINDOW_DAYS * DAY_MS);
    expect(store.get('referralDevices/devhash1')).toMatchObject({ referredUid: 'bob', marketerId: 'm1' });
    expect(store.get('users/bob')).toMatchObject({ referredBy: 'm1' });
  });

  it('increments the marketer install counter once (funnel top)', async () => {
    const { store, db } = seeded({ 'marketers/m1': { type: 'affiliate', status: 'active', email: 'a@b.co', installs: 4 } });
    await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    expect(store.get('marketers/m1').installs).toBe(5);
    // Idempotent re-run (already attributed) must NOT bump it again.
    await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    expect(store.get('marketers/m1').installs).toBe(5);
  });

  it('normalizes a lowercased / spaced manually-entered code', async () => {
    const { store, db } = seeded();
    const res = await recordReferralAttributionHandler(
      { code: ' abcd-1234 ', source: 'manual' }, ctx('bob'), deps(db),
    );
    expect(res.marketerId).toBe('m1');
    expect(store.get('referrals/bob')).toMatchObject({ code: 'ABCD1234' });
  });

  it('does NOT create the user doc when attribution precedes profile creation', async () => {
    const { store, db } = seeded(); // no users/bob seeded
    await recordReferralAttributionHandler({ code: 'ABCD1234', deviceHash: 'd' }, ctx('bob'), deps(db));
    // referral is recorded (canonical), but no partial users/bob doc is created
    // that would later trip the serverOnlyField('referredBy') rule.
    expect(store.has('users/bob')).toBe(false);
    expect(store.get('referrals/bob')).toMatchObject({ marketerId: 'm1', milestone: 'attributed' });
  });

  it('falls back to attribution time when the auth creation time is unavailable', async () => {
    const { store, db } = seeded();
    await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db, null));
    expect(store.get('referrals/bob').qualificationWindowEndsAt.toMillis())
      .toBe(NOW.getTime() + QUALIFY_WINDOW_DAYS * DAY_MS);
  });

  it('anchors the qualification window on server attribution time, not signup', async () => {
    const signup = Date.parse('2026-06-20T00:00:00Z'); // 12 days before NOW
    // Client also plants a bogus far-future createdAt — must be ignored either way.
    const { store, db } = seeded({ 'users/bob': { createdAt: 9_999_999_999_999 } });
    await recordReferralAttributionHandler({ code: 'ABCD1234', deviceHash: 'd' }, ctx('bob'), deps(db, signup));
    const ref = store.get('referrals/bob');
    // signupAt still records the real auth-creation instant (audit only).
    expect(ref.signupAt.toMillis()).toBe(signup);
    // The window runs from attribution time (server now()), NOT signup. This fixes
    // late code entry (>14d after signup) and is inherently un-gameable — now() is
    // server-set, so there is no client-editable field to extend the window with.
    expect(ref.qualificationWindowStartsAt.toMillis()).toBe(NOW.getTime());
    expect(ref.qualificationWindowEndsAt.toMillis()).toBe(NOW.getTime() + QUALIFY_WINDOW_DAYS * DAY_MS);
  });

  it('lets a late-referred user still qualify (window opens at attribution, not signup)', async () => {
    // User signed up 30 days ago, then entered a code today — under signup-anchoring
    // the window closed 16 days ago and they could NEVER qualify. Attribution-anchoring
    // gives them a fresh 14-day window from now.
    const signup = NOW.getTime() - 30 * DAY_MS;
    const { store, db } = seeded({ 'users/bob': { createdAt: signup } });
    await recordReferralAttributionHandler({ code: 'ABCD1234', deviceHash: 'd', source: 'manual' }, ctx('bob'), deps(db, signup));
    const ref = store.get('referrals/bob');
    expect(ref.qualificationWindowEndsAt.toMillis()).toBe(NOW.getTime() + QUALIFY_WINDOW_DAYS * DAY_MS);
    expect(ref.qualificationWindowEndsAt.toMillis()).toBeGreaterThan(NOW.getTime());
  });
});

describe('recordReferralAttributionHandler — idempotency', () => {
  it('returns already_attributed without rewriting on a second call', async () => {
    const { store, db } = seeded();
    await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    const firstWindow = store.get('referrals/bob').qualificationWindowEndsAt.toMillis();

    const again = await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    expect(again).toMatchObject({ status: 'already_attributed', marketerId: 'm1' });
    expect(store.get('referrals/bob').qualificationWindowEndsAt.toMillis()).toBe(firstWindow);
  });

  it('returns the PERSISTED attribution when it loses the create race', async () => {
    const { store, db } = seeded();
    const origRunTx = db.runTransaction;
    // Simulate a concurrent winner creating referrals/bob AFTER the pre-tx read
    // but BEFORE this transaction runs.
    db.runTransaction = async (fn: any) => {
      store.set('referrals/bob', { marketerId: 'm1', flags: ['self_referral'] });
      return origRunTx(fn);
    };
    const res = await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    expect(res).toEqual({ status: 'already_attributed', marketerId: 'm1' });
    // Flags are persisted server-side but never echoed back to the caller.
    expect(store.get('referrals/bob').flags).toEqual(['self_referral']);
  });
});

describe('recordReferralAttributionHandler — fraud flags', () => {
  it('flags a user-referrer redeeming their own link (self_referral)', async () => {
    const { store, db } = makeDb({
      'marketers/m1': { type: 'user', referrerUid: 'bob', email: 'x@y.com', status: 'active' },
      'referralCodes/USERCODE': { marketerId: 'm1' },
    });
    const res = await recordReferralAttributionHandler({ code: 'USERCODE' }, ctx('bob', 'bob@x.com'), deps(db));
    expect(res).toEqual({ status: 'attributed', marketerId: 'm1' });
    // Fraud flags are recorded on the doc, never returned to the caller.
    expect(store.get('referrals/bob').flags).toContain('self_referral');
    expect(store.get('referrals/bob').payoutState).toBe('none');
  });

  it('flags an email match against the marketer (self_referral)', async () => {
    const { db, store } = seeded();
    const res = await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob', 'Ada@Example.com'), deps(db));
    expect(res).toEqual({ status: 'attributed', marketerId: 'm1' });
    expect(store.get('referrals/bob').flags).toContain('self_referral');
    expect(store.get('referrals/bob').referredEmailHash).toEqual(expect.any(String));
  });

  it('flags a missing device hash as ADVISORY — surfaced but still payable', async () => {
    const { store, db } = seeded();
    // No deviceHash at all → device-reuse detection can't run for this install.
    const res = await recordReferralAttributionHandler({ code: 'ABCD1234' }, ctx('bob'), deps(db));
    expect(res).toEqual({ status: 'attributed', marketerId: 'm1' });
    const ref = store.get('referrals/bob');
    expect(ref.deviceHash).toBeNull();
    expect(ref.flags).toEqual(['missing_device_hash']);
    // Advisory only — attribution is not blocked and the milestone still records.
    expect(ref.payoutState).toBe('none');
    expect(ref.milestone).toBe('attributed');
  });

  it('does NOT add missing_device_hash when a valid hash is provided', async () => {
    const { store, db } = seeded();
    await recordReferralAttributionHandler({ code: 'ABCD1234', deviceHash: 'devhash9' }, ctx('bob'), deps(db));
    expect(store.get('referrals/bob').flags).toEqual([]);
  });

  it('flags device reuse and does not re-claim the device', async () => {
    const { store, db } = seeded({ 'referralDevices/dev1': { referredUid: 'someone-else', marketerId: 'm0' } });
    const res = await recordReferralAttributionHandler(
      { code: 'ABCD1234', deviceHash: 'dev1' }, ctx('bob'), deps(db),
    );
    expect(res).toEqual({ status: 'attributed', marketerId: 'm1' });
    expect(store.get('referrals/bob').flags).toContain('device_reuse');
    // The device stays owned by the original user — not re-pointed at bob.
    expect(store.get('referralDevices/dev1')).toEqual({ referredUid: 'someone-else', marketerId: 'm0' });
    expect(store.get('referrals/bob').deviceHash).toBe('dev1');
  });
});
