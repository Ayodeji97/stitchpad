import { collectibleTransition, collectPushCopy } from '../../notifications/orderCollectNotify';

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
  it('push copy names the garment, state, and amount', () => {
    const copy = collectPushCopy({ customerName: 'Ada Obi', garmentSummary: 'Agbada', amount: 8500, status: 'READY' });
    expect(copy.body).toContain('Agbada');
    expect(copy.body).toContain('ready');
    expect(copy.body).toContain('8,500');
  });
});
