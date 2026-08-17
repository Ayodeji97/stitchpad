import {
  isEngagementDay,
  liveCampaigns,
  needsOrders,
  selectCampaign,
  SelectionContext,
} from '../../notifications/engagementSelector';
import { EngagementCampaign, EngagementConfig } from '../../notifications/engagementConfig';
import { Segment } from '../../notifications/segmentDetector';

// Lagos weekdays for these instants are pinned in lagosTime.test.ts.
const TUESDAY = Date.parse('2026-08-18T09:00:00Z');
const WEDNESDAY = Date.parse('2026-08-19T09:00:00Z');
const FRIDAY = Date.parse('2026-08-21T09:00:00Z');

const campaign = (over: Partial<EngagementCampaign> = {}): EngagementCampaign => ({
  id: 'c1',
  segment: 'no_customer',
  title: 'T',
  body: 'B',
  target: 'inbox',
  priority: 0,
  startAt: null,
  endAt: null,
  maxSendsPerUser: 0,
  ...over,
});

const config = (over: Partial<EngagementConfig> = {}): EngagementConfig => ({
  enabled: true,
  daysOfWeek: [2, 5],
  minDaysBetween: 3,
  campaigns: [campaign()],
  ...over,
});

const ctx = (over: Partial<SelectionContext> = {}): SelectionContext => ({
  segments: ['no_customer', 'all'],
  sendCount: 0,
  campaignCounts: {},
  ...over,
});

describe('isEngagementDay', () => {
  it('is true on the configured days and false otherwise', () => {
    const c = config();
    expect(isEngagementDay(c, TUESDAY)).toBe(true);
    expect(isEngagementDay(c, FRIDAY)).toBe(true);
    expect(isEngagementDay(c, WEDNESDAY)).toBe(false);
  });

  // 22:00 UTC Monday is still Monday in Lagos; 23:30 UTC Monday is Tuesday.
  // A naive UTC weekday would send a day early here.
  it('uses the Lagos calendar day at the UTC boundary', () => {
    const c = config();
    expect(isEngagementDay(c, Date.parse('2026-08-17T22:00:00Z'))).toBe(false);
    expect(isEngagementDay(c, Date.parse('2026-08-17T23:30:00Z'))).toBe(true);
  });

  it('respects a reconfigured day list', () => {
    expect(isEngagementDay(config({ daysOfWeek: [3] }), WEDNESDAY)).toBe(true);
    expect(isEngagementDay(config({ daysOfWeek: [3] }), TUESDAY)).toBe(false);
  });
});

describe('liveCampaigns', () => {
  it('is empty when the config is disabled, whatever the campaigns say', () => {
    expect(liveCampaigns(config({ enabled: false }), TUESDAY)).toEqual([]);
  });

  it('excludes campaigns outside their window and includes those inside', () => {
    const c = config({
      campaigns: [
        campaign({ id: 'past', endAt: TUESDAY - 1 }),
        campaign({ id: 'future', startAt: TUESDAY + 1 }),
        campaign({ id: 'live', startAt: TUESDAY - 1, endAt: TUESDAY + 1 }),
      ],
    });
    expect(liveCampaigns(c, TUESDAY).map((x) => x.id)).toEqual(['live']);
  });

  it('treats window bounds as inclusive', () => {
    const c = config({ campaigns: [campaign({ startAt: TUESDAY, endAt: TUESDAY })] });
    expect(liveCampaigns(c, TUESDAY)).toHaveLength(1);
  });
});

describe('needsOrders', () => {
  // The cost lever: loading every order per user is the expensive part of the run,
  // and only the `quiet` segment needs it.
  it('is false when no live campaign targets quiet', () => {
    const c = config({
      campaigns: [campaign({ segment: 'no_customer' }), campaign({ id: 'c2', segment: 'all' })],
    });
    expect(needsOrders(c, TUESDAY)).toBe(false);
  });

  it('is true when a live campaign targets quiet', () => {
    const c = config({ campaigns: [campaign({ id: 'q', segment: 'quiet' })] });
    expect(needsOrders(c, TUESDAY)).toBe(true);
  });

  it('is false when the only quiet campaign is outside its window', () => {
    const c = config({
      campaigns: [campaign({ id: 'q', segment: 'quiet', endAt: TUESDAY - 1 })],
    });
    expect(needsOrders(c, TUESDAY)).toBe(false);
  });

  it('is false when the config is disabled', () => {
    const c = config({ enabled: false, campaigns: [campaign({ segment: 'quiet' })] });
    expect(needsOrders(c, TUESDAY)).toBe(false);
  });
});

