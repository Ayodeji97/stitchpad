/**
 * Branded HTML for the email-verification message. Kept as a pure function so
 * it can be unit-tested without sending anything. Inline styles only — email
 * clients strip <style> blocks and external CSS.
 */

const BRAND_SAFFRON = '#E8A800';
const INK = '#1A1A1A';
const MUTED = '#6B6B6B';
const SUPPORT_EMAIL = 'support@getstitchpad.com';

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function buildVerificationEmailHtml(params: {
  displayName?: string;
  verifyLink: string;
}): string {
  const name = params.displayName?.trim() ? escapeHtml(params.displayName.trim()) : 'there';
  const link = escapeHtml(params.verifyLink);

  return `<!DOCTYPE html>
<html lang="en">
  <body style="margin:0;padding:0;background-color:#F5F2ED;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#F5F2ED;padding:32px 16px;">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:#FFFFFF;border-radius:16px;overflow:hidden;">
            <tr>
              <td style="background-color:${BRAND_SAFFRON};padding:24px 32px;">
                <span style="font-size:20px;font-weight:800;color:${INK};letter-spacing:-0.3px;">StitchPad</span>
              </td>
            </tr>
            <tr>
              <td style="padding:32px;">
                <h1 style="margin:0 0 16px;font-size:22px;font-weight:800;color:${INK};">Verify your email</h1>
                <p style="margin:0 0 12px;font-size:15px;line-height:1.5;color:${INK};">Hello ${name},</p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.5;color:${INK};">
                  Thanks for signing up for StitchPad. Tap the button below to verify your email address and start managing your tailoring business.
                </p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                  <tr>
                    <td align="center" style="border-radius:12px;background-color:${BRAND_SAFFRON};">
                      <a href="${link}" target="_blank"
                        style="display:inline-block;padding:14px 32px;font-size:16px;font-weight:700;color:${INK};text-decoration:none;border-radius:12px;">
                        Verify Email
                      </a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:${MUTED};">
                  If the button doesn't work, copy and paste this link into your browser:
                </p>
                <p style="margin:0 0 24px;font-size:13px;line-height:1.5;word-break:break-all;">
                  <a href="${link}" target="_blank" style="color:#1769AA;">${link}</a>
                </p>
                <p style="margin:0;font-size:13px;line-height:1.5;color:${MUTED};">
                  If you didn't create a StitchPad account, you can safely ignore this email.
                </p>
              </td>
            </tr>
            <tr>
              <td style="padding:20px 32px;border-top:1px solid #EEEAE3;">
                <p style="margin:0;font-size:12px;line-height:1.5;color:${MUTED};">
                  Need help? Reach us at
                  <a href="mailto:${SUPPORT_EMAIL}" style="color:#1769AA;">${SUPPORT_EMAIL}</a>.<br />
                  &copy; StitchPad
                </p>
              </td>
            </tr>
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}
