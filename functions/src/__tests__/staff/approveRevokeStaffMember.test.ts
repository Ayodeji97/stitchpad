import { approveStaffMemberHandler } from '../../staff/approveStaffMember';
import { revokeStaffMemberHandler } from '../../staff/revokeStaffMember';
import { buildLaunchGrantFields, LAUNCH_GRANT_SOURCE } from '../../freemium/launchGrant';
import { makeStaffDb, makeClaimsRecorder, authedCtx } from './staffTestDb';

const NOW = new Date('2026-07-28T10:00:00Z');
const deps = (db: ReturnType<typeof makeStaffDb>['db'], claims = makeClaimsRecorder()) => ({
  db,
  setClaims: claims.setClaims,
  now: () => NOW,
  _claims: claims.claims,
});

// Revoke-only deps: adds the launch-grant hooks on top of the shared `deps()` shape.
// `writeGrant` mirrors production exactly (real buildLaunchGrantFields through the fake
// db) so tests assert on real field content, not a stub.
const revokeDeps = (
  db: ReturnType<typeof makeStaffDb>['db'],
  overrides: Partial<{
    isGrantEnabled: () => Promise<boolean>;
    writeGrant: (uid: string, now: Date) => Promise<void>;
  }> = {},
  claims = makeClaimsRecorder(),
) => ({
  ...deps(db, claims),
  isGrantEnabled: async () => true,
  writeGrant: async (uid: string, now: Date) => {
    await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
  },
  ...overrides,
});

describe('approveStaffMemberHandler', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = makeStaffDb();
    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx(), deps(db)),
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('404s an unknown membership', async () => {
    const { db } = makeStaffDb();
    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db)),
    ).rejects.toMatchObject({ code: 'not-found', message: 'membership_not_found' });
  });

  it('activates a pending membership and sets the staff custom claim', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'pending' } });
    const d = deps(db);
    const res = await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), d);

    expect(res).toEqual({ staffAuthUid: 'chidi', status: 'active' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'active' });
    expect(d._claims.get('chidi')).toEqual({ workshopUid: 'alice', role: 'staff' });
  });

  it('refuses to re-approve a revoked membership without minting a claim', async () => {
    const { db } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'revoked' } });
    const d = deps(db);
    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), d),
    ).rejects.toMatchObject({ message: 'membership_revoked' });
    // Rejected before any claim was issued (no active-looking session left behind).
    expect(d._claims.has('chidi')).toBe(false);
  });

  it('rolls the claim back if the membership transaction fails', async () => {
    // Claim-first, but a claim on a non-active doc would show an active-looking
    // session with denied reads. If the doc transaction fails, the claim must be
    // cleared so we never leave a claim without a matching active doc.
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'pending' } });
    const claims = makeClaimsRecorder();
    // Make the membership transaction fail (e.g. contention / precondition).
    const failingDb = {
      ...db,
      runTransaction: async () => { throw new Error('firestore_down'); },
    } as unknown as typeof db;

    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), {
        db: failingDb,
        setClaims: claims.setClaims,
        now: () => NOW,
      }),
    ).rejects.toThrow('firestore_down');

    // Doc never flipped, and the claim was rolled back to null.
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'pending' });
    expect(claims.claims.get('chidi')).toBeNull();
  });

  it('approve creates the staff roster doc in the same transaction', async () => {
    const claims = makeClaimsRecorder();
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending', staffName: 'Chidi O' },
    });
    await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db, claims));
    expect(store.get('users/alice/team/chidi')).toMatchObject({
      name: 'Chidi O', kind: 'staff', status: 'active',
    });
    expect(typeof (store.get('users/alice/team/chidi') as { colorSeed?: number }).colorSeed).toBe('number');
  });

  it('blank staffName falls back to the email local part on the roster doc', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': {
        status: 'pending', staffName: '', staffEmail: 'chidi.okafor@example.com',
      },
    });
    await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ name: 'chidi.okafor' });
  });

  it('blank staffName AND email keeps the generic roster label', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending', staffName: '' },
    });
    await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ name: 'Staff member' });
  });

  it('re-approve after cancel reactivates the roster doc via merge', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending', staffName: 'Chidi O' },
      'users/alice/team/chidi': { name: 'Chidi O', kind: 'staff', status: 'archived', colorSeed: 3 },
    });
    await approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({
      name: 'Chidi O', kind: 'staff', status: 'active', colorSeed: 3,
    });
  });

  it('TOCTOU race: membership flipped to revoked between pre-check and tx, rolls back both claims and roster', async () => {
    // Simulate TOCTOU race: membership passes pre-check as pending, but a
    // concurrent cancel flips it to revoked before the transaction runs.
    // The in-tx re-check at approveStaffMember.ts:68-69 catches it and throws.
    // Because the fake now buffers writes, the roster doc is NOT created.
    // The catch at line 74 rolls back the claim to null.
    const claims = makeClaimsRecorder();
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending', staffName: 'Chidi O' },
    });

    // Intercept runTransaction to simulate status flip mid-flight.
    const origRunTx = db.runTransaction.bind(db);
    db.runTransaction = async (fn: any) => {
      // Between pre-check and in-tx, a concurrent cancel flips status to revoked.
      store.set('users/alice/memberships/chidi', {
        ...(store.get('users/alice/memberships/chidi') as object),
        status: 'revoked',
      });
      return origRunTx(fn);
    };

    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db, claims)),
    ).rejects.toMatchObject({ code: 'failed-precondition' });

    // Because buffered writes were discarded, roster doc was NOT created.
    expect(store.has('users/alice/team/chidi')).toBe(false);
    // Claim was set before the tx, but rolled back in the catch.
    expect(claims.claims.get('chidi')).toBeNull();
  });
});

