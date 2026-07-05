import {
  aggregateRows,
  buildMetricsEmail,
  DailyOnboardingMetricsDoc,
  EmailParams,
  MetricsIO,
  OnboardingRow,
  runOnboardingMetrics,
} from '../../metrics/runOnboardingMetrics';

function fakeIO(rows: OnboardingRow[], overrides: Partial<MetricsIO> = {}) {
  const written: DailyOnboardingMetricsDoc[] = [];
  const emails: EmailParams[] = [];
  const io: MetricsIO = {
    queryOnboardingRows: jest.fn(async () => rows),
    writeDailyMetrics: jest.fn(async (m) => { written.push(m); }),
    sendEmail: jest.fn(async (e) => { emails.push(e); }),
    ...overrides,
  };
  return { io, written, emails };
}

const NOW = 1_770_000_000_000;

describe('aggregateRows', () => {
  it('groups by date and splits sign_up / workshop_setup_completed by platform', () => {
    const rows: OnboardingRow[] = [
      { eventDate: '20260701', platform: 'ANDROID', eventName: 'sign_up', users: 4 },
      { eventDate: '20260701', platform: 'IOS', eventName: 'sign_up', users: 2 },
      { eventDate: '20260701', platform: 'ANDROID', eventName: 'workshop_setup_completed', users: 3 },
      { eventDate: '20260702', platform: 'IOS', eventName: 'sign_up', users: 1 },
    ];

    const days = aggregateRows(rows);

    expect(days).toHaveLength(2);
    const [d1, d2] = days; // sorted ascending by date
    expect(d1.date).toBe('2026-07-01');
    expect(d1.signups).toEqual({ ios: 2, android: 4, other: 0, total: 6 });
    expect(d1.setups).toEqual({ ios: 0, android: 3, other: 0, total: 3 });
    expect(d2.date).toBe('2026-07-02');
    expect(d2.signups).toEqual({ ios: 1, android: 0, other: 0, total: 1 });
  });

  it('buckets unknown/null platforms as other and still counts them in total', () => {
    const days = aggregateRows([
      { eventDate: '20260701', platform: 'WEB', eventName: 'sign_up', users: 2 },
      { eventDate: '20260701', platform: null, eventName: 'sign_up', users: 1 },
    ]);
    expect(days[0].signups).toEqual({ ios: 0, android: 0, other: 3, total: 3 });
  });

  it('ignores unrelated events and malformed dates', () => {
    const days = aggregateRows([
      { eventDate: '20260701', platform: 'IOS', eventName: 'app_open', users: 9 },
      { eventDate: 'bad', platform: 'IOS', eventName: 'sign_up', users: 9 },
      { eventDate: '20260701', platform: 'IOS', eventName: 'sign_up', users: 1 },
    ]);
    expect(days).toHaveLength(1);
    expect(days[0].signups.total).toBe(1);
  });
});

describe('runOnboardingMetrics', () => {
  it('upserts one doc per day stamped with generatedAt and reports counts', async () => {
    const rows: OnboardingRow[] = [
      { eventDate: '20260701', platform: 'ANDROID', eventName: 'sign_up', users: 2 },
      { eventDate: '20260702', platform: 'IOS', eventName: 'sign_up', users: 1 },
    ];
    const { io, written } = fakeIO(rows);

    const result = await runOnboardingMetrics(io, NOW, { windowDays: 7, emailTo: 'ops@x.com' });

    expect(io.queryOnboardingRows).toHaveBeenCalledWith(7);
    expect(written).toHaveLength(2);
    expect(written.every((d) => d.generatedAt === NOW)).toBe(true);
    expect(written.map((d) => d.date)).toEqual(['2026-07-01', '2026-07-02']);
    expect(result).toEqual({ windowDays: 7, rows: 2, days: 2, emailSent: true });
  });

  it('sends the summary email to the configured recipient', async () => {
    const { io, emails } = fakeIO([
      { eventDate: '20260701', platform: 'IOS', eventName: 'sign_up', users: 3 },
    ]);
    await runOnboardingMetrics(io, NOW, { emailTo: 'ops@x.com' });
    expect(emails).toHaveLength(1);
    expect(emails[0].to).toBe('ops@x.com');
    expect(emails[0].subject).toContain('onboarding');
  });

  it('skips email when no recipient is configured', async () => {
    const { io, emails } = fakeIO([
      { eventDate: '20260701', platform: 'IOS', eventName: 'sign_up', users: 3 },
    ]);
    const result = await runOnboardingMetrics(io, NOW, {});
    expect(emails).toHaveLength(0);
    expect(result.emailSent).toBe(false);
  });

  it('still persists metrics when the email send fails (best-effort email)', async () => {
    const { io, written } = fakeIO(
      [{ eventDate: '20260701', platform: 'IOS', eventName: 'sign_up', users: 3 }],
      { sendEmail: jest.fn(async () => { throw new Error('resend down'); }) },
    );
    const result = await runOnboardingMetrics(io, NOW, { emailTo: 'ops@x.com' });
    expect(written).toHaveLength(1);
    expect(result.emailSent).toBe(false);
  });
});

describe('buildMetricsEmail', () => {
  it('lists days newest-first with a window total and includes platform splits', () => {
    const email = buildMetricsEmail(
      aggregateRows([
        { eventDate: '20260701', platform: 'ANDROID', eventName: 'sign_up', users: 2 },
        { eventDate: '20260703', platform: 'IOS', eventName: 'sign_up', users: 5 },
      ]),
      7,
    );
    // newest date appears before the older one in the body
    expect(email.text.indexOf('2026-07-03')).toBeLessThan(email.text.indexOf('2026-07-01'));
    expect(email.text).toContain('7 sign-ups');
    expect(email.html).toContain('Android');
  });
});
