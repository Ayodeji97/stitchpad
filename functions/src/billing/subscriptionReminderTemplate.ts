/**
 * Branded HTML+text for the prepaid renewal reminder. Pure function so it can be
 * unit-tested without sending. Inline styles only (email clients strip <style>).
 *
 * Mirrors passwordResetEmailTemplate.ts (Adire Atelier brand). Palette duplicated
 * intentionally so each email type stays independent. The CTA deep-links into the
 * app's Upgrade screen so the tailor can re-pay in a couple taps; a multipart
 * text part is included for spam-filter friendliness.
 */

import { BillingTier } from './paystackBilling';

// Adire Atelier palette (mirrors DesignTokens.kt).
const INDIGO = '#2C3E7C';
const INDIGO_CTA = '#1E2B5C';
const WHITE = '#FFFFFF';
const INK = '#252320';
const MUTED = '#57534C';
const FAINT = '#A8A49D';
const BORDER = '#E5E3DF';
const LINE = '#F2F1EF';
const SUPPORT_EMAIL = 'support@getstitchpad.com';
const FONT_STACK =
  '\'Plus Jakarta Sans\',-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';
const LOGO_URL =
  'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3'; // gitleaks:allow

const TIER_LABEL: Record<BillingTier, string> = { pro: 'Tailor Pro', atelier: 'Tailor Atelier' };
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** "9 June 2026" in Africa/Lagos (UTC+1, no DST). */
function formatLagosDate(date: Date): string {
  const lagos = new Date(date.getTime() + 60 * 60 * 1000); // UTC+1
  return `${lagos.getUTCDate()} ${MONTHS[lagos.getUTCMonth()]} ${lagos.getUTCFullYear()}`;
}

export function buildRenewalReminderEmail(params: {
  name?: string;
  tier: BillingTier;
  daysLeft: number;
  renewalDate: Date;
  payUrl: string;
}): { subject: string; html: string; text: string } {
  const name = params.name?.trim() ? escapeHtml(params.name.trim()) : 'there';
  const plainName = params.name?.trim() ? params.name.trim() : 'there';
  const tierLabel = TIER_LABEL[params.tier];
  const dayWord = params.daysLeft === 1 ? 'day' : 'days';
  const when = formatLagosDate(params.renewalDate);
  const payUrl = escapeHtml(params.payUrl);
  const subject = `Your StitchPad ${tierLabel} plan ends in ${params.daysLeft} ${dayWord}`;

  const html = `<!DOCTYPE html>
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
                <h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:28px;font-weight:700;color:${INDIGO};line-height:1.2;">Your plan ends soon</h1>
                <p style="margin:0 0 6px;font-size:15px;line-height:1.6;color:${INK};">Hi ${name},</p>
                <p style="margin:0 0 30px;font-size:15px;line-height:1.6;color:${MUTED};">
                  Your <strong>${tierLabel}</strong> plan ends in <strong>${params.daysLeft} ${dayWord}</strong>, on ${when}. Renew now to keep unlimited customers and your Smart tools without interruption.
                </p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;">
                  <tr>
                    <td style="border-radius:10px;background-color:${INDIGO_CTA};">
                      <a href="${payUrl}" target="_blank"
                        style="display:inline-block;padding:14px 38px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:10px;">
                        Renew ${tierLabel}
                      </a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 24px;font-size:12px;line-height:1.6;color:${FAINT};">
                  Button not working? Open the StitchPad app and tap Upgrade in Settings.
                </p>
                <p style="margin:0;padding-top:22px;border-top:1px solid ${LINE};font-size:12px;line-height:1.6;color:${FAINT};">
                  If your plan lapses, your data stays safe — you just return to the Free plan until you renew.<br />
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

  const text = `Hi ${plainName},

Your ${tierLabel} plan ends in ${params.daysLeft} ${dayWord}, on ${when}. Renew now to keep unlimited customers and your Smart tools without interruption.

Renew here: ${params.payUrl}
(or open the StitchPad app and tap Upgrade in Settings)

If your plan lapses, your data stays safe — you just return to the Free plan until you renew.
Need help? ${SUPPORT_EMAIL}

© StitchPad`;

  return { subject, html, text };
}
