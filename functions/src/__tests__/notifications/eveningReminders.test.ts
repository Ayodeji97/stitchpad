import { runEveningReminders } from '../../notifications/runEveningReminders';
import {
  detectReminders, entryKey, nudgeDecision, pruneEntries, stampSent,
} from '../../notifications/reminderDetector';
import { nudgePush, reminderPush } from '../../notifications/reminderCopy';
import { reminderInboxDocs } from '../../notifications/notificationDocs';
import { parseReminderState, recipientFromUserDoc } from '../../notifications/eveningReminders';
import { DAY_MS, lagosDateKey, lagosDayIndex } from '../../notifications/lagosTime';
import { OrderScanDoc } from '../../notifications/types';
import {
  ReminderIO, ReminderItem, ReminderPushPayload, ReminderRecipient, ReminderState,
} from '../../notifications/reminderTypes';

/** Monday 23 Nov 2026, 18:00 Lagos. */
const NOW = Date.parse('2026-11-23T17:00:00Z');
const TODAY_KEY = lagosDateKey(NOW);
const TODAY = lagosDayIndex(NOW);
/** Tuesday noon Lagos. */
const TOMORROW_NOON = Date.parse('2026-11-24T11:00:00Z');
const daysAgo = (n: number) => NOW - n * DAY_MS;

const order = (over: Partial<OrderScanDoc> = {}): OrderScanDoc => ({
  id: 'o1',
  customerName: 'Ada',
  status: 'IN_PROGRESS',
  deadline: null,
  archivedAt: null,
  currency: 'NGN',
  totalPrice: 0,
  discount: 0,
  payments: [],
  items: [{ garmentType: 'agbada' }],
  statusHistory: [],
  createdAt: daysAgo(10),
  updatedAt: daysAgo(2),
  ...over,
});

const dueTomorrow = (over: Partial<OrderScanDoc> = {}) => order({ deadline: TOMORROW_NOON, ...over });
const readyFor = (days: number, over: Partial<OrderScanDoc> = {}) => order({
  status: 'READY', statusHistory: [{ status: 'READY', changedAt: daysAgo(days) }], ...over,
});
const deliveredOwing = (days: number, over: Partial<OrderScanDoc> = {}) => order({
  status: 'DELIVERED',
  totalPrice: 25_000,
  payments: [{ amount: 10_000 }],
  statusHistory: [{ status: 'DELIVERED', changedAt: daysAgo(days) }],
  ...over,
});

const emptyState = (over: Partial<ReminderState> = {}): ReminderState => ({
  lastEveningPushDate: null, lastNudgeDayIndex: null, entries: {}, engagementLastSentDate: null, ...over,
});

const recip = (over: Partial<ReminderRecipient> = {}): ReminderRecipient => ({
  uid: 'u1', email: 'u1@x.com', pushEnabled: true, activityPushEnabled: true, ...over,
});

interface Spy {
  io: ReminderIO;
  pushes: { uid: string; payload: ReminderPushPayload }[];
  saved: Record<string, ReminderState>;
  inbox: Record<string, ReminderItem[]>;
  deleted: string[];
  calls: { listRecipients: number; customerActivity: number };
}

function fakeIO(over: {
  enabled?: boolean | Error;
  recipients?: ReminderRecipient[];
  orders?: Record<string, OrderScanDoc[]>;
  state?: Record<string, ReminderState>;
  customerActivity?: boolean;
  tokens?: string[];
  successCount?: number;
  invalidTokens?: string[];
  allowed?: boolean;
  throwFor?: string;
} = {}): Spy {
  const pushes: Spy['pushes'] = [];
  const saved: Spy['saved'] = {};
  const inbox: Spy['inbox'] = {};
  const deleted: string[] = [];
  const calls = { listRecipients: 0, customerActivity: 0 };
  let currentUid = '';
  const io: ReminderIO = {
    isEnabled: async () => {
      if (over.enabled instanceof Error) throw over.enabled;
      return over.enabled ?? true;
    },
    listRecipients: async () => { calls.listRecipients++; return over.recipients ?? [recip()]; },
    isAllowed: () => over.allowed ?? true,
    loadState: async (uid) => {
      if (over.throwFor === uid) throw new Error('boom');
      currentUid = uid;
      return saved[uid] ?? over.state?.[uid] ?? emptyState();
    },
    saveState: async (uid, s) => { saved[uid] = s; },
    loadOrders: async (uid) => over.orders?.[uid] ?? [],
    hasCustomerActivitySince: async () => { calls.customerActivity++; return over.customerActivity ?? false; },
    writeInbox: async (uid, items) => { inbox[uid] = [...(inbox[uid] ?? []), ...items]; },
    loadPushTokens: async () => over.tokens ?? ['tok'],
    sendPush: async (tokens, payload) => {
      pushes.push({ uid: currentUid, payload });
      return { successCount: over.successCount ?? tokens.length, invalidTokens: over.invalidTokens ?? [] };
    },
    deletePushTokens: async (_uid, tokens) => { deleted.push(...tokens); },
  };
  return { io, pushes, saved, inbox, deleted, calls };
}

