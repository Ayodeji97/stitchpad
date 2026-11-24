// functions/src/notifications/notificationDocs.ts
import { DigestItem, DigestModel } from './types';
import { notificationCurrency } from './moneyDisplay';
import { ReminderItem } from './reminderTypes';

export type NotificationType = 'OVERDUE' | 'DUE_SOON' | 'TO_COLLECT';

export interface NotificationDocData {
  orderId: string;
  type: NotificationType;
  customerName: string;
  garmentSummary: string;
  amount: number | null;   // major units, set for TO_COLLECT
  currency: string;        // immutable order currency; legacy defaults to NGN
  deadline: number | null; // set for OVERDUE / DUE_SOON
  isOverdue: boolean;      // TO_COLLECT: money owed >= 7 days since Ready/Delivered; false for OVERDUE/DUE_SOON
}

export interface NotificationDocSpec {
  id: string;              // `${orderId}__${type}` — deterministic for dedup
  data: NotificationDocData;
}

function toSpec(item: DigestItem, type: NotificationType): NotificationDocSpec {
  return {
    id: `${item.orderId}__${type}`,
    data: {
      orderId: item.orderId,
      type,
      customerName: item.customerName,
      garmentSummary: item.garmentSummary,
      amount: item.amount ?? null,
      currency: notificationCurrency(item.currency),
      deadline: item.deadline ?? null,
      isOverdue: item.isOverdue ?? false,
    },
  };
}

/** Pure: every actionable item across all buckets → a deterministic notification doc spec. */
export function notificationDocsFromModel(model: DigestModel): NotificationDocSpec[] {
  return [
    ...model.overdue.map((i) => toSpec(i, 'OVERDUE')),
    ...model.dueSoon.map((i) => toSpec(i, 'DUE_SOON')),
    ...model.outstanding.map((i) => toSpec(i, 'TO_COLLECT')),
  ];
}

/**
 * Inbox entries for the evening reminder push (reminderTypes.ts). Only the three
 * types every shipped app renders, so this needs no app release.
 *
 * due   → `${orderId}__DUE_SOON`, the SAME id the morning digest uses: it is the
 *         same fact, and create-once means one row, not two.
 * owed  → `${orderId}__TO_COLLECT__owed${round}`
 * ready → `${orderId}__TO_COLLECT__ready${round}`
 *         A fresh unread row per send, so the repeat a week later shows up in the
 *         inbox the push opens. At most two per order by construction.
 */
export function reminderInboxDocs(items: ReminderItem[]): NotificationDocSpec[] {
  return items.map((item) => ({
    id: item.kind === 'due'
      ? `${item.orderId}__DUE_SOON`
      : `${item.orderId}__TO_COLLECT__${item.kind}${item.round}`,
    data: {
      orderId: item.orderId,
      type: item.kind === 'due' ? 'DUE_SOON' : 'TO_COLLECT',
      customerName: item.customerName,
      garmentSummary: item.garmentSummary,
      amount: item.kind === 'due' ? null : item.amount ?? null,
      currency: notificationCurrency(item.currency),
      deadline: item.kind === 'due' ? item.deadline ?? null : null,
      // Same 7-day "overdue to collect" meaning the digest uses.
      isOverdue: item.kind === 'owed' && (item.daysSince ?? 0) >= 7,
    },
  }));
}
