import { runEngagementPush } from '../../notifications/runEngagementPush';
import {
  EngagementCounts,
  EngagementIO,
  EngagementRecipient,
  EngagementUserState,
} from '../../notifications/engagementTypes';
import { lagosDateKey, lagosDayIndex } from '../../notifications/lagosTime';
import { OrderScanDoc } from '../../notifications/types';

const TUESDAY = Date.parse('2026-08-18T09:00:00Z');
const WEDNESDAY = Date.parse('2026-08-19T09:00:00Z');
const TODAY_KEY = lagosDateKey(TUESDAY);
const TODAY_INDEX = lagosDayIndex(TUESDAY);

const rawCampaign = (over: Record<string, unknown> = {}) => ({
  id: 'c1',
  segment: 'no_customer',
  title: 'Start with one customer',
  body: 'Add your first customer.',
  target: 'inbox',
  ...over,
});

const rawConfig = (over: Record<string, unknown> = {}) => ({
  enabled: true,
  daysOfWeek: [2, 5],
  minDaysBetween: 3,
  campaigns: [rawCampaign()],
  ...over,
});

const recip = (over: Partial<EngagementRecipient> = {}): EngagementRecipient => ({
  uid: 'u1',
  email: 'u1@x.com',
  announcementsEnabled: true,
  tier: 'free',
  hasReferralLink: false,
  businessName: 'Apeke Couture',
  points: 0,
  welcomeDaysLeft: null,
  ...over,
});

const state = (over: Partial<EngagementUserState> = {}): EngagementUserState => ({
  lastPushDate: null,
  lastSentDayIndex: null,
  sendCount: 0,
  campaignCounts: {},
  ...over,
});

/** A brand-new tailor: zero of everything, so the ladder yields no_customer. */
const counts = (over: Partial<EngagementCounts> = {}): EngagementCounts => ({
  customerCount: 0,
  orderCount: 0,
  teamCount: 0,
  ...over,
});

interface Spy {
  io: EngagementIO;
  pushes: { uid: string; campaignId: string; tokens: string[]; title: string; body: string }[];
  recorded: { uid: string; campaignId: string; dateKey: string; dayIndex: number }[];
  deleted: { uid: string; tokens: string[] }[];
  calls: { listRecipients: number; loadOrders: number; loadCounts: number };
}

function fakeIO(over: {
  config?: unknown;
  recipients?: EngagementRecipient[];
  stateByUid?: Record<string, EngagementUserState>;
  countsByUid?: Record<string, EngagementCounts>;
  ordersByUid?: Record<string, OrderScanDoc[]>;
  tokensByUid?: Record<string, string[]>;
  invalidTokens?: string[];
  pushSuccessCount?: number;
  isAllowed?: (uid: string, email: string) => boolean;
  throwForUid?: string;
} = {}): Spy {
  const pushes: Spy['pushes'] = [];
  const recorded: Spy['recorded'] = [];
  const deleted: Spy['deleted'] = [];
  const calls = { listRecipients: 0, loadOrders: 0, loadCounts: 0 };

  const io: EngagementIO = {
    loadConfig: async () => (over.config === undefined ? rawConfig() : over.config),
    listRecipients: async () => {
      calls.listRecipients++;
      return over.recipients ?? [recip()];
    },
    loadState: async (uid) => {
      if (over.throwForUid === uid) throw new Error('firestore exploded');
      return over.stateByUid?.[uid] ?? state();
    },
    loadCounts: async (uid) => {
      calls.loadCounts++;
      return over.countsByUid?.[uid] ?? counts();
    },
    loadOrders: async (uid) => {
      calls.loadOrders++;
      return over.ordersByUid?.[uid] ?? [];
    },
    loadPushTokens: async (uid) => over.tokensByUid?.[uid] ?? ['tok-1'],
    sendPush: async (tokens, campaign) => {
      pushes.push({
        uid: 'n/a', campaignId: campaign.id, tokens,
        title: campaign.title, body: campaign.body,
      });
      const invalid = over.invalidTokens ?? [];
      const successCount = over.pushSuccessCount !== undefined
        ? over.pushSuccessCount
        : tokens.length - invalid.length;
      return { successCount, invalidTokens: invalid };
    },
    deletePushTokens: async (uid, tokens) => { deleted.push({ uid, tokens }); },
    recordSent: async (uid, campaignId, dateKey, dayIndex) => {
      recorded.push({ uid, campaignId, dateKey, dayIndex });
    },
    isAllowed: over.isAllowed ?? (() => true),
  };

  return { io, pushes, recorded, deleted, calls };
}

