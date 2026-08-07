import * as functions from 'firebase-functions/v1';
import {
  buildContactDoc,
  buildMoneyDoc,
  migrateSensitiveFieldsHandler,
} from '../../staff/migrateSensitiveFields';

// Fake Firestore covering exactly what the migration uses: a single page of
// users, per-user customers/orders subcollections, and batched merge-sets.
function makeDb(users: Record<string, { customers?: Record<string, any>; orders?: Record<string, any> }>) {
  const writes: Array<{ path: string; data: any; merge: boolean }> = [];
  const collection = (path: string) => {
    const col: any = {
      orderBy: () => col,
      limit: () => col,
      startAfter: () => col,
      get: async () => {
        if (path === 'users') {
          const docs = Object.keys(users).map((id) => ({ id, data: () => ({}) }));
          return { empty: docs.length === 0, size: docs.length, docs };
        }
        const m = path.match(/^users\/([^/]+)\/(customers|orders)$/);
        if (m) {
          const entries = (users[m[1]] as any)?.[m[2]] ?? {};
          const docs = Object.entries(entries).map(([id, d]) => ({ id, data: () => d }));
          return { empty: docs.length === 0, size: docs.length, docs };
        }
        return { empty: true, size: 0, docs: [] };
      },
    };
    return col;
  };
  const db: any = {
    collection,
    doc: (path: string) => ({ path }),
    batch: () => {
      const ops: Array<{ path: string; data: any; merge: boolean }> = [];
      return {
        set: (ref: any, data: any, opts?: { merge?: boolean }) =>
          ops.push({ path: ref.path, data, merge: opts?.merge === true }),
        commit: async () => {
          writes.push(...ops);
        },
      };
    },
  };
  return { db, writes };
}

const ctx = (isAdmin?: boolean): functions.https.CallableContext =>
  ({ auth: { uid: 'a', token: isAdmin ? { admin: true } : {} } } as unknown as functions.https.CallableContext);

describe('buildContactDoc', () => {
  it('maps phone/email/address and stamps ownerId + customerId', () => {
    expect(
      buildContactDoc({ phone: '+2348011112222', email: 'a@b.co', address: '12 Marina' }, 'owner-1', 'c1'),
    ).toEqual({
      ownerId: 'owner-1',
      customerId: 'c1',
      phone: '+2348011112222',
      email: 'a@b.co',
      address: '12 Marina',
    });
  });

  it('defaults missing phone to empty and email/address to null', () => {
    expect(buildContactDoc({}, 'owner-1', 'c1')).toEqual({
      ownerId: 'owner-1',
      customerId: 'c1',
      phone: '',
      email: null,
      address: null,
    });
  });
});

describe('buildMoneyDoc', () => {
  it('maps money fields, relocates item prices, and stamps ownerId + orderId', () => {
    const money = buildMoneyDoc(
      {
        totalPrice: 40000,
        discount: 5000,
        discountReason: 'loyal',
        payments: [{ id: 'p1', amount: 10000 }],
        costs: [{ category: 'FABRIC', amount: 3000 }],
        items: [{ id: 'i1', price: 1000 }, { id: 'i2', price: 2500 }],
      },
      'owner-1',
      'o1',
    );
    expect(money).toEqual({
      ownerId: 'owner-1',
      orderId: 'o1',
      totalPrice: 40000,
      discount: 5000,
      discountReason: 'loyal',
      payments: [{ id: 'p1', amount: 10000 }],
      costs: [{ category: 'FABRIC', amount: 3000 }],
      itemPrices: { i1: 1000, i2: 2500 },
    });
  });

  it('defaults missing money to zero/empty', () => {
    expect(buildMoneyDoc({}, 'owner-1', 'o1')).toEqual({
      ownerId: 'owner-1',
      orderId: 'o1',
      totalPrice: 0,
      discount: 0,
      discountReason: null,
      payments: [],
      costs: [],
      itemPrices: {},
    });
  });

  it('synthesizes legacy-deposit payment for deposit-only legacy order', () => {
    const money = buildMoneyDoc(
      {
        depositPaid: 5000,
        payments: [],
        createdAt: 1690000000,
        items: [],
      },
      'owner-1',
      'o1',
    );
    expect(money.payments).toEqual([
      {
        id: 'legacy-deposit',
        amount: 5000,
        method: 'OTHER',
        type: 'DEPOSIT',
        recordedAt: 1690000000,
        note: null,
      },
    ]);
  });

  it('does not duplicate legacy-deposit payment if already present', () => {
    const money = buildMoneyDoc(
      {
        depositPaid: 5000,
        payments: [{ id: 'legacy-deposit', amount: 5000, method: 'OTHER', type: 'DEPOSIT' }],
        createdAt: 1690000000,
        items: [],
      },
      'owner-1',
      'o1',
    );
    expect((money.payments as any[])).toHaveLength(1);
    expect(((money.payments as any[])[0])).toEqual({ id: 'legacy-deposit', amount: 5000, method: 'OTHER', type: 'DEPOSIT' });
  });

  it('leaves payments unchanged when depositPaid is zero or absent', () => {
    const money1 = buildMoneyDoc(
      {
        depositPaid: 0,
        payments: [{ id: 'p1', amount: 1000 }],
        items: [],
      },
      'owner-1',
      'o1',
    );
    expect(money1.payments).toEqual([{ id: 'p1', amount: 1000 }]);

    const money2 = buildMoneyDoc(
      {
        payments: [{ id: 'p1', amount: 1000 }],
        items: [],
      },
      'owner-1',
      'o1',
    );
    expect(money2.payments).toEqual([{ id: 'p1', amount: 1000 }]);
  });
});

describe('migrateSensitiveFieldsHandler', () => {
  const sample = {
    u1: {
      customers: { c1: { phone: '+234', email: null, address: null } },
      orders: { o1: { totalPrice: 100, items: [{ id: 'i1', price: 100 }] } },
    },
  };

  it('rejects a non-admin caller', async () => {
    const { db } = makeDb(sample);
    await expect(migrateSensitiveFieldsHandler({}, ctx(false), { db })).rejects.toMatchObject({
      code: 'permission-denied',
      message: 'admin_only',
    });
  });

  it('dry-run (default) counts but writes nothing', async () => {
    const { db, writes } = makeDb(sample);
    const res = await migrateSensitiveFieldsHandler({}, ctx(true), { db });
    expect(res).toEqual({ dryRun: true, users: 1, customersWritten: 1, ordersWritten: 1 });
    expect(writes).toHaveLength(0);
  });

  it('real run writes the contact and money sub-docs with merge', async () => {
    const { db, writes } = makeDb(sample);
    const res = await migrateSensitiveFieldsHandler({ dryRun: false }, ctx(true), { db });
    expect(res.dryRun).toBe(false);
    const paths = writes.map((w) => w.path);
    expect(paths).toContain('users/u1/customers/c1/private/contact');
    expect(paths).toContain('users/u1/orders/o1/private/money');
    expect(writes.every((w) => w.merge)).toBe(true);
  });
});
