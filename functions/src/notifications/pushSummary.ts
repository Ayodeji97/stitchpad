import { DigestModel } from './types';

export interface PushSummary {
  title: string;
  body: string;
}

/** Thousands-separated naira figure, e.g. 18000 -> "18,000". */
function formatNaira(amount: number): string {
  return Math.round(amount).toLocaleString('en-US');
}

/**
 * Pure one-line summary for the daily push. Caller guarantees the model is
 * non-empty (suppress-when-empty happens in the run loop). Lead item priority:
 * overdue -> due-soon -> outstanding (most urgent first); the remaining count
 * becomes a "+N more" tail.
 */
export function pushSummary(model: DigestModel): PushSummary {
  const total = model.overdue.length + model.dueSoon.length + model.outstanding.length;

  let lead: string;
  if (model.overdue.length > 0) {
    const o = model.overdue[0];
    lead = `${o.customerName}'s ${o.garmentSummary} is overdue`;
  } else if (model.dueSoon.length > 0) {
    const d = model.dueSoon[0];
    lead = `${d.customerName}'s ${d.garmentSummary} is due soon`;
  } else {
    const s = model.outstanding[0];
    lead = `${s.customerName} owes ₦${formatNaira(s.amount ?? 0)}`;
  }

  const moreCount = total - 1;
  const body = moreCount > 0 ? `${lead} + ${moreCount} more need attention` : lead;
  return { title: 'StitchPad', body };
}
