import * as functions from 'firebase-functions/v1';
import {
  buildContactDoc,
  buildMoneyDoc,
  buildMoneyDocWithOverlay,
  classifyCustomerMirror,
  classifyOrderMirror,
  isMirrorStamped,
  migrateSensitiveFieldsHandler,
} from '../../staff/migrateSensitiveFields';
/* eslint-disable @typescript-eslint/no-var-requires, @typescript-eslint/no-require-imports */
const backfillScript = require('../../../scripts/backfillSensitiveFields.js');
/* eslint-enable @typescript-eslint/no-var-requires, @typescript-eslint/no-require-imports */

// Fake Firestore covering exactly what the migration uses: a single page of
// users, per-user customers/orders subcollections, existing /private mirrors
// (keyed by full path), and batched merge-sets.
function makeDb(
  users: Record<string, { customers?: Record<string, any>; orders?: Record<string, any> }>,
  mirrors: Record<string, any> = {},
) {
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
    doc: (path: string) => ({
      path,
      get: async () => ({
        exists: Object.prototype.hasOwnProperty.call(mirrors, path),
        data: () => mirrors[path],
      }),
    }),
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

// The `create` path payload. An UNSTAMPED mirror is not necessarily empty: the
// 8d-1 client's recordPayment/updateCosts write payments/costs to it with
// merge=true and no ownerId stamp, so it can hold data NEWER than the base doc.
// Rebuilding wholly from base would discard those and stamp the stale result.
describe('buildMoneyDocWithOverlay', () => {
  const baseOrder = {
    totalPrice: 40000,
    discount: 5000,
    discountReason: 'loyal',
    payments: [{ id: 'p1', amount: 10000 }],
    costs: [{ category: 'FABRIC', amount: 3000 }],
    items: [{ id: 'i1', price: 1000 }],
  };

  it('is identical to buildMoneyDoc when no mirror exists', () => {
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', undefined)).toEqual(
      buildMoneyDoc(baseOrder, 'owner-1', 'o1'),
    );
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', null)).toEqual(
      buildMoneyDoc(baseOrder, 'owner-1', 'o1'),
    );
  });

  it('appends mirror-only payments after the base payments', () => {
    const money = buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {
      payments: [{ id: 'p-new', amount: 7000 }],
    });
    expect(money.payments).toEqual([
      { id: 'p1', amount: 10000 },
      { id: 'p-new', amount: 7000 },
    ]);
    // Everything else is still base-derived and the doc is stamped.
    expect(money.ownerId).toBe('owner-1');
    expect(money.orderId).toBe('o1');
    expect(money.totalPrice).toBe(40000);
    expect(money.itemPrices).toEqual({ i1: 1000 });
  });

  it('still synthesizes the legacy deposit, then appends the mirror payment', () => {
    const money = buildMoneyDocWithOverlay(
      { depositPaid: 5000, createdAt: 1690000000, payments: [], items: [] },
      'owner-1',
      'o1',
      { payments: [{ id: 'p-new', amount: 200 }] },
    );
    expect(money.payments).toEqual([
      { id: 'legacy-deposit', amount: 5000, method: 'OTHER', type: 'DEPOSIT', recordedAt: 1690000000, note: null },
      { id: 'p-new', amount: 200 },
    ]);
  });

  it('does not duplicate a payment id present in both base and mirror (base entry wins)', () => {
    const money = buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {
      payments: [
        { id: 'p1', amount: 999999, note: 'mirror copy' },
        { id: 'p-new', amount: 7000 },
        { id: 'p-new', amount: 7000 },
      ],
    });
    expect(money.payments).toEqual([
      { id: 'p1', amount: 10000 },
      { id: 'p-new', amount: 7000 },
    ]);
  });

  it('tolerates a non-array/absent mirror payments field and skips null/id-less entries', () => {
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {}).payments).toEqual([
      { id: 'p1', amount: 10000 },
    ]);
    expect(
      buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', { payments: 'nonsense' }).payments,
    ).toEqual([{ id: 'p1', amount: 10000 }]);
    expect(
      buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {
        payments: [null, { amount: 1 }, { id: '', amount: 2 }, { id: 'p-new', amount: 3 }],
      }).payments,
    ).toEqual([
      { id: 'p1', amount: 10000 },
      { id: 'p-new', amount: 3 },
    ]);
  });

  it('prefers non-empty mirror costs (updateCosts writes the complete list)', () => {
    expect(
      buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {
        costs: [{ category: 'TRIM', amount: 900 }],
      }).costs,
    ).toEqual([{ category: 'TRIM', amount: 900 }]);
  });

  it('falls back to base costs when the mirror costs are empty, absent, or malformed', () => {
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', { costs: [] }).costs).toEqual([
      { category: 'FABRIC', amount: 3000 },
    ]);
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', {}).costs).toEqual([
      { category: 'FABRIC', amount: 3000 },
    ]);
    expect(buildMoneyDocWithOverlay(baseOrder, 'owner-1', 'o1', { costs: 3 }).costs).toEqual([
      { category: 'FABRIC', amount: 3000 },
    ]);
  });
});

