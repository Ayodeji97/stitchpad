#!/usr/bin/env node
/*
 * QA state-tweaker for the referral payout lifecycle. Lets you reach the Slice 7
 * branches in seconds instead of waiting out the ~7-day hold or deleting a real
 * Auth account. It only nudges test state — it never moves money or changes
 * payoutState itself; the confirm/clawback/sweep handlers do that (that's the point
 * of the smoke test).
 *
 * Usage:
 *   node scripts/referralQaTweak.js <uid> [ops...]
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/key.json node scripts/referralQaTweak.js <uid> --expire-hold
 *
 * Ops (combine freely; --show/--dry-run print without writing):
 *   --expire-hold        set holdEndsAt to 1 minute ago → confirmReferralPayouts will act
 *   --flag <name>        add a fraud flag (self_referral | device_reuse | velocity)
 *   --clear-flags        reset flags to []
 *   --delete-user        delete the users/{uid} doc (NOT Auth, NOT subcollections) so
 *                        clawback/sweep see the account as gone
 *   --show               print current referral (+ marketer) state and exit
 *   --dry-run            print the planned writes without committing
 *
 * Reset a run: --clear-flags re-arms a rejected-by-flag referral for the confirm
 * path (though payoutState won't rewind — re-seed a fresh referral to re-test from
 * pending).
 */
const admin = require('firebase-admin');

const VALID_FLAGS = ['self_referral', 'device_reuse', 'velocity'];

function parseArgs(argv) {
  const opts = { uid: null, expireHold: false, addFlag: null, clearFlags: false, deleteUser: false, show: false, dryRun: false };
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--expire-hold') opts.expireHold = true;
    else if (a === '--flag') opts.addFlag = argv[++i];
    else if (a === '--clear-flags') opts.clearFlags = true;
    else if (a === '--delete-user') opts.deleteUser = true;
    else if (a === '--show') opts.show = true;
    else if (a === '--dry-run') opts.dryRun = true;
    else rest.push(a);
  }
  opts.uid = rest[0];
  return opts;
}

function pickReferral(data) {
  if (!data) return null;
  const { payoutState, payoutAmount, payoutRejectedReason, flags, holdEndsAt, marketerId } = data;
  return {
    payoutState,
    payoutAmount,
    payoutRejectedReason: payoutRejectedReason ?? null,
    flags: flags ?? [],
    holdEndsAt: holdEndsAt?.toDate?.().toISOString() ?? null,
    marketerId,
  };
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (!opts.uid) {
    console.error('Usage: node scripts/referralQaTweak.js <uid> [--expire-hold] [--flag <name>] [--clear-flags] [--delete-user] [--show] [--dry-run]');
    process.exit(1);
  }
  if (opts.addFlag && !VALID_FLAGS.includes(opts.addFlag)) {
    console.error(`--flag must be one of: ${VALID_FLAGS.join(', ')}`);
    process.exit(1);
  }

  admin.initializeApp();
  const db = admin.firestore();

  const referralRef = db.doc(`referrals/${opts.uid}`);
  const snap = await referralRef.get();
  if (!snap.exists) {
    console.error(`No referrals/${opts.uid} — seed/attribute one first.`);
    process.exit(1);
  }
  const data = snap.data();
  const userExists = (await db.doc(`users/${opts.uid}`).get()).exists;
  console.log('BEFORE:', JSON.stringify({ referral: pickReferral(data), userExists }, null, 2));

  if (opts.show) return;

  const nowMs = Date.now();
  const nowTs = admin.firestore.Timestamp.fromMillis(nowMs);
  const update = { updatedAt: nowTs };
  const plan = [];

  if (opts.expireHold) {
    update.holdEndsAt = admin.firestore.Timestamp.fromMillis(nowMs - 60 * 1000);
    plan.push('holdEndsAt → 1 min ago');
  }
  if (opts.clearFlags) {
    update.flags = [];
    plan.push('flags → []');
  }
  if (opts.addFlag) {
    // Respect --clear-flags if both given (clear wins as the base), else append.
    const base = opts.clearFlags ? [] : (data.flags ?? []);
    update.flags = Array.from(new Set([...base, opts.addFlag]));
    plan.push(`flags += ${opts.addFlag} → [${update.flags.join(', ')}]`);
  }

  const willTouchReferral = Object.keys(update).length > 1; // more than just updatedAt
  if (!willTouchReferral && !opts.deleteUser) {
    console.log('\nNo ops given. Use --expire-hold / --flag / --clear-flags / --delete-user (or --show).');
    return;
  }

  console.log('\nPlan:');
  for (const p of plan) console.log(`  referrals/${opts.uid}: ${p}`);
  if (opts.deleteUser) console.log(`  users/${opts.uid}: DELETE doc (Auth + subcollections untouched)`);

  if (opts.dryRun) {
    console.log('\n[dry-run] no writes made.');
    return;
  }

  if (willTouchReferral) await referralRef.set(update, { merge: true });
  if (opts.deleteUser) await db.doc(`users/${opts.uid}`).delete();

  const after = (await referralRef.get()).data();
  const userExistsAfter = (await db.doc(`users/${opts.uid}`).get()).exists;
  console.log('\nAFTER:', JSON.stringify({ referral: pickReferral(after), userExists: userExistsAfter }, null, 2));
  console.log('\nNow run: node scripts/debugConfirm.js --uid ' + opts.uid + (opts.deleteUser ? ' --sweep' : ''));
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
