import {
  detectSegment,
  segmentNeedsDigest,
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

describe('detectSegment', () => {
  it('no_customer when the tailor has never added a customer', () => {
    expect(detectSegment(activated({ customerCount: 0 }))).toBe('no_customer');
  });

  it('no_order when they have customers but no orders', () => {
    expect(detectSegment(activated({ customerCount: 2, orderCount: 0 }))).toBe('no_order');
  });

  it('busy_no_team when order volume is high and there is no team', () => {
    expect(detectSegment(activated({ orderCount: 25, teamCount: 0 }))).toBe('busy_no_team');
  });

  it('no_referral for an active tailor who never pulled their link', () => {
    expect(detectSegment(activated({ hasReferralLink: false }))).toBe('no_referral');
  });

  it('quiet when they are working but nothing is actionable today', () => {
    expect(detectSegment(activated({ digestEmpty: true }))).toBe('quiet');
  });

  it('all when every milestone is met and today has real work', () => {
    expect(detectSegment(activated())).toBe('all');
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
      expect(detectSegment(brandNew)).toBe('no_customer');
    });

    it('no_order outranks no_referral and quiet', () => {
      expect(detectSegment(activated({
        orderCount: 0, hasReferralLink: false, digestEmpty: true,
      }))).toBe('no_order');
    });

    it('busy_no_team outranks no_referral', () => {
      expect(detectSegment(activated({
        orderCount: 40, teamCount: 0, hasReferralLink: false,
      }))).toBe('busy_no_team');
    });

    it('no_referral outranks quiet', () => {
      expect(detectSegment(activated({ hasReferralLink: false, digestEmpty: true })))
        .toBe('no_referral');
    });
  });

  describe('busy_no_team boundary', () => {
    it('does not fire one order below the threshold', () => {
      expect(detectSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD - 1, teamCount: 0,
      }))).not.toBe('busy_no_team');
    });

    it('fires at exactly the threshold', () => {
      expect(detectSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD, teamCount: 0,
      }))).toBe('busy_no_team');
    });

    it('does not fire for a busy shop that already has a team', () => {
      expect(detectSegment(activated({
        orderCount: BUSY_ORDER_THRESHOLD + 50, teamCount: 2,
      }))).not.toBe('busy_no_team');
    });
  });

  describe('defensive against bad counts', () => {
    // .count() should never return a negative, but treating <= 0 as "none" means
    // a corrupt read degrades into the first-step nudge rather than a crash.
    it('treats negative counts as zero', () => {
      expect(detectSegment(activated({ customerCount: -1 }))).toBe('no_customer');
      expect(detectSegment(activated({ orderCount: -3 }))).toBe('no_order');
    });
  });

  describe('tier', () => {
    // The detector must NOT branch on tier — same signals, same segment. Copy is
    // what differs, and that is chosen by the campaign, not here.
    it('does not change the segment', () => {
      const signals = { orderCount: 30, teamCount: 0 };
      expect(detectSegment(activated({ ...signals, tier: 'free' }))).toBe('busy_no_team');
      expect(detectSegment(activated({ ...signals, tier: 'pro' }))).toBe('busy_no_team');
      expect(detectSegment(activated({ ...signals, tier: 'atelier' }))).toBe('busy_no_team');
    });
  });
});

describe('segmentNeedsDigest', () => {
  it('is true only for quiet — the one segment needing a full orders read', () => {
    expect(segmentNeedsDigest('quiet')).toBe(true);
    expect(segmentNeedsDigest('no_customer')).toBe(false);
    expect(segmentNeedsDigest('busy_no_team')).toBe(false);
    expect(segmentNeedsDigest('all')).toBe(false);
  });
});
