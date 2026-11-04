import * as functions from 'firebase-functions/v1';
import { getWorkshopMarketHandler } from '../../staff/getWorkshopMarket';
import { makeStaffDb, authedCtx } from './staffTestDb';

const staff = (workshopUid: unknown = 'owner') => ({
  ...authedCtx('staff'), auth: { uid: 'staff', token: { role: 'staff', workshopUid } },
}) as unknown as functions.https.CallableContext;

describe('getWorkshopMarketHandler', () => {
  it('requires auth and forbids arbitrary or mismatched workshop selection', async () => {
    const { db } = makeStaffDb();
    await expect(getWorkshopMarketHandler({ workshopUid: 'owner' }, authedCtx(), db)).rejects.toMatchObject({ code: 'unauthenticated' });
    await expect(getWorkshopMarketHandler({ workshopUid: 'other' }, staff(), db)).rejects.toMatchObject({ code: 'permission-denied' });
    await expect(getWorkshopMarketHandler({ workshopUid: 'owner' }, authedCtx('outsider'), db)).rejects.toMatchObject({ code: 'permission-denied' });
    await expect(getWorkshopMarketHandler({ workshopUid: 'owner' }, staff(null), db)).rejects.toMatchObject({ code: 'permission-denied' });
  });

  it.each(['pending', 'revoked', 'missing'])('rejects stale staff claims with %s membership', async (status) => {
    const { db } = makeStaffDb({
      'users/owner': { country: 'GH' },
      ...(status === 'missing' ? {} : { 'users/owner/memberships/staff': { status } }),
    });
    await expect(getWorkshopMarketHandler({ workshopUid: 'owner' }, staff(), db)).rejects.toMatchObject({ code: 'permission-denied' });
  });

  it('returns only market fields and derives currency from country', async () => {
    const { db } = makeStaffDb({
      'users/owner': { country: 'gh', currency: 'NGN', timeZone: 'Africa/Lagos', email: 'private', bankAccountNumber: 'secret', subscriptionTier: 'atelier' },
      'users/owner/memberships/staff': { status: 'active' },
    });
    expect(await getWorkshopMarketHandler({ workshopUid: 'owner' }, staff(), db)).toEqual({
      workshopUid: 'owner', country: 'GH', currency: 'GHS', timeZone: 'Africa/Accra',
    });
  });

  it('preserves legacy Nigerian defaults and a valid eastern-Congo time zone', async () => {
    const { db, store } = makeStaffDb({ 'users/owner': { businessName: 'Legacy' } });
    expect(await getWorkshopMarketHandler({ workshopUid: 'owner' }, authedCtx('owner'), db)).toEqual({
      workshopUid: 'owner', country: 'NG', currency: 'NGN', timeZone: 'Africa/Lagos',
    });
    store.set('users/owner', { country: 'CD', timeZone: 'Africa/Lubumbashi' });
    expect(await getWorkshopMarketHandler({ workshopUid: 'owner' }, authedCtx('owner'), db)).toEqual({
      workshopUid: 'owner', country: 'CD', currency: 'CDF', timeZone: 'Africa/Lubumbashi',
    });
  });

  it.each([
    ['NG', 'NGN'], ['GH', 'GHS'], ['KE', 'KES'], ['CI', 'XOF'], ['SN', 'XOF'],
    ['BJ', 'XOF'], ['TG', 'XOF'], ['ML', 'XOF'], ['BF', 'XOF'], ['NE', 'XOF'],
    ['CM', 'XAF'], ['GA', 'XAF'], ['CG', 'XAF'], ['GN', 'GNF'], ['CD', 'CDF'],
  ])('derives %s currency as %s without trusting profile currency', async (country, currency) => {
    const { db } = makeStaffDb({ 'users/owner': { country, currency: 'wrong' } });
    expect(await getWorkshopMarketHandler({ workshopUid: 'owner' }, authedCtx('owner'), db))
      .toMatchObject({ country, currency });
  });

  it('does not label a missing staff workshop Nigerian', async () => {
    const { db } = makeStaffDb({ 'users/owner/memberships/staff': { status: 'active' } });
    await expect(getWorkshopMarketHandler({ workshopUid: 'owner' }, staff(), db)).rejects.toMatchObject({ code: 'not-found' });
  });
});