// ─── Rule 1: due tomorrow ──────────────────────────────────────────────────
describe('due tomorrow', () => {
  it('flags an open order due tomorrow', () => {
    const plan = detectReminders([dueTomorrow()], emptyState(), NOW);
    expect(plan.due.map((i) => i.orderId)).toEqual(['o1']);
  });

  it.each(['READY', 'DELIVERED'])('ignores a %s order due tomorrow', (status) => {
    expect(detectReminders([dueTomorrow({ status })], emptyState(), NOW).due).toEqual([]);
  });

  it('ignores orders due today, the day after tomorrow, with no deadline, or archived', () => {
    const plan = detectReminders([
      order({ id: 'today', deadline: Date.parse('2026-11-23T12:00:00Z') }),
      order({ id: 'later', deadline: Date.parse('2026-11-25T12:00:00Z') }),
      order({ id: 'none', deadline: null }),
      dueTomorrow({ id: 'archived', archivedAt: daysAgo(1) }),
    ], emptyState(), NOW);
    expect(plan.due).toEqual([]);
  });

  it('sends once per deadline, and again only if the deadline moves', () => {
    const sent = emptyState({ entries: { [entryKey('due', 'o1')]: { count: 1, lastSentAt: daysAgo(1), anchor: TODAY + 1 } } });
    expect(detectReminders([dueTomorrow()], sent, NOW).due).toEqual([]);
    const moved = emptyState({ entries: { [entryKey('due', 'o1')]: { count: 1, lastSentAt: daysAgo(3), anchor: TODAY - 2 } } });
    expect(detectReminders([dueTomorrow()], moved, NOW).due).toHaveLength(1);
  });

  it('copy: one order', () => {
    const p = reminderPush(detectReminders([dueTomorrow()], emptyState(), NOW), NOW);
    expect(p).toEqual({
      title: 'Due tomorrow',
      body: 'Ada\'s agbada is due tomorrow and isn\'t Ready yet.',
      target: 'order',
      orderId: 'o1',
    });
  });

  it('copy: several orders become one summary push opening the inbox', () => {
    const plan = detectReminders([
      dueTomorrow({ id: 'a', customerName: 'Ada', deadline: TOMORROW_NOON }),
      dueTomorrow({ id: 'b', customerName: 'Bisi', deadline: TOMORROW_NOON + 3600_000 }),
      dueTomorrow({ id: 'c', customerName: 'Chidi', deadline: TOMORROW_NOON + 7200_000 }),
    ], emptyState(), NOW);
    const p = reminderPush(plan, NOW);
    expect(p.title).toBe('3 orders due tomorrow');
    expect(p.body).toBe('Ada\'s agbada and 2 more are due tomorrow and not Ready yet.');
    expect(p.target).toBe('inbox');
    expect(p.orderId).toBeUndefined();
  });
});

