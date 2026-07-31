#!/usr/bin/env node
/*
 * One-shot setup for the local staff smoke-test emulator (see
 * docs/staff/slice8-emulator-smoke-runbook.md). Runs against the RUNNING Firebase
 * emulators (Auth + Firestore) — it never touches production.
 *
 * Creates:
 *  - Fola (owner) + Gabby (staff) auth users, email-verified, known passwords.
 *  - Gabby's custom claims { role:'staff', workshopUid:<Fola> } — the token claim
 *    that makes the app resolve her as active staff on Fola's tree.
 *  - Gabby's membership doc under Fola (status:'active') — the rules' isActiveMember
 *    reads this, so the Slice-8e-preview `allow list` lets her read Fola's orders.
 *  - Minimal owner user docs + a realistic seed of Fola's customers/orders in the
 *    full dual-write shape (base + /private with ownerId), matching the app.
 *
 * Usage (emulators must already be running):
 *   firebase emulators:start --config firebase.emulator.json     # in one terminal
 *   cd functions && node scripts/emulatorSetupStaff.js           # in another
 */
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';

const admin = require('firebase-admin');
admin.initializeApp({ projectId: 'stitchpad-30607' });

const DAY = 24 * 60 * 60 * 1000;
const FOLA = { uid: '8J1YLUh32yQTLcBlAaOe7MUZKay2', email: 'fola@gmail.com', password: 'fola123', name: 'Fola Joy' };
const GABBY = { uid: 'pulzPGPXnRgRk8hZnof3LoFJPaj1', email: 'gabby@gmail.com', password: 'gabby123', name: 'Gabby Gala' };

async function ensureUser(u, claims) {
  const props = { uid: u.uid, email: u.email, password: u.password, emailVerified: true, displayName: u.name };
  try {
    await admin.auth().createUser(props);
  } catch (e) {
    if (e.code === 'auth/uid-already-exists' || e.code === 'auth/email-already-exists') {
      await admin.auth().updateUser(u.uid, { email: u.email, password: u.password, emailVerified: true, displayName: u.name });
    } else {
      throw e;
    }
  }
  if (claims) await admin.auth().setCustomUserClaims(u.uid, claims);
}

const CUSTOMERS = [
  { id: 'seed-cust-1', name: 'Folake Adebayo', phone: '+2348031112221' },
  { id: 'seed-cust-2', name: 'Adaeze Okafor', phone: '+2348031112222' },
  { id: 'seed-cust-3', name: 'Chinedu Balogun', phone: '+2348031112223' },
  { id: 'seed-cust-4', name: 'Aisha Bello', phone: '+2348031112224' },
];
const ORDERS = [
  { id: 'seed-ord-1', cust: 0, garment: 'Asoebi', status: 'PENDING', sub: null, deadlineDays: -7, price: 45000, deposit: 20000 },
  { id: 'seed-ord-2', cust: 1, garment: 'Agbada', status: 'IN_PROGRESS', sub: 'CUTTING', deadlineDays: -1, price: 60000, deposit: 30000 },
  { id: 'seed-ord-3', cust: 2, garment: 'Kaftan', status: 'IN_PROGRESS', sub: 'SEWING', deadlineDays: 0, price: 35000, deposit: 15000 },
  { id: 'seed-ord-4', cust: 3, garment: 'Gown', status: 'IN_PROGRESS', sub: 'FITTING', deadlineDays: 2, price: 50000, deposit: 25000 },
  { id: 'seed-ord-5', cust: 0, garment: 'Senator', status: 'READY', sub: null, deadlineDays: 1, price: 40000, deposit: 40000 },
  { id: 'seed-ord-6', cust: 1, garment: 'Aso Oke', status: 'DELIVERED', sub: null, deadlineDays: -10, price: 80000, deposit: 80000 },
];

async function main() {
  await ensureUser(FOLA, null);
  await ensureUser(GABBY, { role: 'staff', workshopUid: FOLA.uid });

  const db = admin.firestore();
  const uid = FOLA.uid;
  const now = Date.now();

  // Minimal owner user docs so both accounts function in the app.
  await db.doc(`users/${uid}`).set({ name: FOLA.name, subscriptionTier: 'atelier', subscriptionStatus: 'active' }, { merge: true });
  await db.doc(`users/${GABBY.uid}`).set({ name: GABBY.name, subscriptionTier: 'atelier', subscriptionStatus: 'active' }, { merge: true });

  // Gabby's active membership (rules' isActiveMember reads this).
  await db.doc(`users/${uid}/memberships/${GABBY.uid}`).set(
    { staffAuthUid: GABBY.uid, staffEmail: GABBY.email, staffName: GABBY.name, status: 'active' },
    { merge: true },
  );

  const batch = db.batch();
  for (const c of CUSTOMERS) {
    batch.set(db.doc(`users/${uid}/customers/${c.id}`), {
      id: c.id, name: c.name, phone: c.phone, email: null, address: null, slotState: 'ACTIVE',
      serverCreatedAt: admin.firestore.FieldValue.serverTimestamp(), createdAt: now, updatedAt: now,
    });
    batch.set(db.doc(`users/${uid}/customers/${c.id}/private/contact`), {
      ownerId: uid, customerId: c.id, phone: c.phone, email: null, address: null,
    });
  }
  for (const o of ORDERS) {
    const cust = CUSTOMERS[o.cust];
    const itemId = `${o.id}-item-1`;
    const payments = o.deposit > 0
      ? [{ id: `${o.id}-pay-1`, amount: o.deposit, method: 'CASH', type: 'DEPOSIT', recordedAt: now, note: null }]
      : [];
    batch.set(db.doc(`users/${uid}/orders/${o.id}`), {
      id: o.id, customerId: cust.id, customerName: cust.name, status: o.status, subStatus: o.sub, priority: 'NORMAL',
      totalPrice: o.price, discount: 0, discountReason: null, payments, costs: [],
      deadline: now + o.deadlineDays * DAY, notes: null, archivedAt: null,
      items: [{ id: itemId, garmentType: 'CUSTOM', customGarmentName: o.garment, description: o.garment, price: o.price, quantity: 1 }],
      statusHistory: [], serverCreatedAt: admin.firestore.FieldValue.serverTimestamp(), createdAt: now, updatedAt: now,
    });
    batch.set(db.doc(`users/${uid}/orders/${o.id}/private/money`), {
      ownerId: uid, orderId: o.id, totalPrice: o.price, discount: 0, discountReason: null,
      payments, costs: [], itemPrices: { [itemId]: o.price },
    });
  }
  await batch.commit();

  console.log(`Emulator staff setup complete: Fola (owner) + Gabby (staff, claim+membership) + ` +
    `${CUSTOMERS.length} customers, ${ORDERS.length} orders on Fola's tree.`);
  console.log('Sign in on the emulator-connected app: fola@gmail.com/fola123 (owner) or gabby@gmail.com/gabby123 (staff).');
}

main().then(() => process.exit(0), (err) => { console.error(err); process.exit(1); });
