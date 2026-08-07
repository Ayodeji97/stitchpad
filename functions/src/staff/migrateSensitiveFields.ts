import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';

const REGION = 'europe-west1';

// One-time, idempotent backfill for the Owner + Staff data restructure.
//
// Copies the money on every existing order into its owner-only
// `.../orders/{oid}/private/money` sub-doc, and every customer's contact into
// `.../customers/{cid}/private/contact`, deriving both from the base docs.
// `dryRun` (default true) counts without writing.
//
// MIRROR-FIRST SAFETY (Slice 8e): once the 8d-1 client is adopted, the /private
// mirrors are the ONLY authoritative store for money and contact — the client no
// longer writes them to the base doc (the base gets `updatedAt` only), and the 8d
// strip removes them from the base entirely. Rebuilding a mirror from its base doc
// after that point would OVERWRITE authoritative data with stale/empty values. So
// this migration reads each existing mirror first and classifies it:
//   - missing / unstamped (no ownerId) → build the full mirror from the base
//     (pre-8d-1 doc: the base is still the source of truth for it);
//   - stamped but legacy-deposit-incomplete (orders only) → TARGETED heal that
//     prepends the synthesized `legacy-deposit` payment to the MIRROR's own
//     payments array and touches nothing else;
//   - stamped and complete → skipped entirely, never written.
// The extra read per doc is deliberate and acceptable for a one-off migration.
//
// Admin-gated (`admin: true` custom claim) like the other operational callables.

const USER_PAGE_SIZE = 200;
const BATCH_LIMIT = 500;

export const LEGACY_DEPOSIT_PAYMENT_ID = 'legacy-deposit';

export interface MigrateSensitiveFieldsRequest {
  dryRun?: unknown;
}

export interface MigrateSensitiveFieldsResponse {
  dryRun: boolean;
  users: number;
  customersMirrorCreated: number;
  customersAlreadyMirrored: number;
  ordersMirrorCreated: number;
  ordersHealedLegacyDeposit: number;
  ordersAlreadyMirrored: number;
}

export interface MigrateSensitiveFieldsDeps {
  db: admin.firestore.Firestore;
}

/**
 * What to do with one base doc's /private mirror. `create` = write the full
 * mirror built from the base; `healLegacyDeposit` = write ONLY `payments` (the
 * mirror's own array with the synthesized legacy deposit prepended); `skip` =
 * leave the mirror untouched.
 */
export type MirrorPlan =
  | { action: 'create' }
  | { action: 'healLegacyDeposit'; payments: admin.firestore.DocumentData[] }
  | { action: 'skip' };

/**
 * A mirror counts as "stamped" — i.e. written by the app or a previous backfill,
 * and therefore authoritative — only if it exists with a non-blank `ownerId`.
 * Same sentinel the strip script uses (`ownerId` is the completeness marker in
 * Order.withMoney / Customer.withContact).
 */
export function isMirrorStamped(mirror: admin.firestore.DocumentData | null | undefined): boolean {
  const ownerId = mirror?.ownerId;
  return typeof ownerId === 'string' && ownerId.length > 0;
}

/** The synthesized payment standing in for a legacy `depositPaid` on a base order. */
export function buildLegacyDepositPayment(
  order: admin.firestore.DocumentData,
): admin.firestore.DocumentData {
  return {
    id: LEGACY_DEPOSIT_PAYMENT_ID,
    amount: typeof order.depositPaid === 'number' ? order.depositPaid : 0,
    method: 'OTHER',
    type: 'DEPOSIT',
    recordedAt: typeof order.createdAt === 'number' ? order.createdAt : 0,
    note: null,
  };
}

/**
 * True when the base order carries a legacy `depositPaid > 0` that the given
 * payments array has not yet absorbed as a `legacy-deposit` entry.
 */
export function needsLegacyDeposit(
  order: admin.firestore.DocumentData,
  payments: admin.firestore.DocumentData[],
): boolean {
  const depositPaid = typeof order.depositPaid === 'number' ? order.depositPaid : 0;
  return depositPaid > 0 && !payments.some((p) => p && p.id === LEGACY_DEPOSIT_PAYMENT_ID);
}