describe('selectCampaign', () => {
  it('returns null when disabled', () => {
    expect(selectCampaign(config({ enabled: false }), ctx(), TUESDAY)).toBeNull();
  });

  it('returns null when no campaign matches any segment in the chain', () => {
    expect(selectCampaign(config(), ctx({ segments: ['busy_no_team'] }), TUESDAY)).toBeNull();
  });

  it('matches a campaign to the user segment', () => {
    const c = config({
      campaigns: [
        campaign({ id: 'cust', segment: 'no_customer' }),
        campaign({ id: 'team', segment: 'busy_no_team' }),
      ],
    });
    expect(selectCampaign(c, ctx({ segments: ['busy_no_team', 'all'] }), TUESDAY)?.id).toBe('team');
  });

  it('excludes a campaign outside its window', () => {
    const c = config({ campaigns: [campaign({ startAt: TUESDAY + 1 })] });
    expect(selectCampaign(c, ctx(), TUESDAY)).toBeNull();
  });

  describe('rotation', () => {
    const two = config({
      campaigns: [
        campaign({ id: 'a-first', segment: 'all' }),
        campaign({ id: 'b-second', segment: 'all' }),
      ],
    });

    it('alternates deterministically as the send count grows', () => {
      const pick = (sendCount: number) =>
        selectCampaign(two, ctx({ segments: ['all'], sendCount }), TUESDAY)?.id;
      expect(pick(0)).toBe('a-first');
      expect(pick(1)).toBe('b-second');
      expect(pick(2)).toBe('a-first');
      expect(pick(3)).toBe('b-second');
    });

    // Config key order is not stable coming out of Firestore, so rotation sorts by
    // id — otherwise a reordered array would reshuffle everyone's sequence.
    it('is independent of the order campaigns appear in the config', () => {
      const reversed = config({
        campaigns: [
          campaign({ id: 'b-second', segment: 'all' }),
          campaign({ id: 'a-first', segment: 'all' }),
        ],
      });
      expect(selectCampaign(reversed, ctx({ segments: ['all'], sendCount: 0 }), TUESDAY)?.id)
        .toBe('a-first');
    });

    it('survives a corrupt send count without indexing out of bounds', () => {
      expect(selectCampaign(two, ctx({ segments: ['all'], sendCount: -7 }), TUESDAY)).not.toBeNull();
      expect(selectCampaign(two, ctx({ segments: ['all'], sendCount: 2.9 }), TUESDAY)).not.toBeNull();
    });
  });

  describe('priority override', () => {
    it('beats the rotation regardless of send count', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'a-normal', segment: 'all' }),
          campaign({ id: 'z-announcement', segment: 'all', priority: 100 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['all'], sendCount: 0 }), TUESDAY)?.id)
        .toBe('z-announcement');
      expect(selectCampaign(c, ctx({ segments: ['all'], sendCount: 1 }), TUESDAY)?.id)
        .toBe('z-announcement');
    });

    it('picks the highest priority when several compete', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'low', segment: 'all', priority: 5 }),
          campaign({ id: 'high', segment: 'all', priority: 50 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['all'] }), TUESDAY)?.id).toBe('high');
    });

    it('falls back to id order when priorities tie, so the choice is never arbitrary', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'b', segment: 'all', priority: 10 }),
          campaign({ id: 'a', segment: 'all', priority: 10 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['all'] }), TUESDAY)?.id).toBe('a');
    });

    it('is ignored once the prioritised campaign is outside its window', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'a-normal', segment: 'all' }),
          campaign({ id: 'z-expired', segment: 'all', priority: 100, endAt: TUESDAY - 1 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['all'] }), TUESDAY)?.id).toBe('a-normal');
    });
  });

  describe('per-user caps', () => {
    it('excludes a campaign the user has already maxed out', () => {
      const c = config({ campaigns: [campaign({ id: 'capped', maxSendsPerUser: 2 })] });
      expect(selectCampaign(c, ctx({ campaignCounts: { capped: 1 } }), TUESDAY)?.id).toBe('capped');
      expect(selectCampaign(c, ctx({ campaignCounts: { capped: 2 } }), TUESDAY)).toBeNull();
      expect(selectCampaign(c, ctx({ campaignCounts: { capped: 9 } }), TUESDAY)).toBeNull();
    });

    it('treats maxSendsPerUser 0 as unlimited', () => {
      const c = config({ campaigns: [campaign({ id: 'free', maxSendsPerUser: 0 })] });
      expect(selectCampaign(c, ctx({ campaignCounts: { free: 999 } }), TUESDAY)?.id).toBe('free');
    });

    // This is what stops a permanently churned tailor being nudged every Tuesday
    // forever: once each campaign for their segment is spent, they go quiet.
    it('returns null once every campaign for the segment is exhausted', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'a', segment: 'all', maxSendsPerUser: 1 }),
          campaign({ id: 'b', segment: 'all', maxSendsPerUser: 1 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['all'], campaignCounts: { a: 1, b: 1 } }), TUESDAY))
        .toBeNull();
    });

    it('rotates onto the remaining campaign when one is exhausted', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'a-spent', segment: 'all', maxSendsPerUser: 1 }),
          campaign({ id: 'b-left', segment: 'all' }),
        ],
      });
      const picked = selectCampaign(
        c, ctx({ segments: ['all'], sendCount: 0, campaignCounts: { 'a-spent': 1 } }), TUESDAY,
      );
      expect(picked?.id).toBe('b-left');
    });
  });

  // Both of these were REAL bugs when selection matched exactly one segment.
  describe('segment-chain fallback (regressions)', () => {
    // BUG 1: a `segment: "all"` release announcement reached only fully-activated
    // tailors on a busy day — i.e. almost nobody — because exact-match selection
    // never considered `all` for a user sitting in `no_customer`.
    it('an all-segment announcement reaches a tailor whose specific segment is no_customer', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'first-customer', segment: 'no_customer' }),
          campaign({ id: 'release-note', segment: 'all', priority: 100 }),
        ],
      });
      expect(selectCampaign(c, ctx({ segments: ['no_customer', 'all'] }), TUESDAY)?.id)
        .toBe('release-note');
    });

    // BUG 2: once a tailor exhausted their most specific segment's campaigns they
    // went PERMANENTLY silent, even with live campaigns that applied to them. Since
    // most tailors never mint a referral link, that silenced most of the base after
    // two sends.
    it('falls through to a later segment once the specific one is exhausted', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'founding', segment: 'no_referral', maxSendsPerUser: 2 }),
          campaign({ id: 'catchall', segment: 'all' }),
        ],
      });
      const spent = ctx({
        segments: ['no_referral', 'all'],
        sendCount: 2,
        campaignCounts: { founding: 2 },
      });
      expect(selectCampaign(c, spent, TUESDAY)?.id).toBe('catchall');
    });

    it('still prefers the most specific segment while it has copy left', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'founding', segment: 'no_referral', maxSendsPerUser: 2 }),
          campaign({ id: 'catchall', segment: 'all' }),
        ],
      });
      const fresh = ctx({ segments: ['no_referral', 'all'], campaignCounts: { founding: 1 } });
      expect(selectCampaign(c, fresh, TUESDAY)?.id).toBe('founding');
    });

    it('returns null only when every segment in the chain is exhausted', () => {
      const c = config({
        campaigns: [
          campaign({ id: 'founding', segment: 'no_referral', maxSendsPerUser: 1 }),
          campaign({ id: 'catchall', segment: 'all', maxSendsPerUser: 1 }),
        ],
      });
      expect(selectCampaign(c, ctx({
        segments: ['no_referral', 'all'],
        campaignCounts: { founding: 1, catchall: 1 },
      }), TUESDAY)).toBeNull();
    });
  });

  describe('every segment can be targeted', () => {
    it.each<Segment>(['no_customer', 'no_order', 'busy_no_team', 'no_referral', 'quiet', 'all'])(
      'selects a campaign for %s',
      (segment) => {
        const c = config({ campaigns: [campaign({ id: `c-${segment}`, segment })] });
        expect(selectCampaign(c, ctx({ segments: [segment] }), TUESDAY)?.id).toBe(`c-${segment}`);
      },
    );
  });
});
