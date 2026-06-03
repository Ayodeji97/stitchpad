import { lagosDayIndex } from './lagosTime';
import { DigestItem, DigestModel, OrderScanDoc } from './types';

const CAP = 5;
const MIN_BALANCE = 1; // ignore sub-naira rounding residue from totalPrice - payments

function balanceRemaining(o: OrderScanDoc): number {
  const paid = o.payments.reduce((sum, p) => sum + (p.amount || 0), 0);
  return Math.max(0, o.totalPrice - paid);
}

function summariseGarments(items: OrderScanDoc['items']): string {
  if (!items || items.length === 0) return 'Order';
  const f = items[0];
  const name = (f.customGarmentName?.trim() || f.garmentType?.trim() || f.description?.trim() || 'Garment');
  return items.length > 1 ? `${name} +${items.length - 1} more` : name;
}

export function digestDetector(orders: OrderScanDoc[], now: number): DigestModel {
  const today = lagosDayIndex(now);
  const dueSoon: DigestItem[] = [];
  const overdue: DigestItem[] = [];
  const outstanding: DigestItem[] = [];

  // A single order can land in two buckets at once and that is intentional:
  // e.g. a READY order past its deadline with a balance is both `overdue`
  // (open + past deadline) and `outstanding` (READY/DELIVERED + owed). The
  // tailor needs both signals; the email renders each bucket independently.
  for (const o of orders) {
    const open = o.status !== 'DELIVERED' && o.archivedAt == null;

    if (open && o.deadline != null) {
      const day = lagosDayIndex(o.deadline);
      const item: DigestItem = { customerName: o.customerName, garmentSummary: summariseGarments(o.items), deadline: o.deadline };
      if (day < today) overdue.push(item);
      else if (day === today || day === today + 1) dueSoon.push(item);
    }

    // Outstanding draws from READY (open) and DELIVERED (not open); excludes archived.
    if ((o.status === 'READY' || o.status === 'DELIVERED') && o.archivedAt == null) {
      const bal = balanceRemaining(o);
      if (bal >= MIN_BALANCE) {
        outstanding.push({ customerName: o.customerName, garmentSummary: summariseGarments(o.items), amount: Math.round(bal) });
      }
    }
  }

  overdue.sort((a, b) => (a.deadline! - b.deadline!));  // most overdue first
  dueSoon.sort((a, b) => (a.deadline! - b.deadline!));  // soonest first
  outstanding.sort((a, b) => (b.amount! - a.amount!)); // biggest owed first

  return {
    dueSoon: dueSoon.slice(0, CAP),
    overdue: overdue.slice(0, CAP),
    outstanding: outstanding.slice(0, CAP),
    dueSoonTotal: dueSoon.length,
    overdueTotal: overdue.length,
    outstandingTotal: outstanding.length,
  };
}

export function isDigestEmpty(m: DigestModel): boolean {
  return m.dueSoonTotal === 0 && m.overdueTotal === 0 && m.outstandingTotal === 0;
}
