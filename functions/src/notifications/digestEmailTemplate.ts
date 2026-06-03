/**
 * Pure HTML+text builder for the daily digest. Inline styles only (email clients
 * strip <style>). Adire Atelier palette mirrors verificationEmailTemplate.ts;
 * duplicated here intentionally so the two email types stay independent.
 */
import { DigestItem, DigestModel } from './types';

const INDIGO = '#2C3E7C';
const WHITE = '#FFFFFF';
const INK = '#252320';
const MUTED = '#57534C';
const FAINT = '#A8A49D';
const BORDER = '#E5E3DF';
const FONT_STACK = '\'Plus Jakarta Sans\',-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif';
const SERIF_STACK = 'Georgia,\'Times New Roman\',serif';
const LOGO_URL = 'https://firebasestorage.googleapis.com/v0/b/stitchpad-30607.firebasestorage.app/o/stitchpad-email-logo.png?alt=media&token=d05c88f4-d9c4-4085-a0a8-a136e0c9d8b3'; // gitleaks:allow

function escapeHtml(v: string): string {
  return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function naira(amount: number): string {
  return `₦${Math.round(amount).toLocaleString('en-NG')}`;
}

export function buildDigestEmail(model: DigestModel, tailorName: string): { subject: string; html: string; text: string } {
  const name = tailorName?.trim() ? tailorName.trim() : 'there';

  const subjectParts: string[] = [];
  if (model.overdueTotal > 0) subjectParts.push(`${model.overdueTotal} overdue`);
  if (model.dueSoonTotal > 0) subjectParts.push(`${model.dueSoonTotal} due soon`);
  if (model.outstandingTotal > 0) subjectParts.push(`${model.outstandingTotal} to collect`);
  const subject = `StitchPad: ${subjectParts.join(', ')}`;

  const sections: { title: string; items: DigestItem[]; total: number; line: (i: DigestItem) => string }[] = [
    { title: 'Overdue', items: model.overdue, total: model.overdueTotal, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'Due soon', items: model.dueSoon, total: model.dueSoonTotal, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'To collect', items: model.outstanding, total: model.outstandingTotal, line: (i) => `${i.customerName} · ${i.garmentSummary} — ${naira(i.amount || 0)}` },
  ];

  const htmlSections = sections.filter((s) => s.total > 0).map((s) => {
    const rows = s.items.map((i) => `<p style="margin:0 0 6px;font-size:14px;line-height:1.5;color:${MUTED};">${escapeHtml(s.line(i))}</p>`).join('');
    const more = s.total > s.items.length ? `<p style="margin:6px 0 0;font-size:13px;color:${FAINT};">+${s.total - s.items.length} more</p>` : '';
    return `<div style="margin:0 0 24px;"><h2 style="margin:0 0 10px;font-size:13px;font-weight:800;letter-spacing:0.6px;text-transform:uppercase;color:${INDIGO};">${escapeHtml(s.title)} (${s.total})</h2>${rows}${more}</div>`;
  }).join('');

  const html = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8" /><meta name="viewport" content="width=device-width, initial-scale=1.0" /><meta name="color-scheme" content="light only" /></head>
<body style="margin:0;padding:0;background-color:${WHITE};font-family:${FONT_STACK};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:${WHITE};padding:44px 16px;"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background-color:${WHITE};border:1px solid ${BORDER};border-radius:14px;"><tr><td style="padding:36px 44px 40px;">
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 30px;"><tr>
<td style="vertical-align:middle;padding-right:10px;"><img src="${escapeHtml(LOGO_URL)}" width="34" height="34" alt="StitchPad" style="display:block;border:0;width:34px;height:34px;" /></td>
<td style="vertical-align:middle;"><span style="font-size:18px;font-weight:800;color:${INDIGO};letter-spacing:-0.2px;">StitchPad</span></td>
</tr></table>
<h1 style="margin:0 0 18px;font-family:${SERIF_STACK};font-size:26px;font-weight:700;color:${INDIGO};line-height:1.2;">Good morning, ${escapeHtml(name)}</h1>
<p style="margin:0 0 28px;font-size:15px;line-height:1.6;color:${INK};">Here's what needs your attention today.</p>
${htmlSections}
<p style="margin:30px 0 0;font-size:12px;line-height:1.6;color:${FAINT};">You're getting this because daily summaries are on. Turn them off in Settings → Notifications.</p>
</td></tr></table></td></tr></table></body></html>`;

  const textSections = sections.filter((s) => s.total > 0).map((s) => {
    const rows = s.items.map((i) => `  - ${s.line(i)}`).join('\n');
    const more = s.total > s.items.length ? `\n  +${s.total - s.items.length} more` : '';
    return `${s.title} (${s.total}):\n${rows}${more}`;
  }).join('\n\n');
  const text = `Good morning, ${name}\nHere's what needs your attention today.\n\n${textSections}\n\nTurn off daily summaries in Settings → Notifications.`;

  return { subject, html, text };
}
