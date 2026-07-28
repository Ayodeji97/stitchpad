import { generateStaffInviteHandler } from '../../staff/generateStaffInvite';
import { makeStaffDb, authedCtx } from './staffTestDb';

const deps = (db: ReturnType<typeof makeStaffDb>['db'], code = 'ABCD2345') => ({
  db,
  now: () => new Date('2026-07-28T10:00:00Z'),
  randomCode: () => code,
});

describe('generateStaffInviteHandler', () => {
  it('rejects an unauthenticated caller', async () => {
    const { db } = makeStaffDb();
    await expect(generateStaffInviteHandler({}, authedCtx(), deps(db))).rejects.toMatchObject({
      code: 'unauthenticated',
    });
  });

  it('mints an OPEN invite scoped to the owner with an expiry', async () => {
    const { db, store } = makeStaffDb({ 'users/alice': { businessName: 'Ada Atelier' } });
    const res = await generateStaffInviteHandler({}, authedCtx('alice'), deps(db, 'CODE2345'));

    expect(res.code).toBe('CODE2345');
    const invite = store.get('staffInvites/CODE2345');
    expect(invite).toMatchObject({
      code: 'CODE2345',
      workshopUid: 'alice',
      status: 'open',
      workshopName: 'Ada Atelier',
    });
    // expiresAt is now + TTL and returned to the caller.
    expect(res.expiresAt).toBe(invite?.expiresAt);
    expect(res.expiresAt).toBeGreaterThan(new Date('2026-07-28T10:00:00Z').getTime());
  });

  it('blocks generating past the seat cap (2 pending/active members)', async () => {
    const { db } = makeStaffDb({
      'users/alice/memberships/s1': { status: 'active' },
      'users/alice/memberships/s2': { status: 'pending' },
    });
    await expect(
      generateStaffInviteHandler({}, authedCtx('alice'), deps(db)),
    ).rejects.toMatchObject({ code: 'failed-precondition', message: 'seat_cap_reached' });
  });

  it('does not count a revoked membership against the cap', async () => {
    const { db, store } = makeStaffDb({
      'users/alice/memberships/s1': { status: 'active' },
      'users/alice/memberships/s2': { status: 'revoked' },
    });
    await generateStaffInviteHandler({}, authedCtx('alice'), deps(db, 'OKAY2345'));
    expect(store.has('staffInvites/OKAY2345')).toBe(true);
  });
});
