import { isDigestAllowed, isDigestTester, DIGEST_ALLOWLIST } from '../../notifications/rollout';

describe('isDigestAllowed', () => {
  it('allows allowlisted emails (case-insensitive) during staging', () => {
    const email = DIGEST_ALLOWLIST[0];
    expect(isDigestAllowed('uid', email.toUpperCase())).toBe(true);
  });
  it('blocks non-allowlisted recipients during staging', () => {
    expect(isDigestAllowed('uid', 'stranger@example.com')).toBe(false);
  });
  it('isDigestTester is true only for allowlisted emails (case-insensitive, STAGING-independent)', () => {
    expect(isDigestTester(DIGEST_ALLOWLIST[0].toUpperCase())).toBe(true);
    expect(isDigestTester('  ' + DIGEST_ALLOWLIST[0] + '  ')).toBe(true);
    expect(isDigestTester('stranger@example.com')).toBe(false);
  });
});
