#!/usr/bin/env node
/*
 * QA seed: make an already-attributed referral QUALIFY, so the Slice 6 grader
 * (reconcileReferrals / debugReconcileReferrals) has something real to promote.
 *
 * It does exactly what a genuinely-engaged tailor would do in their first 14 days:
 *   - sets users/{uid}.businessName            → satisfies "activated"
 *   - writes N customers on N distinct Lagos    → satisfies "qualified"
 *     calendar days inside the qualification       (needs QUALIFY_DISTINCT_DAYS = 4)
 *     window
 * createdAt on each customer is a plain epoch-millis Long (exactly what the app
 * writes and what gatherSignals reads), NOT a server Timestamp.
 *
 * It does NOT flip any milestone/payout fields itself — that is the grader's job.
 * After running this, call debugReconcileReferrals (admin) and watch the referral
 * go attributed → qualified, payoutState → pending.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/serviceAccount.json \
 *     node scripts/seedReferralQualification.js <uid> [options]
 *   # or, with application-default creds:
 *   node scripts/seedReferralQualification.js <uid>
 *
 * Options:
 *   --business "Name"   businessName to set (default "QA Test Workshop")
 *   --days N            distinct active days to seed (default 4 = QUALIFY_DISTINCT_DAYS)
 *   --refresh-window    reset signupAt=now & qualificationWindowEndsAt=now+14d before
 *                       seeding — use when the original window has already closed so
 *                       the grader's "within window (+2d grace)" scan still picks it up
 *   --dry-run          print the plan without writing
 *
 * Notes:
 *   - The referral must already exist (run recordReferralAttribution first). This
 *     script never creates referrals/{uid} or invents an attribution.
 *   - Idempotent-ish: re-running overwrites the same N seeded customer docs
 *     (fixed ids qa_seed_day_0..N-1), so it won't pile up duplicates.
 */
const admin = require('firebase-admin');

const DAY_MS = 24 * 60 * 60 * 1000;
const QUALIFY_WINDOW_DAYS = 14;
const DEFAULT_DISTINCT_DAYS = 4; // mirror QUALIFY_DISTINCT_DAYS
const SEED_HOUR_OFFSET_MS = 6 * 60 * 60 * 1000; // 06:00-ish into each day, clear of midnight
const LAGOS_OFFSET_MS = 60 * 60 * 1000; // Africa/Lagos = UTC+1, no DST (display only)

function parseArgs(argv) {
  const opts = { business: 'QA Test Workshop', days: DEFAULT_DISTINCT_DAYS, refreshWindow: false, dryRun: false };
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--business') opts.business = argv[++i];
    else if (a === '--days') opts.days = parseInt(argv[++i], 10);
    else if (a === '--refresh-window') opts.refreshWindow = true;
    else if (a === '--dry-run') opts.dryRun = true;
    else rest.push(a);
  }
  opts.uid = rest[0];
  return opts;
}

function lagosDay(ms) {
  return new Date(ms + LAGOS_OFFSET_MS).toISOString().slice(0, 10);
}

async function main() {
  const opts = parseArgs(process.argv.slice(2));
  if (!opts.uid || !Number.isFinite(opts.days) || opts.days < 1) {
    console.error('Usage: node scripts/seedReferralQualification.js <uid> [--business "Name"] [--days N] [--refresh-window] [--dry-run]');
    process.exit(1);
  }

  admin.initializeApp();
  const db = admin.firestore();
  const nowMs = Date.now();

  const referralRef = db.doc(`referrals/${opts.uid}`);
  const referralSnap = await referralRef.get();
  if (!referralSnap.exists) {
    console.error(`No referrals/${opts.uid} — run recordReferralAttribution (sign up with the code) first.`);
    process.exit(1);
  }
  const r = referralSnap.data();
  console.log(`referrals/${opts.uid}: milestone=${r.milestone} payoutState=${r.payoutState} marketerId=${r.marketerId}`);
  if (r.milestone === 'qualified') {
    console.warn('  ! Already qualified — the grader is idempotent and will not re-open a payout. Nothing to prove by re-seeding.');
  }

  let signupMs = r.signupAt?.toMillis?.() ?? nowMs;
  let windowEndMs = r.qualificationWindowEndsAt?.toMillis?.() ?? signupMs + QUALIFY_WINDOW_DAYS * DAY_MS;

  const windowClosedForScan = windowEndMs < nowMs - 2 * DAY_MS; // grader grace = 2 days
  if (opts.refreshWindow || windowClosedForScan) {
    if (windowClosedForScan && !opts.refreshWindow) {
      console.warn(`  ! Window closed (ended ${lagosDay(windowEndMs)}); resetting signup window so the grader still scans it.`);
    }
    signupMs = nowMs;
    windowEndMs = nowMs + QUALIFY_WINDOW_DAYS * DAY_MS;
  }

  // Place `days` writes on distinct Lagos days, each safely inside [signupMs, windowEndMs).
  const baseMs = signupMs;
  const customers = [];
  for (let i = 0; i < opts.days; i++) {
    const createdAt = baseMs + i * DAY_MS + SEED_HOUR_OFFSET_MS;
    if (createdAt >= windowEndMs) {
      console.error(`  ! day ${i} (${lagosDay(createdAt)}) falls outside the window — reduce --days or --refresh-window.`);
      process.exit(1);
    }
    customers.push({ id: `qa_seed_day_${i}`, createdAt });
  }

  const distinctDays = new Set(customers.map((c) => lagosDay(c.createdAt)));
  console.log('\nPlan:');
  console.log(`  users/${opts.uid}.businessName = ${JSON.stringify(opts.business)}`);
  if (opts.refreshWindow || windowClosedForScan) {
    console.log(`  signupAt → ${lagosDay(signupMs)}, qualificationWindowEndsAt → ${lagosDay(windowEndMs)}`);
  }
  for (const c of customers) {
    console.log(`  users/${opts.uid}/customers/${c.id}.createdAt = ${c.createdAt} (${lagosDay(c.createdAt)})`);
  }
  console.log(`  → ${distinctDays.size} distinct Lagos day(s); qualifies = ${distinctDays.size >= DEFAULT_DISTINCT_DAYS}`);

  if (opts.dryRun) {
    console.log('\n[dry-run] no writes made.');
    return;
  }

  const nowTs = admin.firestore.Timestamp.fromMillis(nowMs);
  const batch = db.batch();
  batch.set(db.doc(`users/${opts.uid}`), { businessName: opts.business, updatedAt: nowTs }, { merge: true });
  if (opts.refreshWindow || windowClosedForScan) {
    batch.set(
      referralRef,
      {
        signupAt: admin.firestore.Timestamp.fromMillis(signupMs),
        qualificationWindowEndsAt: admin.firestore.Timestamp.fromMillis(windowEndMs),
        updatedAt: nowTs,
      },
      { merge: true },
    );
  }
  for (const c of customers) {
    // A minimal customer doc — only createdAt matters to the grader, but give it a
    // name so it looks like real data in the console.
    batch.set(db.doc(`users/${opts.uid}/customers/${c.id}`), {
      name: `QA Seed ${c.id}`,
      createdAt: c.createdAt,
    });
  }
  await batch.commit();

  console.log('\nSeeded. Now call debugReconcileReferrals (admin) and re-check the referral:');
  console.log('  expect milestone=qualified, payoutState=pending, payoutAmount=<marketer.payoutRatePerUser>,');
  console.log(`  holdEndsAt≈now+7d, activeDays=${distinctDays.size}; marketer qualified+1 / activated+1 / pendingAmount+=rate.`);
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
