import { cancelStaffMembershipHandler } from '../../staff/cancelStaffMembership';
import { makeStaffDb, makeClaimsRecorder, authedCtx } from './staffTestDb';

const NOW = new Date('2026-07-29T10:00:00Z');
const deps = (db: ReturnType<typeof makeStaffDb>['db'], claims = makeClaimsRecorder()) => ({
  db,
  setClaims: claims.setClaims,
  now: () => NOW,
  _claims: claims.claims,
});

describe('cancelStaffMembershipHandler', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx(), deps(db)),
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('rejects a missing workshop uid', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({}, authedCtx('chidi'), deps(db)),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
  });

  it('404s an unknown membership', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), deps(db)),
    ).rejects.toMatchObject({ code: 'not-found' });
  });

  it('revokes the caller own pending membership and clears any claim', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'pending' } });
    const d = deps(db);
    const res = await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), d);

    expect(res).toEqual({ workshopUid: 'alice', status: 'revoked' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    expect(d._claims.get('chidi')).toBeNull();
  });

  it('deletes the owner staff-pending notification on cancel', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending' },
      'users/alice/notifications/staff_pending__chidi': { type: 'STAFF_PENDING', isRead: false },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), deps(db));

    expect(store.has('users/alice/notifications/staff_pending__chidi')).toBe(false);
  });

  it('uses the caller uid as the membership doc id (cannot cancel another member)', async () => {
    // Only chidi's own doc under alice exists; calling as chidi targets that doc.
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/memberships/bola': { status: 'active' },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), deps(db));

    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    // bola untouched.
    expect(store.get('users/alice/memberships/bola')).toMatchObject({ status: 'active' });
  });
});
