import { serverLanguage } from '../../localization/language';
import { buildVerificationEmailHtml } from '../../auth/verificationEmailTemplate';
import { buildPasswordResetEmail } from '../../auth/passwordResetEmailTemplate';
import { buildDigestEmail } from '../../notifications/digestEmailTemplate';
import { buildNudgeEmail } from '../../notifications/nudgeEmailTemplate';
import { pushSummary } from '../../notifications/pushSummary';
import { buildSystemPrompt, buildUserPrompt } from '../../smart/promptBuilder';
import { sendPasswordResetEmailHandler } from '../../auth/sendPasswordResetEmail';

const options = { ctaUrl: 'https://example.com/open?a=1&b=2', unsubscribeUrl: 'https://example.com/unsubscribe?x=1&y=2', locale: 'fr-SN' };
const model = { overdue: [], dueSoon: [], outstanding: [
  { orderId: 'gh', customerName: '<Amina>', garmentSummary: 'Boubou', amount: 12.34, currency: 'GHS' },
  { orderId: 'sn', customerName: 'Saliou', garmentSummary: 'Boubou', amount: 3000, currency: 'XOF' },
] };

describe('French messages', () => {
  it.each(['fr', 'fr-SN', ' FR_cd '])('normalises French %s independently of country', locale => {
    expect(serverLanguage(locale)).toBe('fr');
  });
  it.each([undefined, null, 2, {}, '', 'francais', 'en', 'fr-'])('keeps legacy English for unsupported input %p', locale => {
    expect(serverLanguage(locale)).toBe('en');
  });
  it('escapes auth names and action links in French HTML', () => {
    const html = buildVerificationEmailHtml({ locale: 'fr', displayName: '<script>x</script>', verifyLink: options.ctaUrl });
    expect(html).toContain('lang="fr"');
    expect(html).toContain('&lt;script&gt;');
    expect(html).not.toContain('<script>');
    expect(html).toContain('a=1&amp;b=2');
    expect(html).toContain('Confirmer mon adresse');
  });
  it('provides matching French password reset text and HTML', () => {
    const result = buildPasswordResetEmail({ locale: 'fr', resetLink: options.ctaUrl });
    expect(result.html).toContain('lang="fr"');
    expect(result.text).toContain('Réinitialiser mon mot de passe');
    expect(result.text).toContain(options.ctaUrl);
  });
  it('keeps mixed currency digest rows separate and escapes customer input', () => {
    const result = buildDigestEmail(model, '<Tailor>', options);
    expect(result.html).toContain('lang="fr"');
    expect(result.text).toContain('À encaisser · GHS');
    expect(result.text).toContain('À encaisser · XOF');
    expect(result.text).toContain('12.34');
    expect(result.text).not.toContain('₦');
    expect(result.html).toContain('&lt;Amina&gt;');
    expect(result.html).toContain('Se désabonner');
  });
  it.each(['quiet', 'setup', 'first_order'] as const)('localises nudge %s', kind => {
    const result = buildNudgeEmail(kind, 'Amina', options);
    expect(result.html).toContain('lang="fr"');
    expect(result.text).toContain('Bonjour Amina');
    expect(result.text).toContain(options.unsubscribeUrl);
  });
  it('localises a push without changing stamped money', () => {
    const result = pushSummary(model, 'fr');
    expect(result.title).toContain('2 à encaisser');
    expect(result.body).toContain('doit');
    expect(result.body).toContain('12.34');
    expect(result.body).not.toContain('₦');
  });
  it('instructs French drafts while retaining exact monetary context', () => {
    const prompt = buildUserPrompt({ language: 'fr', intentType: 'balance_reminder', context: {
      country: 'SN', currency: 'XOF', customerFirstName: 'Saliou', garmentLabel: 'Boubou',
      depositFormatted: 'F CFA 1,000', balanceFormatted: 'F CFA 3,000', deadlineFormatted: 'vendredi 6 novembre',
    } });
    expect(prompt).toContain('in French');
    expect(prompt).toContain('F CFA 3,000');
    expect(buildSystemPrompt()).toContain('ENTIRE message in natural professional French');
  });
  it('queues French reset preference without checking account existence', async () => {
    const io = { reserveSend: jest.fn(async () => true), releaseSend: jest.fn(), enqueue: jest.fn() };
    await expect(sendPasswordResetEmailHandler({ email: 'person@example.com', locale: 'fr' }, io)).resolves.toEqual({ ok: true });
    expect(io.enqueue).toHaveBeenCalledWith('person@example.com', 'fr');
  });
});
