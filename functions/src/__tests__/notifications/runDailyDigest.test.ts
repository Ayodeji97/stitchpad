import { runDailyDigest } from '../../notifications/runDailyDigest';
import { DigestIO, DigestRecipient, OrderScanDoc } from '../../notifications/types';
import { lagosDateKey } from '../../notifications/lagosTime';
import { ResendError } from '../../email/resendClient';

const NOW = Date.parse('2026-06-03T06:00:00Z');
const DAY = 86_400_000;

function fakeIO(over: Partial<DigestIO> & {
  recipients: DigestRecipient[];
  ordersByUid: Record<string, OrderScanDoc[]>;
  staffByOwner?: Record<string, string[]>;
  staffPushDisabled?: string[];
  tokensByUid?: Record<string, string[]>;
  customersByUid?: Record<string, boolean>;
  emailError?: Error;
  invalidTokens?: string[];
  pushSuccessCount?: number;
}): {
  io: DigestIO;
  sent: { to: string; subject: string; html: string; headers?: Record<string, string> }[];
  bounced: string[];
  pushes: { tokens: string[]; body: string; target?: string }[];
  pushStamps: Record<string, string>;
  deletedTokens: { uid: string; tokens: string[] }[];
  stamps: Record<string, string>;
  notified: Record<string, number>;
} {
  const sent: { to: string; subject: string; html: string; headers?: Record<string, string> }[] = [];
  const bounced: string[] = [];
  const stamps: Record<string, string> = {};
  const pushStamps: Record<string, string> = {};
  const pushes: { tokens: string[]; body: string; target?: string }[] = [];
  const deletedTokens: { uid: string; tokens: string[] }[] = [];
  const notified: Record<string, number> = {};
  const io: DigestIO = {
    listRecipients: async () => over.recipients,
    loadOrders: async (uid) => over.ordersByUid[uid] || [],
    getLastSentDate: async (uid) => stamps[uid] ?? null,
    setLastSentDate: async (uid, d) => { stamps[uid] = d; },
    writeNotifications: async (uid) => { notified[uid] = (notified[uid] || 0) + 1; },
    sendEmail: async (p) => {
      if (over.emailError) throw over.emailError;
      sent.push({ to: p.to, subject: p.subject, html: p.html, headers: p.headers });
    },
    isAllowed: over.isAllowed ?? (() => true),
    hasCustomers: async (uid) => over.customersByUid?.[uid] ?? false,
    markHardBounce: async (uid) => { bounced.push(uid); },
    loadPushTokens: async (uid) => over.tokensByUid?.[uid] ?? [],
    sendPush: async (tokens, payload) => {
      pushes.push({ tokens, body: payload.body, target: payload.target });
      const invalid = over.invalidTokens ?? [];
      const successCount = over.pushSuccessCount !== undefined
        ? over.pushSuccessCount
        : tokens.length - invalid.length;
      return { successCount, invalidTokens: invalid };
    },
    deletePushTokens: async (uid, tokens) => { deletedTokens.push({ uid, tokens }); },
    getLastPushDate: async (uid) => pushStamps[uid] ?? null,
    setLastPushDate: async (uid, d) => { pushStamps[uid] = d; },
    listStaffUids: async (ownerUid) => over.staffByOwner?.[ownerUid] ?? [],
    isStaffPushEnabled: async (staffUid) => over.staffPushDisabled?.includes(staffUid) !== true,
  };
  return { io, sent, bounced, pushes, pushStamps, deletedTokens, stamps, notified };
}

const recip = (p: Partial<DigestRecipient> = {}): DigestRecipient => ({ uid: 'u1', email: 'u1@x.com', emailVerified: true, name: 'Ada', digestEnabled: true, pushEnabled: true, emailOptOut: false, hardBounce: false, platform: null, unsubscribeUrl: 'https://unsub.test/e?u=u1&t=abc', ...p });
const order = (p: Partial<OrderScanDoc>): OrderScanDoc => ({ id: 'o', customerName: 'C', status: 'IN_PROGRESS', deadline: null, archivedAt: null, totalPrice: 0, payments: [], items: [], ...p });

