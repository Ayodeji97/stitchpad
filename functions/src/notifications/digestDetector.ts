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
  // READY and DELIVERED are tracked separately so READY surfaces first in the digest
  // (the tailor can collect immediately when a garment is ready for pickup).
  const readyItems: DigestItem[] = [];
  const deliveredItems: DigestItem[] = [];

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
        const item: DigestItem = { customerName: o.customerName, garmentSummary: summariseGarments(o.items), amount: Math.round(bal) };
        if (o.status === 'READY') readyItems.push(item);
        else deliveredItems.push(item);
      }
    }
  }

  overdue.sort((a, b) => (a.deadline! - b.deadline!));  // most overdue first
  dueSoon.sort((a, b) => (a.deadline! - b.deadline!));  // soonest first
  // READY before DELIVERED; biggest owed first within each group.
  readyItems.sort((a, b) => (b.amount! - a.amount!));
  deliveredItems.sort((a, b) => (b.amount! - a.amount!));
  const outstanding = [...readyItems, ...deliveredItems];

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
