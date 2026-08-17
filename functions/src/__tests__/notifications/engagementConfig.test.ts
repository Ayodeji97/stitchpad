import {
  parseEngagementConfig,
  DEFAULT_DAYS_OF_WEEK,
  DEFAULT_MIN_DAYS_BETWEEN,
} from '../../notifications/engagementConfig';

const validCampaign = (over: Record<string, unknown> = {}) => ({
  id: 'c1',
  segment: 'no_customer',
  title: 'Start with one customer',
  body: 'Add your first customer.',
  target: 'inbox',
  ...over,
});

describe('parseEngagementConfig — fail safe', () => {
  // The doc is hand-edited in the Firebase console with no review step, so every
  // unusable input must resolve to "send nothing" rather than a guess.
  it.each([
    ['undefined', undefined],
    ['null', null],
    ['a string', 'enabled'],
    ['a number', 7],
    ['an array', [{ id: 'c1' }]],
  ])('returns a disabled config for %s', (_label, raw) => {
    const c = parseEngagementConfig(raw);
    expect(c.enabled).toBe(false);
    expect(c.campaigns).toEqual([]);
  });

  it('an empty object is disabled with defaults', () => {
    const c = parseEngagementConfig({});
    expect(c.enabled).toBe(false);
    expect(c.daysOfWeek).toEqual(DEFAULT_DAYS_OF_WEEK);
    expect(c.minDaysBetween).toBe(DEFAULT_MIN_DAYS_BETWEEN);
  });

  // A truthy-looking typo must not switch on a live send to every user.
  it.each([['the string "true"', 'true'], ['1', 1], ['"yes"', 'yes']])(
    'does not treat %s as enabled',
    (_label, enabled) => {
      expect(parseEngagementConfig({ enabled }).enabled).toBe(false);
    },
  );

  it('enables only on a strict boolean true', () => {
    expect(parseEngagementConfig({ enabled: true }).enabled).toBe(true);
  });
});

