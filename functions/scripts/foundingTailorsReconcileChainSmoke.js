#!/usr/bin/env node
/*
 * Backend emulator smoke for Task 1 — the qualifiedAt stamp — via the FULL,
 * REAL reconcile chain (no forged referral docs):
 *
 *   getOrCreateMyReferralLink  → mint a program referrer + code
 *   recordReferralAttribution  → attribute a distinct referred user (window anchored)
 *   [ write 1 customer/day, run reconcileReferrals the next night ] x4
 *
 * This faithfully exercises the Lane-A ratchet: the grader credits at MOST one
 * completed Lagos day per run (floor = lastRunDateKey), so qualification takes 4
 * nightly runs across 4 Lagos dates — exactly what an honest 4-day user needs.
 * Pre-seeding 4 future-dated customers would instead trip future_dated_activity,
 * so each day's customer is written just before the run that should observe it.
 *
 * Asserts:
 *   - milestone advances attributed → activated → qualified
 *   - qualifiedAt is stamped from the EARNING day (the 4th distinct active Lagos
 *     day at noon Lagos = 11:00Z), NOT the grader run instant  (fix bc88d15e)
 *   - a later run never overwrites qualifiedAt (qualified referrals leave the
 *     [attributed, activated] scan set, so the stamp is terminal)
 *   - the marketer's qualified counter is exactly 1
 *   - no blocking fraud flag is present
 *
 * Requires: npm run build, and the Firestore emulator running.
 * Usage (from functions/):
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=stitchpad-30607 \
 *     node scripts/foundingTailorsReconcileChainSmoke.js
 */
const admin = require('firebase-admin');

const { getOrCreateMyReferralLinkHandler } = require('../lib/referral/getOrCreateMyReferralLink');
const { recordReferralAttributionHandler } = require('../lib/referral/recordAttribution');
const { reconcileReferralsHandler } = require('../lib/referral/reconcileReferrals');

const REFERRER_UID = 'ftq_chain_referrer';
const REFERRED_UID = 'ftq_chain_referred';
const REFERRED_EMAIL = 'referred-chain@example.com';
// Attribution anchors the qualification window at this instant (Lagos 07:00, 08-04).
const ATTRIBUTION_ISO = '2026-08-04T06:00:00Z';
const ms = (iso) => new Date(iso).getTime();

