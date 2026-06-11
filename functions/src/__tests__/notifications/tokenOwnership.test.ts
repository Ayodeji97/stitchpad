import { dupeTokenRefsToPrune } from '../../notifications/tokenOwnership';

describe('dupeTokenRefsToPrune', () => {
  it('returns every doc not owned by the new owner', () => {
    expect(dupeTokenRefsToPrune('B', [
      { uid: 'A', ref: 'refA' },
      { uid: 'B', ref: 'refB' },
      { uid: 'C', ref: 'refC' },
    ])).toEqual(['refA', 'refC']);
  });

  it('returns empty when only the new owner holds the token', () => {
    expect(dupeTokenRefsToPrune('B', [{ uid: 'B', ref: 'refB' }])).toEqual([]);
  });

  it('returns empty for no docs', () => {
    expect(dupeTokenRefsToPrune('B', [])).toEqual([]);
  });
});