// ─── Rule 2: ready but not collected ──────────────────────────────────────
describe('ready, not collected', () => {
  it('waits 3 full days after Ready', () => {
    expect(detectReminders([readyFor(2)], emptyState(), NOW).ready).toEqual([]);
    expect(detectReminders([readyFor(3)], emptyState(), NOW).ready).toHaveLength(1);
  });

  it('uses the LATEST move to Ready', () => {
    const o = readyFor(10, {
      statusHistory: [
        { status: 'READY', changedAt: daysAgo(10) },
        { status: 'IN_PROGRESS', changedAt: daysAgo(9) },
        { status: 'READY', changedAt: daysAgo(1) },
      ],
    });
    expect(detectReminders([o], emptyState(), NOW).ready).toEqual([]);
  });

  it('falls back to updatedAt when statusHistory is missing, and skips with no timestamps at all', () => {
    expect(detectReminders([order({ status: 'READY', statusHistory: undefined, updatedAt: daysAgo(4) })], emptyState(), NOW).ready).toHaveLength(1);
    expect(detectReminders([order({ status: 'READY', statusHistory: undefined, updatedAt: daysAgo(1) })], emptyState(), NOW).ready).toEqual([]);
    expect(detectReminders([order({ status: 'READY', statusHistory: [], updatedAt: undefined, createdAt: undefined })], emptyState(), NOW).ready).toEqual([]);
  });

  it('ignores orders Ready for longer than the 60-day window', () => {
    expect(detectReminders([readyFor(61)], emptyState(), NOW).ready).toEqual([]);
  });

  it('sends once, repeats once a week later, then never again', () => {
    const at = (count: number, lastSentDaysAgo: number) => emptyState({
      entries: { [entryKey('ready', 'o1')]: { count, lastSentAt: daysAgo(lastSentDaysAgo), anchor: daysAgo(12) } },
    });
    expect(detectReminders([readyFor(12)], emptyState(), NOW).ready[0].round).toBe(1);
    expect(detectReminders([readyFor(12)], at(1, 6), NOW).ready).toEqual([]);
    expect(detectReminders([readyFor(12)], at(1, 7), NOW).ready[0].round).toBe(2);
    expect(detectReminders([readyFor(12)], at(2, 30), NOW).ready).toEqual([]);
  });

  it('copy: weekday within the week, day count beyond it', () => {
    // 3 days before Monday 23 Nov is Friday 20 Nov.
    expect(reminderPush(detectReminders([readyFor(3)], emptyState(), NOW), NOW).body)
      .toBe('Call Ada — the agbada has been ready since Friday.');
    expect(reminderPush(detectReminders([readyFor(10)], emptyState(), NOW), NOW).body)
      .toBe('Call Ada — the agbada has been ready for 10 days.');
    expect(reminderPush(detectReminders([readyFor(3)], emptyState(), NOW), NOW).title).toBe('Ready for collection');
  });
});

// ─── Rule 3: payment still owed ───────────────────────────────────────────
describe('payment still owed after delivery', () => {
  it('waits 3 days after delivery and needs a balance', () => {
    expect(detectReminders([deliveredOwing(2)], emptyState(), NOW).owed).toEqual([]);
    expect(detectReminders([deliveredOwing(3)], emptyState(), NOW).owed[0].amount).toBe(15_000);
    expect(detectReminders([deliveredOwing(5, { payments: [{ amount: 25_000 }] })], emptyState(), NOW).owed).toEqual([]);
  });

  it('honours the whole-order discount', () => {
    const o = deliveredOwing(5, { discount: 15_000 });
    expect(detectReminders([o], emptyState(), NOW).owed).toEqual([]);
  });

  it('sends once plus one repeat a week later', () => {
    const at = (count: number, lastSentDaysAgo: number) => emptyState({
      entries: { [entryKey('owed', 'o1')]: { count, lastSentAt: daysAgo(lastSentDaysAgo), anchor: daysAgo(20) } },
    });
    expect(detectReminders([deliveredOwing(20)], at(1, 3), NOW).owed).toEqual([]);
    expect(detectReminders([deliveredOwing(20)], at(1, 8), NOW).owed[0].round).toBe(2);
    expect(detectReminders([deliveredOwing(20)], at(2, 30), NOW).owed).toEqual([]);
  });

  it('uses the order currency', () => {
    const p = reminderPush(detectReminders([deliveredOwing(4, { currency: 'XOF' })], emptyState(), NOW), NOW);
    expect(p.body).toBe('Ada still owes F CFA 15,000 for the agbada.');
    const ng = reminderPush(detectReminders([deliveredOwing(4)], emptyState(), NOW), NOW);
    expect(ng).toEqual({ title: 'Payment still owed', body: 'Ada still owes ₦15,000 for the agbada.', target: 'order', orderId: 'o1' });
  });

  it('skips archived orders', () => {
    expect(detectReminders([deliveredOwing(5, { archivedAt: daysAgo(1) })], emptyState(), NOW).owed).toEqual([]);
  });
});

