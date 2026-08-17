import { moneyFromDoc, withMoney } from '../../notifications/orderMoney';
import { OrderScanDoc } from '../../notifications/types';
import { balanceRemaining } from '../../notifications/digestDetector';

const order = (over: Partial<OrderScanDoc> = {}): OrderScanDoc => ({
  id: 'o1',
  customerName: 'Folake',
  status: 'READY',
  deadline: null,
  archivedAt: null,
  totalPrice: 0,
  payments: [],
  items: [],
  ...over,
});

describe('moneyFromDoc', () => {
  it('reads the money fields', () => {
    expect(moneyFromDoc({
      totalPrice: 15000, discount: 1000,
      payments: [{ amount: 5000 }], costs: [{ amount: 2000 }],
    })).toEqual({
      totalPrice: 15000, discount: 1000,
      payments: [{ amount: 5000 }], costs: [{ amount: 2000 }],
    });
  });

  it('normalises a missing or malformed doc to zeroes rather than throwing', () => {
    expect(moneyFromDoc(undefined)).toEqual({
      totalPrice: 0, discount: 0, payments: [], costs: [],
    });
    expect(moneyFromDoc({ totalPrice: 'lots', payments: 'none' })).toEqual({
      totalPrice: 0, discount: 0, payments: [], costs: [],
    });
  });

  it('coerces non-numeric amounts inside the arrays', () => {
    expect(moneyFromDoc({ payments: [{ amount: '5000' }, { amount: 200 }] }).payments)
      .toEqual([{ amount: 0 }, { amount: 200 }]);
  });
});

describe('withMoney', () => {
  // THE regression this file exists for. The base order doc carries no money since
  // Slice 8d-1, so balanceRemaining computed max(0, 0-0) = 0 for every order in the
  // database — the digest's money bucket and onOrderCollectible were both dead.
  it('makes a balance computable where the base doc alone yields zero', () => {
    const base = order({ totalPrice: 0, payments: [] });
    expect(balanceRemaining(base)).toBe(0);

    const merged = withMoney([base], new Map([['o1', {
      totalPrice: 15000, discount: 0, payments: [{ amount: 5000 }], costs: [],
    }]]))[0];
    expect(balanceRemaining(merged)).toBe(10000);
  });

  it('applies the discount from the mirror', () => {
    const merged = withMoney([order()], new Map([['o1', {
      totalPrice: 20000, discount: 5000, payments: [], costs: [],
    }]]))[0];
    expect(balanceRemaining(merged)).toBe(15000);
  });

  it('carries costs through for the profit nudge', () => {
    const merged = withMoney([order()], new Map([['o1', {
      totalPrice: 10000, discount: 0, payments: [], costs: [{ amount: 3000 }],
    }]]))[0];
    expect(merged.costs).toEqual([{ amount: 3000 }]);
  });

  // Legacy docs written before the split still carry real numbers on the base doc.
  // Reading those beats reading zero.
  it('leaves an order untouched when it has no mirror', () => {
    const legacy = order({ totalPrice: 8000, payments: [{ amount: 3000 }] });
    const [result] = withMoney([legacy], new Map());
    expect(result).toBe(legacy);
    expect(balanceRemaining(result)).toBe(5000);
  });

  it('keeps the base values when the mirror is present but empty', () => {
    const legacy = order({ totalPrice: 8000, payments: [{ amount: 3000 }] });
    const merged = withMoney([legacy], new Map([['o1', {
      totalPrice: 0, discount: 0, payments: [], costs: [],
    }]]))[0];
    expect(balanceRemaining(merged)).toBe(5000);
  });

  it('maps each order to its own money and ignores unrelated entries', () => {
    const merged = withMoney(
      [order({ id: 'a' }), order({ id: 'b' })],
      new Map([
        ['a', { totalPrice: 1000, discount: 0, payments: [], costs: [] }],
        ['zzz', { totalPrice: 9999, discount: 0, payments: [], costs: [] }],
      ]),
    );
    expect(balanceRemaining(merged[0])).toBe(1000);
    expect(balanceRemaining(merged[1])).toBe(0);
  });
});
