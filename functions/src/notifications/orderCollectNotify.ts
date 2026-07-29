import { balanceRemaining, summariseGarments } from './digestDetector';

const COLLECTIBLE = new Set(['READY', 'DELIVERED']);

export interface CollectNotification {
  customerName: string;
  garmentSummary: string;
  amount: number;               // rounded naira
  status: 'READY' | 'DELIVERED';
}

/** First entry into a collectible (READY/DELIVERED) state with a balance owing → notify. */
export function collectibleTransition(before: unknown, after: any): CollectNotification | null {
  const beforeStatus = (before as { status?: string })?.status ?? '';
  if (COLLECTIBLE.has(beforeStatus)) return null;      // already collectible → not a first entry
  if (!COLLECTIBLE.has(after?.status)) return null;
  const bal = balanceRemaining(after);
  if (bal <= 0) return null;
  return {
    customerName: after.customerName ?? '',
    garmentSummary: summariseGarments(after.items ?? []),
    amount: Math.round(bal),
    status: after.status,
  };
}

export function collectPushCopy(n: CollectNotification): { title: string; body: string } {
  const state = n.status === 'READY' ? 'ready' : 'delivered';
  const firstName = (n.customerName.trim().split(/\s+/)[0]) || n.customerName;
  return {
    title: 'StitchPad',
    body: `${firstName}'s ${n.garmentSummary} is ${state} — ₦${n.amount.toLocaleString('en-NG')} to collect`,
  };
}