describe('runDailyDigest', () => {
  it('sends one email to a tailor with actionable orders and stamps the date', async () => {
    const { io, sent, stamps } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(1);
    expect(r.sent).toBe(1);
    expect(stamps.u1).toBe('2026-06-03');
  });

  // Until 2026-08-20 a quiet morning sent nothing at all, which silenced 81 of 109
  // owners on the first production run. Silence is now the bug, not the feature.
  it('sends a nudge instead of falling silent when nothing is actionable', async () => {
    const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    const r = await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(1);
    expect(r.nudged).toBe(1);
    expect(r.sent).toBe(0);
    expect(r.suppressedEmpty).toBe(0);
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

  it('writes notifications even when the digest is empty', async () => {
    const { io, notified } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    await runDailyDigest(io, NOW);
    expect(notified.u1).toBe(1);
  });

  describe('daily nudge on a quiet morning', () => {
    it('asks a working tailor to record today\'s job', async () => {
      const { io, sent } = fakeIO({
        recipients: [recip()],
        // Has orders, but none due/overdue/owing → digest model is empty.
        ordersByUid: { u1: [order({ status: 'DELIVERED' })] },
      });
      const r = await runDailyDigest(io, NOW);
      expect(sent[0].subject).toBe('Did any job come in today?');
      expect(r.nudged).toBe(1);
    });

    it('asks for a first job when there are customers but no orders', async () => {
      const { io, sent } = fakeIO({
        recipients: [recip()], ordersByUid: { u1: [] }, customersByUid: { u1: true },
      });
      await runDailyDigest(io, NOW);
      expect(sent[0].subject).toBe('One step from your first job');
    });

    it('asks for a first customer when the workshop is empty', async () => {
      const { io, sent } = fakeIO({
        recipients: [recip()], ordersByUid: { u1: [] }, customersByUid: { u1: false },
      });
      await runDailyDigest(io, NOW);
      expect(sent[0].subject).toBe('Add your first customer');
    });

    // Regression: the CTA was briefly a hardcoded store URL chosen from the user's
    // FCM token platform. 33 of 152 accounts have no token, so those defaulted to
    // Google Play — sending iPhone owners to an Android store. The App Link opens
    // the app on both platforms and the hosted /r page handles the not-installed
    // case per platform, so no store URL belongs in an email at all.
    it('uses the platform-neutral App Link, never a store URL', async () => {
      const { io, sent } = fakeIO({ recipients: [recip({ platform: 'ios' })], ordersByUid: { u1: [] } });
      await runDailyDigest(io, NOW);
      expect(sent[0].html).toContain('https://link.getstitchpad.com/r');
      expect(sent[0].html).not.toContain('play.google.com');
      expect(sent[0].html).not.toContain('apps.apple.com');
    });

    it('sends the same App Link regardless of platform', async () => {
      const ios = fakeIO({ recipients: [recip({ platform: 'ios' })], ordersByUid: { u1: [] } });
      const android = fakeIO({ recipients: [recip({ platform: 'android' })], ordersByUid: { u1: [] } });
      const unknown = fakeIO({ recipients: [recip({ platform: null })], ordersByUid: { u1: [] } });
      await runDailyDigest(ios.io, NOW);
      await runDailyDigest(android.io, NOW);
      await runDailyDigest(unknown.io, NOW);
      for (const f of [ios, android, unknown]) {
        expect(f.sent[0].html).toContain('https://link.getstitchpad.com/r');
        expect(f.sent[0].html).not.toContain('play.google.com');
      }
    });

    it('attaches RFC 8058 one-click unsubscribe headers', async () => {
      const { io, sent } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
      await runDailyDigest(io, NOW);
      expect(sent[0].headers).toEqual({
        'List-Unsubscribe': '<https://unsub.test/e?u=u1&t=abc>',
        'List-Unsubscribe-Post': 'List-Unsubscribe=One-Click',
      });
    });
  });

  describe('email suppression', () => {
    it('never emails someone who used the unsubscribe link', async () => {
      const { io, sent } = fakeIO({
        recipients: [recip({ emailOptOut: true })],
        ordersByUid: { u1: [order({ deadline: NOW - DAY })] },
      });
      const r = await runDailyDigest(io, NOW);
      expect(sent).toHaveLength(0);
      expect(r.skippedOptedOut).toBe(1);
    });

    it('never re-sends to an address that already hard-bounced', async () => {
      const { io, sent } = fakeIO({
        recipients: [recip({ hardBounce: true })],
        ordersByUid: { u1: [order({ deadline: NOW - DAY })] },
      });
      const r = await runDailyDigest(io, NOW);
      expect(sent).toHaveLength(0);
      expect(r.skippedBounced).toBe(1);
    });

    it('suppresses an address permanently on a 4xx from Resend', async () => {
      const { io, bounced } = fakeIO({
        recipients: [recip()], ordersByUid: { u1: [] },
        emailError: new ResendError('bad address', 422),
      });
      const r = await runDailyDigest(io, NOW);
      expect(bounced).toEqual(['u1']);
      expect(r.failed).toBe(1);
    });

    // A transient outage must stay retryable — suppressing on 5xx would silently
    // delete recipients from the list every time Resend has a bad minute.
    it('does NOT suppress on a transient 5xx', async () => {
      const { io, bounced } = fakeIO({
        recipients: [recip()], ordersByUid: { u1: [] },
        emailError: new ResendError('upstream down', 503),
      });
      await runDailyDigest(io, NOW);
      expect(bounced).toEqual([]);
    });

    it('still stamps the inbox and push for an opted-out recipient', async () => {
      const { io, notified } = fakeIO({
        recipients: [recip({ emailOptOut: true })],
        ordersByUid: { u1: [order({ deadline: NOW - DAY })] },
      });
      await runDailyDigest(io, NOW);
      expect(notified.u1).toBe(1);
    });
  });
});

describe('runDailyDigest — push', () => {
  const overdueOrder: OrderScanDoc = {
    id: 'o1', customerName: 'Folake', status: 'IN_PROGRESS',
    deadline: 0, archivedAt: null, totalPrice: 1000, payments: [],
    items: [{ garmentType: 'Asoebi' }],
  };
  const recipient = (over: Partial<DigestRecipient> = {}): DigestRecipient =>
    ({ uid: 'u1', email: 'a@b.com', emailVerified: true, name: 'Shop', digestEnabled: true, pushEnabled: true, emailOptOut: false, hardBounce: false, platform: null, unsubscribeUrl: 'https://unsub.test/e?u=u1&t=abc', ...over });

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

describe('runDailyDigest — staff digests', () => {
  const NOW = Date.parse('2026-06-03T06:00:00Z');
  const DAY_MS = 86_400_000;

  it('pushes a staff member a digest of their own assigned work', async () => {
    const { io, pushes } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    const r = await runDailyDigest(io, NOW);
    expect(r.staffPushed).toBe(1);
    expect(pushes.some((p) => p.tokens.includes('gabby-tok'))).toBe(true);
  });

  // Regression: listRecipients used to drop an owner whose email was unverified,
  // which silenced every staff push in that workshop as collateral. Verification
  // now gates the owner's EMAIL only.
  it('still pushes staff when the OWNER email is unverified', async () => {
    const { io, pushes, sent } = fakeIO({
      recipients: [recip({ emailVerified: false })],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    const r = await runDailyDigest(io, NOW);
    expect(r.staffPushed).toBe(1);
    expect(pushes.some((p) => p.tokens.includes('gabby-tok'))).toBe(true);
    // The owner's own push still goes out too — it does not need a reachable address.
    expect(pushes.some((p) => p.tokens.includes('owner-tok'))).toBe(true);
    // ...but the email is suppressed, and counted as such.
    expect(sent).toHaveLength(0);
    expect(r.skippedUnverified).toBe(1);
    expect(r.sent).toBe(0);
  });

  it('does not push a staff member who has nothing assigned', async () => {
    const { io, pushes } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: null })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    const r = await runDailyDigest(io, NOW);
    expect(r.staffPushed).toBe(0);
    expect(pushes.some((p) => p.tokens.includes('gabby-tok'))).toBe(false);
  });

  it('honours a staff member own push opt-out', async () => {
    const { io, pushes } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      staffPushDisabled: ['gabby'],
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    const r = await runDailyDigest(io, NOW);
    expect(r.staffPushed).toBe(0);
    expect(pushes.some((p) => p.tokens.includes('gabby-tok'))).toBe(false);
  });

  // The owner's digest is the more important of the two and must be insulated.
  it('still sends the owner digest when the staff step throws', async () => {
    const base = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
    });
    const io = { ...base.io, listStaffUids: async () => { throw new Error('roster exploded'); } };
    const r = await runDailyDigest(io, NOW);
    expect(r.sent).toBe(1);
    expect(r.staffPushed).toBe(0);
    expect(r.failed).toBe(0);
  });

  it('does not stack on a staff member who already got a push today', async () => {
    const { io } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    await runDailyDigest(io, NOW);
    const second = await runDailyDigest(io, NOW);
    expect(second.staffPushed).toBe(0);
  });
});

describe('runDailyDigest — staff gating and routing (regressions)', () => {
  const NOW = Date.parse('2026-06-03T06:00:00Z');
  const DAY_MS = 86_400_000;

  // The staff block runs BEFORE the owner's gate, so without an explicit check an
  // emergency STAGING flip would stop owner digests while staff pushes carried on.
  it('respects the rollout gate for staff, not just the owner', async () => {
    const { io, pushes } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
      isAllowed: () => false,
    });
    const r = await runDailyDigest(io, NOW);
    expect(r.staffPushed).toBe(0);
    expect(pushes.some((p) => p.tokens.includes('gabby-tok'))).toBe(false);
  });

  // firestore.rules denies staff the money surface, so 'to_collect' dead-ends on a
  // blank screen — and aims the money screen at people who must never see it.
  it('routes a staff push to the dashboard, never the money screen', async () => {
    const { io, pushes } = fakeIO({
      recipients: [recip()],
      ordersByUid: { u1: [order({ deadline: NOW - DAY_MS, assignedMemberId: 'gabby' })] },
      staffByOwner: { u1: ['gabby'] },
      tokensByUid: { u1: ['owner-tok'], gabby: ['gabby-tok'] },
    });
    await runDailyDigest(io, NOW);
    const staffPush = pushes.find((p) => p.tokens.includes('gabby-tok'));
    expect(staffPush?.target).toBe('dashboard');
  });
});
