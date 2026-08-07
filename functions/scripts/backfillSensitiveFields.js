#!/usr/bin/env node
/*
 * Slice 8b — one-off backfill of the owner-only /private sub-docs for EVERY
 * existing order and customer:
 *   - users/{uid}/orders/{oid}/private/money    (from the base order doc)
 *   - users/{uid}/customers/{cid}/private/contact (from the base customer doc)
 *
 * Why: Slice 8a switched the owner to read money/contact from these sub-docs via a
 * `collectionGroup("private").where("ownerId","==",uid)` query. Docs the app wrote
 * itself already have the sub-doc + ownerId; legacy/seeded orders and customers
 * (created before the dual-write, or seeded via REST) do NOT — so the owner's app
 * falls back to base money/contact for them. This backfill creates the missing
 * sub-docs (stamping ownerId + parent id) so the collection-group read is complete
 * before any future base-strip (Slice 8d).
 *
 * MIRROR-FIRST SAFETY (Slice 8e): after the 8d-1 client is adopted the /private
 * mirror is the ONLY authoritative store for money and contact — the client stops
 * writing them to the base doc (base gets `updatedAt` only) and the 8d strip
 * removes them from the base entirely. Rebuilding a mirror from its base doc after
 * that point would OVERWRITE authoritative data with stale/empty values. So this
 * script reads each existing mirror FIRST and classifies it:
 *   - missing / unstamped (no or blank ownerId) → write the full mirror built from
 *     the base (correct for pre-8d-1 docs, where the base is still authoritative);
 *   - stamped but legacy-deposit-incomplete (orders only: base depositPaid > 0 and
 *     the MIRROR's payments have no `legacy-deposit` entry) → TARGETED heal that
 *     writes ONLY `payments`, prepending the synthesized deposit to the mirror's
 *     own payments array. Nothing else on the mirror is touched;
 *   - stamped and complete → skipped entirely, never written.
 * The extra read per doc is deliberate and acceptable for a one-off script.
 *
 * Field shape and classification MUST stay in lockstep with
 * functions/src/staff/migrateSensitiveFields.ts (the callable equivalent) and with
 * the client OrderMoneyDto / CustomerContactDto.
 *
 * PREREQUISITE: run this only AFTER Slice 8a (#323) is merged/released — the shape
 * below (ownerId + orderId/customerId) is 8a's; running earlier is harmless but the
 * ownerId stamp only becomes USEFUL once the 8a read path + collection-group rule +
 * index are live.
 *
 * Usage:
 *   # dry run (default) — counts what WOULD be written, writes nothing:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js
 *   # apply:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js --commit
 *
 * Auth: application-default credentials with Firestore access, e.g.
 *   gcloud auth application-default login
 */
const admin = require('firebase-admin');

const BATCH_LIMIT = 400;
const USER_PAGE_SIZE = 200;
const LEGACY_DEPOSIT_PAYMENT_ID = 'legacy-deposit';

// Lockstep with migrateSensitiveFields.ts buildContactDoc.
function buildContactDoc(customer, ownerId, customerId) {
  return {
    ownerId,
    customerId,
    phone: typeof customer.phone === 'string' ? customer.phone : '',
    email: customer.email ?? null,
    address: customer.address ?? null,
  };
}

// Pure. The synthesized payment standing in for a legacy `depositPaid`.
// Lockstep with migrateSensitiveFields.ts buildLegacyDepositPayment and with
// OrderMapper.kt migrateLegacyDeposit.
function buildLegacyDepositPayment(order) {
  return {
    id: LEGACY_DEPOSIT_PAYMENT_ID,
    amount: typeof order.depositPaid === 'number' ? order.depositPaid : 0,
    method: 'OTHER',
    type: 'DEPOSIT',
    recordedAt: typeof order.createdAt === 'number' ? order.createdAt : 0,
    note: null,
  };
}

// Pure. True when the base order carries a legacy depositPaid > 0 that the given
// payments array has not absorbed as a `legacy-deposit` entry yet.
function needsLegacyDeposit(order, payments) {
  const depositPaid = typeof order.depositPaid === 'number' ? order.depositPaid : 0;
  return depositPaid > 0 && !payments.some((p) => p && p.id === LEGACY_DEPOSIT_PAYMENT_ID);
}

// Pure. A mirror is authoritative ("stamped") only if it exists with a non-blank
// ownerId — the same completeness sentinel the strip script uses.
function isMirrorStamped(mirror) {
  const ownerId = mirror && mirror.ownerId;
  return typeof ownerId === 'string' && ownerId.length > 0;
}

// Pure. Lockstep with migrateSensitiveFields.ts classifyOrderMirror.
// Returns { action: 'create' } | { action: 'healLegacyDeposit', payments } | { action: 'skip' }.
function classifyOrderMirror(order, mirror) {
  if (!isMirrorStamped(mirror)) {
    return { action: 'create' };
  }
  const mirrorPayments = Array.isArray(mirror.payments) ? mirror.payments : [];
  if (needsLegacyDeposit(order, mirrorPayments)) {
    return {
      action: 'healLegacyDeposit',
      payments: [buildLegacyDepositPayment(order)].concat(mirrorPayments),
    };
  }
  return { action: 'skip' };
}

