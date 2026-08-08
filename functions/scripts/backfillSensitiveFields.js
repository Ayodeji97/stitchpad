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
 *     the base (correct for pre-8d-1 docs, where the base is still authoritative),
 *     OVERLAID with whatever the unstamped mirror already holds. An unstamped mirror
 *     is not necessarily empty: the 8d-1 client's recordPayment/updateCosts write
 *     `payments`/`costs` to it with merge=true and DELIBERATELY no ownerId stamp, so
 *     it can hold payments/costs NEWER than the base doc (which only got updatedAt
 *     from those writes). See buildMoneyDocWithOverlay;
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
// no legacy-field migration, so it is build-or-skip. No overlay is needed on the
// customer create path: every client contact write goes through toCustomerContactDto,
// which always stamps ownerId — an unstamped contact mirror can never hold newer data.
function classifyCustomerMirror(_customer, mirror) {
  return isMirrorStamped(mirror) ? { action: 'skip' } : { action: 'create' };
}

// Pure. The usable dedup key of a payment entry: a non-blank string `id`.
// Lockstep with migrateSensitiveFields.ts paymentId.
function paymentId(payment) {
  const id = payment && payment.id;
  return typeof id === 'string' && id.length > 0 ? id : undefined;
}

// Lockstep with migrateSensitiveFields.ts buildMoneyDoc. Base-only build; the
// create path calls buildMoneyDocWithOverlay, which layers any existing
// (unstamped) mirror data on top of this.
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

// Pure. The payload for the `create` path. Lockstep with
// migrateSensitiveFields.ts buildMoneyDocWithOverlay.
//
// An UNSTAMPED mirror is still partially authoritative: the 8d-1 client's
// recordPayment does `set({payments: arrayUnion(...)}, merge=true)` and updateCosts
// does `set({costs: [...]}, merge=true)`, both create-if-absent and both without the
// ownerId stamp (the completeness sentinel). Those writes put payments/costs on the
// mirror that the base doc never received. Rebuilding wholly from the base would
// discard them and then stamp the stale result as authoritative. So:
//   - payments: base-derived payments (incl. legacy-deposit synthesis) FIRST, then
//     any mirror payment whose id is not already present (union, base wins position);
//   - costs: updateCosts writes the COMPLETE list, so whenever the mirror carries a
//     costs array it wins outright — including an EMPTY one, which is a legitimate
//     "cleared every cost" state. Only an absent/malformed key falls back to the base's;
//   - everything else comes from the base, as before.
// With no existing mirror this is exactly buildMoneyDoc.
function buildMoneyDocWithOverlay(order, ownerId, orderId, existingMirror) {
  const base = buildMoneyDoc(order, ownerId, orderId);
  if (!existingMirror) {
    return base;
  }

  const basePayments = Array.isArray(base.payments) ? base.payments : [];
  const mirrorPayments = Array.isArray(existingMirror.payments) ? existingMirror.payments : [];
  const seen = new Set(basePayments.map(paymentId).filter((id) => id !== undefined));
  const extraPayments = [];
  for (const p of mirrorPayments) {
    const id = paymentId(p);
    // Null / id-less mirror entries cannot be deduped safely — drop them.
    if (id === undefined || seen.has(id)) continue;
    seen.add(id);
    extraPayments.push(p);
  }

  // Presence-based, NOT length-based: updateCosts writes the complete list, so an
  // empty array on the mirror is a legitimate "cleared every cost" state and must win
  // over the base. Only an absent (or malformed) costs key means "never written"
  // — e.g. a recordPayment-only mirror — and falls back to the base's costs.
  const mirrorHasCosts = Array.isArray(existingMirror.costs);

  return Object.assign({}, base, {
    payments: basePayments.concat(extraPayments),
    costs: mirrorHasCosts ? existingMirror.costs : base.costs,
  });
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
        const mirror = await readMirror(db, path);
        const plan = classifyOrderMirror(d.data(), mirror);
        if (plan.action === 'create') {
          counts.ordersMirrorCreated += 1;
          // Overlay, not a bare rebuild: an unstamped mirror may already hold
          // payments/costs the base doc never got (8d-1 partial client writes).
          moneyWrites.push({
            ref: db.doc(path),
            data: buildMoneyDocWithOverlay(d.data(), uid, d.id, mirror),
          });
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
  buildMoneyDocWithOverlay,
  paymentId,
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
