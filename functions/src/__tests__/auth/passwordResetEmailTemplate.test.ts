import { buildPasswordResetEmail } from '../../auth/passwordResetEmailTemplate';

describe('buildPasswordResetEmail', () => {
  it('embeds the reset link in the button and the personalized greeting', () => {
    const { html, text } = buildPasswordResetEmail({
      displayName: 'Tunde',
      resetLink: 'https://reset.example/abc',
    });
    expect(html).toContain('href="https://reset.example/abc"');
    expect(html).toContain('Reset password');
    expect(html).toContain('Hi Tunde,');
    // Copy-paste fallback for when the button doesn't work.
    expect(html).toContain('Button not working?');
    // Multipart text alternative carries the raw link too.
    expect(text).toContain('https://reset.example/abc');
    expect(text).toContain('Hi Tunde,');
  });

  it('embeds the brand logo, headline, and indigo CTA', () => {
    const { html } = buildPasswordResetEmail({ resetLink: 'https://x/y' });
    expect(html).toContain('stitchpad-email-logo.png');
    expect(html).toContain('alt="StitchPad"');
    expect(html).toContain('Reset your password');
    expect(html).toContain('#1E2B5C'); // indigo CTA fill
  });

  it('falls back to a generic greeting and escapes HTML in the name', () => {
    const withoutName = buildPasswordResetEmail({ resetLink: 'https://x/y' });
    expect(withoutName.html).toContain('Hi there,');

    const malicious = buildPasswordResetEmail({
      displayName: '<script>x</script>',
      resetLink: 'https://x/y',
    });
    expect(malicious.html).not.toContain('<script>x</script>');
    expect(malicious.html).toContain('&lt;script&gt;');
  });
});
