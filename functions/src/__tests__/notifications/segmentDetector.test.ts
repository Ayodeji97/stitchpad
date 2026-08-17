import {
  activationSegment,
  needsDigestForSegments,
  segmentChain,
  BUSY_ORDER_THRESHOLD,
  DORMANT_DAYS,
  WELCOME_ENDING_DAYS,
  UserSignals,
} from '../../notifications/segmentDetector';

/** A fully-activated tailor: every rung satisfied, so the fallback applies. */
const activated = (over: Partial<UserSignals> = {}): UserSignals => ({
  customerCount: 5,
  orderCount: 3,
  teamCount: 1,
  hasReferralLink: true,
  digestEmpty: false,
  daysSinceLastOrder: 0,
  welcomeDaysLeft: null,
  deliveredWithoutCosts: 0,
  tier: 'pro',
  ...over,
});

/** The most specific segment in the chain — what the old detectSegment returned. */
const firstSegment = (s: UserSignals) => segmentChain(s)[0];

describe('segmentChain — most specific segment', () => {
  it('no_customer when the tailor has never added a customer', () => {
    expect(firstSegment(activated({ customerCount: 0 }))).toBe('no_customer');
  });

  it('no_order when they have customers but no orders', () => {
    expect(firstSegment(activated({ customerCount: 2, orderCount: 0 }))).toBe('no_order');
  });

  it('busy_no_team when order volume is high and there is no team', () => {
    expect(firstSegment(activated({ orderCount: 25, teamCount: 0 }))).toBe('busy_no_team');
  });

  it('no_referral for an active tailor who never pulled their link', () => {
    expect(firstSegment(activated({ hasReferralLink: false }))).toBe('no_referral');
  });

  it('quiet when they are working but nothing is actionable today', () => {
    expect(firstSegment(activated({ digestEmpty: true }))).toBe('quiet');
  });

  it('all when every milestone is met and today has real work', () => {
    expect(firstSegment(activated())).toBe('all');
  });

  describe('ladder precedence', () => {
    // The core guarantee: a brand-new user matches several conditions at once
    // (no customers AND no team AND no referral link AND an empty digest) and
    // must get the FIRST rung, not a message about staff accounts.
    it('a brand-new user gets no_customer, not busy_no_team or no_referral', () => {
      const brandNew: UserSignals = {
        customerCount: 0,
        orderCount: 0,
        teamCount: 0,
        hasReferralLink: false,
        digestEmpty: true,
        daysSinceLastOrder: null,
        welcomeDaysLeft: 1,
        deliveredWithoutCosts: null,
        tier: 'free',
      };
      expect(firstSegment(brandNew)).toBe('no_customer');
    });

    it('no_order outranks no_referral and quiet', () => {
      expect(firstSegment(activated({
        orderCount: 0, hasReferralLink: false, digestEmpty: true,
      }))).toBe('no_order');
    });

    it('busy_no_team outranks no_referral', () => {
      expect(firstSegment(activated({
        orderCount: 40, teamCount: 0, hasReferralLink: false,
      }))).toBe('busy_no_team');
    });

    // Reversed deliberately: `no_referral` used to outrank `quiet`, but almost no
    // tailor ever mints a referral link, so the referral ask shadowed the quiet-day
    // work nudge for nearly the whole base. A message about the tailor's own work
    // beats a message about our growth.
    it('quiet outranks no_referral', () => {
      expect(firstSegment(activated({ hasReferralLink: false, digestEmpty: true })))
        .toBe('quiet');
    });
  });

  describe('busy_no_team boundary', () => {
    it('does not fire one order below the threshold', () => {
      expect(firstSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD - 1, teamCount: 0,
      }))).not.toBe('busy_no_team');
    });

    it('fires at exactly the threshold', () => {
      expect(firstSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD, teamCount: 0,
      }))).toBe('busy_no_team');
    });

    it('does not fire for a busy shop that already has a team', () => {
      expect(firstSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD + 50, teamCount: 2,
      }))).not.toBe('busy_no_team');
    });
  });

  describe('defensive against bad counts', () => {
    // .count() should never return a negative, but treating <= 0 as "none" means
    // a corrupt read degrades into the first-step nudge rather than a crash.
    it('treats negative counts as zero', () => {
      expect(firstSegment(activated({ customerCount: -1 }))).toBe('no_customer');
      expect(firstSegment(activated({ orderCount: -3 }))).toBe('no_order');
    });
  });

  describe('tier', () => {
    // The detector must NOT branch on tier — same signals, same segment. Copy is
    // what differs, and that is chosen by the campaign, not here.
    it('does not change the segment', () => {
      const signals = { orderCount: 30, teamCount: 0 };
      expect(firstSegment(activated({ ...signals, tier: 'free' }))).toBe('busy_no_team');
      expect(firstSegment(activated({ ...signals, tier: 'pro' }))).toBe('busy_no_team');
      expect(firstSegment(activated({ ...signals, tier: 'atelier' }))).toBe('busy_no_team');
    });
  });
});

