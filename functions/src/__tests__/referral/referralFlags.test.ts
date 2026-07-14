import { hasBlockingFlag } from '../../referral/referralConstants';

// A referral flag is either BLOCKING (withholds the payout) or ADVISORY (surfaced
// for review but still payable). `missing_device_hash` is the only advisory flag:
// a missing/malformed device hash disables device-reuse detection, so we record
// the fact without punishing a referral that may be perfectly legitimate.
describe('hasBlockingFlag', () => {
  it('is false for no flags', () => {
    expect(hasBlockingFlag(undefined)).toBe(false);
    expect(hasBlockingFlag([])).toBe(false);
  });

  it('is true for each blocking flag', () => {
    expect(hasBlockingFlag(['self_referral'])).toBe(true);
    expect(hasBlockingFlag(['device_reuse'])).toBe(true);
    expect(hasBlockingFlag(['velocity'])).toBe(true);
  });

  it('is false for the advisory missing_device_hash alone', () => {
    expect(hasBlockingFlag(['missing_device_hash'])).toBe(false);
  });

  it('is true when a blocking flag accompanies the advisory one', () => {
    expect(hasBlockingFlag(['missing_device_hash', 'device_reuse'])).toBe(true);
  });
});
