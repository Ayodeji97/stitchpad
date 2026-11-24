import { lagosMonthKey, reservePage } from '../../notebook/pageCap';
import { fakeDb } from './fakeDb';

const NOW = new Date('2026-11-24T10:00:00Z');
const PAID = {
  subscriptionSource: 'paystack',
  subscriptionStatus: 'active',
  subscriptionEndsAt: new Date('2027-01-01T00:00:00Z').getTime(),
};

describe('reservePage', () => {
  it('cap_freeLifetime300: allows 300 pages then refuses the 301st', async () => {
    const { db, store } = fakeDb({
      'users/u1': {},
      'users/u1/usage/notebookImport': { lifetimePages: 299, monthKey: '2026-11', monthPages: 299 },
    });
    expect(await reservePage(db, 'u1', NOW)).toEqual({ ok: true });
    expect(store.get('users/u1/usage/notebookImport').lifetimePages).toBe(300);
    expect(await reservePage(db, 'u1', NOW)).toEqual({ ok: false, reason: 'page_cap_reached' });
    expect(store.get('users/u1/usage/notebookImport').lifetimePages).toBe(300);
  });

  it('free cap does not reset with a new month', async () => {
    const { db } = fakeDb({
      'users/u1': {},
      'users/u1/usage/notebookImport': { lifetimePages: 300, monthKey: '2026-10', monthPages: 3 },
    });
    expect(await reservePage(db, 'u1', NOW)).toEqual({ ok: false, reason: 'page_cap_reached' });
  });

  it('cap_paidMonthly1000_resetsNextMonth', async () => {
    const { db, store } = fakeDb({
      'users/u1': PAID,
      'users/u1/usage/notebookImport': { lifetimePages: 1500, monthKey: '2026-11', monthPages: 1000 },
    });
    expect(await reservePage(db, 'u1', NOW)).toEqual({ ok: false, reason: 'page_cap_reached' });
    const nextMonth = new Date('2026-12-01T00:00:01Z');
    expect(await reservePage(db, 'u1', nextMonth)).toEqual({ ok: true });
    expect(store.get('users/u1/usage/notebookImport')).toEqual({
      lifetimePages: 1501, monthKey: '2026-12', monthPages: 1,
    });
  });

  it('a launch grant is not paid and stays on the lifetime cap', async () => {
    const { db } = fakeDb({
      'users/u1': { subscriptionSource: 'launch_free', subscriptionStatus: 'active' },
      'users/u1/usage/notebookImport': { lifetimePages: 300, monthKey: '2026-11', monthPages: 0 },
    });
    expect(await reservePage(db, 'u1', NOW)).toEqual({ ok: false, reason: 'page_cap_reached' });
  });
});

describe('lagosMonthKey', () => {
  it('uses the Africa/Lagos calendar month, not UTC', () => {
    expect(lagosMonthKey(new Date('2026-11-30T22:30:00Z'))).toBe('2026-11');
    expect(lagosMonthKey(new Date('2026-11-30T23:30:00Z'))).toBe('2026-12');
  });
});