describe('activationSegment', () => {
  it('decides the activation rungs from counts alone', () => {
    expect(activationSegment({ customerCount: 0, orderCount: 0, teamCount: 0 })).toBe('no_customer');
    expect(activationSegment({ customerCount: 2, orderCount: 0, teamCount: 0 })).toBe('no_order');
    expect(activationSegment({ customerCount: 2, orderCount: 20, teamCount: 0 })).toBe('busy_no_team');
    expect(activationSegment({ customerCount: 2, orderCount: 3, teamCount: 1 })).toBeNull();
  });
});

describe('needsDigestForSegments — the per-user cost lever', () => {
  // Only a tailor who has cleared every activation rung can land in `quiet`, so
  // everyone else must skip the expensive orders read entirely.
  it('is false while any activation rung is unmet', () => {
    expect(needsDigestForSegments({ customerCount: 0, orderCount: 0, teamCount: 0 })).toBe(false);
    expect(needsDigestForSegments({ customerCount: 5, orderCount: 0, teamCount: 0 })).toBe(false);
    expect(needsDigestForSegments({ customerCount: 5, orderCount: 30, teamCount: 0 })).toBe(false);
  });

  it('is true only once the tailor is fully activated', () => {
    expect(needsDigestForSegments({ customerCount: 5, orderCount: 5, teamCount: 2 })).toBe(true);
  });
});

describe('segmentChain — fallback chain', () => {
  // REGRESSION: with a single segment, a tailor whose only segment ran out of copy
  // went permanently silent. Every chain must end in 'all' so there is always a
  // fallback.
  it('always ends in all', () => {
    expect(segmentChain(activated()).at(-1)).toBe('all');
    expect(segmentChain(activated({ customerCount: 0 })).at(-1)).toBe('all');
    expect(segmentChain(activated({ digestEmpty: true, hasReferralLink: false })).at(-1)).toBe('all');
  });

  it('gives an unactivated tailor only their rung plus all', () => {
    // Notably NOT no_referral: asking someone with zero customers to recruit is
    // exactly the noise the ladder exists to prevent.
    expect(segmentChain(activated({
      customerCount: 0, hasReferralLink: false, digestEmpty: true,
    }))).toEqual(['no_customer', 'all']);
  });

  it('puts quiet ahead of no_referral for an activated tailor', () => {
    // "Nothing due today" is about the tailor's work; "invite a friend" is about ours.
    expect(segmentChain(activated({ digestEmpty: true, hasReferralLink: false })))
      .toEqual(['quiet', 'no_referral', 'all']);
  });

  it('omits quiet on a day with real work', () => {
    expect(segmentChain(activated({ digestEmpty: false, hasReferralLink: false })))
      .toEqual(['no_referral', 'all']);
  });

  it('is just all for a fully-activated tailor with a link and work to do', () => {
    expect(segmentChain(activated({ hasReferralLink: true, digestEmpty: false }))).toEqual(['all']);
  });
});

