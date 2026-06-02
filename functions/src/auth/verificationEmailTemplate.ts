/**
 * Branded HTML for the email-verification message. Kept as a pure function so
 * it can be unit-tested without sending anything. Inline styles only — email
 * clients strip <style> blocks and external CSS.
 *
 * Adire Atelier brand, clean editorial treatment: white surface, indigo
 * primary, a serif headline. Single light design, table layout, max-width
 * ~480px. Short, warm copy.
 */

// Adire Atelier palette (mirrors DesignTokens.kt).
const INDIGO = '#2C3E7C'; // primary — wordmark, serif headline, links
const INDIGO_CTA = '#1E2B5C'; // CTA fill
const WHITE = '#FFFFFF'; // page + card
const INK = '#252320'; // greeting
const MUTED = '#57534C'; // body
const FAINT = '#A8A49D'; // footer
const BORDER = '#E5E3DF'; // card border
const LINE = '#F2F1EF'; // footer divider
const SUPPORT_EMAIL = 'support@getstitchpad.com';
const FONT_STACK =
  '\'Plus Jakarta Sans\',-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';

// Real StitchPad logo mark (notebook + measuring-tape), hosted on Firebase
// Storage as a PNG (email clients don't render SVG). 512px source, shown ~34px.
// The ?token= is a public, read-only Firebase Storage download token for a
// static brand image (it's embedded in every verification email we send) — not
// a sensitive credential, hence the gitleaks:allow.
const LOGO_URL =
  'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3'; // gitleaks:allow

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
  <body style="margin:0;padding:0;background-color:${WHITE};font-family:${FONT_STACK};">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:${WHITE};padding:44px 16px;">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:${WHITE};border:1px solid ${BORDER};border-radius:14px;">
            <tr>
              <td style="padding:36px 44px 40px;">
                <!-- Logo + wordmark -->
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;">
                  <tr>
                    <td style="vertical-align:middle;padding-right:10px;">
                      <img src="${escapeHtml(LOGO_URL)}" width="34" height="34" alt="StitchPad"
                        style="display:block;border:0;outline:none;text-decoration:none;width:34px;height:34px;" />
                    </td>
                    <td style="vertical-align:middle;">
                      <span style="font-size:18px;font-weight:800;color:${INDIGO};letter-spacing:-0.2px;">StitchPad</span>
                    </td>
                  </tr>
                </table>
                <h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:28px;font-weight:700;color:${INDIGO};line-height:1.2;">Welcome to StitchPad</h1>
                <p style="margin:0 0 6px;font-size:15px;line-height:1.6;color:${INK};">Hi ${name},</p>
                <p style="margin:0 0 30px;font-size:15px;line-height:1.6;color:${MUTED};">
                  We're happy you signed up. Kindly confirm your email to start managing your workshop on StitchPad.
                </p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;">
                  <tr>
                    <td style="border-radius:10px;background-color:${INDIGO_CTA};">
                      <a href="${link}" target="_blank"
                        style="display:inline-block;padding:14px 38px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:10px;">
                        Verify email
                      </a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0;padding-top:22px;border-top:1px solid ${LINE};font-size:12px;line-height:1.6;color:${FAINT};">
                  Didn't sign up? You can safely ignore this email.<br />
                  Need help? <a href="mailto:${SUPPORT_EMAIL}" style="color:${INDIGO};text-decoration:none;">${SUPPORT_EMAIL}</a>
                </p>
              </td>
            </tr>
          </table>
          <p style="max-width:480px;margin:18px auto 0;font-size:11px;color:${FAINT};">&copy; StitchPad</p>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}
