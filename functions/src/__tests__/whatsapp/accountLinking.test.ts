import { detectAccountIntent, phoneCandidates } from '../../whatsapp/accountLinking';

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
  it('produces the common Nigerian formats from an E.164 WhatsApp id', () => {
    const c = phoneCandidates('2348012345678');
    expect(c).toEqual(expect.arrayContaining(['+2348012345678', '2348012345678', '08012345678', '8012345678']));
  });

  it('normalizes a local 0-prefixed number to the same candidate set', () => {
    expect(phoneCandidates('08012345678')).toEqual(expect.arrayContaining(['+2348012345678', '08012345678']));
  });

  it('normalizes a +234 formatted number too', () => {
    expect(phoneCandidates('+234 801 234 5678')).toEqual(expect.arrayContaining(['2348012345678', '8012345678']));
  });
});