describe('welcome_ending', () => {
  it('fires inside the warning window', () => {
    expect(segmentChain(activated({ welcomeDaysLeft: WELCOME_ENDING_DAYS })))
      .toContain('welcome_ending');
    expect(segmentChain(activated({ welcomeDaysLeft: 1 }))).toContain('welcome_ending');
  });

  it('does not fire while there is still plenty of window left', () => {
    expect(segmentChain(activated({ welcomeDaysLeft: WELCOME_ENDING_DAYS + 1 })))
      .not.toContain('welcome_ending');
  });

  it('does not fire when the window does not apply', () => {
    expect(segmentChain(activated({ welcomeDaysLeft: null }))).not.toContain('welcome_ending');
  });

  // Time-boxed, so it outranks the segments that will still be true next week.
  it('outranks dormant and quiet', () => {
    const chain = segmentChain(activated({
      welcomeDaysLeft: 2, daysSinceLastOrder: 60, digestEmpty: true,
    }));
    expect(chain[0]).toBe('welcome_ending');
  });

  // A tailor with no customers is not an upsell candidate; the basics come first.
  it('never applies before the activation rungs are cleared', () => {
    expect(segmentChain(activated({ customerCount: 0, welcomeDaysLeft: 1 })))
      .toEqual(['no_customer', 'all']);
  });
});

describe('dormant', () => {
  it('fires at the threshold and beyond', () => {
    expect(segmentChain(activated({ daysSinceLastOrder: DORMANT_DAYS })))
      .toContain('dormant');
    expect(segmentChain(activated({ daysSinceLastOrder: 90 }))).toContain('dormant');
  });

  it('does not fire one day short', () => {
    expect(segmentChain(activated({ daysSinceLastOrder: DORMANT_DAYS - 1 })))
      .not.toContain('dormant');
  });

  it('does not fire when the orders read was skipped', () => {
    expect(segmentChain(activated({ daysSinceLastOrder: null }))).not.toContain('dormant');
  });

  // Dormant is the sharper signal, but quiet stays behind it so a tailor whose
  // dormant copy is spent still has somewhere to fall back to.
  it('outranks quiet, and quiet remains as a fallback', () => {
    const chain = segmentChain(activated({ daysSinceLastOrder: 30, digestEmpty: true }));
    expect(chain.indexOf('dormant')).toBeLessThan(chain.indexOf('quiet'));
  });

  it('never applies before the activation rungs are cleared', () => {
    expect(segmentChain(activated({ orderCount: 0, daysSinceLastOrder: 99 })))
      .toEqual(['no_order', 'all']);
  });
});

describe('no_costs', () => {
  it('fires when delivered work has no cost recorded', () => {
    expect(segmentChain(activated({ deliveredWithoutCosts: 3 }))).toContain('no_costs');
  });

  it('does not fire when every delivered order has costs', () => {
    expect(segmentChain(activated({ deliveredWithoutCosts: 0 }))).not.toContain('no_costs');
  });

  it('does not fire when the orders read was skipped', () => {
    expect(segmentChain(activated({ deliveredWithoutCosts: null }))).not.toContain('no_costs');
  });

  // Getting paid outranks bookkeeping: dormancy and an expiring window are both more
  // urgent than a missing cost figure.
  it('ranks below welcome_ending and dormant', () => {
    const chain = segmentChain(activated({
      welcomeDaysLeft: 2, daysSinceLastOrder: 40, deliveredWithoutCosts: 5,
    }));
    expect(chain.indexOf('welcome_ending')).toBeLessThan(chain.indexOf('no_costs'));
    expect(chain.indexOf('dormant')).toBeLessThan(chain.indexOf('no_costs'));
  });
});
