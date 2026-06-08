import { digestDetector, isDigestEmpty } from '../../notifications/digestDetector';
import { OrderScanDoc } from '../../notifications/types';

const NOW = Date.parse('2026-06-03T06:00:00Z'); // 07:00 Lagos
const DAY = 86_400_000;

function order(p: Partial<OrderScanDoc>): OrderScanDoc {
  return {
    id: 'o', customerName: 'Ada', status: 'IN_PROGRESS', deadline: null,
    archivedAt: null, totalPrice: 0, payments: [], items: [{ garmentType: 'Agbada' }], ...p,
  };
}

describe('digestDetector', () => {
  it('flags an order due tomorrow as dueSoon, not overdue', () => {
    const m = digestDetector([order({ deadline: NOW + DAY })], NOW);
    expect(m.dueSoon.length).toBe(1);
    expect(m.overdue.length).toBe(0);
    expect(m.dueSoon[0].customerName).toBe('Ada');
  });

  it('flags an order due today as dueSoon', () => {
    const m = digestDetector([order({ deadline: NOW + 5 * 3600_000 })], NOW);
    expect(m.dueSoon.length).toBe(1);
  });

  it('flags a past-day deadline as overdue', () => {
    const m = digestDetector([order({ deadline: NOW - DAY })], NOW);
    expect(m.overdue.length).toBe(1);
    expect(m.dueSoon.length).toBe(0);
  });

  it('does not flag deadlines beyond tomorrow', () => {
    const m = digestDetector([order({ deadline: NOW + 3 * DAY })], NOW);
    expect(isDigestEmpty(m)).toBe(true);
  });

  it('ignores null-deadline and archived orders for due/overdue', () => {
    const m = digestDetector([
      order({ deadline: null }),
      order({ deadline: NOW - DAY, archivedAt: NOW }),
    ], NOW);
    expect(m.dueSoon.length + m.overdue.length).toBe(0);
  });

  it('flags DELIVERED-with-balance and READY-with-balance as outstanding', () => {
    const m = digestDetector([
      order({ status: 'DELIVERED', totalPrice: 10000, payments: [{ amount: 4000 }] }),
      order({ status: 'READY', totalPrice: 5000, payments: [] }),
    ], NOW);
    expect(m.outstanding.length).toBe(2);
    expect(m.outstanding[0].amount).toBe(6000); // biggest owed first
  });

  it('excludes in-progress balances and sub-naira residue from outstanding', () => {
    const m = digestDetector([
      order({ status: 'IN_PROGRESS', totalPrice: 9000, payments: [{ amount: 1000 }] }),
      order({ status: 'DELIVERED', totalPrice: 5000, payments: [{ amount: 4999.7 }] }),
    ], NOW);
    expect(m.outstanding.length).toBe(0);
  });

  it('returns all 8 overdue orders without capping', () => {
    const many = Array.from({ length: 8 }, (_, i) => order({ deadline: NOW - DAY, customerName: `c${i}` }));
    const m = digestDetector(many, NOW);
    expect(m.overdue.length).toBe(8);
  });

  it('carries orderId on every item', () => {
    const m = digestDetector([
      order({ id: 'o1', deadline: NOW - DAY }),                                  // overdue
      order({ id: 'o2', deadline: NOW + DAY }),                                  // due soon
      order({ id: 'o3', status: 'DELIVERED', totalPrice: 5000, payments: [] }), // outstanding
    ], NOW);
    expect(m.overdue[0].orderId).toBe('o1');
    expect(m.dueSoon[0].orderId).toBe('o2');
    expect(m.outstanding[0].orderId).toBe('o3');
  });

  it('lists a READY past-deadline unpaid order in both overdue and outstanding', () => {
    const m = digestDetector([
      order({ status: 'READY', deadline: NOW - DAY, totalPrice: 8000, payments: [] }),
    ], NOW);
    expect(m.overdue.length).toBe(1);
    expect(m.outstanding.length).toBe(1);
    expect(m.overdue[0].customerName).toBe(m.outstanding[0].customerName);
  });

  it('summarises multiple garments', () => {
    const m = digestDetector([order({
      deadline: NOW + DAY,
      items: [{ garmentType: 'Agbada' }, { garmentType: 'Buba' }],
    })], NOW);
    expect(m.dueSoon[0].garmentSummary).toBe('Agbada +1 more');
  });

  it('counts legacy depositPaid as paid when payments is empty', () => {
    const m = digestDetector([
      order({ status: 'DELIVERED', totalPrice: 10000, payments: [], depositPaid: 4000 }),
    ], NOW);
    expect(m.outstanding.length).toBe(1);
    expect(m.outstanding[0].amount).toBe(6000); // 10000 - 4000 legacy deposit
  });

  it('ignores depositPaid when payments are present (payments are source of truth)', () => {
    const m = digestDetector([
      order({ status: 'DELIVERED', totalPrice: 10000, payments: [{ amount: 10000 }], depositPaid: 4000 }),
    ], NOW);
    expect(m.outstanding.length).toBe(0); // fully paid via payments; depositPaid ignored
  });
});
