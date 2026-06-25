/**
 * Branded HTML+text for gift-subscription emails. Pure functions so they can be
 * unit-tested without sending. Inline styles only (email clients strip <style>).
 *
 * Mirrors subscriptionReminderTemplate.ts (Adire Atelier brand). Two emails:
 *   - buildGiftReceivedEmail: the "Gift me" auto-applied path (no action needed).
 *   - buildGiftClaimEmail:    the public path, carrying a claim link + bearer code.
 *
 * The claim link is an https Universal Link / App Link (NOT a custom scheme) because
 * Gmail's iOS app refuses custom-scheme hrefs. Kept in sync with
 * applinks:link.getstitchpad.com and the Android intent-filter (/claim path).
 */

import type { BillingTier, BillingCadence } from './paystackBilling';

// Adire Atelier palette (mirrors DesignTokens.kt).
const INDIGO = '#2C3E7C';
const INDIGO_CTA = '#1E2B5C';
const WHITE = '#FFFFFF';
const INK = '#252320';
const MUTED = '#57534C';
const FAINT = '#A8A49D';
const BORDER = '#E5E3DF';
const LINE = '#F2F1EF';
const CODE_BG = '#F6F4EE';
const SUPPORT_EMAIL = 'support@getstitchpad.com';
const FONT_STACK =
  '-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';
const MONO_STACK = '\'SFMono-Regular\',Consolas,\'Liberation Mono\',Menlo,monospace';
const LOGO_URL =
  'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3'; // gitleaks:allow

const TIER_LABEL: Record<BillingTier, string> = { pro: 'Tailor Pro', atelier: 'Tailor Atelier' };

/** "1 month" / "3 months" / "1 year" / "2 years" for the gifted span. */
function durationLabel(cadence: BillingCadence, quantity: number): string {
  const n = Number.isInteger(quantity) && quantity >= 1 ? quantity : 1;
  const unit = cadence === 'annual' ? 'year' : 'month';
  return `${n} ${unit}${n === 1 ? '' : 's'}`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function shell(innerHtml: string): string {
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
${innerHtml}
                <p style="margin:0;padding-top:22px;border-top:1px solid ${LINE};font-size:12px;line-height:1.6;color:${FAINT};">
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

function gifterPhrase(gifterName?: string): { html: string; text: string } {
  const trimmed = gifterName?.trim();
  if (!trimmed) return { html: 'Someone', text: 'Someone' };
  return { html: escapeHtml(trimmed), text: trimmed };
}

/** "Gift me" auto-applied: the recipient's plan is already live, no action needed. */
export function buildGiftReceivedEmail(params: {
  gifterName?: string;
  tier: BillingTier;
  cadence: BillingCadence;
  quantity?: number;
}): { subject: string; html: string; text: string } {
  const tierLabel = TIER_LABEL[params.tier];
  const duration = durationLabel(params.cadence, params.quantity ?? 1);
  const gifter = gifterPhrase(params.gifterName);
  const subject = `You have been gifted StitchPad ${tierLabel}`;

  const inner = `<h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:28px;font-weight:700;color:${INDIGO};line-height:1.2;">A gift for your workshop</h1>
                <p style="margin:0 0 30px;font-size:15px;line-height:1.6;color:${MUTED};">
                  ${gifter.html} gifted you <strong>${tierLabel}</strong> for <strong>${duration}</strong>. It is already active on your account, so unlimited customers and your Smart tools are unlocked right now. Open StitchPad to start using them.
                </p>`;

  const text = `${gifter.text} gifted you StitchPad ${tierLabel} for ${duration}.

It is already active on your account, so unlimited customers and your Smart tools are unlocked right now. Open StitchPad to start using them.

Need help? ${SUPPORT_EMAIL}

© StitchPad`;

  return { subject, html: shell(inner), text };
}

/** Public gift: recipient must claim with the link or the bearer code in-app. */
export function buildGiftClaimEmail(params: {
  gifterName?: string;
  note?: string;
  code: string;
  claimUrl: string;
  tier: BillingTier;
  cadence: BillingCadence;
  quantity?: number;
}): { subject: string; html: string; text: string } {
  const tierLabel = TIER_LABEL[params.tier];
  const duration = durationLabel(params.cadence, params.quantity ?? 1);
  const gifter = gifterPhrase(params.gifterName);
  const code = escapeHtml(params.code);
  const claimUrl = escapeHtml(params.claimUrl);
  const note = params.note?.trim();
  // Transactional subject (no gifter name / "gift!" marketing) so Gmail keeps it
  // in Primary rather than Promotions. The gifter is still named in the body.
  const subject = 'Your StitchPad gift code';

  const noteBlockHtml = note
    ? `<p style="margin:0 0 24px;padding:14px 16px;background-color:${CODE_BG};border-radius:10px;font-size:14px;line-height:1.6;color:${INK};font-style:italic;">&ldquo;${escapeHtml(note)}&rdquo;</p>`
    : '';

  const inner = `<h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:28px;font-weight:700;color:${INDIGO};line-height:1.2;">You have a gift</h1>
                <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:${MUTED};">
                  ${gifter.html} gifted you <strong>${tierLabel}</strong> for <strong>${duration}</strong> on StitchPad. Claim it to unlock unlimited customers and your Smart tools.
                </p>
                ${noteBlockHtml}
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
                  <tr>
                    <td style="border-radius:10px;background-color:${INDIGO_CTA};">
                      <a href="${claimUrl}" target="_blank"
                        style="display:inline-block;padding:14px 38px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:10px;">
                        Claim your gift
                      </a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 8px;font-size:13px;line-height:1.6;color:${MUTED};">
                  If the button does not open the app, open StitchPad, tap <strong>Redeem a gift</strong> in Settings, and enter this code:
                </p>
                <p style="margin:0 0 30px;padding:12px 16px;background-color:${CODE_BG};border:1px solid ${BORDER};border-radius:10px;font-family:${MONO_STACK};font-size:20px;font-weight:700;letter-spacing:2px;color:${INK};text-align:center;">
                  ${code}
                </p>
                <p style="margin:0 0 24px;font-size:12px;line-height:1.6;color:${FAINT};">
                  New to StitchPad? The link will help you create an account first, then apply your gift. This gift is valid for 12 months.
                </p>`;

  const text = `${gifter.text} gifted you StitchPad ${tierLabel} for ${duration}.
${note ? `\n"${note}"\n` : ''}
Claim your gift: ${params.claimUrl}

If the button does not open the app, open StitchPad, tap "Redeem a gift" in Settings, and enter this code:
${params.code}

New to StitchPad? The link will help you create an account first, then apply your gift. This gift is valid for 12 months.

Need help? ${SUPPORT_EMAIL}

© StitchPad`;

  return { subject, html: shell(inner), text };
}
