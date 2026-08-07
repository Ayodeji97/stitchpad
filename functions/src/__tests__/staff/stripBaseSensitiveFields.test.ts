/* eslint-disable @typescript-eslint/no-var-requires, @typescript-eslint/no-require-imports */
const {
  buildOrderStrip,
  buildCustomerStrip,
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
