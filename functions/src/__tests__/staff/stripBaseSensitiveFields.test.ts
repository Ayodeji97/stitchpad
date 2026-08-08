/* eslint-disable @typescript-eslint/no-var-requires, @typescript-eslint/no-require-imports */
const {
  buildOrderStrip,
  buildCustomerStrip,
  isLegacyDepositIncomplete,
} = require('../../../scripts/stripBaseSensitiveFields.js');
/* eslint-enable @typescript-eslint/no-var-requires, @typescript-eslint/no-require-imports */

const DELETE = Symbol('delete');
const fieldValue = { delete: () => DELETE };

describe('buildOrderStrip', () => {
  it('deletes every money field present and rewrites items[] without price', () => {
    const update = buildOrderStrip(
      {
        customerName: 'Ada',
        totalPrice: 5000,
        discount: 200,
        discountReason: 'loyal',
        depositPaid: 1000,
        balanceRemaining: 3800,
        payments: [{ id: 'p1', amount: 1000 }],
        costs: [{ id: 'c1', amount: 300 }],
        items: [
          { id: 'i1', garmentType: 'Agbada', price: 5000 },
          { id: 'i2', garmentType: 'Cap' },
        ],
      },
      fieldValue,
    );
    expect(update.totalPrice).toBe(DELETE);
    expect(update.discount).toBe(DELETE);
    expect(update.discountReason).toBe(DELETE);
    expect(update.depositPaid).toBe(DELETE);
    expect(update.balanceRemaining).toBe(DELETE);
    expect(update.payments).toBe(DELETE);
    expect(update.costs).toBe(DELETE);
    expect(update.items).toEqual([
      { id: 'i1', garmentType: 'Agbada' },
      { id: 'i2', garmentType: 'Cap' },
    ]);
    expect(update.customerName).toBeUndefined();
  });

  it('returns an empty map for an already-clean order', () => {
    const update = buildOrderStrip(
      { customerName: 'Ada', status: 'PENDING', items: [{ id: 'i1', garmentType: 'Cap' }] },
      fieldValue,
    );
    expect(update).toEqual({});
  });
});

describe('buildCustomerStrip', () => {
  it('deletes contact fields present and nothing else', () => {
    const update = buildCustomerStrip(
      { name: 'Ada', phone: '0801', email: 'a@x.com', address: 'Lagos', slotState: 'active' },
      fieldValue,
    );
    expect(update).toEqual({ phone: DELETE, email: DELETE, address: DELETE });
  });

  it('returns an empty map for an already-clean customer', () => {
    expect(buildCustomerStrip({ name: 'Ada', slotState: 'active' }, fieldValue)).toEqual({});
  });
});

describe('isLegacyDepositIncomplete', () => {
  it('returns true when order has depositPaid > 0 but money lacks legacy-deposit payment', () => {
    const order = { depositPaid: 5000 };
    const money = { payments: [] };
    expect(isLegacyDepositIncomplete(order, money)).toBe(true);
  });

  it('returns false when money.payments contains a legacy-deposit entry', () => {
    const order = { depositPaid: 5000 };
    const money = { payments: [{ id: 'legacy-deposit', amount: 5000, type: 'DEPOSIT' }] };
    expect(isLegacyDepositIncomplete(order, money)).toBe(false);
  });

  it('returns false when order.depositPaid is zero', () => {
    const order = { depositPaid: 0 };
    const money = { payments: [] };
    expect(isLegacyDepositIncomplete(order, money)).toBe(false);
  });

  it('returns false when order.depositPaid is absent', () => {
    const order = {};
    const money = { payments: [] };
    expect(isLegacyDepositIncomplete(order, money)).toBe(false);
  });

  // Fail-safe: a mirror with no payments array cannot have absorbed the deposit,
  // so an order with depositPaid > 0 is treated as incomplete and skipped.
  it('returns true when money.payments is absent and the order has a deposit', () => {
    const order = { depositPaid: 5000 };
    const money = {};
    expect(isLegacyDepositIncomplete(order, money)).toBe(true);
  });
});
