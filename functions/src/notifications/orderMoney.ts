/**
 * Reads order money from `users/{uid}/orders/{oid}/private/money`.
 *
 * WHY THIS EXISTS — this fixed a silent, total failure of every money-based
 * notification.
 *
 * Slice 8d-1 moved money off the base order doc: `OrderBaseDto`, the write shape, has
 * no totalPrice, payments, discount or costs. The notification code kept reading those
 * fields from the base doc, so `balanceRemaining()` computed max(0, 0 - 0) = 0 for
 * every order in the database. Consequences, all silent:
 *
 *   - the digest's "money to collect" bucket was always empty
 *   - onOrderCollectible ("your customer owes you") never fired for anyone
 *   - the digest email's money section was always blank
 *
 * A production sample when this was found: 0 of 42 orders had money on the base doc,
 * and every one of them had a populated /private/money mirror.
 *
 * OrderMoneyDto carries `ownerId` precisely so the whole workshop's money can be read
 * with one collection-group query instead of one document read per order — the
 * `private`/`ownerId` COLLECTION_GROUP field override already exists in
 * firestore.indexes.json for the client's own use.
 */
import * as functions from 'firebase-functions/v1';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import { OrderScanDoc } from './types';

/** The money fields a detector needs. Mirrors OrderMoneyDto's relevant half. */
export interface OrderMoney {
  totalPrice: number;
  discount: number;
  payments: { amount: number }[];
  costs: { amount: number }[];
}

function num(v: unknown): number {
  return typeof v === 'number' && Number.isFinite(v) ? v : 0;
}

function amounts(v: unknown): { amount: number }[] {
  return Array.isArray(v) ? v.map((p: DocumentData) => ({ amount: num(p?.amount) })) : [];
}

/**
 * The completeness sentinel, mirroring `Order.withMoney` on the client
 * (`OrderMapper.kt`): a mirror WITHOUT `ownerId` is a partial write — `recordPayment`
 * and `updateCosts` deliberately leave it unstamped — and must be ignored entirely
 * rather than treated as authoritative. Trusting one would zero out totalPrice and
 * silence the very notification this module exists to restore.
 */
export function isStampedMoney(d: DocumentData | undefined): boolean {
  return typeof d?.ownerId === 'string' && d.ownerId.trim().length > 0;
}

export function moneyFromDoc(d: DocumentData | undefined): OrderMoney {
  return {
    totalPrice: num(d?.totalPrice),
    discount: num(d?.discount),
    payments: amounts(d?.payments),
    costs: amounts(d?.costs),
  };
}

/**
 * Every order's money for one workshop, keyed by order id.
 *
 * One collection-group query for the whole workshop. Returns an empty map on failure
 * rather than throwing: a money read that fails should cost the money BUCKETS, not the
 * deadline reminders in the same digest.
 */
export async function loadMoneyByOrderId(
  db: Firestore,
  ownerUid: string,
): Promise<Map<string, OrderMoney>> {
  const byOrderId = new Map<string, OrderMoney>();
  try {
    const snap = await db.collectionGroup('private').where('ownerId', '==', ownerUid).get();
    for (const doc of snap.docs) {
      // The same collection group holds other private mirrors (a customer's contact
      // details, for one), which also carry ownerId. Only money docs have the id
      // 'money' AND an orderId, so require both rather than trusting either alone.
      if (doc.id !== 'money') continue;
      const data = doc.data();
      const orderId = typeof data?.orderId === 'string' ? data.orderId : null;
      if (!orderId) continue;
      byOrderId.set(orderId, moneyFromDoc(data));
    }
  } catch (err) {
    functions.logger.error('loadMoneyByOrderId failed — money buckets will be empty', {
      uid: ownerUid,
      error: err instanceof Error ? err.message : String(err),
    });
  }
  return byOrderId;
}

/**
 * Folds money onto scanned orders, matching `Order.withMoney` on the client exactly.
 *
 * A STAMPED mirror is authoritative and REPLACES the base values outright — no
 * truthiness fallback. That distinction matters: `m.totalPrice || o.totalPrice` would
 * resurrect a stale base price the moment a tailor legitimately corrected a price to
 * zero, or removed a discount, or deleted a payment, and the digest would then report
 * money owed on an order that is settled.
 *
 * Orders with no entry in the map keep their base values untouched. That covers both
 * legacy docs written before the split (which still carry real numbers) and unstamped
 * partial mirrors, which loadMoneyByOrderId's `ownerId` filter already excludes.
 */
export function withMoney(
  orders: OrderScanDoc[],
  byOrderId: Map<string, OrderMoney>,
): OrderScanDoc[] {
  return orders.map((o) => {
    const m = byOrderId.get(o.id);
    if (!m) return o;
    return { ...o, totalPrice: m.totalPrice, discount: m.discount, payments: m.payments, costs: m.costs };
  });
}
