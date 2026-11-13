import * as admin from 'firebase-admin';
import {
  shouldGrantLaunchFree,
  buildLaunchGrantFields,
  grantLaunchFreeOnStaffDeparture,
  LAUNCH_GRANT_SOURCE,
} from '../../freemium/launchGrant';
import { makeLaunchGrantDeps, makeStaffDb } from '../staff/staffTestDb';

describe('shouldGrantLaunchFree', () => {
  it('grants a brand-new doc with no subscription fields', () => {
    expect(shouldGrantLaunchFree(undefined)).toBe(true);
    expect(shouldGrantLaunchFree({})).toBe(true);
  });

  it('grants an active free user', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'free', subscriptionStatus: 'active' }),
    ).toBe(true);
  });

  it('grants an expired former subscriber', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'expired' }),
    ).toBe(true);
  });

  it('skips an active paid subscriber (real payer / tester / gift)', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'active' }),
    ).toBe(false);
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'atelier', subscriptionStatus: 'active' }),
    ).toBe(false);
  });
});

describe('buildLaunchGrantFields', () => {
  it('sets tier=atelier + active + tag, and deletes stale billing fields', () => {
    const fields = buildLaunchGrantFields(new Date('2026-07-06T00:00:00Z'));
    expect(fields.subscriptionTier).toBe('atelier');
    expect(fields.subscriptionStatus).toBe('active');
    expect(fields.grantSource).toBe(LAUNCH_GRANT_SOURCE);

    const deleteSentinel = admin.firestore.FieldValue.delete();
    const staleFields = [
      'subscriptionRenews',
      'subscriptionSource',
      'subscriptionFallbackTier',
      'subscriptionFallbackEndsAt',
      'appleOriginalTransactionId',
      'appleLastSignedDate',
    ] as const;

    for (const field of staleFields) {
      expect(deleteSentinel.isEqual(fields[field])).toBe(true);
    }
  });

  it('never writes or deletes subscriptionEndsAt (a grant cannot extend/remove an end date)', () => {
    const fields = buildLaunchGrantFields(new Date('2026-07-06T00:00:00Z'));
    expect(Object.prototype.hasOwnProperty.call(fields, 'subscriptionEndsAt')).toBe(false);
  });
});

// Invariant: the launch grant is first-time only and never extends or removes an
// existing end date. These drive the real predicate + the real field builder through
// the in-memory Firestore fake used by the staff suites.
describe('launch grant re-grant invariant', () => {
  const NOW = new Date('2026-11-13T12:00:00Z');
  const PAST = admin.firestore.Timestamp.fromDate(new Date('2026-10-01T00:00:00Z'));
  const FUTURE = admin.firestore.Timestamp.fromDate(new Date('2027-01-01T00:00:00Z'));

  async function departAndRead(user?: Record<string, unknown>) {
    const { db, store } = makeStaffDb(user ? { 'users/chidi': user } : {});
    const writes: string[] = [];
    const hooks = makeLaunchGrantDeps(db);
    const writeGrant = hooks.writeGrant;
    await grantLaunchFreeOnStaffDeparture(
      db,
      { ...hooks, writeGrant: async (uid, now) => { writes.push(uid); await writeGrant(uid, now); } },
      'chidi',
      NOW,
    );
    return { doc: store.get('users/chidi'), writes };
  }

  it('first grant: a user with no doc gets the launch grant with no end date', async () => {
    const { doc, writes } = await departAndRead();
    expect(writes).toEqual(['chidi']);
    expect(doc).toMatchObject({
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
    });
    expect(doc?.subscriptionEndsAt).toBeUndefined();
  });

  it('first grant: a free user with no end date and no prior grant is granted (unchanged behaviour)', async () => {
    expect(shouldGrantLaunchFree({ subscriptionTier: 'free', subscriptionStatus: 'active' })).toBe(true);
    const { writes } = await departAndRead({ subscriptionTier: 'free', subscriptionStatus: 'active' });
    expect(writes).toEqual(['chidi']);
  });

  it('re-grant for a user with a FUTURE end date leaves the doc untouched', async () => {
    const user = {
      subscriptionTier: 'free',
      subscriptionStatus: 'expired',
      subscriptionEndsAt: FUTURE,
      subscriptionRenews: false,
    };
    expect(shouldGrantLaunchFree(user)).toBe(false);
    const { doc, writes } = await departAndRead(user);
    expect(writes).toEqual([]);
    expect(doc).toEqual(user);
  });

  it('re-grant for a user with a PAST end date stays expired, not endless', async () => {
    const user = {
      subscriptionTier: 'free',
      subscriptionStatus: 'expired',
      subscriptionEndsAt: PAST,
      subscriptionRenews: false,
      subscriptionSource: 'paystack',
    };
    expect(shouldGrantLaunchFree(user)).toBe(false);
    const { doc, writes } = await departAndRead(user);
    expect(writes).toEqual([]);
    expect(doc).toEqual(user);
  });

  it('a launch_free user whose time-limited grant lapsed is NOT re-granted on staff departure', async () => {
    // Shape after the trial cutoff migration sets an end date and expirePrepaidSubscriptions
    // lapses the doc: tier free / expired, end date kept, launch marker kept.
    const user = {
      subscriptionTier: 'free',
      subscriptionStatus: 'expired',
      subscriptionEndsAt: PAST,
      subscriptionRenews: false,
      grantSource: LAUNCH_GRANT_SOURCE,
    };
    const { doc, writes } = await departAndRead(user);
    expect(writes).toEqual([]);
    expect(doc).toEqual(user);
  });

  it('a prior launch grant with no end date that is no longer active is not re-minted', async () => {
    const user = { subscriptionTier: 'free', subscriptionStatus: 'expired', grantSource: LAUNCH_GRANT_SOURCE };
    expect(shouldGrantLaunchFree(user)).toBe(false);
    const { writes } = await departAndRead(user);
    expect(writes).toEqual([]);
  });

  it('a user with an endless launch grant today is left exactly as-is', async () => {
    const grantedAt = admin.firestore.Timestamp.fromDate(new Date('2026-07-06T00:00:00Z'));
    const user = {
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
      grantedAt,
    };
    const { doc, writes } = await departAndRead(user);
    expect(writes).toEqual([]);
    expect(doc).toEqual(user);
    expect(doc?.subscriptionEndsAt).toBeUndefined();
  });

  it('a time-limited launch grant (cutoff set, still active) keeps its end date', async () => {
    const user = {
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
      subscriptionEndsAt: FUTURE,
      subscriptionRenews: false,
    };
    const { doc, writes } = await departAndRead(user);
    expect(writes).toEqual([]);
    expect(doc).toEqual(user);
  });

  it('a purchase racing in before the write keeps its end date (builder never clears it)', async () => {
    const { db, store } = makeStaffDb({
      'users/chidi': { subscriptionTier: 'pro', subscriptionStatus: 'active', subscriptionEndsAt: FUTURE },
    });
    await db.doc('users/chidi').set(buildLaunchGrantFields(NOW), { merge: true });
    expect(store.get('users/chidi')?.subscriptionEndsAt).toBe(FUTURE);
  });

  it('does nothing when the launch-free flag is off', async () => {
    const { db, store } = makeStaffDb();
    await grantLaunchFreeOnStaffDeparture(
      db,
      makeLaunchGrantDeps(db, { isGrantEnabled: async () => false }),
      'chidi',
      NOW,
    );
    expect(store.has('users/chidi')).toBe(false);
  });
});
