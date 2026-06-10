import * as admin from 'firebase-admin';
import {
  abandonStalePendingCheckoutsHandler,
  STALE_PENDING_MS,
} from '../../billing/abandonStalePending';

describe('abandonStalePendingCheckoutsHandler', () => {
  it('marks pending checkouts older than the cutoff as abandoned (mark, not delete)', async () => {
    const writes: { path: string; status: string; reason: string }[] = [];
    const docs = [
      { ref: { path: 'users/a/billingTransactions/stp_old1' } },
      { ref: { path: 'users/b/billingTransactions/stp_old2' } },
    ];
    const query: any = {
      where: jest.fn(() => query),
      get: jest.fn(async () => ({ docs })),
    };
    const batch = {
      set: jest.fn((ref: any, data: any) => writes.push({ path: ref.path, status: data.status, reason: data.failureReason })),
      commit: jest.fn(),
    };
    const db = {
      collectionGroup: jest.fn(() => query),
      batch: jest.fn(() => batch),
    };

    const now = new Date('2026-06-10T00:00:00Z');
    const result = await abandonStalePendingCheckoutsHandler({ db: db as never, now: () => now });

    expect(db.collectionGroup).toHaveBeenCalledWith('billingTransactions');
    expect(query.where).toHaveBeenCalledWith('status', '==', 'pending');
    expect(query.where).toHaveBeenCalledWith(
      'createdAt',
      '<=',
      admin.firestore.Timestamp.fromDate(new Date(now.getTime() - STALE_PENDING_MS)),
    );
    expect(writes).toEqual([
      { path: 'users/a/billingTransactions/stp_old1', status: 'abandoned', reason: 'checkout_abandoned' },
      { path: 'users/b/billingTransactions/stp_old2', status: 'abandoned', reason: 'checkout_abandoned' },
    ]);
    // No deletes — a late charge.success must still be able to apply on the doc.
    expect(result).toEqual({ scanned: 2, abandoned: 2 });
  });

  it('does nothing when no stale pending checkouts exist', async () => {
    const query: any = { where: jest.fn(() => query), get: jest.fn(async () => ({ docs: [] })) };
    const batchFactory = jest.fn();
    const db = { collectionGroup: jest.fn(() => query), batch: batchFactory };

    const result = await abandonStalePendingCheckoutsHandler({
      db: db as never,
      now: () => new Date('2026-06-10T00:00:00Z'),
    });

    expect(result).toEqual({ scanned: 0, abandoned: 0 });
    expect(batchFactory).not.toHaveBeenCalled();
  });

  it('uses a 120-hour (5 working day) staleness window', () => {
    expect(STALE_PENDING_MS).toBe(120 * 60 * 60 * 1000);
  });
});
