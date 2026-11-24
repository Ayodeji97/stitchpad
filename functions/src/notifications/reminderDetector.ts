/**
 * Pure rules for the evening reminder push. No IO, no clock beyond `now`.
 *
 * Every threshold lives here so the PR, the tests and the run loop agree on one
 * definition of "due tomorrow", "ready but not collected" and "still owed".
 */
import { balanceRemaining, summariseGarments } from './digestDetector';
import { DAY_MS, LAGOS_OFFSET_MS, lagosDayIndex } from './lagosTime';
import { notificationCurrency } from './moneyDisplay';
import { daysSinceNewestOrder } from './runEngagementPush';
import { DORMANT_DAYS } from './segmentDetector';
import { OrderScanDoc } from './types';
import { ReminderEntry, ReminderItem, ReminderKind, ReminderPlan, ReminderState } from './reminderTypes';

/** Ready this many whole days, still not Delivered → "call the customer". */
export const READY_UNCOLLECTED_DAYS = 3;
/** Delivered this many whole days with a balance → "they still owe you". */
export const OWED_AFTER_DELIVERY_DAYS = 3;
/** The single repeat (owed / ready) waits this long after the first send. */
export const REPEAT_AFTER_DAYS = 7;
/** owed / ready: the first send plus at most one repeat. */
export const MAX_SENDS_PER_ORDER = 2;
/**
 * Orders that became Ready/Delivered longer ago than this are left alone. Without
 * it, the first evening after deploy would dredge up every year-old unpaid order.
 * The morning digest still lists them.
 */
export const MAX_AGE_DAYS = 60;
/** End-of-day nudge: at most one every this many days. */
export const NUDGE_MIN_DAYS_BETWEEN = 3;

/** Epoch ms of the most recent change INTO `status`, or null when statusHistory has none. */
function latestChangeTo(o: OrderScanDoc, status: string): number | null {
  const times = (o.statusHistory ?? [])
    .filter((c) => c.status === status && c.changedAt > 0)
    .map((c) => c.changedAt);
  return times.length > 0 ? Math.max(...times) : null;
}

/**
 * When the order entered `status`. Falls back to updatedAt, then createdAt, for
 * legacy orders written before statusHistory existed. Both fallbacks are LATER
 * than or equal to the real moment, so a wrong guess delays a reminder rather
 * than firing one early. null = nothing to go on, so no reminder.
 */
export function enteredStatusAt(o: OrderScanDoc, status: string): number | null {
  const t = latestChangeTo(o, status) ?? o.updatedAt ?? o.createdAt ?? null;
  return typeof t === 'number' && t > 0 ? t : null;
}

export function entryKey(kind: ReminderKind, orderId: string): string {
  return `${kind}:${orderId}`;
}

function wholeDays(from: number, now: number): number {
  return Math.floor((now - from) / DAY_MS);
}

/** Round to send now for a repeatable (owed/ready) reminder, or null when capped / too soon. */
function repeatRound(entry: ReminderEntry | undefined, now: number): number | null {
  if (!entry) return 1;
  if (entry.count >= MAX_SENDS_PER_ORDER) return null;
  if (now - entry.lastSentAt < REPEAT_AFTER_DAYS * DAY_MS) return null;
  return entry.count + 1;
}

function base(o: OrderScanDoc, kind: ReminderKind, anchor: number, round: number): ReminderItem {
  return {
    kind,
    orderId: o.id,
    customerName: o.customerName,
    garmentSummary: summariseGarments(o.items),
    anchor,
    round,
    currency: notificationCurrency(o.currency),
  };
}

/**
 * Everything worth saying tonight, before the per-user cap picks what to send.
 *
 * Archived orders are ignored. An order can appear in more than one list only in
 * theory (the statuses are disjoint), so each order yields at most one item.
 */
export function detectReminders(orders: OrderScanDoc[], state: ReminderState, now: number): ReminderPlan {
  const today = lagosDayIndex(now);
  const plan: ReminderPlan = { due: [], owed: [], ready: [] };

  for (const o of orders) {
    if (o.archivedAt != null) continue;

    // 1. Due tomorrow, not Ready yet. Once per (order, deadline day): a moved
    // deadline is a new promise and earns its own reminder.
    if (o.status !== 'READY' && o.status !== 'DELIVERED' && o.deadline != null) {
      const deadlineDay = lagosDayIndex(o.deadline);
      if (deadlineDay === today + 1) {
        const sent = state.entries[entryKey('due', o.id)];
        if (!sent || sent.anchor !== deadlineDay) {
          plan.due.push({ ...base(o, 'due', deadlineDay, 1), deadline: o.deadline });
        }
      }
      continue;
    }

    // 2. Delivered with a balance, a few days on.
    if (o.status === 'DELIVERED') {
      const balance = balanceRemaining(o);
      const at = enteredStatusAt(o, 'DELIVERED');
      if (balance <= 0 || at === null) continue;
      const days = wholeDays(at, now);
      if (days < OWED_AFTER_DELIVERY_DAYS || days > MAX_AGE_DAYS) continue;
      const round = repeatRound(state.entries[entryKey('owed', o.id)], now);
      if (round === null) continue;
      plan.owed.push({ ...base(o, 'owed', at, round), amount: balance, daysSince: days });
      continue;
    }

    // 3. Ready, not collected.
    if (o.status === 'READY') {
      const at = enteredStatusAt(o, 'READY');
      if (at === null) continue;
      const days = wholeDays(at, now);
      if (days < READY_UNCOLLECTED_DAYS || days > MAX_AGE_DAYS) continue;
      const round = repeatRound(state.entries[entryKey('ready', o.id)], now);
      if (round === null) continue;
      const balance = balanceRemaining(o);
      plan.ready.push({
        ...base(o, 'ready', at, round),
        daysSince: days,
        ...(balance > 0 ? { amount: balance } : {}),
      });
    }
  }

  plan.due.sort((a, b) => (a.deadline ?? 0) - (b.deadline ?? 0));
  // Biggest debt leads. Amounts in different currencies are never compared.
  plan.owed.sort((a, b) => (a.currency ?? '').localeCompare(b.currency ?? '') || (b.amount ?? 0) - (a.amount ?? 0));
  plan.ready.sort((a, b) => a.anchor - b.anchor); // waiting longest first
  return plan;
}

