#!/usr/bin/env node
/*
 * QA trigger for the Slice 7 payout back-half. Runs the EXACT handlers the
 * nightly schedules (confirmReferralPayouts 04:00, sweepDeletedReferralUsers
 * 04:15) and their admin callables run — invoked locally against production
 * Firestore with admin (ADC) creds, so you don't need to deploy the callables or
 * mint an admin ID token to exercise them.
 *
 * Requires the functions to be BUILT first (loads the compiled handlers):
 *   npm run build            # on the feat/referral-confirm-clawback branch
 *
 * Usage:
 *   node scripts/debugConfirm.js [--uid <uid>] [--sweep | --sweep-only]
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/key.json node scripts/debugConfirm.js --uid <uid> --sweep
 *
 *   (no flag)      run confirmReferralPayouts only
 *   --sweep        run confirm, then the deleted-user sweep
 *   --sweep-only   run the sweep only
 *   --uid <uid>    print that referral (+ marketer aggregates) before and after
 *
 * Typical flow (after seeding a qualified/pending referral via Slice 6 tooling):
 *   node scripts/referralQaTweak.js <uid> --expire-hold   # backdate holdEndsAt
 *   node scripts/debugConfirm.js --uid <uid>              # → confirmed
 * Re-run to confirm idempotency (a confirmed payout is not re-moved).
 */
const admin = require('firebase-admin');

function parseArgs(argv) {
  const opts = { uid: null, sweep: false, sweepOnly: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--uid') opts.uid = argv[++i];
    else if (a === '--sweep') opts.sweep = true;
    else if (a === '--sweep-only') opts.sweepOnly = true;
  }
  return opts;
}

function pickReferral(data) {
  if (!data) return null;
  const { payoutState, payoutAmount, payoutRejectedReason, flags, marketerId } = data;
  return { payoutState, payoutAmount, payoutRejectedReason: payoutRejectedReason ?? null, flags: flags ?? [], marketerId };
}

function pickMarketer(data) {
  if (!data) return null;
  const { pendingAmount, confirmedAmount, paidAmount } = data;
  return { pendingAmount, confirmedAmount, paidAmount };
}

async function snapshot(db, uid) {
  const rSnap = await db.doc(`referrals/${uid}`).get();
  const r = rSnap.data();
  const userExists = (await db.doc(`users/${uid}`).get()).exists;
  let marketer = null;
  if (r?.marketerId) marketer = pickMarketer((await db.doc(`marketers/${r.marketerId}`).get()).data());
  return { referral: pickReferral(r), userExists, marketer };
}

function load(path, name) {
  try {
    return require(path)[name];
  } catch (e) {
    console.error(`Could not load ${path} — run \`npm run build\` first (on the confirm-clawback branch).`);
    console.error(e.message);
    process.exit(1);
  }
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));

  admin.initializeApp();
  const db = admin.firestore();

  const confirmHandler = load('../lib/referral/confirmReferralPayouts', 'confirmReferralPayoutsHandler');
  const sweepHandler = load('../lib/referral/sweepDeletedReferralUsers', 'sweepDeletedReferralUsersHandler');

  if (opts.uid) console.log('BEFORE:', JSON.stringify(await snapshot(db, opts.uid), null, 2));

  if (!opts.sweepOnly) {
    const r = await confirmHandler({ db, now: () => new Date() });
    console.log('\nconfirmReferralPayouts result:', JSON.stringify(r)); // { scanned, confirmed, rejected, failed }
  }
  if (opts.sweep || opts.sweepOnly) {
    const r = await sweepHandler({ db, now: () => new Date() });
    console.log('sweepDeletedReferralUsers result:', JSON.stringify(r)); // { scanned, clawedBack, failed }
  }

  if (opts.uid) console.log('\nAFTER:', JSON.stringify(await snapshot(db, opts.uid), null, 2));
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
