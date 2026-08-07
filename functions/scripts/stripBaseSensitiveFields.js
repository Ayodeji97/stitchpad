#!/usr/bin/env node
/**
 * Slice 8d-2 (enables 8e): strip money fields off base order docs and contact
 * fields off base customer docs. Lockstep with the client's OrderBaseDto /
 * CustomerBaseDto (Slice 8d-1) — the fields deleted here are exactly the ones
 * the new client no longer writes.
 *
 * HARD PREREQS (see docs/staff/slice8e-rollout-runbook.md):
 *   - 8b backfill has stamped ownerId on every /private/money and /private/contact
 *   - 8c version floor is live (old clients can no longer re-write these fields)
 *   - A Firestore export has been taken (this is the irreversible step)
 *
 * Safety: a doc whose /private mirror is missing or has a blank ownerId is
 * SKIPPED and counted, never stripped — stripping it would zero the owner's
 * money/contact on read (ownerId is the completeness sentinel in Order.withMoney
 * / Customer.withContact).
 *
 * Dry run by default. Usage:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js [--commit]
 */
const admin = require('firebase-admin');

const USER_PAGE_SIZE = 200;
const BATCH_LIMIT = 400;

const ORDER_MONEY_FIELDS = [
  'totalPrice',
  'discount',
  'discountReason',
  'depositPaid',
  'balanceRemaining',
  'payments',
  'costs',
];
const CUSTOMER_CONTACT_FIELDS = ['phone', 'email', 'address'];

// Pure. Update map deleting every present money field; items[] is rewritten as a
// whole array minus price (array elements cannot be field-deleted individually).
function buildOrderStrip(order, fieldValue) {
  const update = {};
  for (const field of ORDER_MONEY_FIELDS) {
    if (field in order) {
      update[field] = fieldValue.delete();
    }
  }
  if (Array.isArray(order.items) && order.items.some((item) => item && 'price' in item)) {
    update.items = order.items.map((item) => {
      const { price, ...rest } = item;
      return rest;
    });
  }
  return update;
}

// Pure. Update map deleting every present contact field.
function buildCustomerStrip(customer, fieldValue) {
  const update = {};
  for (const field of CUSTOMER_CONTACT_FIELDS) {
    if (field in customer) {
      update[field] = fieldValue.delete();
    }
  }
  return update;
}

async function commitBatched(db, writes) {
  for (let i = 0; i < writes.length; i += BATCH_LIMIT) {
    const batch = db.batch();
    for (const write of writes.slice(i, i + BATCH_LIMIT)) {
      batch.update(write.ref, write.data);
    }
    await batch.commit();
  }
}

async function isStamped(db, privatePath) {
  const snap = await db.doc(privatePath).get();
  return snap.exists && typeof snap.get('ownerId') === 'string' && snap.get('ownerId').length > 0;
}

async function main() {
  const commit = process.argv.includes('--commit');
  admin.initializeApp();
  const db = admin.firestore();
  const fieldValue = admin.firestore.FieldValue;

  let users = 0;
  const counts = {
    ordersStripped: 0,
    ordersClean: 0,
    ordersSkippedUnstamped: 0,
    customersStripped: 0,
    customersClean: 0,
    customersSkippedUnstamped: 0,
  };

  let cursor = null;
  for (;;) {
    let query = db
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
      const writes = [];

      const ordersSnap = await db.collection(`users/${uid}/orders`).get();
      for (const orderDoc of ordersSnap.docs) {
        const update = buildOrderStrip(orderDoc.data(), fieldValue);
        if (Object.keys(update).length === 0) {
          counts.ordersClean += 1;
        } else if (await isStamped(db, `users/${uid}/orders/${orderDoc.id}/private/money`)) {
          counts.ordersStripped += 1;
          writes.push({ ref: orderDoc.ref, data: update });
        } else {
          counts.ordersSkippedUnstamped += 1;
          console.warn(`SKIP unstamped money: users/${uid}/orders/${orderDoc.id}`);
        }
      }

      const customersSnap = await db.collection(`users/${uid}/customers`).get();
      for (const customerDoc of customersSnap.docs) {
        const update = buildCustomerStrip(customerDoc.data(), fieldValue);
        if (Object.keys(update).length === 0) {
          counts.customersClean += 1;
        } else if (await isStamped(db, `users/${uid}/customers/${customerDoc.id}/private/contact`)) {
          counts.customersStripped += 1;
          writes.push({ ref: customerDoc.ref, data: update });
        } else {
          counts.customersSkippedUnstamped += 1;
          console.warn(`SKIP unstamped contact: users/${uid}/customers/${customerDoc.id}`);
        }
      }

      if (commit && writes.length > 0) {
        await commitBatched(db, writes);
      }
    }

    if (page.size < USER_PAGE_SIZE) {
      break;
    }
    cursor = page.docs[page.docs.length - 1];
  }

  const label = commit ? 'COMMITTED' : 'DRY RUN';
  console.log(
    `${label} — users=${users} ` +
      `ordersStripped=${counts.ordersStripped} ordersClean=${counts.ordersClean} ` +
      `ordersSkippedUnstamped=${counts.ordersSkippedUnstamped} ` +
      `customersStripped=${counts.customersStripped} customersClean=${counts.customersClean} ` +
      `customersSkippedUnstamped=${counts.customersSkippedUnstamped}`,
  );
}

module.exports = { buildOrderStrip, buildCustomerStrip, ORDER_MONEY_FIELDS, CUSTOMER_CONTACT_FIELDS };

if (require.main === module) {
  main().catch((err) => {
    console.error(err);
    process.exitCode = 1;
  });
}
