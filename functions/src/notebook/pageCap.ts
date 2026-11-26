import * as admin from 'firebase-admin';
import { isPaidSubscriber } from '../notifications/digestAudience';

export const FREE_LIFETIME_PAGE_CAP = 300;
export const PAID_MONTHLY_PAGE_CAP = 1000;

export type CapResult = { ok: true } | { ok: false; reason: 'page_cap_reached' };

interface NotebookUsageDoc {
  lifetimePages: number;
  monthKey: string;
  monthPages: number;
}

const LAGOS_MONTH = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Lagos', year: 'numeric', month: '2-digit' });

/** 'YYYY-MM' of the Africa/Lagos calendar month containing `now`. */
export function lagosMonthKey(now: Date): string {
  const parts = LAGOS_MONTH.formatToParts(now);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${get('year')}-${get('month')}`;
}

/**
 * Atomically reserves one page of scanning quota. Free accounts get a lifetime
 * cap; paid subscribers get a monthly cap (Africa/Lagos calendar month) that resets.
 */
export async function reservePage(
  db: admin.firestore.Firestore,
  uid: string,
  now: Date,
): Promise<CapResult> {
  const userRef = db.doc(`users/${uid}`);
  const usageRef = db.doc(`users/${uid}/usage/notebookImport`);
  const monthKey = lagosMonthKey(now);
  return db.runTransaction(async (tx) => {
    const [userSnap, usageSnap] = await Promise.all([tx.get(userRef), tx.get(usageRef)]);
    const paid = isPaidSubscriber(userSnap.data() as Record<string, unknown> | undefined, now.getTime());
    const existing = usageSnap.exists ? (usageSnap.data() as Partial<NotebookUsageDoc>) : {};
    const lifetimePages = existing.lifetimePages ?? 0;
    const monthPages = existing.monthKey === monthKey ? (existing.monthPages ?? 0) : 0;

    if (paid ? monthPages >= PAID_MONTHLY_PAGE_CAP : lifetimePages >= FREE_LIFETIME_PAGE_CAP) {
      return { ok: false, reason: 'page_cap_reached' } as const;
    }
    const next: NotebookUsageDoc = { lifetimePages: lifetimePages + 1, monthKey, monthPages: monthPages + 1 };
    tx.set(usageRef, next);
    return { ok: true } as const;
  });
}
