import { runDailyDigest } from '../../notifications/runDailyDigest';
import { DigestIO, DigestRecipient, OrderScanDoc } from '../../notifications/types';
import { lagosDateKey } from '../../notifications/lagosTime';

const NOW = Date.parse('2026-06-03T06:00:00Z');
const DAY = 86_400_000;

function fakeIO(over: Partial<DigestIO> & {
  recipients: DigestRecipient[];
  ordersByUid: Record<string, OrderScanDoc[]>;
  tokensByUid?: Record<string, string[]>;
  invalidTokens?: string[];
  pushSuccessCount?: number;
}): {
  io: DigestIO;
  sent: { to: string; subject: string }[];
  pushes: { tokens: string[]; body: string }[];
  pushStamps: Record<string, string>;
  deletedTokens: { uid: string; tokens: string[] }[];
  stamps: Record<string, string>;
  notified: Record<string, number>;
} {
  const sent: { to: string; subject: string }[] = [];
  const stamps: Record<string, string> = {};
  const pushStamps: Record<string, string> = {};
  const pushes: { tokens: string[]; body: string }[] = [];
  const deletedTokens: { uid: string; tokens: string[] }[] = [];
  const notified: Record<string, number> = {};
  const io: DigestIO = {
    listRecipients: async () => over.recipients,
    loadOrders: async (uid) => over.ordersByUid[uid] || [],
    getLastSentDate: async (uid) => stamps[uid] ?? null,
    setLastSentDate: async (uid, d) => { stamps[uid] = d; },
    writeNotifications: async (uid) => { notified[uid] = (notified[uid] || 0) + 1; },
    sendEmail: async (p) => { sent.push({ to: p.to, subject: p.subject }); },
    isAllowed: over.isAllowed ?? (() => true),
    loadPushTokens: async (uid) => over.tokensByUid?.[uid] ?? [],
    sendPush: async (tokens, payload) => {
      pushes.push({ tokens, body: payload.body });
      const invalid = over.invalidTokens ?? [];
      const successCount = over.pushSuccessCount !== undefined
        ? over.pushSuccessCount
        : tokens.length - invalid.length;
      return { successCount, invalidTokens: invalid };
    },
    deletePushTokens: async (uid, tokens) => { deletedTokens.push({ uid, tokens }); },
    getLastPushDate: async (uid) => pushStamps[uid] ?? null,
    setLastPushDate: async (uid, d) => { pushStamps[uid] = d; },
  };
  return { io, sent, pushes, pushStamps, deletedTokens, stamps, notified };
}

const recip = (p: Partial<DigestRecipient> = {}): DigestRecipient => ({ uid: 'u1', email: 'u1@x.com', name: 'Ada', digestEnabled: true, pushEnabled: true, ...p });
const order = (p: Partial<OrderScanDoc>): OrderScanDoc => ({ id: 'o', customerName: 'C', status: 'IN_PROGRESS', deadline: null, archivedAt: null, totalPrice: 0, payments: [], items: [], ...p });