/**
 * Decide what the backfill may do to an order's `/private/money` mirror.
 *
 * Mirror-first: a stamped mirror is authoritative (post-8d-1 the base doc has no
 * money at all), so it is never rebuilt from the base. The only permitted write
 * to a stamped mirror is the targeted legacy-deposit heal, which prepends the
 * synthesized deposit to the MIRROR's payments — never the base's.
 */
export function classifyOrderMirror(
  order: admin.firestore.DocumentData,
  mirror: admin.firestore.DocumentData | null | undefined,
): MirrorPlan {
  if (!isMirrorStamped(mirror)) {
    return { action: 'create' };
  }
  const mirrorPayments: admin.firestore.DocumentData[] = Array.isArray(mirror?.payments)
    ? (mirror?.payments as admin.firestore.DocumentData[])
    : [];
  if (needsLegacyDeposit(order, mirrorPayments)) {
    return {
      action: 'healLegacyDeposit',
      payments: [buildLegacyDepositPayment(order)].concat(mirrorPayments),
    };
  }
  return { action: 'skip' };
}

/**
 * Decide what the backfill may do to a customer's `/private/contact` mirror.
 * Contact has no legacy-field migration, so it is build-or-skip.
 */
export function classifyCustomerMirror(
  _customer: admin.firestore.DocumentData,
  mirror: admin.firestore.DocumentData | null | undefined,
): MirrorPlan {
  return isMirrorStamped(mirror) ? { action: 'skip' } : { action: 'create' };
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
 *
 * Used ONLY on the `create` path (missing/unstamped mirror), where the base doc
 * is still the source of truth. Never used to overwrite a stamped mirror.
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
  const migratedPayments = needsLegacyDeposit(order, payments)
    ? [buildLegacyDepositPayment(order)].concat(payments)
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

/** Existing mirror payload, or undefined if the sub-doc does not exist yet. */
async function readMirror(
  ref: admin.firestore.DocumentReference,
): Promise<admin.firestore.DocumentData | undefined> {
  const snap = await ref.get();
  return snap.exists ? snap.data() : undefined;
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
  let customersMirrorCreated = 0;
  let customersAlreadyMirrored = 0;
  let ordersMirrorCreated = 0;
  let ordersHealedLegacyDeposit = 0;
  let ordersAlreadyMirrored = 0;

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
      const contactWrites: Array<{
        ref: admin.firestore.DocumentReference;
        data: Record<string, unknown>;
      }> = [];
      for (const d of customersSnap.docs) {
        const ref = deps.db.doc(`users/${uid}/customers/${d.id}/private/contact`);
        const plan = classifyCustomerMirror(d.data(), await readMirror(ref));
        if (plan.action === 'create') {
          customersMirrorCreated += 1;
          contactWrites.push({ ref, data: buildContactDoc(d.data(), uid, d.id) });
        } else {
          customersAlreadyMirrored += 1;
        }
      }
      if (!dryRun) {
        await commitBatched(deps.db, contactWrites);
      }

      const ordersSnap = await deps.db.collection(`users/${uid}/orders`).get();
      const moneyWrites: Array<{
        ref: admin.firestore.DocumentReference;
        data: Record<string, unknown>;
      }> = [];
      for (const d of ordersSnap.docs) {
        const ref = deps.db.doc(`users/${uid}/orders/${d.id}/private/money`);
        const plan = classifyOrderMirror(d.data(), await readMirror(ref));
        if (plan.action === 'create') {
          ordersMirrorCreated += 1;
          moneyWrites.push({ ref, data: buildMoneyDoc(d.data(), uid, d.id) });
        } else if (plan.action === 'healLegacyDeposit') {
          ordersHealedLegacyDeposit += 1;
          // Payments ONLY — every other field on the stamped mirror is authoritative.
          moneyWrites.push({ ref, data: { payments: plan.payments } });
        } else {
          ordersAlreadyMirrored += 1;
        }
      }
      if (!dryRun) {
        await commitBatched(deps.db, moneyWrites);
      }
    }

    if (page.size < USER_PAGE_SIZE) {
      break;
    }
    cursor = page.docs[page.docs.length - 1];
  }

  const result: MigrateSensitiveFieldsResponse = {
    dryRun,
    users,
    customersMirrorCreated,
    customersAlreadyMirrored,
    ordersMirrorCreated,
    ordersHealedLegacyDeposit,
    ordersAlreadyMirrored,
  };
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