export function planSize(plan: ReminderPlan): number {
  return plan.due.length + plan.owed.length + plan.ready.length;
}

/** Priority: deadline risk > money > collection. */
export function leadKind(plan: ReminderPlan): ReminderKind | null {
  if (plan.due.length > 0) return 'due';
  if (plan.owed.length > 0) return 'owed';
  if (plan.ready.length > 0) return 'ready';
  return null;
}

export type NudgeDecision =
  | 'eligible'
  /** No orders ever: engagementPush's no_customer / no_order rungs own this tailor. */
  | 'never_used'
  /** No new order in DORMANT_DAYS: engagementPush's `dormant` campaign owns this tailor. */
  | 'dormant'
  | 'active_today'
  /** The Tue/Fri engagement push already spoke today. */
  | 'engagement_today'
  | 'cadence';

/** Epoch ms of 00:00 Lagos on `now`'s Lagos day. */
export function lagosStartOfDay(now: number): number {
  return lagosDayIndex(now) * DAY_MS - LAGOS_OFFSET_MS;
}

/**
 * The end-of-day nudge, decided from orders alone. The caller still has to rule
 * out customer activity today (one cheap query) when this returns 'eligible'.
 *
 * Its purpose is deliberately narrower than the engagement push: it is a HABIT
 * prompt for a tailor who is actively using the app (an order within the dormant
 * window) on the one evening they recorded nothing. Activation (never used) and
 * win-back (dormant) stay with engagementPush, so the two never chase one person.
 */
export function nudgeDecision(orders: OrderScanDoc[], state: ReminderState, now: number, todayKey: string): NudgeDecision {
  if (orders.length === 0) return 'never_used';
  const sinceNewest = daysSinceNewestOrder(orders, now);
  if (sinceNewest === null || sinceNewest >= DORMANT_DAYS) return 'dormant';
  const start = lagosStartOfDay(now);
  const touchedToday = orders.some((o) =>
    (o.createdAt ?? 0) >= start || (o.updatedAt ?? 0) >= start);
  if (touchedToday) return 'active_today';
  if (state.engagementLastSentDate === todayKey) return 'engagement_today';
  if (state.lastNudgeDayIndex !== null && lagosDayIndex(now) - state.lastNudgeDayIndex < NUDGE_MIN_DAYS_BETWEEN) {
    return 'cadence';
  }
  return 'eligible';
}

/**
 * Drops entries that can no longer matter, so the state doc stays small: the
 * order is gone or archived, its due day has passed, or it left the status the
 * reminder was about (which also makes a later return a fresh episode).
 */
export function pruneEntries(
  entries: Record<string, ReminderEntry>,
  orders: OrderScanDoc[],
  now: number,
): Record<string, ReminderEntry> {
  const byId = new Map(orders.map((o) => [o.id, o]));
  const today = lagosDayIndex(now);
  const kept: Record<string, ReminderEntry> = {};
  for (const [key, entry] of Object.entries(entries)) {
    const sep = key.indexOf(':');
    const kind = key.slice(0, sep);
    const o = byId.get(key.slice(sep + 1));
    if (!o || o.archivedAt != null) continue;
    const keep = (kind === 'due' && entry.anchor >= today)
      || (kind === 'ready' && o.status === 'READY')
      || (kind === 'owed' && o.status === 'DELIVERED' && balanceRemaining(o) > 0);
    if (keep) kept[key] = entry;
  }
  return kept;
}

/** State after a successful send: every item mentioned is stamped, stale entries pruned. */
export function stampSent(
  state: ReminderState,
  items: ReminderItem[],
  orders: OrderScanDoc[],
  now: number,
  todayKey: string,
  nudged: boolean,
): ReminderState {
  const entries = pruneEntries(state.entries, orders, now);
  for (const item of items) {
    entries[entryKey(item.kind, item.orderId)] = { count: item.round, lastSentAt: now, anchor: item.anchor };
  }
  return {
    ...state,
    lastEveningPushDate: todayKey,
    lastNudgeDayIndex: nudged ? lagosDayIndex(now) : state.lastNudgeDayIndex,
    entries,
  };
}
