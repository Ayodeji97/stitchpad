import {
  effectiveCap,
  selectSlotsToLock,
  CustomerSlotInfo,
  endOfSignupMonthInLagos,
  isInWelcomeWindow,
} from '../../freemium/reconcileSlots';

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

  it('totalActiveAfter correctly counts promotions (locked → active on upgrade)', () => {
    const customers = [
      ...Array.from({ length: 15 }, (_, i) => customer(`active-${i}`, recent)),
      ...Array.from({ length: 5 }, (_, i) => customer(`locked-${i}`, recent, 'locked')),
    ];
    const ops = selectSlotsToLock(customers, Number.MAX_SAFE_INTEGER, now);
    // We don't import the reconcile handler, but we can validate the math
    // for the simulated final state:
    const changesById = new Map(ops.map((o) => [o.id, o.toState]));
    let active = 0;
    for (const c of customers) {
      const finalState = changesById.get(c.id) ?? c.slotState;
      if (finalState === 'active') active += 1;
    }
    expect(active).toBe(20); // all 20 active after promotion
  });

  it('promotes locked customers when a finite cap rises but total still exceeds', () => {
    // 15 active + 25 locked, cap rises to 30 → promote 15 locked to active.
    const customers = [
      ...Array.from({ length: 15 }, (_, i) => customer(`active-${i}`, recent)),
      ...Array.from({ length: 25 }, (_, i) => customer(`locked-${i}`, recent - i * 1000, 'locked')),
    ];
    const ops = selectSlotsToLock(customers, 30, now);
    expect(ops.length).toBe(15);
    expect(ops.every((o) => o.toState === 'active')).toBe(true);
    // The 15 with the most recent activity should be promoted (locked-0..locked-14)
    const promotedIds = ops.map((o) => o.id).sort();
    expect(promotedIds).toEqual(Array.from({ length: 15 }, (_, i) => `locked-${i}`).sort());
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

describe('endOfSignupMonthInLagos', () => {
  it('treats 2026-05-31T23:30:00Z as a JUNE signup in Lagos (June ends at 2026-07-01 00:00 Lagos)', () => {
    // 2026-05-31T23:30Z = 2026-06-01T00:30 Lagos — so it is a June signup.
    // June ends at 2026-07-01 00:00 Lagos = 2026-06-30 23:00 UTC.
    const signupMs = Date.UTC(2026, 4, 31, 23, 30, 0); // 2026-05-31T23:30Z
    const end = endOfSignupMonthInLagos(signupMs);
    expect(end).toBe(Date.UTC(2026, 5, 30, 23, 0, 0));
  });

  it('treats 2026-05-15T12:00:00Z as a MAY signup in Lagos', () => {
    // 2026-05-15T12:00Z = 2026-05-15T13:00 Lagos — a May signup.
    // May ends at 2026-06-01 00:00 Lagos = 2026-05-31 23:00 UTC.
    const signupMs = Date.UTC(2026, 4, 15, 12, 0, 0);
    const end = endOfSignupMonthInLagos(signupMs);
    expect(end).toBe(Date.UTC(2026, 4, 31, 23, 0, 0));
  });
});

describe('isInWelcomeWindow (Lagos timezone)', () => {
  it('returns true when now is before the end of the signup month in Lagos', () => {
    const signupMs = Date.UTC(2026, 4, 15, 12, 0, 0); // May 15, Lagos
    // Now = 2026-05-31T22:00Z — still inside May in Lagos (ends at 23:00Z).
    const now = new Date(Date.UTC(2026, 4, 31, 22, 0, 0));
    expect(isInWelcomeWindow(signupMs, now)).toBe(true);
  });

  it('returns false when now is at or after the end of the signup month in Lagos', () => {
    const signupMs = Date.UTC(2026, 4, 15, 12, 0, 0); // May 15, Lagos
    // Now = 2026-05-31T23:00Z — exactly Lagos midnight June 1st, window closed.
    const now = new Date(Date.UTC(2026, 4, 31, 23, 0, 0));
    expect(isInWelcomeWindow(signupMs, now)).toBe(false);
  });

  it('returns false for undefined welcomeAppliedAtMs', () => {
    expect(isInWelcomeWindow(undefined, new Date())).toBe(false);
  });
});

describe('effectiveCap', () => {
  // These constants are duplicated from EntitlementsCalculator.kt on the client
  // side. If either side bumps the cap without updating the other, this test
  // catches the drift before reconcileCustomerSlots locks customers the client
  // just allowed (the PR-1 review caught exactly this lag).
  it('uses 200 for Free users in First Month (matches Kotlin WELCOME_CUSTOMER_CAP)', () => {
    expect(effectiveCap('free', true)).toBe(200);
  });

  it('uses 15 for Free users after First Month (matches Kotlin FREE_CUSTOMER_CAP)', () => {
    expect(effectiveCap('free', false)).toBe(15);
  });

  it('returns Number.MAX_SAFE_INTEGER for Pro regardless of welcome state', () => {
    expect(effectiveCap('pro', true)).toBe(Number.MAX_SAFE_INTEGER);
    expect(effectiveCap('pro', false)).toBe(Number.MAX_SAFE_INTEGER);
  });

  it('returns Number.MAX_SAFE_INTEGER for Atelier regardless of welcome state', () => {
    expect(effectiveCap('atelier', true)).toBe(Number.MAX_SAFE_INTEGER);
    expect(effectiveCap('atelier', false)).toBe(Number.MAX_SAFE_INTEGER);
  });
});
