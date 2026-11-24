import {
  ActivityIO,
  activityInboxDoc,
  activityInboxDocId,
  activityPushCopy,
  assignmentEvent,
  isActivityPushEnabled,
  orderActivityEvents,
  runOrderActivity,
  staffStatusEvent,
} from '../../notifications/orderActivity';
import type { PushPayload } from '../../notifications/fcm';

const OWNER = 'owner1';
const STAFF = 'staff1';
const T0 = 1_760_000_000_000;

function order(p: Record<string, unknown> = {}): any {
  return {
    status: 'IN_PROGRESS',
    customerName: 'Ada Obi',
    items: [{ garmentType: 'Agbada' }],
    statusHistory: [{ status: 'IN_PROGRESS', changedAt: T0 - 1000, changedBy: OWNER }],
    ...p,
  };
}

function statusChange(to: string, by: string | undefined, at = T0): [any, any] {
  const before = order();
  const entry: Record<string, unknown> = { status: to, changedAt: at };
  if (by !== undefined) entry.changedBy = by;
  const after = order({ status: to, statusHistory: [...before.statusHistory, entry] });
  return [before, after];
}

describe('staffStatusEvent', () => {
  it('notifies the OWNER when staff changes the status', () => {
    const [b, a] = statusChange('READY', STAFF);
    const e = staffStatusEvent(OWNER, 'o1', b, a);
    expect(e).toMatchObject({
      kind: 'STAFF_STATUS', recipientUid: OWNER, actorUid: STAFF, status: 'READY', eventAt: T0,
      customerName: 'Ada Obi', garmentSummary: 'Agbada',
    });
  });

  it('never notifies the owner about their own change', () => {
    const [b, a] = statusChange('READY', OWNER);
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toBeNull();
  });

  it('missing actor (pre-1.4.0 client) → no notification, never a guess', () => {
    const [b, a] = statusChange('READY', undefined);
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toBeNull();
    const [b2, a2] = statusChange('READY', '  ');
    expect(staffStatusEvent(OWNER, 'o1', b2, a2)).toBeNull();
  });

  it('ignores updates that do not change the status', () => {
    const b = order();
    const a = order({ notes: 'hem shorter' });
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toBeNull();
  });

  it('ignores a status change with no new history entry for the new status', () => {
    const b = order();
    const a = order({ status: 'READY' }); // history untouched (e.g. legacy full-doc write)
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toBeNull();
  });

  it('uses the newest appended entry when a write appends several', () => {
    const b = order();
    const a = order({
      status: 'READY',
      statusHistory: [...b.statusHistory,
        { status: 'READY', changedAt: T0, changedBy: OWNER },
        { status: 'READY', changedAt: T0 + 5, changedBy: STAFF }],
    });
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toMatchObject({ actorUid: STAFF, eventAt: T0 + 5 });
  });

  it('rejects unknown status values', () => {
    const [b, a] = statusChange('BURNT', STAFF);
    expect(staffStatusEvent(OWNER, 'o1', b, a)).toBeNull();
  });
});

describe('assignmentEvent', () => {
  const assigned = (p: Record<string, unknown> = {}) =>
    order({ assignedMemberId: STAFF, assignedMemberName: 'Tunde', assignedBy: OWNER, assignedAt: T0, deadline: T0 + 86_400_000, ...p });

  it('notifies the assignee when the owner assigns', () => {
    const e = assignmentEvent(OWNER, 'o1', order(), assigned());
    expect(e).toMatchObject({ kind: 'ORDER_ASSIGNED', recipientUid: STAFF, actorUid: OWNER, eventAt: T0 });
  });

  it('a staff self-claim (no owner marker in this write) → no notification', () => {
    expect(assignmentEvent(OWNER, 'o1', order(), order({ assignedMemberId: STAFF }))).toBeNull();
  });

  it('a stale owner marker does not dress up a later self-claim', () => {
    // Owner assigned + unassigned earlier (marker left at T0); staff then claims.
    const before = order({ assignedMemberId: null, assignedBy: OWNER, assignedAt: T0 });
    const after = order({ assignedMemberId: STAFF, assignedBy: OWNER, assignedAt: T0 });
    expect(assignmentEvent(OWNER, 'o1', before, after)).toBeNull();
  });

  it('does not notify when the assignee is unchanged, cleared, or the owner', () => {
    expect(assignmentEvent(OWNER, 'o1', assigned({ assignedAt: T0 - 1 }), assigned())).toBeNull();
    expect(assignmentEvent(OWNER, 'o1', assigned(), assigned({ assignedMemberId: null, assignedAt: T0 + 1 }))).toBeNull();
    expect(assignmentEvent(OWNER, 'o1', order(), assigned({ assignedMemberId: OWNER }))).toBeNull();
  });

  it('missing markers (old owner client) → no notification', () => {
    expect(assignmentEvent(OWNER, 'o1', order(), assigned({ assignedBy: undefined }))).toBeNull();
    expect(assignmentEvent(OWNER, 'o1', order(), assigned({ assignedAt: undefined }))).toBeNull();
  });
});

