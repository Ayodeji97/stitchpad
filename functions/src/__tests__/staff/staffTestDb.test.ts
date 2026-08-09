import { makeStaffDb } from './staffTestDb';

describe('staffTestDb fake runTransaction buffer-discard semantics', () => {
  it('buffers tx writes and discards them if callback throws', async () => {
    const { db, store } = makeStaffDb();

    // Transaction callback that queues a write and then throws.
    await expect(
      db.runTransaction(async (tx) => {
        tx.set(db.doc('users/alice/team/probe'), { name: 'x' });
        throw new Error('transaction_failed');
      }),
    ).rejects.toThrow('transaction_failed');

    // Write was queued but never applied because callback threw.
    expect(store.has('users/alice/team/probe')).toBe(false);
  });

  it('buffers tx writes and applies them if callback succeeds', async () => {
    const { db, store } = makeStaffDb();

    // Same transaction but without throwing.
    await db.runTransaction(async (tx) => {
      tx.set(db.doc('users/alice/team/probe'), { name: 'x' });
    });

    // Write was queued and applied because callback succeeded.
    expect(store.get('users/alice/team/probe')).toMatchObject({ name: 'x' });
  });
});
