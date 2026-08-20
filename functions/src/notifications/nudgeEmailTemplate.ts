/**
 * Pure HTML+text builder for the DAILY NUDGE — the email a tailor gets on a morning
 * when nothing is due.
 *
 * Why this exists: until 2026-08-20 `runDailyDigest` hit `if (isDigestEmpty(model))
 * continue;` and sent nothing at all. On the first production morning that silenced
 * 81 of 109 owners — the daily touchpoint was conditioned on the tailor having
 * already done the thing we want them to do, so the people who most needed a nudge
 * were exactly the ones who never heard from us.
 *
 * Inline styles only (email clients strip <style>). The Adire Atelier palette is
 * duplicated from digestEmailTemplate.ts on purpose, matching the convention there:
 * each email type stays independently editable.
 */

const INDIGO = '#2C3E7C';
const WHITE = '#FFFFFF';
const INK = '#252320';
const FAINT = '#A8A49D';
const BORDER = '#E5E3DF';
const FONT_STACK = '\'Plus Jakarta Sans\',-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';
const LOGO_URL = 'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-app-icon.png?alt=media&token=dd7952b1-63a7-4d84-b376-a9b5a8d7184e'; // gitleaks:allow

/**
 * Which nudge a tailor gets, by how far into the product they actually are.
 * `quiet` assumes a working board; asking "did a job come in today?" of someone with
 * no customers at all reads as nonsense, which is why `setup` is separate.
 */
export type NudgeKind = 'quiet' | 'first_order' | 'setup';

export interface NudgeOptions {
  /** Where the CTA points — the platform-neutral App Link that opens the app
   *  directly, falling back to the correct store per platform when it is absent. */
  ctaUrl: string;
  /** One-click unsubscribe. Required by Gmail/Yahoo bulk-sender rules. */
  unsubscribeUrl: string;
}

function escapeHtml(v: string): string {
  return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

const COPY: Record<NudgeKind, { subject: string; intro: string; body: string; cta: string }> = {
  quiet: {
    subject: 'Did any job come in today?',
    intro: 'Your board is clear today.',
    body: 'If work came in, record it now — measurements, deadline, price. StitchPad holds it so nothing slips.',
    cta: 'Record today’s job',
  },
  first_order: {
    subject: 'One step from your first job',
    intro: 'Your customers are saved.',
    body: 'Now add the job one of them brought you. StitchPad tracks the deadline and warns you before it is late, so you never lose a customer to a missed date.',
    cta: 'Record a job',
  },
  setup: {
    subject: 'Add your first customer',
    intro: 'Your workshop is ready.',
    body: 'Save one customer with their measurements. Next time they come, you will not measure again — it is already there.',
    cta: 'Add a customer',
  },
};

export function buildNudgeEmail(
  kind: NudgeKind,
  tailorName: string,
  opts: NudgeOptions,
): { subject: string; html: string; text: string } {
  const name = tailorName?.trim() ? tailorName.trim() : 'there';
  const c = COPY[kind];

  const html = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /><meta name="color-scheme" content="light only" /></head>
<body style="margin:0;padding:0;background-color:${WHITE};font-family:${FONT_STACK};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:${WHITE};padding:44px 16px;"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:${WHITE};border:1px solid ${BORDER};border-radius:14px;"><tr><td style="padding:36px 44px 40px;">
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;"><tr>
<td style="vertical-align:middle;padding-right:10px;"><img src="${escapeHtml(LOGO_URL)}" width="36" height="36" alt="StitchPad" style="display:block;border:0;width:36px;height:36px;border-radius:8px;" /></td>
<td style="vertical-align:middle;"><span style="font-size:18px;font-weight:800;color:${INDIGO};letter-spacing:-0.2px;">StitchPad</span></td>
</tr></table>
<h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:26px;font-weight:700;color:${INDIGO};line-height:1.2;">Good morning, ${escapeHtml(name)}</h1>
<p style="margin:0 0 14px;font-size:15px;line-height:1.6;color:${INK};">${escapeHtml(c.intro)}</p>
<p style="margin:0 0 28px;font-size:15px;line-height:1.6;color:${INK};">${escapeHtml(c.body)}</p>
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 8px;"><tr><td style="background-color:${INDIGO};border-radius:10px;">
<a href="${escapeHtml(opts.ctaUrl)}" style="display:inline-block;padding:13px 26px;font-size:15px;font-weight:700;color:${WHITE};text-decoration:none;">${escapeHtml(c.cta)}</a>
</td></tr></table>
<p style="margin:26px 0 0;font-size:12px;line-height:1.6;color:${FAINT};">You're getting this because daily summaries are on. Turn them off under Settings → Preferences → Daily summary email, or <a href="${escapeHtml(opts.unsubscribeUrl)}" style="color:${FAINT};">unsubscribe</a>.</p>
</td></tr></table></td></tr></table></body></html>`;

  const text = `Good morning, ${name}\n\n${c.intro}\n\n${c.body}\n\n${c.cta}: ${opts.ctaUrl}\n\nTurn off daily summaries under Settings → Preferences → Daily summary email, or unsubscribe: ${opts.unsubscribeUrl}`;

  return { subject: c.subject, html, text };
}
