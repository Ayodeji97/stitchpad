/**
 * Normalises a raw `users/{uid}/orders/{id}` document into {@link OrderScanDoc}.
 *
 * Extracted from dailyDigest so the engagement push shares one definition of
 * "what an order looks like to the detectors". Duplicating it would let the two
 * jobs disagree about the same tailor — one seeing an order as overdue and the
 * other not — which is exactly the class of drift digestDetector's comments warn
 * about.
 *
 * Every field is defensively normalised: legacy docs predate the payments array,
 * the discount field, and statusHistory, and a missing field must not throw
 * inside a loop that runs over every user.
 */
import type { DocumentData } from 'firebase-admin/firestore';
import { OrderScanDoc } from './types';

export function mapOrderScanDoc(id: string, d: DocumentData): OrderScanDoc {
  return {
    id,
    customerName: d.customerName ?? '',
    status: d.status ?? 'PENDING',
    deadline: typeof d.deadline === 'number' ? d.deadline : null,
    archivedAt: typeof d.archivedAt === 'number' ? d.archivedAt : null,
    totalPrice: typeof d.totalPrice === 'number' ? d.totalPrice : 0,
    discount: typeof d.discount === 'number' ? d.discount : 0,
    payments: Array.isArray(d.payments)
      ? d.payments.map((p: DocumentData) => ({ amount: Number(p?.amount) || 0 }))
      : [],
    depositPaid: typeof d.depositPaid === 'number' ? d.depositPaid : 0,
    items: Array.isArray(d.items)
      ? d.items.map((i: DocumentData) => ({
        garmentType: i?.garmentType,
        customGarmentName: i?.customGarmentName,
        description: i?.description,
      }))
      : [],
    statusHistory: Array.isArray(d.statusHistory)
      ? d.statusHistory.map((c: DocumentData) => ({
        status: c?.status ?? '',
        changedAt: Number(c?.changedAt) || 0,
      }))
      : [],
    updatedAt: typeof d.updatedAt === 'number' ? d.updatedAt : undefined,
    createdAt: typeof d.createdAt === 'number' ? d.createdAt : undefined,
  };
}
