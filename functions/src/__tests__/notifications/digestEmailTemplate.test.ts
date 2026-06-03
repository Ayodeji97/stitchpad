import { buildDigestEmail } from '../../notifications/digestEmailTemplate';
import { DigestModel } from '../../notifications/types';

function model(p: Partial<DigestModel> = {}): DigestModel {
  return { dueSoon: [], overdue: [], outstanding: [], dueSoonTotal: 0, overdueTotal: 0, outstandingTotal: 0, ...p };
}

describe('buildDigestEmail', () => {
  it('builds a subject ordered overdue → due → balance', () => {
    const { subject } = buildDigestEmail(model({
      overdueTotal: 1, dueSoonTotal: 2, outstandingTotal: 3,
    }), 'Ada Couture');
    expect(subject).toBe('StitchPad: 1 overdue, 2 due soon, 3 to collect');
  });

  it('greets by tailor name and renders only non-empty sections', () => {
    const { html, text } = buildDigestEmail(model({
      overdue: [{ customerName: 'Bola', garmentSummary: 'Agbada', deadline: 0 }],
      overdueTotal: 1,
    }), 'Ada Couture');
    expect(html).toContain('Ada Couture');
    expect(html).toContain('Bola');
    expect(html).not.toContain('Due soon'); // empty section omitted
    expect(text).toContain('Bola');
  });

  it('shows a +N more line when a bucket is capped', () => {
    const { html } = buildDigestEmail(model({
      overdue: Array.from({ length: 5 }, (_, i) => ({ customerName: `c${i}`, garmentSummary: 'x', deadline: 0 })),
      overdueTotal: 8,
    }), 'Ada');
    expect(html).toContain('+3 more');
  });

  it('escapes HTML in customer/garment names (no raw script tags in output)', () => {
    const { html } = buildDigestEmail(model({
      overdue: [{ customerName: '<script>alert(1)</script>', garmentSummary: 'Agbada', deadline: 0 }],
      overdueTotal: 1,
    }), 'Ada');
    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('formats outstanding amounts as naira with thousands separators', () => {
    const { html } = buildDigestEmail(model({
      outstanding: [{ customerName: 'Ada', garmentSummary: 'Buba', amount: 15000 }],
      outstandingTotal: 1,
    }), 'Ada');
    expect(html).toContain('₦15,000');
  });

  it('footer points opt-out to the real toggle location (Settings → Preferences)', () => {
    const { html, text } = buildDigestEmail(model({
      overdue: [{ customerName: 'Bola', garmentSummary: 'Agbada', deadline: 0 }],
      overdueTotal: 1,
    }), 'Ada');
    for (const body of [html, text]) {
      expect(body).toContain('Settings → Preferences → Daily summary email');
      expect(body).not.toContain('Settings → Notifications'); // the toggle is NOT in a Notifications section
    }
  });
});
