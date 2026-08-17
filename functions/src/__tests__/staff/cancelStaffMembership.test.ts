import { cancelStaffMembershipHandler } from '../../staff/cancelStaffMembership';
import { LAUNCH_GRANT_SOURCE } from '../../freemium/launchGrant';
import {
  makeStaffDb,
  makeClaimsRecorder,
  makeLaunchGrantDeps,
  LaunchGrantHooks,
  authedCtx,
} from './staffTestDb';

const NOW = new Date('2026-07-29T10:00:00Z');
const deps = (db: ReturnType<typeof makeStaffDb>['db'], claims = makeClaimsRecorder()) => ({
  db,
  setClaims: claims.setClaims,
  now: () => NOW,
  _claims: claims.claims,
});

// Cancel deps: the shared launch-grant hooks on top of the shared `deps()` shape.
const cancelDeps = (
  db: ReturnType<typeof makeStaffDb>['db'],
  overrides: Partial<LaunchGrantHooks> = {},
  claims = makeClaimsRecorder(),
) => ({
  ...deps(db, claims),
  ...makeLaunchGrantDeps(db, overrides),
});

describe('cancelStaffMembershipHandler', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx(), cancelDeps(db)),
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('rejects a missing workshop uid', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({}, authedCtx('chidi'), cancelDeps(db)),
    ).rejects.toMatchObject({ code: 'invalid-argument' });
  });

  it('404s an unknown membership', async () => {
    const { db } = makeStaffDb();
    await expect(
      cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db)),
    ).rejects.toMatchObject({ code: 'not-found' });
  });

  it('revokes the caller own pending membership and clears any claim', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'pending' } });
    const d = cancelDeps(db);
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
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));

    expect(store.has('users/alice/notifications/staff_pending__chidi')).toBe(false);
  });

  it('uses the caller uid as the membership doc id (cannot cancel another member)', async () => {
    // Only chidi's own doc under alice exists; calling as chidi targets that doc.
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/memberships/bola': { status: 'active' },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));

    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    // bola untouched.
    expect(store.get('users/alice/memberships/bola')).toMatchObject({ status: 'active' });
  });

  it('cancel archives the caller own roster doc', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/alice/team/chidi': { name: 'Chidi O', kind: 'staff', status: 'active', colorSeed: 5 },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));
    expect(store.get('users/alice/team/chidi')).toMatchObject({ status: 'archived', name: 'Chidi O' });
  });

  it('cancel of a pending member (no roster doc) does not create a stub', async () => {
    // Pending members are never approved, so no roster doc exists yet.
    // Cancelling them should not create a malformed stub roster doc.
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'pending' },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));
    expect(store.has('users/alice/team/chidi')).toBe(false);
  });

  it('grants launch-free to the departing staffer when they have no users/{uid} doc', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));
    expect(store.get('users/chidi')).toMatchObject({
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
    });
  });

  it('does NOT grant launch-free when the cancelled membership was only pending', async () => {
    // Abuse path: this callable takes any workshopUid and keys the doc off the
    // caller's own uid, so redeem-then-cancel would otherwise be a self-serve
    // Atelier upgrade, repeatable at will. A pending member never had access to
    // lose, so there is nothing to compensate.
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'pending' } });
    const res = await cancelStaffMembershipHandler(
      { workshopUid: 'alice' },
      authedCtx('chidi'),
      cancelDeps(db),
    );

    expect(res).toEqual({ workshopUid: 'alice', status: 'revoked' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    expect(store.has('users/chidi')).toBe(false);
  });

  it('grants launch-free to a departing staffer whose doc exists as lapsed/free', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/chidi': { subscriptionTier: 'pro', subscriptionStatus: 'expired' },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));
    expect(store.get('users/chidi')).toMatchObject({
      subscriptionTier: 'atelier',
      subscriptionStatus: 'active',
      grantSource: LAUNCH_GRANT_SOURCE,
    });
  });

  it('skips the grant when the launch-free flag is off', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    await cancelStaffMembershipHandler(
      { workshopUid: 'alice' },
      authedCtx('chidi'),
      cancelDeps(db, { isGrantEnabled: async () => false }),
    );
    expect(store.has('users/chidi')).toBe(false);
  });

  it('skips the grant for an active atelier/pro subscriber', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/chidi': { status: 'active' },
      'users/chidi': { subscriptionTier: 'atelier', subscriptionStatus: 'active' },
    });
    await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), cancelDeps(db));
    expect(store.get('users/chidi')).toEqual({ subscriptionTier: 'atelier', subscriptionStatus: 'active' });
  });

  it('cancel still succeeds when the grant write throws', async () => {
    const { db, store } = makeStaffDb({ 'users/alice/memberships/chidi': { status: 'active' } });
    const d = cancelDeps(db, {
      writeGrant: async () => {
        throw new Error('grant_write_failed');
      },
    });
    const res = await cancelStaffMembershipHandler({ workshopUid: 'alice' }, authedCtx('chidi'), d);
    expect(res).toEqual({ workshopUid: 'alice', status: 'revoked' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({ status: 'revoked' });
    expect(d._claims.get('chidi')).toBeNull();
  });
});