describe('runDailyDigest', () => {
  it('sends one email to a tailor with actionable orders and stamps the date', async () => {
    const { io, sent, stamps } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(1);
    expect(r.sent).toBe(1);
    expect(stamps.u1).toBe('2026-06-03');
  });

  it('suppresses when there is nothing actionable', async () => {
    const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.suppressedEmpty).toBe(1);
  });

  it('skips opted-out tailors', async () => {
    const { io, sent } = fakeIO({ recipients: [recip({ digestEnabled: false })], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedDisabled).toBe(1);
  });

  it('skips when already sent today', async () => {
    const { io, sent, stamps } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    stamps.u1 = '2026-06-03';
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedAlreadySent).toBe(1);
  });

  it('skips non-allowlisted recipients', async () => {
    const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] }, isAllowed: () => false });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(r.skippedNotAllowed).toBe(1);
  });

  it('isolates a failing recipient so others still send', async () => {
    const { io, sent, notified } = fakeIO({
      recipients: [recip({ uid: 'u1', email: 'u1@x.com' }), recip({ uid: 'u2', email: 'u2@x.com' })],
      ordersByUid: { u1: [order({ deadline: NOW - DAY })], u2: [order({ deadline: NOW - DAY })] },
    });
    io.loadOrders = async (uid) => { if (uid === 'u1') throw new Error('boom'); return [order({ deadline: NOW - DAY })]; };
    const r = await runDailyDigest(io, NOW);
    expect(r.sent).toBe(1);
    expect(r.failed).toBe(1);
    expect(sent.map((s) => s.to)).toEqual(['u2@x.com']);
    expect(notified.u1).toBeUndefined(); // loadOrders threw before writeNotifications → no zombie write
  });

  it('writes notifications for a disabled recipient even though no email is sent', async () => {
    const { io, sent, notified } = fakeIO({ recipients: [recip({ digestEnabled: false })], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(notified.u1).toBe(1);
  });

  it('writes notifications even when the digest is empty (no email)', async () => {
    const { io, sent, notified } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(notified.u1).toBe(1);
  });
});

describe('runDailyDigest — push', () => {
  const overdueOrder: OrderScanDoc = {
    id: 'o1', customerName: 'Folake', status: 'IN_PROGRESS',
    deadline: 0, archivedAt: null, totalPrice: 1000, payments: [],
    items: [{ garmentType: 'Asoebi' }],
  };
  const recipient = (over: Partial<DigestRecipient> = {}): DigestRecipient =>
    ({ uid: 'u1', email: 'a@b.com', name: 'Shop', digestEnabled: true, pushEnabled: true, ...over });

  it('sends one push for an enabled, allowed recipient with actionable orders + a token', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(1);
    expect(f.pushes[0].tokens).toEqual(['tok1']);
    expect(f.pushStamps.u1).toBeTruthy();
  });
  it('skips push when pushEnabled is false (but still emails)', async () => {
    const f = fakeIO({ recipients: [recipient({ pushEnabled: false })], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
    expect(f.sent).toHaveLength(1);
  });
  it('skips push when the recipient has no tokens', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: [] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
    expect(f.pushStamps.u1).toBeUndefined();
  });
  it('skips push when not allowed by the rollout allowlist', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] }, isAllowed: () => false });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });
  it('skips push when the model is empty (suppress-when-empty)', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });
  it('skips push when already pushed today', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    f.pushStamps.u1 = lagosDateKey(1_000_000_000_000);
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });
  it('prunes invalid tokens reported by sendPush', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1', 'bad'] }, invalidTokens: ['bad'] });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.deletedTokens).toEqual([{ uid: 'u1', tokens: ['bad'] }]);
  });
  it('does NOT stamp lastPushDate when all tokens are invalid (successCount 0)', async () => {
    const f = fakeIO({
      recipients: [recipient()],
      ordersByUid: { u1: [overdueOrder] },
      tokensByUid: { u1: ['bad1', 'bad2'] },
      invalidTokens: ['bad1', 'bad2'],
      pushSuccessCount: 0,
    });
    await runDailyDigest(f.io, 1_000_000_000_000);
    // push was attempted
    expect(f.pushes).toHaveLength(1);
    // invalid tokens were still pruned
    expect(f.deletedTokens).toEqual([{ uid: 'u1', tokens: ['bad1', 'bad2'] }]);
    // but the once-per-day guard must NOT be stamped
    expect(f.pushStamps.u1).toBeUndefined();
  });

  it('a push failure does not block the email digest', async () => {
    const f = fakeIO({
      recipients: [recipient()],
      ordersByUid: { u1: [overdueOrder] },
      tokensByUid: { u1: ['tok1'] },
    });
    // Make the push path throw.
    f.io.sendPush = async () => { throw new Error('FCM down'); };
    const result = await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.sent).toHaveLength(1);        // email still went out
    expect(result.sent).toBe(1);
    expect(result.failed).toBe(0);         // push failure not counted as a recipient failure
  });
});
