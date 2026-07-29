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

  it('refuses to re-approve a revoked membership (must re-invite)', async () => {
    const { db } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'revoked' } });
    await expect(
      approveStaffMemberHandler({ staffAuthUid: 'chidi' }, authedCtx('alice'), deps(db)),
    ).rejects.toMatchObject({ message: 'membership_revoked' });
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
});
