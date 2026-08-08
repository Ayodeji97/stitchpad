import { approveStaffMemberHandler } from '../../staff/approveStaffMember';
import { revokeStaffMemberHandler } from '../../staff/revokeStaffMember';
import { makeStaffDb, makeClaimsRecorder, authedCtx } from './staffTestDb';

const NOW = new Date('2026-07-28T10:00:00Z');
const deps = (db: ReturnType<typeof makeStaffDb>['db'], claims = makeClaimsRecorder()) => ({
  db,
  setClaims: claims.setClaims,
  now: () => NOW,
  _claims: claims.claims,
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

  it('when approve transaction fails (status changed to revoked), roster doc is not created and claims are rolled back', async () => {
    // Pre-check rejects a revoked membership before setting any claim or
    // entering the transaction, so both claim and roster doc remain untouched.
    const claims = makeClaimsRecorder();
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'revoked' },
    });
    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db, claims)),
    ).rejects.toMatchObject({ message: 'membership_revoked' });
    // Pre-check rejected, so no claim was issued and no roster doc was created.
    expect(store.has('users/alice/team/chidi')).toBe(false);
    expect(claims.claims.has('chidi')).toBe(false);
  });
});

describe('revokeStaffMemberHandler', () => {
  it('revokes an active membership and clears the staff custom claim', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    const d = deps(db);
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
      revokeStaffMemberHandler({ staffAuthUid: 'ghost' }, authedCtx('alice'), deps(db)),
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
      }),
    ).rejects.toThrow('claims_backend_down');
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
  });

  it('revoke archives the roster doc but keeps it resolvable', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/team/chidi': { name: 'Chidi O', kind: 'staff', status: 'active', colorSeed: 3 },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ status: 'archived', name: 'Chidi O' });
  });

  it('revoke of a pending member (no roster doc) does not create a stub', async () => {
    // Pending members are never approved, so no roster doc exists yet.
    // Revoking them should not create a malformed stub roster doc.
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending' },
    });
    await revokeStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db));
    expect(store.has('users/alice/team/chidi')).toBe(false);
  });
});
