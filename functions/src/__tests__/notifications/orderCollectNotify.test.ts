import { isCollectibleTransition, collectibleTransition, collectPushCopy } from '../../notifications/orderCollectNotify';

function order(p: Partial<any> = {}): any {
  return { status: 'IN_PROGRESS', totalPrice: 0, discount: 0, payments: [], depositPaid: 0,
    customerName: 'Ada Obi', items: [{ garmentType: 'Agbada' }], ...p };
}

describe('collectibleTransition', () => {
  it('fires on IN_PROGRESS → READY with a balance', () => {
    const n = collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'READY', totalPrice: 10000, payments: [{ amount: 1500 }] }));
    expect(n).not.toBeNull();
    expect(n!.amount).toBe(8500);
    expect(n!.status).toBe('READY');
  });
  it('fires on PENDING → DELIVERED with a balance', () => {
    const n = collectibleTransition(order({ status: 'PENDING' }),
      order({ status: 'DELIVERED', totalPrice: 5000, payments: [] }));
    expect(n?.amount).toBe(5000);
  });
  it('does NOT fire on READY → DELIVERED (already collectible)', () => {
    expect(collectibleTransition(order({ status: 'READY', totalPrice: 5000 }),
      order({ status: 'DELIVERED', totalPrice: 5000 }))).toBeNull();
  });
  it('does NOT fire when the balance is zero', () => {
    expect(collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'READY', totalPrice: 5000, payments: [{ amount: 5000 }] }))).toBeNull();
  });
  it('does NOT fire on a non-status edit that stays non-collectible', () => {
    expect(collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'IN_PROGRESS', totalPrice: 9000 }))).toBeNull();
  });
  it('does NOT throw and returns the correct balance on a legacy doc with no `payments` field', () => {
    const before = order({ status: 'IN_PROGRESS' });
    const after = order({ status: 'READY', totalPrice: 10000, depositPaid: 4000 });
    delete after.payments; // legacy doc: field absent entirely, not just an empty array
    expect(after.payments).toBeUndefined();
    let n: ReturnType<typeof collectibleTransition> = null;
    expect(() => {
      n = collectibleTransition(before, after);
    }).not.toThrow();
    expect(n).not.toBeNull();
    expect(n!.amount).toBe(6000); // totalPrice(10000) - depositPaid(4000)
    expect(n!.status).toBe('READY');
  });
  it('push copy names the garment, state, and amount', () => {
    const copy = collectPushCopy({ customerName: 'Ada Obi', garmentSummary: 'Agbada', amount: 8500, status: 'READY' });
    expect(copy.body).toContain('Agbada');
    expect(copy.body).toContain('ready');
    expect(copy.body).toContain('8,500');
  });
});

describe('isCollectibleTransition — the cheap gate before any Firestore read', () => {
  it('is true only on a first entry into a collectible state', () => {
    expect(isCollectibleTransition({ status: 'IN_PROGRESS' }, { status: 'READY' })).toBe(true);
    expect(isCollectibleTransition({ status: 'READY' }, { status: 'DELIVERED' })).toBe(false);
    expect(isCollectibleTransition({ status: 'PENDING' }, { status: 'IN_PROGRESS' })).toBe(false);
  });

  // This trigger fires on EVERY order field change; it must not pay a money read
  // just to discover the status did not move.
  it('is false when the status did not change at all', () => {
    expect(isCollectibleTransition({ status: 'IN_PROGRESS' }, { status: 'IN_PROGRESS' })).toBe(false);
  });
});

describe('collectibleTransition — legacy orders with no money mirror', () => {
  // REGRESSION: moneyFromDoc(undefined) is all zeroes. Spreading it over the base doc
  // wiped real base money and silenced the trigger for every pre-mirror order.
  it('still fires from base-doc money when no mirror exists', () => {
    const after = {
      status: 'READY', customerName: 'Folake', items: [],
      totalPrice: 15000, payments: [{ amount: 5000 }],
    };
    expect(collectibleTransition({ status: 'IN_PROGRESS' }, after)?.amount).toBe(10000);
  });
});
