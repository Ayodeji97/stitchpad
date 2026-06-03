import { isDigestAllowed, DIGEST_ALLOWLIST } from '../../notifications/rollout';

describe('isDigestAllowed', () => {
  it('allows allowlisted emails (case-insensitive) during staging', () => {
    const email = DIGEST_ALLOWLIST[0];
    expect(isDigestAllowed('uid', email.toUpperCase())).toBe(true);
  });
  it('blocks non-allowlisted recipients during staging', () => {
    expect(isDigestAllowed('uid', 'stranger@example.com')).toBe(false);
  });
});