describe('parseEngagementConfig — campaign validation', () => {
  it('keeps a fully valid campaign and applies field defaults', () => {
    const c = parseEngagementConfig({ enabled: true, campaigns: [validCampaign()] });
    expect(c.campaigns).toHaveLength(1);
    expect(c.campaigns[0]).toEqual({
      id: 'c1',
      segment: 'no_customer',
      title: 'Start with one customer',
      body: 'Add your first customer.',
      target: 'inbox',
      priority: 0,
      startAt: null,
      endAt: null,
      maxSendsPerUser: 0,
    });
  });

  // The rule that matters most: a typo in one campaign must not silence the rest.
  it('drops only the malformed campaign and keeps its siblings', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [
        validCampaign({ id: 'good1' }),
        validCampaign({ id: 'bad', title: '   ' }),
        validCampaign({ id: 'good2' }),
      ],
    });
    expect(c.campaigns.map((x) => x.id)).toEqual(['good1', 'good2']);
  });

  it.each([
    ['a missing id', { id: undefined }],
    ['a blank id', { id: '  ' }],
    ['a non-string id', { id: 42 }],
    ['an unknown segment', { segment: 'no_such_segment' }],
    ['a missing segment', { segment: undefined }],
    ['an unknown target', { target: 'settings' }],
    ['a missing target', { target: undefined }],
    ['a blank title', { title: '' }],
    ['a blank body', { body: '   ' }],
    ['a non-string body', { body: { text: 'hi' } }],
  ])('drops a campaign with %s', (_label, over) => {
    const c = parseEngagementConfig({ enabled: true, campaigns: [validCampaign(over)] });
    expect(c.campaigns).toEqual([]);
  });

  it('drops a non-object campaign entry', () => {
    const c = parseEngagementConfig({ enabled: true, campaigns: ['oops', null, 5] });
    expect(c.campaigns).toEqual([]);
  });

  it('drops oversized copy rather than truncating mid-sentence', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({ body: 'x'.repeat(501) })],
    });
    expect(c.campaigns).toEqual([]);
  });

  it('trims surrounding whitespace from copy', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({ title: '  Hello  ', body: '  World  ' })],
    });
    expect(c.campaigns[0].title).toBe('Hello');
    expect(c.campaigns[0].body).toBe('World');
  });

  // Two campaigns sharing an id would share one per-user tally and make rotation
  // non-deterministic, so the later duplicate is dropped.
  it('drops a duplicate id, keeping the first', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({ id: 'dup', title: 'First' }), validCampaign({ id: 'dup', title: 'Second' })],
    });
    expect(c.campaigns).toHaveLength(1);
    expect(c.campaigns[0].title).toBe('First');
  });

  // Template variables are validated HERE so a console typo silences one campaign
  // with a logged reason, instead of rendering "Hi {{bussinessName}}" to real users.
  it('accepts every documented template variable', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({
        title: '{{businessName}}, you are on {{points}} points',
        body: '{{customerCount}} customers, {{orderCount}} orders',
      })],
    });
    expect(c.campaigns).toHaveLength(1);
  });

  it.each([
    ['a misspelled variable in the title', { title: 'Hi {{bussinessName}}' }],
    ['a misspelled variable in the body', { body: 'You have {{pointz}}' }],
    ['a wrong-case variable', { title: '{{BusinessName}}' }],
  ])('drops a campaign with %s', (_label, over) => {
    const c = parseEngagementConfig({ enabled: true, campaigns: [validCampaign(over)] });
    expect(c.campaigns).toEqual([]);
  });

  it('keeps valid siblings when one campaign has a bad variable', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [
        validCampaign({ id: 'good', title: 'Hi {{businessName}}' }),
        validCampaign({ id: 'bad', title: 'Hi {{nope}}' }),
      ],
    });
    expect(c.campaigns.map((x) => x.id)).toEqual(['good']);
  });

  it('drops a campaign whose window ends before it starts', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({ startAt: 2_000, endAt: 1_000 })],
    });
    expect(c.campaigns).toEqual([]);
  });

  it('accepts a valid window and non-numeric bounds become unbounded', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [
        validCampaign({ id: 'a', startAt: 1_000, endAt: 2_000 }),
        validCampaign({ id: 'b', startAt: 'soon', endAt: null }),
      ],
    });
    expect(c.campaigns[0].startAt).toBe(1_000);
    expect(c.campaigns[0].endAt).toBe(2_000);
    expect(c.campaigns[1].startAt).toBeNull();
    expect(c.campaigns[1].endAt).toBeNull();
  });

  it('clamps negative and fractional counts', () => {
    const c = parseEngagementConfig({
      enabled: true,
      campaigns: [validCampaign({ priority: -5, maxSendsPerUser: 2.7 })],
    });
    expect(c.campaigns[0].priority).toBe(0);
    expect(c.campaigns[0].maxSendsPerUser).toBe(2);
  });

  it('ignores unknown extra keys so new fields are forward-compatible', () => {
    const c = parseEngagementConfig({
      enabled: true,
      futureFlag: 'whatever',
      campaigns: [validCampaign({ imageUrl: 'https://example.com/a.png' })],
    });
    expect(c.campaigns).toHaveLength(1);
    expect(c.campaigns[0]).not.toHaveProperty('imageUrl');
  });

  it('treats a non-array campaigns field as empty', () => {
    expect(parseEngagementConfig({ enabled: true, campaigns: { c1: {} } }).campaigns).toEqual([]);
  });
});

describe('parseEngagementConfig — cadence fields', () => {
  it('sorts and de-duplicates daysOfWeek', () => {
    expect(parseEngagementConfig({ daysOfWeek: [5, 2, 5, 2] }).daysOfWeek).toEqual([2, 5]);
  });

  it('filters out-of-range and non-integer days', () => {
    expect(parseEngagementConfig({ daysOfWeek: [1, 7, -2, 3.5, 'Tue', 4] }).daysOfWeek).toEqual([1, 4]);
  });

  // An all-junk array resolving to [] would mean "never sends", which is
  // indistinguishable from disabled — fall back to the default instead.
  it('falls back to the default when every day is junk', () => {
    expect(parseEngagementConfig({ daysOfWeek: ['x', 99] }).daysOfWeek).toEqual(DEFAULT_DAYS_OF_WEEK);
  });

  it('falls back when daysOfWeek is absent or not an array', () => {
    expect(parseEngagementConfig({}).daysOfWeek).toEqual(DEFAULT_DAYS_OF_WEEK);
    expect(parseEngagementConfig({ daysOfWeek: 2 }).daysOfWeek).toEqual(DEFAULT_DAYS_OF_WEEK);
  });

  it.each([
    ['absent', undefined],
    ['negative', -1],
    ['non-numeric', 'three'],
  ])('defaults minDaysBetween when %s', (_label, minDaysBetween) => {
    expect(parseEngagementConfig({ minDaysBetween }).minDaysBetween).toBe(DEFAULT_MIN_DAYS_BETWEEN);
  });

  it('accepts an explicit minDaysBetween, including 0', () => {
    expect(parseEngagementConfig({ minDaysBetween: 7 }).minDaysBetween).toBe(7);
    expect(parseEngagementConfig({ minDaysBetween: 0 }).minDaysBetween).toBe(0);
  });
});
