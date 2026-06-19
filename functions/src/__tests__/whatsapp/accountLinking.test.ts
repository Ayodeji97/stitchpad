import { detectAccountIntent, phoneCandidates, resolveUniqueUid, ACCOUNT_LINK_FIELDS } from '../../whatsapp/accountLinking';

describe('ACCOUNT_LINK_FIELDS', () => {
  it('matches the current `whatsapp` field and legacy `whatsappNumber`, not the voice `phone` line', () => {
    // The app writes the primary WhatsApp number to Firestore `whatsapp`;
    // `whatsappNumber` is the legacy pre-rename slot; `phone` is a separate
    // voice/SMS line and must NOT be treated as a WhatsApp match.
    expect(ACCOUNT_LINK_FIELDS).toContain('whatsapp');
    expect(ACCOUNT_LINK_FIELDS).toContain('whatsappNumber');
    expect(ACCOUNT_LINK_FIELDS).not.toContain('phone');
  });
});

describe('detectAccountIntent', () => {
  it('detects questions about the user own plan/tier', () => {
    expect(detectAccountIntent('what plan am I on?')).toBe('tier');
    expect(detectAccountIntent('am I on Pro')).toBe('tier');
    expect(detectAccountIntent('which tier is my account')).toBe('tier');
    expect(detectAccountIntent('my subscription')).toBe('tier');
  });

  it('returns null for ordinary support questions', () => {
    expect(detectAccountIntent('how do I add a customer?')).toBeNull();
    expect(detectAccountIntent('the app keeps crashing')).toBeNull();
  });
});

describe('phoneCandidates', () => {
  it('produces the common Nigerian formats from a 234 E.164 WhatsApp id', () => {
    const c = phoneCandidates('2348012345678');
    expect(c).toEqual(expect.arrayContaining(['+2348012345678', '2348012345678', '08012345678', '8012345678']));
  });

  it('handles a +234 formatted number too', () => {
    expect(phoneCandidates('+234 801 234 5678')).toEqual(expect.arrayContaining(['2348012345678', '8012345678']));
  });

  it('does NOT reshape a non-Nigerian sender into a +234 number (no cross-national collisions)', () => {
    const c = phoneCandidates('15551234567'); // US E.164
    expect(c).toEqual(expect.arrayContaining(['+15551234567', '15551234567']));
    expect(c.some((x) => x.startsWith('234') || x.startsWith('+234'))).toBe(false);
  });
});

describe('resolveUniqueUid', () => {
  it('returns the uid when exactly one user matches across all fields', () => {
    expect(resolveUniqueUid([['uid-1'], []])).toBe('uid-1');
  });

  it('dedupes the same user matching on both whatsappNumber and phone', () => {
    expect(resolveUniqueUid([['uid-1'], ['uid-1']])).toBe('uid-1');
  });

  it('returns null when two different users match (ambiguous, do not disclose)', () => {
    expect(resolveUniqueUid([['uid-1'], ['uid-2']])).toBeNull();
    expect(resolveUniqueUid([['uid-1', 'uid-2'], []])).toBeNull();
  });

  it('returns null when nothing matches', () => {
    expect(resolveUniqueUid([[], []])).toBeNull();
  });
});