describe('revokeStaffMemberHandler', () => {
  it('revokes an active membership and clears the staff custom claim', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    const d = revokeDeps(db);
    // seed a prior claim so we can see it cleared
    await d.setClaims('chidi', { workshopUid: 'alice', role: 'staff' });

    const res = await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), d);

    expect(res).toEqual({ staffAuthUid: 'chidi', status: 'revoked' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    expect(d._claims.get('chidi')).toBeNull();
  });

  it('404s an unknown membership', async () => {
    const { db } = makeStaffDb();
    await expect(
      revokeStaffMemberHandler({ staffAuthUid: 'ghost' }, authedCtx('alice'), revokeDeps(db)),
    ).rejects.toMatchObject({ code: 'not-found' });
  });

  it('flips the membership doc to revoked before clearing the claim', async () => {
    // If setClaims fails, the doc must already be revoked so the rules
    // (which require an active doc) cut access even on a stale claimed token.
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    const failingClaims = async () => {
      throw new Error('claims_backend_down');
    };
    await expect(
      revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), {
        db,
        setClaims: failingClaims,
        now: () => NOW,
        isGrantEnabled: async () => true,
        writeGrant: async () => {},
      }),
    ).rejects.toThrow('claims_backend_down');
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
  });

  it('revoke archives the roster doc but keeps it resolvable', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/team/chidi': { name: 'Chidi O', kind: 'staff', status: 'active', colorSeed: 3 },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), revokeDeps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ status: 'archived', name: 'Chidi O' });
  });

  it('revoke of a pending member (no roster doc) does not create a stub', async () => {
    // Pending members are never approved, so no roster doc exists yet.
    // Revoking them should not create a malformed stub roster doc.
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending' },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), revokeDeps(db));
    expect(store.has('users/alice/team/chidi')).toBe(false);
  });

  it('grants launch-free to the revoked staffer when they have no users/{uid} doc', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), revokeDeps(db));
    expect(store.get('users/chidi')).toMatchObject({
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
    });
  });

  it('grants launch-free to a revoked staffer whose doc exists as lapsed/free', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/chidi': { subscriptionTier: 'pro', subscriptionStatus: 'expired' },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), revokeDeps(db));
    expect(store.get('users/chidi')).toMatchObject({
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
    });
  });

  it('skips the grant when the launch-free flag is off', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    await revokeStaffMemberHandler(
      { staffAuthUid: 'chidi' },
      authedCtx('alice'),
      revokeDeps(db, { isGrantEnabled: async () => false }),
    );
    expect(store.has('users/chidi')).toBe(false);
  });

  it('skips the grant for an active atelier/pro subscriber', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/chidi': { subscriptionTier: 'atelier', subscriptionStatus: 'active' },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), revokeDeps(db));
    expect(store.get('users/chidi')).toEqual({ subscriptionTier: 'atelier', subscriptionStatus: 'active' });
  });

  it('revocation still succeeds when the grant write throws', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    const d = revokeDeps(db, {
      writeGrant: async () => {
        throw new Error('grant_write_failed');
      },
    });
    const res = await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), d);
    expect(res).toEqual({ staffAuthUid: 'chidi', status: 'revoked' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    expect(d._claims.get('chidi')).toBeNull();
  });
});