describe('orderActivityEvents', () => {
  it('returns nothing for an ordinary edit (the cheap gate before any read)', () => {
    expect(orderActivityEvents(OWNER, 'o1', order(), order({ notes: 'x' }))).toEqual([]);
  });
});

describe('copy + inbox doc', () => {
  const [b, a] = statusChange('READY', STAFF);
  const statusEvent = staffStatusEvent(OWNER, 'o1', b, a)!;
  const assignEvent = assignmentEvent(OWNER, 'o1', order(),
    order({ assignedMemberId: STAFF, assignedBy: OWNER, assignedAt: T0, deadline: Date.UTC(2026, 10, 28, 9) }))!;

  it('status copy in English names actor, customer, garment and status', () => {
    expect(activityPushCopy(statusEvent, 'Tunde', 'en')).toEqual({
      title: 'Order ready', body: 'Tunde marked Ada\'s Agbada Ready',
    });
  });

  it('status copy in French', () => {
    const c = activityPushCopy(statusEvent, 'Tunde', 'fr-CI');
    expect(c.title).toBe('Commande prête');
    expect(c.body).toBe('Tunde a passé Agbada de Ada à « Prête »');
  });

  it('assignment copy has the due date and never any money (EN + FR)', () => {
    const en = activityPushCopy(assignEvent, '', 'en');
    expect(en).toEqual({ title: 'New order assigned to you', body: 'Ada\'s Agbada, due 28 Nov' });
    const fr = activityPushCopy(assignEvent, '', 'fr');
    expect(fr.title).toBe('Nouvelle commande pour vous');
    expect(fr.body).toMatch(/^Agbada de Ada, à livrer le 28 nov/);
    for (const c of [en, fr]) expect(c.body).not.toMatch(/₦|NGN|F CFA|\d{1,3},\d{3}/);
  });

  it('inbox docs carry no money and a deterministic id', () => {
    const doc = activityInboxDoc(assignEvent, '', 123);
    expect(doc.amount).toBeNull();
    expect(doc).not.toHaveProperty('currency');
    expect(doc).toMatchObject({ type: 'ORDER_ASSIGNED', orderId: 'o1', isRead: false, createdAt: 123 });
    expect(activityInboxDocId(assignEvent)).toBe(`o1__ORDER_ASSIGNED__${T0}`);
    expect(activityInboxDocId(statusEvent)).toBe(`o1__STAFF_STATUS__${T0}`);
  });
});

describe('isActivityPushEnabled', () => {
  it('own flag wins, then daily push, then email digest; default on; no doc → off', () => {
    expect(isActivityPushEnabled({})).toBe(true);
    expect(isActivityPushEnabled({ activityPushEnabled: false })).toBe(false);
    expect(isActivityPushEnabled({ activityPushEnabled: true, dailyPushEnabled: false })).toBe(true);
    expect(isActivityPushEnabled({ dailyPushEnabled: false })).toBe(false);
    expect(isActivityPushEnabled({ dailyDigestEmailEnabled: false })).toBe(false);
    expect(isActivityPushEnabled({ dailyPushEnabled: true, dailyDigestEmailEnabled: false })).toBe(true);
    expect(isActivityPushEnabled(null)).toBe(false);
  });
});

