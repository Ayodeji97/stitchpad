import * as admin from 'firebase-admin';
import {
  abandonStalePendingCheckoutsHandler,
  STALE_PENDING_MS,
} from '../../billing/abandonStalePending';

/**
 * @param queryPaths paths the (pending) query returned at snapshot time
 * @param current    CURRENT server state per path (may differ if a webhook raced in)
 */
function setup(queryPaths: string[], current: Record<string, any>) {
  const store = new Map<string, any>(Object.entries(current));
  const query: any = {
    where: jest.fn(() => query),
    get: jest.fn(async () => ({ docs: queryPaths.map((p) => ({ ref: { path: p } })) })),
  };
  const tx = {
    get: jest.fn(async (ref: any) => ({ data: () => store.get(ref.path) })),
    set: jest.fn((ref: any, data: any) => store.set(ref.path, { ...store.get(ref.path), ...data })),
  };
  const runTransaction = jest.fn(async (fn: any) => fn(tx));
  const db = { collectionGroup: jest.fn(() => query), runTransaction };
  return { db, store, query, runTransaction };
}

const NOW = new Date('2026-06-10T00:00:00Z');

describe('abandonStalePendingCheckoutsHandler', () => {
  it('marks still-pending stale checkouts as abandoned', async () => {
    const { db, store, query } = setup(
      ['users/a/billingTransactions/stp_old1', 'users/b/billingTransactions/stp_old2'],
      {
        'users/a/billingTransactions/stp_old1': { status: 'pending' },
        'users/b/billingTransactions/stp_old2': { status: 'pending' },
      },
    );

    const result = await abandonStalePendingCheckoutsHandler({ db: db as never, now: () => NOW });

    expect(db.collectionGroup).toHaveBeenCalledWith('billingTransactions');
    expect(query.where).toHaveBeenCalledWith('status', '==', 'pending');
    expect(query.where).toHaveBeenCalledWith(
      'createdAt', '<=', admin.firestore.Timestamp.fromDate(new Date(NOW.getTime() - STALE_PENDING_MS)),
    );
    expect(store.get('users/a/billingTransactions/stp_old1')).toMatchObject({
      status: 'abandoned', failureReason: 'checkout_abandoned',
    });
    expect(store.get('users/b/billingTransactions/stp_old2').status).toBe('abandoned');
    expect(result).toEqual({ scanned: 2, abandoned: 2 });
  });

  it('does NOT overwrite a doc that was paid since the query (race with the webhook)', async () => {
    // The query returned it as pending, but a late charge.success flipped it to paid
    // before the transaction read it.
    const { db, store } = setup(
      ['users/a/billingTransactions/stp_raced'],
      { 'users/a/billingTransactions/stp_raced': { status: 'paid', appliedAt: 'ts', paidAt: 'ts' } },
    );

    const result = await abandonStalePendingCheckoutsHandler({ db: db as never, now: () => NOW });

    const doc = store.get('users/a/billingTransactions/stp_raced');
    expect(doc.status).toBe('paid'); // untouched
    expect(doc.failureReason).toBeUndefined();
    expect(result).toEqual({ scanned: 1, abandoned: 0 });
  });

  it('does nothing when no stale pending checkouts exist', async () => {
    const { db, runTransaction } = setup([], {});
    const result = await abandonStalePendingCheckoutsHandler({ db: db as never, now: () => NOW });
    expect(result).toEqual({ scanned: 0, abandoned: 0 });
    expect(runTransaction).not.toHaveBeenCalled();
  });

  it('uses a 120-hour (5 working day) staleness window', () => {
    expect(STALE_PENDING_MS).toBe(120 * 60 * 60 * 1000);
  });
});
