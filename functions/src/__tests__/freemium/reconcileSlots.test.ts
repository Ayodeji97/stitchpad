import { selectSlotsToLock, CustomerSlotInfo } from '../../freemium/reconcileSlots';

describe('selectSlotsToLock', () => {
  const now = new Date('2026-05-17T08:00:00Z');
  const dayMs = 24 * 60 * 60 * 1000;
  const recent = now.getTime() - 5 * dayMs;
  const stale = now.getTime() - 60 * dayMs;

  function customer(id: string, lastActivityMs: number, slot: 'active' | 'locked' = 'active'): CustomerSlotInfo {
    return { id, lastActivityMs, slotState: slot };
  }

  it('returns empty when count is at or under cap', () => {
    const customers = [customer('a', recent), customer('b', recent)];
    const ops = selectSlotsToLock(customers, 15, now);
    expect(ops).toEqual([]);
  });

  it('locks the right N customers with 50/50 active+inactive split', () => {
    // 25 customers: 12 active (recent), 13 inactive (stale). Cap = 15.
    // Need to lock 10. 50/50 = 5 active + 5 inactive.
    const customers = [
      ...Array.from({ length: 12 }, (_, i) => customer(`active-${i}`, recent - i * 1000)),
      ...Array.from({ length: 13 }, (_, i) => customer(`stale-${i}`, stale - i * 1000)),
    ];
    const ops = selectSlotsToLock(customers, 15, now);
    expect(ops).toHaveLength(10);
    const lockedIds = ops.map((o) => o.id);
    const lockedActives = lockedIds.filter((id) => id.startsWith('active')).length;
    const lockedStales = lockedIds.filter((id) => id.startsWith('stale')).length;
    expect(lockedActives).toBe(5);
    expect(lockedStales).toBe(5);
  });

  it('within each bucket, locks the OLDEST last-touch first', () => {
    // 20 customers, all active. Cap = 15. Need to lock 5.
    const customers = Array.from({ length: 20 }, (_, i) =>
      customer(`c-${i}`, recent - i * 1000),
    );
    const ops = selectSlotsToLock(customers, 15, now);
    // The 5 with the oldest lastActivityMs are at the end of the array — c-15..c-19.
    const lockedIds = ops.map((o) => o.id).sort();
    expect(lockedIds).toEqual(['c-15', 'c-16', 'c-17', 'c-18', 'c-19']);
  });

  it('promotes LOCKED customers to ACTIVE when cap rises (upgrade)', () => {
    const customers = [
      customer('a', recent, 'active'),
      customer('b', recent, 'locked'),
      customer('c', recent, 'locked'),
    ];
    // Cap = unlimited (Pro) — represented as Number.MAX_SAFE_INTEGER.
    const ops = selectSlotsToLock(customers, Number.MAX_SAFE_INTEGER, now);
    // No locks needed; locked → active promotions instead.
    expect(ops.filter((o) => o.toState === 'active').map((o) => o.id).sort())
      .toEqual(['b', 'c']);
  });
});
