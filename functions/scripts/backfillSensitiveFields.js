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
 * falls back to base money/contact for them. This backfill creates/repairs the
 * sub-docs (stamping ownerId + parent id) so the collection-group read is complete
 * before any future base-strip (Slice 8d).
 *
 * Idempotent and safe to re-run: writes with merge:true, so it only fills gaps and
 * refreshes ownerId; it never deletes base fields (the base-strip is a separate,
 * later, deliberate step).
 *
 * Field shape MUST stay in lockstep with buildMoneyDoc / buildContactDoc in
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

// Lockstep with migrateSensitiveFields.ts buildMoneyDoc.
function buildMoneyDoc(order, ownerId, orderId) {
  const items = Array.isArray(order.items) ? order.items : [];
  const itemPrices = {};
  for (const item of items) {
    if (item && typeof item.id === 'string') {
      itemPrices[item.id] = typeof item.price === 'number' ? item.price : 0;
    }
  }
  return {
    ownerId,
    orderId,
    totalPrice: typeof order.totalPrice === 'number' ? order.totalPrice : 0,
    discount: typeof order.discount === 'number' ? order.discount : 0,
    discountReason: order.discountReason ?? null,
    payments: Array.isArray(order.payments) ? order.payments : [],
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

async function main() {
  const commit = process.argv.includes('--commit');
  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = admin.firestore();

  const usersSnap = await db.collection('users').get();

  let users = 0;
  let contactsWritten = 0;
  let moneyWritten = 0;

  for (const userDoc of usersSnap.docs) {
    users += 1;
    const uid = userDoc.id;

    const customersSnap = await db.collection(`users/${uid}/customers`).get();
    const contactWrites = customersSnap.docs.map((d) => ({
      ref: db.doc(`users/${uid}/customers/${d.id}/private/contact`),
      data: buildContactDoc(d.data(), uid, d.id),
    }));
    contactsWritten += contactWrites.length;
    if (commit && contactWrites.length > 0) {
      await commitBatched(db, contactWrites);
    }

    const ordersSnap = await db.collection(`users/${uid}/orders`).get();
    const moneyWrites = ordersSnap.docs.map((d) => ({
      ref: db.doc(`users/${uid}/orders/${d.id}/private/money`),
      data: buildMoneyDoc(d.data(), uid, d.id),
    }));
    moneyWritten += moneyWrites.length;
    if (commit && moneyWrites.length > 0) {
      await commitBatched(db, moneyWrites);
    }
  }

  console.log(
    `${commit ? 'COMMITTED' : 'DRY RUN'} — users=${users} ` +
      `contactDocs=${contactsWritten} moneyDocs=${moneyWritten}`,
  );
  if (!commit) console.log('Re-run with --commit to apply.');
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