// ─── Combining ────────────────────────────────────────────────────────────
describe('one combined push', () => {
  const all = [
    readyFor(4, { id: 'r', customerName: 'Remi' }),
    deliveredOwing(4, { id: 'w', customerName: 'Wale' }),
    dueTomorrow({ id: 'd', customerName: 'Dayo' }),
  ];

  it('leads with deadline risk, then money, then collection', () => {
    const p = reminderPush(detectReminders(all, emptyState(), NOW), NOW);
    expect(p.title).toBe('1 due tomorrow · 1 payment owed · 1 ready to collect');
    expect(p.body).toBe('Dayo\'s agbada is due tomorrow and isn\'t Ready yet.');
    expect(p.target).toBe('inbox');
    const noDue = reminderPush(detectReminders(all.slice(0, 2), emptyState(), NOW), NOW);
    expect(noDue.body).toBe('Wale still owes ₦15,000 for the agbada.');
  });

  it('French copy', () => {
    const fr = reminderPush(detectReminders(all, emptyState(), NOW), NOW, 'fr-SN');
    expect(fr.title).toBe('1 à livrer demain · 1 paiement en attente · 1 à récupérer');
    expect(fr.body).toBe('Dayo · agbada : à livrer demain, pas encore au statut Prêt.');
    expect(reminderPush(detectReminders([deliveredOwing(4, { currency: 'XOF' })], emptyState(), NOW), NOW, 'fr').body)
      .toBe('Ada vous doit encore F CFA 15,000 (agbada).');
    expect(reminderPush(detectReminders([readyFor(3)], emptyState(), NOW), NOW, 'fr').body)
      .toBe('Appelez Ada — agbada est au statut Prêt depuis vendredi.');
    expect(reminderPush(detectReminders([readyFor(3)], emptyState(), NOW), NOW, 'fr').title).toBe('Prêt à récupérer');
    expect(nudgePush('fr')).toEqual({
      title: 'Des nouveaux travaux aujourd\'hui ?', body: 'Enregistrez-les avant d\'oublier.', target: 'dashboard',
    });
    expect(nudgePush('en-NG').title).toBe('Any new jobs today?');
  });
});

// ─── Rule 4: end-of-day nudge ──────────────────────────────────────────────
describe('end-of-day nudge decision', () => {
  const quietOrder = order({ createdAt: daysAgo(5), updatedAt: daysAgo(2) });

  it('is eligible for an active tailor who recorded nothing today', () => {
    expect(nudgeDecision([quietOrder], emptyState(), NOW, TODAY_KEY)).toBe('eligible');
  });

  it('leaves never-used and dormant tailors to the engagement push', () => {
    expect(nudgeDecision([], emptyState(), NOW, TODAY_KEY)).toBe('never_used');
    expect(nudgeDecision([order({ createdAt: daysAgo(21) })], emptyState(), NOW, TODAY_KEY)).toBe('dormant');
  });

  it('stays quiet on a day with order activity', () => {
    expect(nudgeDecision([order({ createdAt: daysAgo(5), updatedAt: NOW - 3600_000 })], emptyState(), NOW, TODAY_KEY)).toBe('active_today');
  });

  it('never doubles up with an engagement push the same day', () => {
    expect(nudgeDecision([quietOrder], emptyState({ engagementLastSentDate: TODAY_KEY }), NOW, TODAY_KEY)).toBe('engagement_today');
  });

  it('at most one nudge every 3 days', () => {
    expect(nudgeDecision([quietOrder], emptyState({ lastNudgeDayIndex: TODAY - 2 }), NOW, TODAY_KEY)).toBe('cadence');
    expect(nudgeDecision([quietOrder], emptyState({ lastNudgeDayIndex: TODAY - 3 }), NOW, TODAY_KEY)).toBe('eligible');
  });
});

