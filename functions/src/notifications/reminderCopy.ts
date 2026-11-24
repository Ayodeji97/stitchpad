/**
 * EN + FR copy for the evening reminder push. Pure.
 *
 * Shape follows pushSummary.ts: the TITLE carries the counts when there is more
 * than one thing to say, the BODY is the single most urgent item. Language comes
 * from the user's `locale` only (serverLanguage), never from country or currency.
 */
import { serverLanguage } from '../localization/language';
import { lagosDayIndex, lagosWeekday } from './lagosTime';
import { formatNotificationMoney } from './moneyDisplay';
import { leadKind, planSize } from './reminderDetector';
import { ReminderItem, ReminderPlan, ReminderPushPayload } from './reminderTypes';

const WEEKDAYS_EN = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const WEEKDAYS_FR = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];

type Lang = 'en' | 'fr';

function who(item: ReminderItem, lang: Lang): string {
  const name = item.customerName.trim();
  if (name) return name;
  return lang === 'fr' ? 'Un client' : 'A customer';
}

function money(item: ReminderItem): string {
  return formatNotificationMoney(item.amount ?? 0, item.currency);
}

/** "since Monday" within the last 6 days, otherwise "for 10 days". */
function since(item: ReminderItem, now: number, lang: Lang): string {
  const days = lagosDayIndex(now) - lagosDayIndex(item.anchor);
  if (days >= 1 && days <= 6) {
    const wd = lagosWeekday(item.anchor);
    return lang === 'fr' ? `depuis ${WEEKDAYS_FR[wd]}` : `since ${WEEKDAYS_EN[wd]}`;
  }
  const n = Math.max(item.daysSince ?? days, days);
  return lang === 'fr' ? `depuis ${n} jours` : `for ${n} days`;
}

function dueBody(items: ReminderItem[], lang: Lang): string {
  const lead = items[0];
  const more = items.length - 1;
  if (lang === 'fr') {
    return more > 0
      ? `${who(lead, lang)} · ${lead.garmentSummary} et ${more} autre${more > 1 ? 's' : ''} : à livrer demain, pas encore au statut Prêt.`
      : `${who(lead, lang)} · ${lead.garmentSummary} : à livrer demain, pas encore au statut Prêt.`;
  }
  return more > 0
    ? `${who(lead, lang)}'s ${lead.garmentSummary} and ${more} more are due tomorrow and not Ready yet.`
    : `${who(lead, lang)}'s ${lead.garmentSummary} is due tomorrow and isn't Ready yet.`;
}

function owedBody(items: ReminderItem[], lang: Lang): string {
  const lead = items[0];
  const more = items.length - 1;
  if (lang === 'fr') {
    const tail = more > 0 ? ` ${more} autre${more > 1 ? 's' : ''} client${more > 1 ? 's' : ''} vous ${more > 1 ? 'doivent' : 'doit'} aussi.` : '';
    return `${who(lead, lang)} vous doit encore ${money(lead)} (${lead.garmentSummary}).${tail}`;
  }
  const tail = more > 0 ? ` ${more} more customer${more > 1 ? 's' : ''} owe${more > 1 ? '' : 's'} you too.` : '';
  return `${who(lead, lang)} still owes ${money(lead)} for the ${lead.garmentSummary}.${tail}`;
}

function readyBody(items: ReminderItem[], now: number, lang: Lang): string {
  const lead = items[0];
  const more = items.length - 1;
  if (lang === 'fr') {
    const tail = more > 0 ? ` ${more} autre${more > 1 ? 's' : ''} commande${more > 1 ? 's' : ''} attend${more > 1 ? 'ent' : ''} aussi.` : '';
    return `Appelez ${who(lead, lang)} — ${lead.garmentSummary} est au statut Prêt ${since(lead, now, lang)}.${tail}`;
  }
  const tail = more > 0 ? ` ${more} more ${more > 1 ? 'are' : 'is'} waiting too.` : '';
  return `Call ${who(lead, lang)} — the ${lead.garmentSummary} has been ready ${since(lead, now, lang)}.${tail}`;
}

/** Title for a push about ONE kind only. */
function singleKindTitle(plan: ReminderPlan, lang: Lang): string {
  const kind = leadKind(plan);
  const n = planSize(plan);
  if (lang === 'fr') {
    if (kind === 'due') return n > 1 ? `${n} commandes à livrer demain` : 'À livrer demain';
    if (kind === 'owed') return n > 1 ? `${n} paiements en attente` : 'Paiement en attente';
    return n > 1 ? `${n} commandes à récupérer` : 'Prêt à récupérer';
  }
  if (kind === 'due') return n > 1 ? `${n} orders due tomorrow` : 'Due tomorrow';
  if (kind === 'owed') return n > 1 ? `${n} payments still owed` : 'Payment still owed';
  return n > 1 ? `${n} orders ready to collect` : 'Ready for collection';
}

/** "2 due tomorrow · 1 payment owed · 1 ready to collect" — empty kinds omitted. */
function countsTitle(plan: ReminderPlan, lang: Lang): string {
  const parts: string[] = [];
  const d = plan.due.length;
  const o = plan.owed.length;
  const r = plan.ready.length;
  if (lang === 'fr') {
    if (d) parts.push(`${d} à livrer demain`);
    if (o) parts.push(`${o} paiement${o > 1 ? 's' : ''} en attente`);
    if (r) parts.push(`${r} à récupérer`);
  } else {
    if (d) parts.push(`${d} due tomorrow`);
    if (o) parts.push(`${o} payment${o > 1 ? 's' : ''} owed`);
    if (r) parts.push(`${r} ready to collect`);
  }
  return parts.join(' · ');
}

/**
 * The ONE evening push for a non-empty plan. Lead = deadline risk > money > collection.
 *
 * Tap target: a single item opens that order; several open the inbox, where each
 * item has its own entry.
 */
export function reminderPush(plan: ReminderPlan, now: number, locale?: string): ReminderPushPayload {
  const lang = serverLanguage(locale);
  const kind = leadKind(plan);
  if (kind === null) throw new Error('reminderPush called with an empty plan');
  const kindsPresent = [plan.due, plan.owed, plan.ready].filter((l) => l.length > 0).length;
  const title = kindsPresent > 1 ? countsTitle(plan, lang) : singleKindTitle(plan, lang);
  const body = kind === 'due' ? dueBody(plan.due, lang)
    : kind === 'owed' ? owedBody(plan.owed, lang)
      : readyBody(plan.ready, now, lang);
  const lead = (kind === 'due' ? plan.due : kind === 'owed' ? plan.owed : plan.ready)[0];
  return planSize(plan) === 1
    ? { title, body, target: 'order', orderId: lead.orderId }
    : { title, body, target: 'inbox' };
}

/** The end-of-day "record today's work" nudge. Opens the dashboard, where new work starts. */
export function nudgePush(locale?: string): ReminderPushPayload {
  return serverLanguage(locale) === 'fr'
    ? { title: 'Des nouveaux travaux aujourd\'hui ?', body: 'Enregistrez-les avant d\'oublier.', target: 'dashboard' }
    : { title: 'Any new jobs today?', body: 'Record them before you forget.', target: 'dashboard' };
}
