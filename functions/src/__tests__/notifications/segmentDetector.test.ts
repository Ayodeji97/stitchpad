import {
  activationSegment,
  needsDigestForSegments,
  segmentChain,
  BUSY_ORDER_THRESHOLD,
  UserSignals,
} from '../../notifications/segmentDetector';

/** A fully-activated tailor: every rung satisfied, so the fallback applies. */
const activated = (over: Partial<UserSignals> = {}): UserSignals => ({
  customerCount: 5,
  orderCount: 3,
  teamCount: 1,
  hasReferralLink: true,
  digestEmpty: false,
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
