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

  // ENGAGEMENT_STAGING was flipped to false on 2026-08-20, alongside
  // `config/engagementPush.enabled = true`. Both switches were required: with only
  // one of them moved the nudge reaches nobody outside DIGEST_ALLOWLIST, which is
  // how the feature shipped in 1.3.0 and then sent zero messages for three days.
  // Asserting the OPEN state on purpose, same as the digest test above: re-gating
  // this must fail a test rather than happen quietly.
  it('allows non-allowlisted recipients now that engagement staging is over', () => {
    expect(isEngagementAllowed('uid', 'stranger@example.com')).toBe(true);
  });

  // Kept even though both gates now read the same way: they are independent flags,
  // and either one can be closed alone as a rollback lever.
  it('is independent of the digest gate', () => {
    const stranger = 'stranger@example.com';
    expect(isDigestAllowed('uid', stranger)).toBe(true);
    expect(isEngagementAllowed('uid', stranger)).toBe(true);
  });
});

describe('isDigestTester', () => {
  it('is true only for allowlisted emails (case-insensitive, trimmed, STAGING-independent)', () => {
    expect(isDigestTester(DIGEST_ALLOWLIST[0].toUpperCase())).toBe(true);
    expect(isDigestTester('  ' + DIGEST_ALLOWLIST[0] + '  ')).toBe(true);
    expect(isDigestTester('stranger@example.com')).toBe(false);
  });
});
