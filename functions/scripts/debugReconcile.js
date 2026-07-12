#!/usr/bin/env node
/*
 * QA trigger for the Slice 6 grader. Runs the EXACT reconcileReferralsHandler that
 * the nightly schedule (reconcileReferrals) and the admin callable
 * (debugReconcileReferrals) run — just invoked locally against production Firestore
 * with admin (ADC) credentials, so you don't need to deploy the callable or mint an
 * admin ID token to exercise the grader.
 *
 * Requires the functions to be BUILT first (it loads the compiled handler):
 *   npm run build            # on the feat/referral-reconcile branch
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/serviceAccount.json \
 *     node scripts/debugReconcile.js [--uid <uid>]
 *   # or with application-default creds:
 *   node scripts/debugReconcile.js --uid G4N3b5gRxzfDehXpFGzt3O3NhzS2
 *
 * With --uid it prints that referral (+ its marketer aggregates) before and after
 * the run, so you can eyeball attributed→qualified / payoutState none→pending and
 * the marketer counter bumps in one shot. Re-run it to confirm idempotency (a
 * second run over an already-qualified referral must change nothing).
 */
const admin = require('firebase-admin');

function parseArgs(argv) {
  const opts = { uid: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--uid') opts.uid = argv[++i];
  }
  return opts;
}

function pickReferral(data) {
  if (!data) return null;
  const { milestone, payoutState, payoutAmount, activeDays, holdEndsAt, marketerId } = data;
  return { milestone, payoutState, payoutAmount, activeDays, holdEndsAt: holdEndsAt?.toDate?.().toISOString() ?? null, marketerId };
}

function pickMarketer(data) {
  if (!data) return null;
  const { installs, activated, qualified, pendingAmount, payoutRatePerUser } = data;
  return { installs, activated, qualified, pendingAmount, payoutRatePerUser };
}

async function snapshot(db, uid) {
  const rSnap = await db.doc(`referrals/${uid}`).get();
  const r = rSnap.data();
  const referral = pickReferral(r);
  let marketer = null;
  if (r?.marketerId) {
    marketer = pickMarketer((await db.doc(`marketers/${r.marketerId}`).get()).data());
  }
  return { referral, marketer };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));

  admin.initializeApp();
  const db = admin.firestore();

  // Load the compiled handler (built from src/referral/reconcileReferrals.ts).
  let reconcileReferralsHandler;
  try {
    ({ reconcileReferralsHandler } = require('../lib/referral/reconcileReferrals'));
  } catch (e) {
    console.error('Could not load ../lib/referral/reconcileReferrals — run `npm run build` first (on the reconcile branch).');
    console.error(e.message);
    process.exit(1);
  }

  if (opts.uid) {
    console.log('BEFORE:', JSON.stringify(await snapshot(db, opts.uid), null, 2));
  }

  const result = await reconcileReferralsHandler({ db, now: () => new Date() });
  console.log('\nreconcileReferrals result:', JSON.stringify(result)); // { scanned, activated, qualified }

  if (opts.uid) {
    console.log('\nAFTER:', JSON.stringify(await snapshot(db, opts.uid), null, 2));
  }
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
