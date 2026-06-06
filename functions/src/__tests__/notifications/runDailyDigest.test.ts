import { runDailyDigest } from '../../notifications/runDailyDigest';
import { DigestIO, DigestRecipient, OrderScanDoc } from '../../notifications/types';

const NOW = Date.parse('2026-06-03T06:00:00Z');
const DAY = 86_400_000;

function fakeIO(over: Partial<DigestIO> & { recipients: DigestRecipient[]; ordersByUid: Record<string, OrderScanDoc[]> }): {
  io: DigestIO; sent: { to: string; subject: string }[]; stamps: Record<string, string>;
} {
  const sent: { to: string; subject: string }[] = [];
  const stamps: Record<string, string> = {};
  const io: DigestIO = {
    listRecipients: async () => over.recipients,
    loadOrders: async (uid) => over.ordersByUid[uid] || [],
    getLastSentDate: async (uid) => stamps[uid] ?? null,
    setLastSentDate: async (uid, d) => { stamps[uid] = d; },
    sendEmail: async (p) => { sent.push({ to: p.to, subject: p.subject }); },
    isAllowed: over.isAllowed ?? (() => true),
  };
  return { io, sent, stamps };
}

const recip = (p: Partial<DigestRecipient> = {}): DigestRecipient => ({ uid: 'u1', email: 'u1@x.com', name: 'Ada', digestEnabled: true, ...p });
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
    const { io, sent } = fakeIO({
      recipients: [recip({ uid: 'u1', email: 'u1@x.com' }), recip({ uid: 'u2', email: 'u2@x.com' })],
      ordersByUid: { u1: [order({ deadline: NOW - DAY })], u2: [order({ deadline: NOW - DAY })] },
    });
    io.loadOrders = async (uid) => { if (uid === 'u1') throw new Error('boom'); return [order({ deadline: NOW - DAY })]; };
    const r = await runDailyDigest(io, NOW);
    expect(r.sent).toBe(1);
    expect(r.failed).toBe(1);
    expect(sent.map((s) => s.to)).toEqual(['u2@x.com']);
  });
});
