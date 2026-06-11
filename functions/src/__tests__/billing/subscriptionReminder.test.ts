import {
  buildUpgradeDeepLink,
  PAY_DEEP_LINK,
  ReminderRecipient,
  runSubscriptionReminder,
  SubscriptionReminderIO,
} from '../../billing/subscriptionReminder';
import { buildRenewalReminderEmail } from '../../billing/subscriptionReminderTemplate';

const DAY = 24 * 60 * 60 * 1000;
const NOW = Date.parse('2026-06-09T00:00:00Z');

class FakeIO implements SubscriptionReminderIO {
  recipients: ReminderRecipient[] = [];
  reminded = new Map<string, number>();
  sent: { to: string; subject: string; html: string }[] = [];
  failOn: string | null = null;
  failStampOn: string | null = null;

  async listExpiring(): Promise<ReminderRecipient[]> {
    return this.recipients;
  }
  async getRemindedForEndsAt(uid: string): Promise<number | null> {
    return this.reminded.get(uid) ?? null;
  }
  async setRemindedForEndsAt(uid: string, endsAtMs: number): Promise<void> {
    if (this.failStampOn === uid) throw new Error('stamp boom');
    this.reminded.set(uid, endsAtMs);
  }
  async sendEmail(p: { to: string; subject: string; html: string; text: string }): Promise<void> {
    if (this.failOn === p.to) throw new Error('resend boom');
    this.sent.push({ to: p.to, subject: p.subject, html: p.html });
  }
}

function recipient(over: Partial<ReminderRecipient> = {}): ReminderRecipient {
  return {
    uid: 'uid-1',
    email: 'ada@example.com',
    name: 'Ada',
    tier: 'pro',
    cadence: 'monthly',
    subscriptionEndsAt: new Date(NOW + 2 * DAY),
    ...over,
  };
}

describe('runSubscriptionReminder', () => {
  it('emails an eligible, not-yet-reminded user and stamps the period', async () => {
    const io = new FakeIO();
    io.recipients = [recipient()];

    const result = await runSubscriptionReminder(io, NOW);

    expect(result).toMatchObject({ considered: 1, sent: 1, skippedAlreadyReminded: 0, failed: 0 });
    expect(io.sent).toHaveLength(1);
    expect(io.sent[0].to).toBe('ada@example.com');
    expect(io.sent[0].subject).toContain('2 days');
    expect(io.reminded.get('uid-1')).toBe(NOW + 2 * DAY);
  });

  it('embeds the renewing tier + cadence in the deep link so the app pre-selects them', async () => {
    const io = new FakeIO();
    io.recipients = [recipient({ tier: 'atelier', cadence: 'annual' })];

    await runSubscriptionReminder(io, NOW);

    expect(io.sent[0].html).toContain('stitchpad://upgrade?tier=atelier&amp;cadence=annual');
  });

  it('skips a user already reminded for the same period', async () => {
    const io = new FakeIO();
    io.recipients = [recipient()];
    io.reminded.set('uid-1', NOW + 2 * DAY); // already reminded for this exact end date

    const result = await runSubscriptionReminder(io, NOW);

    expect(result).toMatchObject({ sent: 0, skippedAlreadyReminded: 1 });
    expect(io.sent).toHaveLength(0);
  });

  it('re-arms and re-sends once the user renews to a new end date', async () => {
    const io = new FakeIO();
    io.recipients = [recipient({ subscriptionEndsAt: new Date(NOW + 1 * DAY) })];
    io.reminded.set('uid-1', NOW - 27 * DAY); // reminded for the PREVIOUS period

    const result = await runSubscriptionReminder(io, NOW);

    expect(result).toMatchObject({ sent: 1, skippedAlreadyReminded: 0 });
    expect(io.reminded.get('uid-1')).toBe(NOW + 1 * DAY);
  });

  it('does not stamp when stamping fails, so the reminder retries (at-least-once)', async () => {
    const io = new FakeIO();
    io.failStampOn = 'uid-1';
    io.recipients = [recipient()];

    const result = await runSubscriptionReminder(io, NOW);

    // The email went out, but the stamp failed → counted as failed, not stamped.
    expect(result).toMatchObject({ sent: 0, failed: 1 });
    expect(io.sent).toHaveLength(1);
    expect(io.reminded.has('uid-1')).toBe(false);
  });

  it('says "1 day" when less than a full day remains', async () => {
    const io = new FakeIO();
    io.recipients = [recipient({ subscriptionEndsAt: new Date(NOW + 3 * 60 * 60 * 1000) })]; // 3 hours

    await runSubscriptionReminder(io, NOW);

    expect(io.sent[0].subject).toContain('1 day');
  });

  it('isolates a failed send so other recipients still get reminded', async () => {
    const io = new FakeIO();
    io.failOn = 'bad@example.com';
    io.recipients = [
      recipient({ uid: 'a', email: 'bad@example.com' }),
      recipient({ uid: 'b', email: 'good@example.com' }),
    ];

    const result = await runSubscriptionReminder(io, NOW);

    expect(result).toMatchObject({ considered: 2, sent: 1, failed: 1 });
    expect(io.sent.map((s) => s.to)).toEqual(['good@example.com']);
    expect(io.reminded.has('a')).toBe(false); // not stamped when send failed
    expect(io.reminded.get('b')).toBe(NOW + 2 * DAY);
  });
});

describe('buildUpgradeDeepLink', () => {
  it('carries tier + cadence as query params on the upgrade scheme', () => {
    expect(buildUpgradeDeepLink('pro', 'monthly')).toBe('stitchpad://upgrade?tier=pro&cadence=monthly');
    expect(buildUpgradeDeepLink('atelier', 'annual')).toBe('stitchpad://upgrade?tier=atelier&cadence=annual');
  });
});

describe('buildRenewalReminderEmail', () => {
  it('deep-links the CTA to the app Upgrade screen', () => {
    const { html, text } = buildRenewalReminderEmail({
      name: 'Ada', tier: 'pro', daysLeft: 3, renewalDate: new Date('2026-06-12T00:00:00Z'), payUrl: PAY_DEEP_LINK,
    });
    expect(html).toContain('stitchpad://upgrade');
    expect(text).toContain('stitchpad://upgrade');
    expect(html).toContain('Tailor Pro');
  });

  it('uses singular "day" when one day is left', () => {
    const { subject } = buildRenewalReminderEmail({
      tier: 'atelier', daysLeft: 1, renewalDate: new Date('2026-06-10T00:00:00Z'), payUrl: PAY_DEEP_LINK,
    });
    expect(subject).toBe('Your StitchPad Tailor Atelier plan ends in 1 day');
  });

  it('escapes the recipient name', () => {
    const { html } = buildRenewalReminderEmail({
      name: '<script>x</script>', tier: 'pro', daysLeft: 2, renewalDate: new Date('2026-06-11T00:00:00Z'), payUrl: PAY_DEEP_LINK,
    });
    expect(html).not.toContain('<script>x</script>');
    expect(html).toContain('&lt;script&gt;');
  });
});