describe('runOrderActivity', () => {
  function fakeIO(over: Partial<ActivityIO> = {}) {
    const inbox = new Map<string, Record<string, unknown>>();
    const sent: { tokens: string[]; payload: PushPayload }[] = [];
    const deleted: { uid: string; tokens: string[] }[] = [];
    const io: ActivityIO = {
      loadRecipient: async () => ({ pushEnabled: true, locale: 'en' }),
      isActiveStaff: async (_o, s) => s === STAFF,
      loadMemberName: async () => 'Tunde Bello',
      createInbox: async (uid, id, data) => {
        const key = `${uid}/${id}`;
        if (inbox.has(key)) return 'exists';
        inbox.set(key, data);
        return 'created';
      },
      loadTokens: async () => ['tok-a', 'tok-dead'],
      send: async (tokens, payload) => {
        sent.push({ tokens, payload });
        return { successCount: 1, invalidTokens: ['tok-dead'] };
      },
      deleteTokens: async (uid, tokens) => { deleted.push({ uid, tokens }); },
      now: () => 42,
      log: () => undefined,
      ...over,
    };
    return { io, inbox, sent, deleted };
  }

  it('staff status → inbox + push to the owner, per-order tag, dead tokens pruned', async () => {
    const { io, inbox, sent, deleted } = fakeIO();
    const [b, a] = statusChange('READY', STAFF);
    const res = await runOrderActivity(OWNER, 'o1', b, a, io);
    expect(res.map((r) => r.outcome)).toEqual(['sent']);
    expect(inbox.get(`${OWNER}/o1__STAFF_STATUS__${T0}`)).toMatchObject({ actorName: 'Tunde Bello', status: 'READY' });
    expect(sent[0].payload).toMatchObject({
      body: 'Tunde Bello marked Ada\'s Agbada Ready',
      data: { target: 'order', orderId: 'o1' },
      androidTag: 'stitchpad_order_o1',
      apnsCollapseId: 'stitchpad_order_o1',
    });
    expect(deleted).toEqual([{ uid: OWNER, tokens: ['tok-dead'] }]);
  });

  it('is idempotent under trigger retry: second run writes nothing and sends nothing', async () => {
    const { io, sent } = fakeIO();
    const [b, a] = statusChange('READY', STAFF);
    await runOrderActivity(OWNER, 'o1', b, a, io);
    const again = await runOrderActivity(OWNER, 'o1', b, a, io);
    expect(again.map((r) => r.outcome)).toEqual(['duplicate']);
    expect(sent).toHaveLength(1);
  });

  it('actor that is not an active staff member → nothing written or sent', async () => {
    const { io, inbox, sent } = fakeIO({ isActiveStaff: async () => false });
    const [b, a] = statusChange('READY', STAFF);
    expect((await runOrderActivity(OWNER, 'o1', b, a, io))[0].outcome).toBe('not_staff');
    expect(inbox.size).toBe(0);
    expect(sent).toHaveLength(0);
  });

  it('push opt-out still writes the inbox but sends nothing', async () => {
    const { io, inbox, sent } = fakeIO({ loadRecipient: async () => ({ pushEnabled: false }) });
    const [b, a] = statusChange('READY', STAFF);
    expect((await runOrderActivity(OWNER, 'o1', b, a, io))[0].outcome).toBe('push_disabled');
    expect(inbox.size).toBe(1);
    expect(sent).toHaveLength(0);
  });

  it('localizes by the RECIPIENT locale and falls back when the roster name is missing', async () => {
    const { io, sent } = fakeIO({
      loadRecipient: async () => ({ pushEnabled: true, locale: 'fr' }),
      loadMemberName: async () => null,
    });
    const [b, a] = statusChange('DELIVERED', STAFF);
    await runOrderActivity(OWNER, 'o1', b, a, io);
    expect(sent[0].payload.title).toBe('Commande livrée');
    expect(sent[0].payload.body).toContain('Un membre de l’équipe');
  });

  it('owner assignment → staff gets inbox + push, no money anywhere', async () => {
    const { io, inbox, sent } = fakeIO();
    const after = order({ assignedMemberId: STAFF, assignedBy: OWNER, assignedAt: T0, totalPrice: 50000 });
    const res = await runOrderActivity(OWNER, 'o1', order(), after, io);
    expect(res.map((r) => [r.event.recipientUid, r.outcome])).toEqual([[STAFF, 'sent']]);
    const doc = inbox.get(`${STAFF}/o1__ORDER_ASSIGNED__${T0}`)!;
    expect(doc.amount).toBeNull();
    expect(JSON.stringify(doc)).not.toContain('50000');
    expect(sent[0].payload.body).not.toMatch(/50,000|₦/);
  });

  it('assignment to a named (non-account) member → nothing', async () => {
    const { io, sent } = fakeIO();
    const after = order({ assignedMemberId: 'named-123', assignedBy: OWNER, assignedAt: T0 });
    expect((await runOrderActivity(OWNER, 'o1', order(), after, io))[0].outcome).toBe('not_staff');
    expect(sent).toHaveLength(0);
  });

  it('an error in one event is caught and reported, never thrown', async () => {
    const { io } = fakeIO({ loadTokens: async () => { throw new Error('boom'); } });
    const [b, a] = statusChange('READY', STAFF);
    await expect(runOrderActivity(OWNER, 'o1', b, a, io)).resolves.toEqual([
      expect.objectContaining({ outcome: 'error' }),
    ]);
  });
});
