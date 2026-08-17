import { staffDigests } from '../../notifications/staffDigest';
import { OrderScanDoc } from '../../notifications/types';

const NOW = Date.parse('2026-08-18T09:00:00Z');
const DAY = 86_400_000;

const order = (over: Partial<OrderScanDoc> = {}): OrderScanDoc => ({
  id: 'o1',
  customerName: 'Folake',
  status: 'IN_PROGRESS',
  deadline: null,
  archivedAt: null,
  totalPrice: 0,
  payments: [],
  items: [],
  assignedMemberId: null,
  ...over,
});

describe('staffDigests', () => {
  it('is empty when the workshop has no staff', () => {
    expect(staffDigests([order({ deadline: NOW - DAY })], [], NOW)).toEqual([]);
  });

  it('gives each staff member only their own assigned work', () => {
    const orders = [
      order({ id: 'a', customerName: 'Ada', deadline: NOW - DAY, assignedMemberId: 'gabby' }),
      order({ id: 'b', customerName: 'Bola', deadline: NOW - DAY, assignedMemberId: 'tunde' }),
    ];
    const digests = staffDigests(orders, ['gabby', 'tunde'], NOW);
    expect(digests).toHaveLength(2);
    expect(digests[0].model.overdue.map((i) => i.customerName)).toEqual(['Ada']);
    expect(digests[1].model.overdue.map((i) => i.customerName)).toEqual(['Bola']);
  });

  it('omits a staff member with nothing assigned', () => {
    const orders = [order({ deadline: NOW - DAY, assignedMemberId: 'gabby' })];
    expect(staffDigests(orders, ['gabby', 'idle'], NOW).map((d) => d.staffUid)).toEqual(['gabby']);
  });

  it('omits a staff member whose assigned work has nothing actionable', () => {
    // Assigned, but no deadline — nothing to chase.
    const orders = [order({ deadline: null, assignedMemberId: 'gabby' })];
    expect(staffDigests(orders, ['gabby'], NOW)).toEqual([]);
  });

  // Money is the owner's business. A staff member is told what to make and when,
  // never what a customer owes — even though the orders array carries the figures.
  it('never includes money owed, however large the balance', () => {
    const orders = [order({
      id: 'a',
      status: 'READY',
      deadline: NOW - DAY,
      assignedMemberId: 'gabby',
      totalPrice: 50000,
      payments: [],
    })];
    const [digest] = staffDigests(orders, ['gabby'], NOW);
    expect(digest.model.outstanding).toEqual([]);
    expect(digest.model.overdue).toHaveLength(1);
  });

  // Owner-created "named" roster rows have arbitrary ids and no auth account, so
  // they can never match a membership uid — there is nobody to notify.
  it('ignores work assigned to a name-only member', () => {
    const orders = [order({ deadline: NOW - DAY, assignedMemberId: 'named-member-xyz' })];
    expect(staffDigests(orders, ['gabby'], NOW)).toEqual([]);
  });

  it('ignores unassigned work', () => {
    const orders = [order({ deadline: NOW - DAY, assignedMemberId: null })];
    expect(staffDigests(orders, ['gabby'], NOW)).toEqual([]);
  });

  it('carries the not-started flag through to the staff member', () => {
    const orders = [order({
      status: 'PENDING', deadline: NOW + DAY, assignedMemberId: 'gabby',
    })];
    const [digest] = staffDigests(orders, ['gabby'], NOW);
    expect(digest.model.dueSoon[0].notStarted).toBe(true);
  });
});
