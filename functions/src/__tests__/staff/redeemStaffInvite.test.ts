import { redeemStaffInviteHandler } from '../../staff/redeemStaffInvite';
import { makeStaffDb, authedCtx } from './staffTestDb';

const NOW = new Date('2026-07-28T10:00:00Z');
const FUTURE = NOW.getTime() + 60_000;
const deps = (db: ReturnType<typeof makeStaffDb>['db']) => ({ db, now: () => NOW });

const openInvite = (over: Record<string, unknown> = {}) => ({
  'staffInvites/CODE': {
    code: 'CODE',
    workshopUid: 'alice',
    workshopName: 'Ada Atelier',
    status: 'open',
    expiresAt: FUTURE,
    ...over,
  },
});

describe('redeemStaffInviteHandler', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = makeStaffDb(openInvite());
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx(), deps(db))).rejects.toMatchObject({
      code: 'unauthenticated',
    });
  });

  it('rejects an empty/invalid code', async () => {
    const { db } = makeStaffDb();
    await expect(redeemStaffInviteHandler({ code: '  ' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      code: 'invalid-argument',
    });
  });

  it('404s an unknown invite', async () => {
    const { db } = makeStaffDb();
    await expect(redeemStaffInviteHandler({ code: 'NOPE' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      code: 'not-found',
      message: 'invite_not_found',
    });
  });

  it('rejects an already-redeemed invite', async () => {
    const { db } = makeStaffDb(openInvite({ status: 'redeemed' }));
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      message: 'invite_not_open',
    });
  });

  it('rejects an expired invite', async () => {
    const { db } = makeStaffDb(openInvite({ expiresAt: NOW.getTime() - 1 }));
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      message: 'invite_expired',
    });
  });

  it('rejects the owner redeeming their own invite', async () => {
    const { db } = makeStaffDb(openInvite());
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx('alice'), deps(db))).rejects.toMatchObject({
      message: 'cannot_join_own_workshop',
    });
  });

  it('creates a pending membership, marks the invite redeemed, and notifies the owner', async () => {
    const { db, store } = makeStaffDb(openInvite());
    const res = await redeemStaffInviteHandler(
      { code: 'CODE' },
      authedCtx('chidi', { email: 'chidi@ex.co', name: 'Chidi' }),
      deps(db),
    );

    expect(res).toEqual({ workshopUid: 'alice', workshopName: 'Ada Atelier', status: 'pending' });
    expect(store.get('users/alice/memberships/chidi')).toMatchObject({
      staffAuthUid: 'chidi',
      staffEmail: 'chidi@ex.co',
      staffName: 'Chidi',
      role: 'staff',
      status: 'pending',
      workshopUid: 'alice',
    });
    expect(store.get('staffInvites/CODE')).toMatchObject({ status: 'redeemed', redeemedByAuthUid: 'chidi' });
    expect(store.get('users/alice/notifications/staff_pending__chidi')).toMatchObject({
      type: 'STAFF_PENDING',
      staffAuthUid: 'chidi',
      isRead: false,
    });
  });

  it('rejects a caller who is already an active member', async () => {
    const { db } = makeStaffDb({
      ...openInvite(),
      'users/alice/memberships/chidi': { status: 'active' },
    });
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      code: 'already-exists',
    });
  });

  it('rejects when the workshop is already at its seat cap', async () => {
    const { db } = makeStaffDb({
      ...openInvite(),
      'users/alice/memberships/s1': { status: 'active' },
      'users/alice/memberships/s2': { status: 'pending' },
    });
    await expect(redeemStaffInviteHandler({ code: 'CODE' }, authedCtx('chidi'), deps(db))).rejects.toMatchObject({
      message: 'seat_cap_reached',
    });
  });
});