// ─── Run loop ──────────────────────────────────────────────────────────────
describe('runEveningReminders', () => {
  it('sends ONE push per user even when every rule applies, and stamps every item', async () => {
    const s = fakeIO({ orders: { u1: [
      readyFor(4, { id: 'r' }), deliveredOwing(4, { id: 'w' }), dueTomorrow({ id: 'd' }),
    ] } });
    const r = await runEveningReminders(s.io, NOW);
    expect(s.pushes).toHaveLength(1);
    expect(r).toMatchObject({ sent: 1, sentDue: 1, items: 3 });
    expect(Object.keys(s.saved.u1.entries).sort()).toEqual(['due:d', 'owed:w', 'ready:r']);
    expect(s.saved.u1.lastEveningPushDate).toBe(TODAY_KEY);
    expect(s.inbox.u1).toHaveLength(3);
  });

  it('a second run the same evening sends nothing', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow()] } });
    await runEveningReminders(s.io, NOW);
    const again = await runEveningReminders(s.io, NOW + 60_000);
    expect(s.pushes).toHaveLength(1);
    expect(again.skippedAlreadyPushedToday).toBe(1);
  });

  it('the nudge never goes on an evening that already has a reminder', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow({ createdAt: daysAgo(3), updatedAt: daysAgo(2) })] } });
    await runEveningReminders(s.io, NOW);
    expect(s.pushes[0].payload.title).toBe('Due tomorrow');
    expect(s.saved.u1.lastNudgeDayIndex).toBeNull();
  });

  it('nudges a quiet active tailor, but not after customer activity today', async () => {
    const quiet = { u1: [order({ createdAt: daysAgo(5), updatedAt: daysAgo(2) })] };
    const s = fakeIO({ orders: quiet });
    const r = await runEveningReminders(s.io, NOW);
    expect(r.sentNudge).toBe(1);
    expect(s.pushes[0].payload).toEqual(nudgePush());
    expect(s.saved.u1.lastNudgeDayIndex).toBe(TODAY);
    expect(s.inbox.u1).toBeUndefined();

    const busy = fakeIO({ orders: quiet, customerActivity: true });
    const r2 = await runEveningReminders(busy.io, NOW);
    expect(busy.pushes).toEqual([]);
    expect(r2.skippedNothingToSay).toBe(1);
  });

  it('no nudge on a day the engagement push already went out', async () => {
    const s = fakeIO({
      orders: { u1: [order({ createdAt: daysAgo(5), updatedAt: daysAgo(2) })] },
      state: { u1: emptyState({ engagementLastSentDate: TODAY_KEY }) },
    });
    await runEveningReminders(s.io, NOW);
    expect(s.pushes).toEqual([]);
    expect(s.calls.customerActivity).toBe(0);
  });

  it('honours the daily-reminders opt-out; the team-alerts switch does not silence it', async () => {
    const s = fakeIO({
      recipients: [recip({ uid: 'a', pushEnabled: false }), recip({ uid: 'b', activityPushEnabled: false })],
      orders: { a: [dueTomorrow()], b: [dueTomorrow()] },
    });
    const r = await runEveningReminders(s.io, NOW);
    expect(r.skippedOptedOut).toBe(1);
    expect(s.pushes).toHaveLength(1);
  });

  it('respects the rollout gate', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow()] }, allowed: false });
    const r = await runEveningReminders(s.io, NOW);
    expect(s.pushes).toEqual([]);
    expect(r.skippedNotAllowed).toBe(1);
  });

  it('kill switch: false (or unreadable) sends nothing and never lists users', async () => {
    for (const enabled of [false, new Error('down')]) {
      const s = fakeIO({ enabled, orders: { u1: [dueTomorrow()] } });
      const r = await runEveningReminders(s.io, NOW);
      expect(r.skippedKillSwitch).toBe(1);
      expect(s.calls.listRecipients).toBe(0);
      expect(s.pushes).toEqual([]);
    }
  });

  it('does not stamp when delivery fails, and prunes dead tokens', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow()] }, successCount: 0, invalidTokens: ['tok'] });
    const r = await runEveningReminders(s.io, NOW);
    expect(r.failed).toBe(1);
    expect(s.saved.u1).toBeUndefined();
    expect(s.deleted).toEqual(['tok']);
  });

  it('no tokens: inbox written, nothing stamped', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow()] }, tokens: [] });
    const r = await runEveningReminders(s.io, NOW);
    expect(r.skippedNoTokens).toBe(1);
    expect(s.inbox.u1).toHaveLength(1);
    expect(s.saved.u1).toBeUndefined();
  });

  it('one failing user does not stop the run', async () => {
    const s = fakeIO({
      recipients: [recip({ uid: 'bad' }), recip({ uid: 'good' })],
      orders: { good: [dueTomorrow()] },
      throwFor: 'bad',
    });
    const r = await runEveningReminders(s.io, NOW);
    expect(r.failed).toBe(1);
    expect(r.sent).toBe(1);
  });

  it('the weekly repeat goes out a week later, then stops for good', async () => {
    const s = fakeIO({ orders: { u1: [deliveredOwing(4)] } });
    await runEveningReminders(s.io, NOW);
    await runEveningReminders(s.io, NOW + 3 * DAY_MS);  // too soon
    await runEveningReminders(s.io, NOW + 7 * DAY_MS);  // repeat
    await runEveningReminders(s.io, NOW + 14 * DAY_MS); // capped
    expect(s.pushes.filter((p) => p.payload.title === 'Payment still owed')).toHaveLength(2);
    expect(s.saved.u1.entries['owed:o1'].count).toBe(2);
  });

  it('ignoreDailyCap lets a tester re-send', async () => {
    const s = fakeIO({ orders: { u1: [dueTomorrow()] }, state: { u1: emptyState({ lastEveningPushDate: TODAY_KEY }) } });
    const r = await runEveningReminders(s.io, NOW, { ignoreDailyCap: true });
    expect(r.sent).toBe(1);
  });
});