let failures = 0;
function check(label, cond, detail) {
  const ok = !!cond;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail !== undefined ? ` — ${detail}` : ''}`);
  if (!ok) failures += 1;
}

async function deleteCollection(db, path) {
  const snap = await db.collection(path).get();
  await Promise.all(snap.docs.map((d) => d.ref.delete()));
}

async function resetState(db) {
  await deleteCollection(db, `users/${REFERRED_UID}/customers`);
  await Promise.all([
    db.doc(`referrals/${REFERRED_UID}`).delete(),
    db.doc(`users/${REFERRED_UID}`).delete(),
    db.doc(`users/${REFERRER_UID}`).delete(),
  ]);
  await Promise.all([
    deleteCollection(db, 'marketers'),
    deleteCollection(db, 'referralCodes'),
    deleteCollection(db, 'referralDevices'),
  ]);
}

async function readReferral(db) {
  const r = (await db.doc(`referrals/${REFERRED_UID}`).get()).data();
  return {
    milestone: r && r.milestone,
    observedDayKeys: (r && r.observedDayKeys) || [],
    flags: (r && r.flags) || [],
    qualifiedAtMs: r && r.qualifiedAt && r.qualifiedAt.toMillis(),
  };
}

async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    console.error('Refusing to run: FIRESTORE_EMULATOR_HOST is not set. This smoke must target the emulator.');
    process.exit(1);
  }
  admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'stitchpad-30607' });
  const db = admin.firestore();
  await resetState(db);

  // --- Mint the referrer's program link ---
  console.log('\n[setup] mint referrer link');
  await db.doc(`users/${REFERRER_UID}`).set({ displayName: 'Ada', businessName: 'Ada Styles', email: 'ada@example.com' });
  let n = 0;
  const link = await getOrCreateMyReferralLinkHandler({}, { auth: { uid: REFERRER_UID, token: {} } }, {
    db, now: () => new Date(ATTRIBUTION_ISO), randomCode: () => `CHAINCODE${n++}`, randomId: () => 'rid',
  });
  check('referrer code minted', !!link.code, link.code);

  // --- Attribute a DISTINCT referred user (no self-referral: different uid + email) ---
  console.log('\n[setup] record attribution for referred user');
  const attrRes = await recordReferralAttributionHandler(
    { code: link.code, deviceHash: 'a'.repeat(64), source: 'manual' },
    { auth: { uid: REFERRED_UID, token: { email: REFERRED_EMAIL } } },
    { db, now: () => new Date(ATTRIBUTION_ISO), userCreationTimeMs: async () => ms(ATTRIBUTION_ISO) },
  );
  check('attribution recorded', attrRes.status === 'attributed', attrRes.status);
  const attr0 = await readReferral(db);
  check('starts at milestone attributed', attr0.milestone === 'attributed', attr0.milestone);
  check('no blocking flag on attribution', attr0.flags.length === 0, JSON.stringify(attr0.flags));

  // --- 4 nights: write day i's customer, then run the grader the next Lagos night ---
  console.log('\n[chain] one customer/day, grader runs the next night x4');
  // Day i active on 2026-08-(04+i) 06:00Z; grader run on 2026-08-(05+i) 02:30Z (Lagos 03:30).
  for (let i = 0; i < 4; i++) {
    const dayIso = `2026-08-0${4 + i}T06:00:00Z`;
    const runIso = `2026-08-0${5 + i}T02:30:00Z`;
    // businessName + a customer satisfy "activated"; each new day feeds the ratchet.
    if (i === 0) await db.doc(`users/${REFERRED_UID}`).set({ businessName: 'Referred Workshop' }, { merge: true });
    await db.doc(`users/${REFERRED_UID}/customers/qa_day_${i}`).set({ name: `Cust ${i}`, createdAt: ms(dayIso) });

    const res = await reconcileReferralsHandler({ db, now: () => new Date(runIso) });
    const state = await readReferral(db);
    console.log(`  run ${runIso.slice(0, 10)}: scanned=${res.scanned} qualified=${res.qualified} → milestone=${state.milestone} observed=${state.observedDayKeys.length} [${state.observedDayKeys.join(',')}]`);
  }

  // --- Assertions after the 4th run ---
  console.log('\n[assert] qualifiedAt stamped from the earning day');
  const q = await readReferral(db);
  check('milestone is qualified', q.milestone === 'qualified', q.milestone);
  check('4 distinct days observed', q.observedDayKeys.length === 4, q.observedDayKeys.join(','));
  check('no blocking flag', q.flags.every((f) => !['self_referral', 'device_reuse', 'velocity'].includes(f)), JSON.stringify(q.flags));
  // Earning day = 4th distinct active Lagos day (2026-08-07) at noon Lagos = 11:00Z.
  const expectedQualifiedAt = ms('2026-08-07T11:00:00Z');
  check('qualifiedAt = earning day (08-07 11:00Z), NOT run day (08-08)',
    q.qualifiedAtMs === expectedQualifiedAt,
    q.qualifiedAtMs ? new Date(q.qualifiedAtMs).toISOString() : 'undefined');

  const codeDoc = (await db.doc(`referralCodes/${link.code}`).get()).data();
  const marketer = (await db.doc(`marketers/${codeDoc.marketerId}`).get()).data();
  check('marketer qualified counter == 1', marketer.qualified === 1, String(marketer.qualified));

  // --- A later grader run must not overwrite the stamp ---
  console.log('\n[assert] later run does not overwrite qualifiedAt');
  const later = await reconcileReferralsHandler({ db, now: () => new Date('2026-08-09T02:30:00Z') });
  const q2 = await readReferral(db);
  check('later run re-qualifies nobody', later.qualified === 0, `qualified=${later.qualified}`);
  check('qualifiedAt unchanged', q2.qualifiedAtMs === expectedQualifiedAt, q2.qualifiedAtMs ? new Date(q2.qualifiedAtMs).toISOString() : 'undefined');
  const marketer2 = (await db.doc(`marketers/${codeDoc.marketerId}`).get()).data();
  check('marketer qualified counter still 1', marketer2.qualified === 1, String(marketer2.qualified));

  console.log(`\n${failures === 0 ? 'ALL CHECKS PASSED' : `${failures} CHECK(S) FAILED`}`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => { console.error('SMOKE ERROR:', e); process.exit(1); });