describe('runEngagementPush — happy path', () => {
  it('sends the segment-matched campaign and records the send', async () => {
    const s = fakeIO();
    const r = await runEngagementPush(s.io, TUESDAY);

    expect(r.considered).toBe(1);
    expect(r.sent).toBe(1);
    expect(s.pushes).toHaveLength(1);
    expect(s.pushes[0].campaignId).toBe('c1');
    expect(s.recorded).toEqual([
      { uid: 'u1', campaignId: 'c1', dateKey: TODAY_KEY, dayIndex: TODAY_INDEX },
    ]);
  });

  it('picks the campaign matching each user own segment', async () => {
    const s = fakeIO({
      config: rawConfig({
        campaigns: [
          rawCampaign({ id: 'first-customer', segment: 'no_customer' }),
          rawCampaign({ id: 'team', segment: 'busy_no_team' }),
        ],
      }),
      recipients: [recip({ uid: 'newbie' }), recip({ uid: 'busy', hasReferralLink: true })],
      countsByUid: {
        newbie: counts(),
        busy: counts({ customerCount: 30, orderCount: 40, teamCount: 0 }),
      },
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.recorded.map((x) => [x.uid, x.campaignId])).toEqual([
      ['newbie', 'first-customer'],
      ['busy', 'team'],
    ]);
  });
});

describe('runEngagementPush — global short-circuits', () => {
  // A disabled config must cost one config read, not a whole users scan.
  it('never lists recipients when the config is disabled', async () => {
    const s = fakeIO({ config: rawConfig({ enabled: false }) });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.listRecipients).toBe(0);
    expect(r.skippedDisabled).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  // null is what .data() yields for a doc that was never created in the console.
  it('never lists recipients when the config doc is missing entirely', async () => {
    const s = fakeIO({ config: null });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.listRecipients).toBe(0);
    expect(r.skippedDisabled).toBe(1);
  });

  // The second brake against a mis-set cron: the weekday is re-checked here.
  it('sends nothing on a day that is not configured', async () => {
    const s = fakeIO();
    const r = await runEngagementPush(s.io, WEDNESDAY);
    expect(s.calls.listRecipients).toBe(0);
    expect(r.skippedNotEngagementDay).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  // Counted as skippedNoValidCampaigns, NOT skippedNoCampaign: "the config is
  // broken" and "this tailor matched nothing" are different problems and used to
  // share one counter, which made the summary log ambiguous.
  it('sends nothing when every campaign was rejected as malformed', async () => {
    const s = fakeIO({ config: rawConfig({ campaigns: [{ id: 'broken' }] }) });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.listRecipients).toBe(0);
    expect(r.skippedNoValidCampaigns).toBe(1);
    expect(r.skippedNoCampaign).toBe(0);
  });
});

describe('runEngagementPush — per-user gates', () => {
  it('skips a user who opted out of tips and announcements', async () => {
    const s = fakeIO({ recipients: [recip({ announcementsEnabled: false })] });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedOptedOut).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  it('skips a user outside the engagement rollout allowlist', async () => {
    const s = fakeIO({ isAllowed: () => false });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNotAllowed).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  // THE anti-stacking test. The 07:00 digest stamps lastPushDate; this job runs at
  // 10:00. Without this check a busy tailor gets two notifications before lunch.
  it('skips a user who already received a push today', async () => {
    const s = fakeIO({ stateByUid: { u1: state({ lastPushDate: TODAY_KEY }) } });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedPushedToday).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  it('still sends when the last push was on a previous day', async () => {
    const s = fakeIO({ stateByUid: { u1: state({ lastPushDate: '2026-08-01' }) } });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.sent).toBe(1);
  });

  it('skips a user inside the minimum gap between engagement pushes', async () => {
    const s = fakeIO({
      stateByUid: { u1: state({ lastSentDayIndex: TODAY_INDEX - 2 }) }, // gap 2 < 3
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedCadence).toBe(1);
  });

  it('sends once the minimum gap has elapsed', async () => {
    const s = fakeIO({
      stateByUid: { u1: state({ lastSentDayIndex: TODAY_INDEX - 3 }) }, // gap 3 >= 3
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.sent).toBe(1);
  });

  // REGRESSION: staff work inside the OWNER's data, so their own customers/orders
  // subcollections are permanently empty. They looked like brand-new tailors and
  // would have received "Add your first customer" twice a week forever. Production
  // excludes them in listRecipients; this asserts the loop honours an empty list
  // rather than, say, treating zero recipients as an error.
  it('sends nothing when every candidate was excluded upstream', async () => {
    const s = fakeIO({ recipients: [] });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.considered).toBe(0);
    expect(r.sent).toBe(0);
    expect(r.failed).toBe(0);
    expect(s.pushes).toEqual([]);
  });

  it('skips a user with no registered devices', async () => {
    const s = fakeIO({ tokensByUid: { u1: [] } });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNoTokens).toBe(1);
    expect(s.recorded).toEqual([]);
  });

  it('skips a user whose segment has no live campaign', async () => {
    const s = fakeIO({
      config: rawConfig({ campaigns: [rawCampaign({ segment: 'busy_no_team' })] }),
      countsByUid: { u1: counts() }, // brand new -> no_customer
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNoCampaign).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  it('skips a user who has exhausted every campaign for their segment', async () => {
    const s = fakeIO({
      config: rawConfig({ campaigns: [rawCampaign({ id: 'c1', maxSendsPerUser: 2 })] }),
      stateByUid: { u1: state({ campaignCounts: { c1: 2 } }) },
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNoCampaign).toBe(1);
  });
});

describe('runEngagementPush — delivery outcomes', () => {
  it('prunes dead tokens', async () => {
    const s = fakeIO({
      tokensByUid: { u1: ['good', 'dead'] },
      invalidTokens: ['dead'],
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.deleted).toEqual([{ uid: 'u1', tokens: ['dead'] }]);
  });

  // Stamping a failed send would burn the user's cadence window and their
  // per-campaign cap for a push they never received.
  it('records nothing when every send failed', async () => {
    const s = fakeIO({ pushSuccessCount: 0 });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.recorded).toEqual([]);
    expect(r.sent).toBe(0);
    expect(r.failed).toBe(1);
  });

  it('records the send when at least one device succeeded', async () => {
    const s = fakeIO({ tokensByUid: { u1: ['a', 'b'] }, pushSuccessCount: 1 });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.recorded).toHaveLength(1);
    expect(r.sent).toBe(1);
  });

  it('one failing recipient does not abort the run', async () => {
    const s = fakeIO({
      recipients: [recip({ uid: 'boom' }), recip({ uid: 'fine' })],
      throwForUid: 'boom',
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.failed).toBe(1);
    expect(r.sent).toBe(1);
    expect(s.recorded.map((x) => x.uid)).toEqual(['fine']);
  });
});

describe('runEngagementPush — orders read is avoided when possible', () => {
  it('never loads orders when no live campaign targets quiet', async () => {
    const s = fakeIO({
      config: rawConfig({ campaigns: [rawCampaign({ segment: 'no_customer' })] }),
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.loadOrders).toBe(0);
    expect(s.calls.loadCounts).toBe(1);
  });

  it('loads orders when a quiet campaign is live, and resolves the quiet segment', async () => {
    const s = fakeIO({
      config: rawConfig({ campaigns: [rawCampaign({ id: 'quiet-nudge', segment: 'quiet' })] }),
      // Fully activated, so the ladder falls through to quiet on an empty digest.
      recipients: [recip({ hasReferralLink: true })],
      countsByUid: { u1: counts({ customerCount: 4, orderCount: 4, teamCount: 1 }) },
      ordersByUid: { u1: [] }, // empty digest
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.loadOrders).toBe(1);
    expect(r.sent).toBe(1);
    expect(s.recorded[0].campaignId).toBe('quiet-nudge');
  });

  // REGRESSION (cost): wantOrders used to be decided once for the whole run, so a
  // single live quiet campaign scanned every order of every user — including the
  // majority whose segment two count queries had already settled.
  it('does not load orders for a tailor whose activation rung is already unmet', async () => {
    const s = fakeIO({
      config: rawConfig({
        campaigns: [
          rawCampaign({ id: 'quiet-nudge', segment: 'quiet' }),
          rawCampaign({ id: 'first-customer', segment: 'no_customer' }),
        ],
      }),
      countsByUid: { u1: counts() }, // 0 customers -> can never reach `quiet`
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(s.calls.loadOrders).toBe(0);
    expect(r.sent).toBe(1);
    expect(s.recorded[0].campaignId).toBe('first-customer');
  });

  it('does not treat a tailor with actionable orders as quiet', async () => {
    const s = fakeIO({
      config: rawConfig({ campaigns: [rawCampaign({ id: 'quiet-nudge', segment: 'quiet' })] }),
      recipients: [recip({ hasReferralLink: true })],
      countsByUid: { u1: counts({ customerCount: 4, orderCount: 4, teamCount: 1 }) },
      ordersByUid: {
        u1: [{
          id: 'o1',
          customerName: 'Tobi',
          status: 'IN_PROGRESS',
          deadline: TUESDAY - 86_400_000, // overdue -> digest not empty
          archivedAt: null,
          totalPrice: 0,
          payments: [],
          items: [],
        }],
      },
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNoCampaign).toBe(1);
    expect(s.pushes).toEqual([]);
  });
});

describe('runEngagementPush — templating', () => {
  it('fills {{businessName}} and {{points}} from the recipient', async () => {
    const s = fakeIO({
      config: rawConfig({
        campaigns: [rawCampaign({
          id: 'personal',
          segment: 'no_customer',
          title: '{{businessName}}, you are on {{points}} points',
          body: 'You have {{customerCount}} customers and {{orderCount}} orders.',
        })],
      }),
      recipients: [recip({ businessName: 'Apeke Couture', points: 12 })],
      countsByUid: { u1: counts({ customerCount: 0, orderCount: 4 }) },
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.pushes[0].title).toBe('Apeke Couture, you are on 12 points');
    expect(s.pushes[0].body).toBe('You have 0 customers and 4 orders.');
  });

  it('renders 0 points for a tailor who never minted a referral link', async () => {
    const s = fakeIO({
      config: rawConfig({
        campaigns: [rawCampaign({ id: 'p', title: '{{points}} points', body: 'b' })],
      }),
      recipients: [recip({ points: 0 })],
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.pushes[0].title).toBe('0 points');
  });

  // The parser is the guard, so bad variables never reach the send path at all.
  it('drops a campaign whose copy uses an unfillable variable', async () => {
    const s = fakeIO({
      config: rawConfig({
        campaigns: [rawCampaign({ id: 'typo', title: 'Hi {{bussinessName}}', body: 'b' })],
      }),
    });
    const r = await runEngagementPush(s.io, TUESDAY);
    expect(r.skippedNoValidCampaigns).toBe(1);
    expect(s.pushes).toEqual([]);
  });

  it('leaves copy without placeholders untouched', async () => {
    const s = fakeIO();
    await runEngagementPush(s.io, TUESDAY);
    expect(s.pushes[0].title).toBe('Start with one customer');
  });
});

describe('runEngagementPush — dormant', () => {
  const DAY = 86_400_000;
  const activatedCounts = counts({ customerCount: 4, orderCount: 4, teamCount: 1 });
  const dormantConfig = rawConfig({
    campaigns: [
      rawCampaign({ id: 'come-back', segment: 'dormant', title: 'It has been a while', body: 'b' }),
      rawCampaign({ id: 'quiet-nudge', segment: 'quiet', title: 'Nothing due today', body: 'b' }),
    ],
  });
  const orderCreated = (daysAgo: number) => ([{
    id: 'o1', customerName: 'C', status: 'DELIVERED', deadline: null, archivedAt: null,
    totalPrice: 0, payments: [], items: [], createdAt: TUESDAY - daysAgo * DAY,
  }] as OrderScanDoc[]);

  it('sends the dormant nudge when the newest order is old', async () => {
    const s = fakeIO({
      config: dormantConfig,
      recipients: [recip({ hasReferralLink: true })],
      countsByUid: { u1: activatedCounts },
      ordersByUid: { u1: orderCreated(40) },
    });
    await runEngagementPush(s.io, TUESDAY);
    expect(s.recorded[0].campaignId).toBe('come-back');
  });

  // Dormancy is the sharper signal, so it must win over quiet for the same tailor.
  it('prefers dormant over quiet, but falls back to quiet once dormant is spent', async () => {
    const spent = fakeIO({
      config: rawConfig({
        campaigns: [
          rawCampaign({ id: 'come-back', segment: 'dormant', title: 't', body: 'b', maxSendsPerUser: 1 }),
          rawCampaign({ id: 'quiet-nudge', segment: 'quiet', title: 't', body: 'b' }),
        ],
      }),
      recipients: [recip({ hasReferralLink: true })],
      countsByUid: { u1: activatedCounts },
      ordersByUid: { u1: orderCreated(40) },
      stateByUid: { u1: state({ campaignCounts: { 'come-back': 1 } }) },
    });
    await runEngagementPush(spent.io, TUESDAY);
    expect(spent.recorded[0].campaignId).toBe('quiet-nudge');
  });

  it('does not fire for a tailor who logged an order recently', async () => {
    const s = fakeIO({
      config: dormantConfig,
      recipients: [recip({ hasReferralLink: true })],
      countsByUid: { u1: activatedCounts },
      ordersByUid: { u1: orderCreated(2) },
    });
    await runEngagementPush(s.io, TUESDAY);
    // Recent order, and the digest is empty (DELIVERED, nothing owed) -> quiet.
    expect(s.recorded[0].campaignId).toBe('quiet-nudge');
  });
});

describe('runEngagementPush — welcome_ending', () => {
  it('sends the welcome nudge inside the window and skips it outside', async () => {
    const cfg = rawConfig({
      campaigns: [
        rawCampaign({
          id: 'welcome', segment: 'welcome_ending',
          title: 'Your First Month is ending', body: 'b',
        }),
        rawCampaign({ id: 'catchall', segment: 'all', title: 'c', body: 'b' }),
      ],
    });
    const inside = fakeIO({
      config: cfg,
      recipients: [recip({ hasReferralLink: true, welcomeDaysLeft: 2 })],
      countsByUid: { u1: counts({ customerCount: 4, orderCount: 4, teamCount: 1 }) },
    });
    await runEngagementPush(inside.io, TUESDAY);
    expect(inside.recorded[0].campaignId).toBe('welcome');

    const outside = fakeIO({
      config: cfg,
      recipients: [recip({ hasReferralLink: true, welcomeDaysLeft: 20 })],
      countsByUid: { u1: counts({ customerCount: 4, orderCount: 4, teamCount: 1 }) },
    });
    await runEngagementPush(outside.io, TUESDAY);
    expect(outside.recorded[0].campaignId).toBe('catchall');
  });
});