describe('isMirrorStamped', () => {
  it('is false for a missing mirror, and for one with a missing or blank ownerId', () => {
    expect(isMirrorStamped(undefined)).toBe(false);
    expect(isMirrorStamped(null)).toBe(false);
    expect(isMirrorStamped({})).toBe(false);
    expect(isMirrorStamped({ ownerId: '' })).toBe(false);
    expect(isMirrorStamped({ ownerId: 42 as unknown as string })).toBe(false);
  });

  it('is true for a mirror carrying a non-blank ownerId', () => {
    expect(isMirrorStamped({ ownerId: 'owner-1' })).toBe(true);
  });
});

// The decision the backfill makes per doc. This is the C1 safety boundary: a
// stamped mirror is authoritative post-8d-1 and must never be rebuilt from base.
describe('classifyOrderMirror', () => {
  const legacyOrder = { depositPaid: 5000, createdAt: 1690000000, totalPrice: 20000 };

  it('creates when the mirror is missing', () => {
    expect(classifyOrderMirror(legacyOrder, undefined)).toEqual({ action: 'create' });
  });

  it('creates when the mirror exists but is unstamped', () => {
    expect(classifyOrderMirror(legacyOrder, { totalPrice: 1, payments: [] })).toEqual({ action: 'create' });
    expect(classifyOrderMirror(legacyOrder, { ownerId: '', payments: [] })).toEqual({ action: 'create' });
  });

  it('skips a stamped mirror that already has the legacy deposit', () => {
    expect(
      classifyOrderMirror(legacyOrder, {
        ownerId: 'owner-1',
        payments: [{ id: 'legacy-deposit', amount: 5000 }],
      }),
    ).toEqual({ action: 'skip' });
  });

  it('skips a stamped mirror when the base has no legacy depositPaid', () => {
    expect(classifyOrderMirror({ totalPrice: 20000 }, { ownerId: 'owner-1', payments: [] })).toEqual({
      action: 'skip',
    });
  });

  it('heals a stamped mirror by prepending the deposit to the MIRROR payments', () => {
    expect(
      classifyOrderMirror(legacyOrder, {
        ownerId: 'owner-1',
        totalPrice: 33333,
        payments: [{ id: 'p-new', amount: 200 }],
      }),
    ).toEqual({
      action: 'healLegacyDeposit',
      payments: [
        { id: 'legacy-deposit', amount: 5000, method: 'OTHER', type: 'DEPOSIT', recordedAt: 1690000000, note: null },
        { id: 'p-new', amount: 200 },
      ],
    });
  });
});

describe('classifyCustomerMirror', () => {
  it('creates when missing or unstamped, skips when stamped', () => {
    expect(classifyCustomerMirror({ phone: '+234' }, undefined)).toEqual({ action: 'create' });
    expect(classifyCustomerMirror({ phone: '+234' }, { phone: 'stale' })).toEqual({ action: 'create' });
    expect(classifyCustomerMirror({ phone: '+234' }, { ownerId: 'owner-1', phone: 'authoritative' })).toEqual({
      action: 'skip',
    });
  });
});

