import {
  isDigestAllowed,
  isEngagementAllowed,
  isDigestTester,
  DIGEST_ALLOWLIST,
} from '../../notifications/rollout';

describe('isDigestAllowed', () => {
  it('allows allowlisted emails (case-insensitive)', () => {
    const email = DIGEST_ALLOWLIST[0];
    expect(isDigestAllowed('uid', email.toUpperCase())).toBe(true);
  });

  // STAGING was flipped to false on 2026-08-17 — the digest is open to everyone.
  // This asserts the OPEN state on purpose: if someone re-gates the digest, this
  // test fails and forces the change to be deliberate rather than incidental.
  it('allows non-allowlisted recipients now that staging is over', () => {
    expect(isDigestAllowed('uid', 'stranger@example.com')).toBe(true);
  });
});

describe('isEngagementAllowed', () => {
  it('allows allowlisted testers', () => {
    expect(isEngagementAllowed('uid', DIGEST_ALLOWLIST[0].toUpperCase())).toBe(true);
  });

  // The whole point of the second flag: the engagement push stays gated while the
  // digest is open. If this ever returns true for a stranger, ENGAGEMENT_STAGING
  // has been flipped — which should be a conscious, reviewed decision.
  it('blocks non-allowlisted recipients while engagement staging is on', () => {
    expect(isEngagementAllowed('uid', 'stranger@example.com')).toBe(false);
  });

  it('is independent of the digest gate', () => {
    const stranger = 'stranger@example.com';
    expect(isDigestAllowed('uid', stranger)).toBe(true);
    expect(isEngagementAllowed('uid', stranger)).toBe(false);
  });
});

describe('isDigestTester', () => {
  it('is true only for allowlisted emails (case-insensitive, trimmed, STAGING-independent)', () => {
    expect(isDigestTester(DIGEST_ALLOWLIST[0].toUpperCase())).toBe(true);
    expect(isDigestTester('  ' + DIGEST_ALLOWLIST[0] + '  ')).toBe(true);
    expect(isDigestTester('stranger@example.com')).toBe(false);
  });
});
