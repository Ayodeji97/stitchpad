import { verifyChallenge } from '../../whatsapp/webhook';

describe('verifyChallenge', () => {
  it('returns the challenge when mode is subscribe and the token matches', () => {
    expect(verifyChallenge({ mode: 'subscribe', token: 'secret', challenge: '12345' }, 'secret')).toBe('12345');
  });

  it('returns null when the token does not match', () => {
    expect(verifyChallenge({ mode: 'subscribe', token: 'wrong', challenge: '12345' }, 'secret')).toBeNull();
  });

  it('returns null when the mode is not subscribe', () => {
    expect(verifyChallenge({ mode: 'unsubscribe', token: 'secret', challenge: '12345' }, 'secret')).toBeNull();
  });
});
