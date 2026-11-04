import { DigestModel } from '../notifications/types';
import { formatNotificationMoney, notificationCurrency } from '../notifications/moneyDisplay';
import { escapeEmailHtml as e } from './language';

export function frenchDigestEmail(model: DigestModel, name: string, opts: {
  ctaUrl: string; unsubscribeUrl: string;
}): { subject: string; html: string; text: string } {
  const sections: { title: string; rows: string[]; total: number }[] = [
    { title: 'En retard', rows: model.overdue.map(i => `${i.customerName} · ${i.garmentSummary}`), total: model.overdue.length },
    { title: 'À livrer bientôt', rows: model.dueSoon.map(i => `${i.customerName} · ${i.garmentSummary}`), total: model.dueSoon.length },
  ];
  const groups = new Map<string, typeof model.outstanding>();
  for (const item of model.outstanding) {
    const currency = notificationCurrency(item.currency);
    groups.set(currency, [...(groups.get(currency) ?? []), item]);
  }
  for (const [currency, items] of [...groups.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    sections.push({ title: `À encaisser · ${currency}`, total: items.length,
      rows: items.map(i => `${i.customerName} · ${i.garmentSummary} — ${formatNotificationMoney(i.amount ?? 0, currency)}${i.isOverdue ? ' (en retard)' : ''}`) });
  }
  const visible = sections.filter(s => s.total > 0);
  const subject = `StitchPad : ${visible.map(s => `${s.total} ${s.title.toLowerCase()}`).join(', ')}`;
  const greeting = name.trim() ? `Bonjour ${name.trim()},` : 'Bonjour,';
  const intro = 'Voici les points à suivre aujourd’hui dans votre atelier.';
  const htmlSections = visible.map(s => `<h2 style="font-size:16px;color:#2C3E7C">${e(s.title)} (${s.total})</h2>${s.rows.slice(0, 5).map(row => `<p>${e(row)}</p>`).join('')}${s.total > 5 ? `<p>+${s.total - 5} autres</p>` : ''}`).join('');
  const textSections = visible.map(s => `${s.title} (${s.total})\n${s.rows.slice(0, 5).join('\n')}${s.total > 5 ? `\n+${s.total - 5} autres` : ''}`).join('\n\n');
  const footer = 'Désactivez les résumés dans Paramètres → Préférences → Résumé quotidien par e-mail, ou désabonnez-vous.';
  return { subject,
    html: `<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"></head><body style="font-family:Arial,sans-serif;background:#FAF6EC;color:#252320;padding:24px"><main style="max-width:480px;margin:auto;background:#fff;padding:32px"><h1 style="font-family:Georgia,serif;color:#2C3E7C">StitchPad</h1><p>${e(greeting)}</p><p>${intro}</p>${htmlSections}<p><a href="${e(opts.ctaUrl)}">Ouvrir StitchPad</a></p><p>${footer} <a href="${e(opts.unsubscribeUrl)}">Se désabonner</a></p></main></body></html>`,
    text: `${greeting}\n${intro}\n\n${textSections}\n\nOuvrir StitchPad : ${opts.ctaUrl}\n${footer}\n${opts.unsubscribeUrl}`,
  };
}