// ─── State + inbox helpers ────────────────────────────────────────────────
describe('state and inbox helpers', () => {
  it('prunes entries for orders that left the status, were archived or deleted', () => {
    const entries = {
      'ready:a': { count: 1, lastSentAt: 0, anchor: 0 },
      'ready:b': { count: 1, lastSentAt: 0, anchor: 0 },
      'owed:c': { count: 1, lastSentAt: 0, anchor: 0 },
      'due:d': { count: 1, lastSentAt: 0, anchor: TODAY - 1 },
      'ready:gone': { count: 1, lastSentAt: 0, anchor: 0 },
    };
    const kept = pruneEntries(entries, [
      readyFor(5, { id: 'a' }),
      order({ id: 'b', status: 'DELIVERED' }),
      deliveredOwing(5, { id: 'c', payments: [{ amount: 25_000 }] }),
      dueTomorrow({ id: 'd' }),
    ], NOW);
    expect(Object.keys(kept)).toEqual(['ready:a']);
  });

  it('stampSent records the round and the evening', () => {
    const plan = detectReminders([readyFor(4)], emptyState(), NOW);
    const s = stampSent(emptyState(), plan.ready, [readyFor(4)], NOW, TODAY_KEY, false);
    expect(s.entries['ready:o1']).toEqual({ count: 1, lastSentAt: NOW, anchor: daysAgo(4) });
    expect(s.lastEveningPushDate).toBe(TODAY_KEY);
  });

  it('inbox docs reuse the digest id for due, fresh per-round ids for money/collection', () => {
    const plan = detectReminders([dueTomorrow({ id: 'd' }), deliveredOwing(8, { id: 'w' }), readyFor(4, { id: 'r' })], emptyState(), NOW);
    const docs = reminderInboxDocs([...plan.due, ...plan.owed, ...plan.ready]);
    expect(docs.map((d) => [d.id, d.data.type])).toEqual([
      ['d__DUE_SOON', 'DUE_SOON'],
      ['w__TO_COLLECT__owed1', 'TO_COLLECT'],
      ['r__TO_COLLECT__ready1', 'TO_COLLECT'],
    ]);
    expect(docs[0].data.deadline).toBe(TOMORROW_NOON);
    expect(docs[1].data).toMatchObject({ amount: 15_000, currency: 'NGN', isOverdue: true });
    expect(docs[2].data.amount).toBeNull();
  });

  it('parses state defensively and reads the engagement send date', () => {
    const s = parseReminderState(
      { lastEveningPushDate: '2026-11-22', lastNudgeDayIndex: 'x', entries: { 'due:a': { count: 1, lastSentAt: 5, anchor: 9 }, 'due:b': { count: 'no' } } },
      { engagement: { lastSentDate: TODAY_KEY } },
    );
    expect(s).toEqual({
      lastEveningPushDate: '2026-11-22',
      lastNudgeDayIndex: null,
      entries: { 'due:a': { count: 1, lastSentAt: 5, anchor: 9 } },
      engagementLastSentDate: TODAY_KEY,
    });
    expect(parseReminderState(undefined, undefined)).toEqual(emptyState());
  });

  it('resolves opt-outs with the digest inheritance', () => {
    expect(recipientFromUserDoc('u', {}).pushEnabled).toBe(true);
    expect(recipientFromUserDoc('u', { dailyDigestEmailEnabled: false }).pushEnabled).toBe(false);
    expect(recipientFromUserDoc('u', { dailyDigestEmailEnabled: false, dailyPushEnabled: true }).pushEnabled).toBe(true);
    expect(recipientFromUserDoc('u', { dailyPushEnabled: false }).pushEnabled).toBe(false);
    expect(recipientFromUserDoc('u', {}).activityPushEnabled).toBe(true);
    expect(recipientFromUserDoc('u', { activityPushEnabled: false }).activityPushEnabled).toBe(false);
  });
});
