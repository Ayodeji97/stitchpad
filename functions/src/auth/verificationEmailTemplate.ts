/**
 * Branded HTML for the email-verification message. Kept as a pure function so
 * it can be unit-tested without sending anything. Inline styles only — email
 * clients strip <style> blocks and external CSS.
 *
 * Styling follows the Adire Atelier brand: indigo primary, warm-paper surface,
 * saffron as a single heritage accent. Single light design (the warm-paper card
 * renders fine in dark clients), table layout, max-width ~500px.
 */

// Adire Atelier palette (mirrors DesignTokens.kt).
const INDIGO = '#2C3E7C'; // primary — wordmark, heading, links
const INDIGO_CTA = '#1E2B5C'; // CTA fill
const SAFFRON = '#E8A800'; // heritage accent (single rule)
const PAPER = '#FAF6EC'; // warm-paper page + header band
const CARD = '#FFFFFF';
const INK = '#252320'; // body text
const MUTED = '#57534C'; // secondary text
const BORDER = '#E5E3DF';
const SUPPORT_EMAIL = 'support@getstitchpad.com';
const FONT_STACK =
  "'Plus Jakarta Sans',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

// Real StitchPad logo mark (notebook + measuring-tape), hosted on Firebase
// Storage as a PNG (email clients don't render SVG). 512px source, shown ~36px.
const LOGO_URL =
  'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3';

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
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="color-scheme" content="light only" />
  </head>
  <body style="margin:0;padding:0;background-color:${PAPER};font-family:${FONT_STACK};">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:${PAPER};padding:32px 16px;">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:500px;background-color:${CARD};border:1px solid ${BORDER};border-radius:16px;overflow:hidden;">
            <!-- Header: logo mark + wordmark on warm paper -->
            <tr>
              <td style="background-color:${PAPER};padding:20px 32px;">
                <table role="presentation" cellpadding="0" cellspacing="0">
                  <tr>
                    <td style="vertical-align:middle;padding-right:10px;">
                      <img src="${LOGO_URL}" width="36" height="36" alt="StitchPad"
                        style="display:block;border:0;outline:none;text-decoration:none;width:36px;height:36px;" />
                    </td>
                    <td style="vertical-align:middle;">
                      <span style="font-size:20px;font-weight:800;color:${INDIGO};letter-spacing:-0.3px;">StitchPad</span>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <!-- Saffron heritage rule -->
            <tr>
              <td style="height:3px;line-height:3px;font-size:0;background-color:${SAFFRON};">&nbsp;</td>
            </tr>
            <!-- Body -->
            <tr>
              <td style="padding:32px;">
                <h1 style="margin:0 0 16px;font-size:22px;font-weight:800;color:${INDIGO};">Verify your email</h1>
                <p style="margin:0 0 12px;font-size:15px;line-height:1.5;color:${INK};">Hello ${name},</p>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.5;color:${INK};">
                  Thanks for signing up for StitchPad. Tap the button below to verify your email address and start managing your tailoring business.
                </p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                  <tr>
                    <td align="center" style="border-radius:12px;background-color:${INDIGO_CTA};">
                      <a href="${link}" target="_blank"
                        style="display:inline-block;padding:14px 32px;font-size:16px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:12px;">
                        Verify Email
                      </a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:${MUTED};">
                  If the button doesn't work, copy and paste this link into your browser:
                </p>
                <p style="margin:0 0 24px;font-size:13px;line-height:1.5;word-break:break-all;">
                  <a href="${link}" target="_blank" style="color:${INDIGO};">${link}</a>
                </p>
                <p style="margin:0;font-size:13px;line-height:1.5;color:${MUTED};">
                  If you didn't create a StitchPad account, you can safely ignore this email.
                </p>
              </td>
            </tr>
            <!-- Footer -->
            <tr>
              <td style="padding:20px 32px;border-top:1px solid ${BORDER};">
                <p style="margin:0;font-size:12px;line-height:1.5;color:${MUTED};">
                  Need help? Reach us at
                  <a href="mailto:${SUPPORT_EMAIL}" style="color:${INDIGO};">${SUPPORT_EMAIL}</a>.<br />
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
