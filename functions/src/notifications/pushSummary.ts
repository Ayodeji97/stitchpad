import { DigestItem, DigestModel } from './types';

export interface PushSummary {
  title: string;
  body: string;
}

/** Thousands-separated naira figure, e.g. 18000 -> "18,000". */
function formatNaira(amount: number): string {
  return Math.round(amount).toLocaleString('en-US');
}

/**
 * " — not started yet" when nobody has begun the work.
 *
 * A deadline on an order still sitting in PENDING is the most actionable thing the
 * digest can say, and it used to be indistinguishable from one already underway.
 */
function startedSuffix(item: DigestItem): string {
  return item.notStarted ? ' — not started yet' : '';
}

/**
 * Compact status line for the notification TITLE, e.g. "2 overdue · 1 to collect".
 *
 * The title used to be the literal string "StitchPad", which both platforms already
 * show as the app name in the notification header — so it rendered as
 * "StitchPad · StitchPad · Folake's Asoebi is overdue" and bought nothing. The title
 * is the line that earns the open, so it carries the counts.
 *
 * Empty buckets are omitted rather than printed as "0".
 */
function pushTitle(model: DigestModel): string {
  const parts: string[] = [];
  if (model.overdue.length > 0) parts.push(`${model.overdue.length} overdue`);
  if (model.dueSoon.length > 0) parts.push(`${model.dueSoon.length} due soon`);
  if (model.outstanding.length > 0) parts.push(`${model.outstanding.length} to collect`);
  // The run loop guarantees a non-empty model, but never render a blank title.
  return parts.length > 0 ? parts.join(' · ') : 'StitchPad';
}

/**
 * Pure one-line summary for the daily push. Caller guarantees the model is
 * non-empty (suppress-when-empty happens in the run loop). Lead item priority:
 * overdue -> due-soon -> outstanding (most urgent first).
 *
 * The body is the single most urgent item and nothing else: the totals now live in
 * the title, so the old "+N more need attention" tail would just repeat them.
 */
export function pushSummary(model: DigestModel): PushSummary {

  let lead: string;
  if (model.overdue.length > 0) {
    const o = model.overdue[0];
    lead = `${o.customerName}'s ${o.garmentSummary} is overdue${startedSuffix(o)}`;
  } else if (model.dueSoon.length > 0) {
    const d = model.dueSoon[0];
    lead = `${d.customerName}'s ${d.garmentSummary} is due soon${startedSuffix(d)}`;
  } else {
    // Within outstanding (no overdue/dueSoon items), an overdue-to-collect item
    // outranks a fresh one for the lead line, even if it isn't the biggest amount.
    const s = model.outstanding.find((i) => i.isOverdue) ?? model.outstanding[0];
    lead = `${s.customerName} owes ₦${formatNaira(s.amount ?? 0)}`;
  }

  return { title: pushTitle(model), body: lead };
}
