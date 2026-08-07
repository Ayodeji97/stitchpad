import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

const REGION = 'europe-west1';

// One-time, idempotent backfill for the Owner + Staff data restructure.
//
// Copies the money on every existing order into its owner-only
// `.../orders/{oid}/private/money` sub-doc, and every customer's contact into
// `.../customers/{cid}/private/contact`, deriving both from the base docs (which
// remain the source of truth during the dual-write window). Writes are `merge:
// true` so the backfill completes any partial sub-docs the app already wrote and
// is safe to re-run. `dryRun` (default true) counts without writing.
//
// Admin-gated (`admin: true` custom claim) like the other operational callables.

const USER_PAGE_SIZE = 200;
const BATCH_LIMIT = 500;

export interface MigrateSensitiveFieldsRequest {
  dryRun?: unknown;
}

export interface MigrateSensitiveFieldsResponse {
  dryRun: boolean;
  users: number;
  customersWritten: number;
  ordersWritten: number;
}

export interface MigrateSensitiveFieldsDeps {
  db: admin.firestore.Firestore;
}

/**
 * Owner-only contact payload derived from a base customer doc. Mirrors the
 * client `CustomerContactDto` defaults (phone "", email/address null).
 */
export function buildContactDoc(
  customer: admin.firestore.DocumentData,
  ownerId: string,
  customerId: string,
): Record<string, unknown> {
  return {
    // Slice 8a: ownerId scopes the owner's collectionGroup("private") read;
    // customerId joins the result back onto the base customer.
    ownerId,
    customerId,
    phone: typeof customer.phone === 'string' ? customer.phone : '',
    email: customer.email ?? null,
    address: customer.address ?? null,
  };
}

/**
 * Owner-only money payload derived from a base order doc. Mirrors the client
 * `OrderMoneyDto`; `itemPrices` relocates each item's `price` keyed by item id.
 * Applies legacy deposit migration (Slice 8d-1): synthesizes a "legacy-deposit"
 * payment if depositPaid > 0 and no payment with that id exists.
 */
export function buildMoneyDoc(
  order: admin.firestore.DocumentData,
  ownerId: string,
  orderId: string,
): Record<string, unknown> {
  const items: admin.firestore.DocumentData[] = Array.isArray(order.items) ? order.items : [];
  const itemPrices: Record<string, number> = {};
  for (const item of items) {
    if (item && typeof item.id === 'string') {
      itemPrices[item.id] = typeof item.price === 'number' ? item.price : 0;
    }
  }

  // Lockstep with OrderMapper.kt migrateLegacyDeposit: if depositPaid > 0 and no
  // "legacy-deposit" payment exists, prepend a synthesized payment entry.
  const payments: admin.firestore.DocumentData[] = Array.isArray(order.payments) ? order.payments : [];
  const depositPaid = typeof order.depositPaid === 'number' ? order.depositPaid : 0;
  const createdAt = typeof order.createdAt === 'number' ? order.createdAt : 0;
  const legacyPayment: admin.firestore.DocumentData = {
    id: 'legacy-deposit',
    amount: depositPaid,
    method: 'OTHER',
    type: 'DEPOSIT',
    recordedAt: createdAt,
    note: null,
  };
  const migratedPayments =
    depositPaid > 0 && !payments.some((p) => p && p.id === 'legacy-deposit')
      ? [legacyPayment].concat(payments)
      : payments;

  return {
    // Slice 8a: ownerId scopes the owner's collectionGroup("private") read;
    // orderId joins the result back onto the base order.
    ownerId,
    orderId,
    totalPrice: typeof order.totalPrice === 'number' ? order.totalPrice : 0,
    discount: typeof order.discount === 'number' ? order.discount : 0,
    discountReason: order.discountReason ?? null,
    payments: migratedPayments,
    costs: Array.isArray(order.costs) ? order.costs : [],
    itemPrices,
  };
}

async function commitBatched(
  db: admin.firestore.Firestore,
  writes: Array<{ ref: admin.firestore.DocumentReference; data: Record<string, unknown> }>,
): Promise<void> {
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const chunk = writes.slice(i, i + BATCH_LIMIT);
    const batch = db.batch();
    for (const w of chunk) {
      batch.set(w.ref, w.data, { merge: true });
    }
    await batch.commit();
  }
}

export async function migrateSensitiveFieldsHandler(
  data: MigrateSensitiveFieldsRequest,
  context: functions.https.CallableContext,
  deps: MigrateSensitiveFieldsDeps,
): Promise<MigrateSensitiveFieldsResponse> {
  if (context.auth?.token?.admin !== true) {
    throw new functions.https.HttpsError('permission-denied', 'admin_only');
  }
  // Default to a dry run: only an explicit `dryRun: false` writes anything.
  const dryRun = data.dryRun !== false;

  let users = 0;
  let customersWritten = 0;
  let ordersWritten = 0;

  let cursor: admin.firestore.QueryDocumentSnapshot | null = null;
  // eslint-disable-next-line no-constant-condition
  while (true) {
    let query = deps.db
      .collection('users')
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(USER_PAGE_SIZE);
    if (cursor) {
      query = query.startAfter(cursor);
    }
    const page = await query.get();
    if (page.empty) {
      break;
    }

    for (const userDoc of page.docs) {
      users += 1;
      const uid = userDoc.id;

      const customersSnap = await deps.db.collection(`users/${uid}/customers`).get();
      const contactWrites = customersSnap.docs.map((d) => ({
        ref: deps.db.doc(`users/${uid}/customers/${d.id}/private/contact`),
        data: buildContactDoc(d.data(), uid, d.id),
      }));
      customersWritten += contactWrites.length;
      if (!dryRun) {
        await commitBatched(deps.db, contactWrites);
      }

      const ordersSnap = await deps.db.collection(`users/${uid}/orders`).get();
      const moneyWrites = ordersSnap.docs.map((d) => ({
        ref: deps.db.doc(`users/${uid}/orders/${d.id}/private/money`),
        data: buildMoneyDoc(d.data(), uid, d.id),
      }));
      ordersWritten += moneyWrites.length;
      if (!dryRun) {
        await commitBatched(deps.db, moneyWrites);
      }
    }

    if (page.size < USER_PAGE_SIZE) {
      break;
    }
    cursor = page.docs[page.docs.length - 1];
  }

  const result: MigrateSensitiveFieldsResponse = { dryRun, users, customersWritten, ordersWritten };
  functions.logger.info('migrateSensitiveFields complete', { ...result });
  return result;
}

export const migrateSensitiveFields = functions
  .region(REGION)
  .https.onCall(
    async (data, context): Promise<MigrateSensitiveFieldsResponse> =>
      migrateSensitiveFieldsHandler(data as MigrateSensitiveFieldsRequest, context, {
        db: admin.firestore(),
      }),
  );