// Pure. Lockstep with migrateSensitiveFields.ts classifyCustomerMirror. Contact has
// no legacy-field migration, so it is build-or-skip.
function classifyCustomerMirror(_customer, mirror) {
  return isMirrorStamped(mirror) ? { action: 'skip' } : { action: 'create' };
}

// Lockstep with migrateSensitiveFields.ts buildMoneyDoc. Used ONLY for the
// create path (missing/unstamped mirror), where the base doc is authoritative.
function buildMoneyDoc(order, ownerId, orderId) {
  const items = Array.isArray(order.items) ? order.items : [];
  const itemPrices = {};
  for (const item of items) {
    if (item && typeof item.id === 'string') {
      itemPrices[item.id] = typeof item.price === 'number' ? item.price : 0;
    }
  }

  const payments = Array.isArray(order.payments) ? order.payments : [];
  const migratedPayments = needsLegacyDeposit(order, payments)
    ? [buildLegacyDepositPayment(order)].concat(payments)
    : payments;

  return {
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

async function commitBatched(db, writes) {
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const chunk = writes.slice(i, i + BATCH_LIMIT);
    const batch = db.batch();
    for (const w of chunk) {
      batch.set(w.ref, w.data, { merge: true });
    }
    await batch.commit();
  }
}

async function readMirror(db, path) {
  const snap = await db.doc(path).get();
  return snap.exists ? snap.data() : undefined;
}

async function main() {
  const commit = process.argv.includes('--commit');
  // Fail loudly rather than letting ADC silently resolve some other project:
  // this script writes to production Firestore.
  const projectId = process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) {
    console.error(
      'GOOGLE_CLOUD_PROJECT is not set. Re-run with the project pinned explicitly, e.g.\n' +
        '  GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js',
    );
    process.exit(1);
  }
  admin.initializeApp({ projectId });
  const db = admin.firestore();

  let users = 0;
  const counts = {
    customersMirrorCreated: 0,
    customersAlreadyMirrored: 0,
    ordersMirrorCreated: 0,
    ordersHealedLegacyDeposit: 0,
    ordersAlreadyMirrored: 0,
  };

  // Page the users query so a large production users collection can't exhaust
  // memory / hit RPC limits by materialising every user at once — matches the
  // paging in migrateSensitiveFieldsHandler.
  let cursor = null;
  for (;;) {
    let query = db
      .collection('users')
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(USER_PAGE_SIZE);
    if (cursor) query = query.startAfter(cursor);
    const page = await query.get();
    if (page.empty) break;

    for (const userDoc of page.docs) {
      users += 1;
      const uid = userDoc.id;

      const customersSnap = await db.collection(`users/${uid}/customers`).get();
      const contactWrites = [];
      for (const d of customersSnap.docs) {
        const path = `users/${uid}/customers/${d.id}/private/contact`;
        const plan = classifyCustomerMirror(d.data(), await readMirror(db, path));
        if (plan.action === 'create') {
          counts.customersMirrorCreated += 1;
          contactWrites.push({ ref: db.doc(path), data: buildContactDoc(d.data(), uid, d.id) });
        } else {
          counts.customersAlreadyMirrored += 1;
        }
      }
      if (commit && contactWrites.length > 0) {
        await commitBatched(db, contactWrites);
      }

      const ordersSnap = await db.collection(`users/${uid}/orders`).get();
      const moneyWrites = [];
      for (const d of ordersSnap.docs) {
        const path = `users/${uid}/orders/${d.id}/private/money`;
        const plan = classifyOrderMirror(d.data(), await readMirror(db, path));
        if (plan.action === 'create') {
          counts.ordersMirrorCreated += 1;
          moneyWrites.push({ ref: db.doc(path), data: buildMoneyDoc(d.data(), uid, d.id) });
        } else if (plan.action === 'healLegacyDeposit') {
          counts.ordersHealedLegacyDeposit += 1;
          console.warn(`HEAL legacy-deposit (payments only): ${path}`);
          // Payments ONLY — every other field on the stamped mirror is authoritative.
          moneyWrites.push({ ref: db.doc(path), data: { payments: plan.payments } });
        } else {
          counts.ordersAlreadyMirrored += 1;
        }
      }
      if (commit && moneyWrites.length > 0) {
        await commitBatched(db, moneyWrites);
      }
    }
    cursor = page.docs[page.docs.length - 1];
  }

  console.log(
    `${commit ? 'COMMITTED' : 'DRY RUN'} — users=${users} ` +
      `ordersMirrorCreated=${counts.ordersMirrorCreated} ` +
      `ordersHealedLegacyDeposit=${counts.ordersHealedLegacyDeposit} ` +
      `ordersAlreadyMirrored=${counts.ordersAlreadyMirrored} ` +
      `customersMirrorCreated=${counts.customersMirrorCreated} ` +
      `customersAlreadyMirrored=${counts.customersAlreadyMirrored}`,
  );
  if (!commit) console.log('Re-run with --commit to apply.');
}

module.exports = {
  buildContactDoc,
  buildMoneyDoc,
  buildLegacyDepositPayment,
  needsLegacyDeposit,
  isMirrorStamped,
  classifyOrderMirror,
  classifyCustomerMirror,
  LEGACY_DEPOSIT_PAYMENT_ID,
};

if (require.main === module) {
  main().then(
    () => process.exit(0),
    (err) => {
      console.error(err);
      process.exit(1);
    },
  );
}
