import { buildGiftReceivedEmail, buildGiftClaimEmail } from '../../billing/giftEmailTemplate';

describe('buildGiftReceivedEmail (gift_me auto-applied)', () => {
  it('names the gifter, tier and duration', () => {
    const { subject, html, text } = buildGiftReceivedEmail({ gifterName: 'Bola', tier: 'pro', cadence: 'monthly' });
    expect(subject).toBe('You have been gifted StitchPad Tailor Pro');
    expect(html).toContain('Bola');
    expect(html).toContain('Tailor Pro');
    expect(html).toContain('1 month');
    expect(text).toContain('Bola gifted you StitchPad Tailor Pro for 1 month');
  });

  it('falls back to "Someone" for an anonymous gifter and labels annual as 1 year', () => {
    const { html } = buildGiftReceivedEmail({ tier: 'atelier', cadence: 'annual' });
    expect(html).toContain('Someone');
    expect(html).toContain('Tailor Atelier');
    expect(html).toContain('1 year');
  });

  it('pluralizes the duration for a multi-period gift', () => {
    expect(buildGiftReceivedEmail({ tier: 'pro', cadence: 'monthly', quantity: 3 }).html).toContain('3 months');
    expect(buildGiftReceivedEmail({ tier: 'atelier', cadence: 'annual', quantity: 2 }).html).toContain('2 years');
  });
});

describe('buildGiftClaimEmail (public claim)', () => {
  it('embeds the code, an https claim link and the optional note', () => {
    const { subject, html, text } = buildGiftClaimEmail({
      gifterName: 'Bola', note: 'For your new shop!', code: 'ABC234', tier: 'pro', cadence: 'annual',
      claimUrl: 'https://link.getstitchpad.com/claim?code=ABC234',
    });
    // Transactional subject (no gifter name / marketing) keeps it out of Gmail Promotions.
    expect(subject).toBe('Your StitchPad gift code');
    expect(html).toContain('ABC234');
    expect(html).toContain('https://link.getstitchpad.com/claim?code=ABC234');
    expect(html).toContain('For your new shop!');
    expect(html).toContain('1 year');
    expect(text).toContain('ABC234');
    // Still names the gifter in the body so the recipient knows who it's from.
    expect(html).toContain('Bola');
  });

  it('tells the recipient how to redeem if the claim link does not open the app', () => {
    const { html, text } = buildGiftClaimEmail({
      code: 'ABC234', tier: 'pro', cadence: 'monthly',
      claimUrl: 'https://link.getstitchpad.com/claim?code=ABC234',
    });
    expect(html).toContain('Settings');
    expect(html).toContain('Redeem a gift');
    expect(text).toContain('Redeem a gift');
  });

  it('escapes HTML in the note to prevent injection', () => {
    const { html } = buildGiftClaimEmail({
      note: '<script>alert(1)</script>', code: 'X', tier: 'pro', cadence: 'monthly',
      claimUrl: 'https://link.getstitchpad.com/claim?code=X',
    });
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('omits the note block when no note is given', () => {
    const { html } = buildGiftClaimEmail({
      code: 'X', tier: 'pro', cadence: 'monthly', claimUrl: 'https://link.getstitchpad.com/claim?code=X',
    });
    expect(html).toContain('You have a gift');
  });
});
