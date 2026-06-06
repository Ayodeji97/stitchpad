// functions/src/notifications/notificationDocs.ts
import { DigestItem, DigestModel } from './types';

export type NotificationType = 'OVERDUE' | 'DUE_SOON' | 'TO_COLLECT';

export interface NotificationDocData {
  orderId: string;
  type: NotificationType;
  customerName: string;
  garmentSummary: string;
  amount: number | null;   // set for TO_COLLECT
  deadline: number | null; // set for OVERDUE / DUE_SOON
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
      deadline: item.deadline ?? null,
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