// The standalone script is the mechanism actually run in production (ADC-based);
// the callable is its in-cluster twin. Same inputs must yield the same decisions.
describe('backfillSensitiveFields.js lockstep', () => {
  const cases: Array<[any, any]> = [
    [{ depositPaid: 5000, createdAt: 7 }, undefined],
    [{ depositPaid: 5000, createdAt: 7 }, { ownerId: '', payments: [] }],
    [{ depositPaid: 5000, createdAt: 7 }, { ownerId: 'o', payments: [{ id: 'x', amount: 1 }] }],
    [{ depositPaid: 5000, createdAt: 7 }, { ownerId: 'o', payments: [{ id: 'legacy-deposit', amount: 5000 }] }],
    [{ totalPrice: 10 }, { ownerId: 'o', payments: [] }],
  ];

  it.each(cases)('classifies order mirrors identically (case %#)', (order, mirror) => {
    expect(backfillScript.classifyOrderMirror(order, mirror)).toEqual(classifyOrderMirror(order, mirror));
  });

  it('classifies customer mirrors identically', () => {
    for (const mirror of [undefined, {}, { ownerId: '' }, { ownerId: 'o' }]) {
      expect(backfillScript.classifyCustomerMirror({ phone: '+234' }, mirror)).toEqual(
        classifyCustomerMirror({ phone: '+234' }, mirror),
      );
    }
  });

  it('builds identical money and contact payloads', () => {
    const order = { totalPrice: 40000, depositPaid: 5000, createdAt: 7, items: [{ id: 'i1', price: 1000 }] };
    expect(backfillScript.buildMoneyDoc(order, 'o', 'o1')).toEqual(buildMoneyDoc(order, 'o', 'o1'));
    const customer = { phone: '+234', email: 'a@b.co' };
    expect(backfillScript.buildContactDoc(customer, 'o', 'c1')).toEqual(buildContactDoc(customer, 'o', 'c1'));
  });

  const overlayCases: Array<[any, any]> = [
    [{ totalPrice: 100, payments: [{ id: 'p1', amount: 1 }], costs: [{ category: 'FABRIC', amount: 3 }] }, undefined],
    [{ totalPrice: 100, payments: [{ id: 'p1', amount: 1 }] }, { payments: [{ id: 'p-new', amount: 2 }] }],
    [{ totalPrice: 100, payments: [{ id: 'p1', amount: 1 }] }, { payments: [{ id: 'p1', amount: 999 }] }],
    [{ depositPaid: 5000, createdAt: 7, payments: [] }, { payments: [{ id: 'p-new', amount: 2 }] }],
    [{ totalPrice: 100, costs: [{ category: 'FABRIC', amount: 3 }] }, { costs: [{ category: 'TRIM', amount: 9 }] }],
    [{ totalPrice: 100, costs: [{ category: 'FABRIC', amount: 3 }] }, { costs: [] }],
    [{ totalPrice: 100 }, { payments: [null, { amount: 1 }, { id: '', amount: 2 }], costs: 'nope' }],
    [{ totalPrice: 100 }, { ownerId: '', payments: 'nonsense' }],
  ];

  it.each(overlayCases)('overlays unstamped mirrors identically (case %#)', (order, mirror) => {
    expect(backfillScript.buildMoneyDocWithOverlay(order, 'o', 'o1', mirror)).toEqual(
      buildMoneyDocWithOverlay(order, 'o', 'o1', mirror),
    );
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
    expect(res).toEqual({
      dryRun: true,
      users: 1,
      customersMirrorCreated: 1,
      customersAlreadyMirrored: 0,
      ordersMirrorCreated: 1,
      ordersHealedLegacyDeposit: 0,
      ordersAlreadyMirrored: 0,
    });
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

  // Mirror-first safety: post-8d-1 the mirror is the ONLY authoritative store for
  // money/contact, so a stamped complete mirror must never be rebuilt from the base.
  it('leaves stamped, complete mirrors untouched', async () => {
    const { db, writes } = makeDb(sample, {
      'users/u1/customers/c1/private/contact': {
        ownerId: 'u1',
        customerId: 'c1',
        phone: '+234-new',
        email: 'new@x.co',
        address: '1 New St',
      },
      'users/u1/orders/o1/private/money': {
        ownerId: 'u1',
        orderId: 'o1',
        totalPrice: 999,
        payments: [{ id: 'p-new', amount: 999 }],
      },
    });
    const res = await migrateSensitiveFieldsHandler({ dryRun: false }, ctx(true), { db });
    expect(writes).toHaveLength(0);
    expect(res).toEqual({
      dryRun: false,
      users: 1,
      customersMirrorCreated: 0,
      customersAlreadyMirrored: 1,
      ordersMirrorCreated: 0,
      ordersHealedLegacyDeposit: 0,
      ordersAlreadyMirrored: 1,
    });
  });

  it('heals a stamped legacy-deposit-incomplete order mirror with a payments-only write', async () => {
    const { db, writes } = makeDb(
      {
        u1: {
          customers: {},
          orders: { o1: { totalPrice: 100, depositPaid: 5000, createdAt: 1690000000, items: [] } },
        },
      },
      {
        'users/u1/orders/o1/private/money': {
          ownerId: 'u1',
          orderId: 'o1',
          totalPrice: 12345,
          discount: 500,
          discountReason: 'authoritative',
          payments: [{ id: 'p-new', amount: 200 }],
          costs: [{ category: 'FABRIC', amount: 300 }],
          itemPrices: { i1: 12345 },
        },
      },
    );
    const res = await migrateSensitiveFieldsHandler({ dryRun: false }, ctx(true), { db });
    expect(res.ordersHealedLegacyDeposit).toBe(1);
    expect(res.ordersMirrorCreated).toBe(0);
    expect(res.ordersAlreadyMirrored).toBe(0);
    expect(writes).toHaveLength(1);
    const write = writes[0];
    expect(write.path).toBe('users/u1/orders/o1/private/money');
    expect(write.merge).toBe(true);
    // ONLY payments — every other authoritative mirror field is left alone.
    expect(Object.keys(write.data)).toEqual(['payments']);
    // Prepended onto the MIRROR's payments, not the base doc's.
    expect(write.data.payments).toEqual([
      { id: 'legacy-deposit', amount: 5000, method: 'OTHER', type: 'DEPOSIT', recordedAt: 1690000000, note: null },
      { id: 'p-new', amount: 200 },
    ]);
  });

  it('rebuilds a mirror that exists but is unstamped (blank ownerId)', async () => {
    const { db, writes } = makeDb(sample, {
      'users/u1/customers/c1/private/contact': { ownerId: '', phone: 'stale' },
      'users/u1/orders/o1/private/money': { ownerId: '', totalPrice: 1 },
    });
    const res = await migrateSensitiveFieldsHandler({ dryRun: false }, ctx(true), { db });
    expect(res.customersMirrorCreated).toBe(1);
    expect(res.ordersMirrorCreated).toBe(1);
    expect(writes).toHaveLength(2);
    const money = writes.find((w) => w.path === 'users/u1/orders/o1/private/money');
    expect(money?.data).toEqual(buildMoneyDoc(sample.u1.orders.o1, 'u1', 'o1'));
  });

  // An unstamped mirror can be partially authoritative: 8d-1's recordPayment /
  // updateCosts create it (merge=true) WITHOUT the ownerId stamp, so its
  // payments/costs can be newer than the base doc's. The create path must overlay
  // them, not discard them.
  it('overlays an unstamped mirror\'s newer payments and costs onto the base build', async () => {
    const { db, writes } = makeDb(
      {
        u1: {
          customers: {},
          orders: {
            o1: {
              totalPrice: 100,
              payments: [{ id: 'p-old', amount: 10 }],
              costs: [{ category: 'FABRIC', amount: 3 }],
              items: [{ id: 'i1', price: 100 }],
            },
          },
        },
      },
      {
        'users/u1/orders/o1/private/money': {
          payments: [{ id: 'p-old', amount: 10 }, { id: 'p-new', amount: 55 }],
          costs: [{ category: 'TRIM', amount: 9 }],
        },
      },
    );
    const res = await migrateSensitiveFieldsHandler({ dryRun: false }, ctx(true), { db });
    expect(res.ordersMirrorCreated).toBe(1);
    expect(writes).toHaveLength(1);
    expect(writes[0].data).toEqual({
      ownerId: 'u1',
      orderId: 'o1',
      totalPrice: 100,
      discount: 0,
      discountReason: null,
      payments: [{ id: 'p-old', amount: 10 }, { id: 'p-new', amount: 55 }],
      costs: [{ category: 'TRIM', amount: 9 }],
      itemPrices: { i1: 100 },
    });
  });
});
