#!/usr/bin/env node
/*
 * Backend emulator smoke for the Founding Tailors leaderboard tickets.
 *
 * Drives the REAL compiled handlers (from lib/) against the local Firestore
 * emulator, exactly like debugReconcile.js does for the grader. No deployed
 * functions, no auth tokens, no production data.
 *
 * Covers, in order:
 *   1. getOrCreateMyReferralLink  — mints a payout-disabled, program-tagged
 *      user-referrer + code + users/{uid}.referralCode.
 *   2. idempotency               — a second mint returns the SAME code, no
 *      second marketer.
 *   3. aggregateFoundingTailorsLeaderboard — awards TIERED points (activation +
 *      per-active-day, from observedDayKeys) to non-blocked, program referrals,
 *      bucketed by the Lagos month each point was earned in; excludes
 *      affiliates + fraud flags.
 *   4. getFoundingTailorsLeaderboard — ranked top rows carry NO marketerIds and
 *      resolve you={rank,points} from the referrer's code.
 *
 * Note: this forges the upstream `referrals/{uid}` docs directly (milestone +
 * observedDayKeys), which is the exact shape reconcileReferrals writes. The
 * observedDayKeys ratchet itself is covered by reconcileReferrals.test.ts and
 * the debugReconcile.js path — not re-proven here.
 *
 * Requires the functions to be BUILT (npm run build) and the emulator running:
 *   firebase emulators:start --config firebase.emulator.json
 *
 * Usage (from functions/):
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=stitchpad-30607 \
 *     node scripts/foundingTailorsSmoke.js
 */
const admin = require('firebase-admin');

const {
  getOrCreateMyReferralLinkHandler,
} = require('../lib/referral/getOrCreateMyReferralLink');
const {
  aggregateFoundingTailorsLeaderboardHandler,
  getFoundingTailorsLeaderboardHandler,
  monthKeyLagos,
} = require('../lib/referral/foundingTailorsLeaderboard');

const REFERRER_UID = 'ftq_referrer';
const NOW = new Date('2026-08-04T10:00:00Z');
const ts = (iso) => admin.firestore.Timestamp.fromDate(new Date(iso));

let failures = 0;
function check(label, cond, detail) {
  const ok = !!cond;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failures += 1;
}

async function resetState(db) {
  // Wipe the docs this smoke touches so re-runs start clean.
  const paths = [
    `users/${REFERRER_UID}`,
    'leaderboards/current',
    'leaderboards/alltime',
    `leaderboards/${monthKeyLagos(NOW.getTime())}`,
    'referrals/ref_clean_1',
    'referrals/ref_clean_2',
    'referrals/ref_blocked',
    'referrals/ref_affiliate',
  ];
  await Promise.all(paths.map((p) => db.doc(p).delete()));
  for (const coll of ['marketers', 'referralCodes']) {
    const snap = await db.collection(coll).get();
    await Promise.all(snap.docs.map((d) => d.ref.delete()));
  }
}

async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    console.error('Refusing to run: FIRESTORE_EMULATOR_HOST is not set. This smoke must target the emulator.');
    process.exit(1);
  }
  admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'stitchpad-30607' });
  const db = admin.firestore();

  await resetState(db);

  // Seed the referrer's user doc so the mint names them from businessName.
  await db.doc(`users/${REFERRER_UID}`).set({
    displayName: 'Ada', businessName: 'Ada Styles', email: 'ada@example.com',
  });

  // --- 1. Mint the referral link (real handler) ---
  console.log('\n[1] getOrCreateMyReferralLink — first mint');
  const ctx = { auth: { uid: REFERRER_UID, token: {} } };
  let n = 0;
  const deps = {
    db,
    now: () => NOW,
    randomCode: () => `FTQCODE${n++}`,
    randomId: () => 'rid',
  };
  const link1 = await getOrCreateMyReferralLinkHandler({}, ctx, deps);
  check('mint returns a code', link1.code === 'FTQCODE0', `code=${link1.code}`);
  check('url is the short link', link1.url === `https://link.getstitchpad.com/r/${link1.code}`, link1.url);

  const codeDoc = (await db.doc(`referralCodes/${link1.code}`).get()).data();
  const marketerId = codeDoc && codeDoc.marketerId;
  const marketer = marketerId ? (await db.doc(`marketers/${marketerId}`).get()).data() : null;
  check('marketer is program-tagged', marketer && marketer.program === 'founding_tailors', marketer && marketer.program);
  check('marketer is payout-disabled', marketer && marketer.payoutRatePerUser === 0, marketer && String(marketer.payoutRatePerUser));
  check('marketer type=user, referrerUid set', marketer && marketer.type === 'user' && marketer.referrerUid === REFERRER_UID);
  check('marketer named from businessName', marketer && marketer.name === 'Ada Styles', marketer && marketer.name);
  const userAfter = (await db.doc(`users/${REFERRER_UID}`).get()).data();
  check('users/{uid}.referralCode stamped', userAfter && userAfter.referralCode === link1.code, userAfter && userAfter.referralCode);

  // --- 2. Idempotency ---
  console.log('\n[2] getOrCreateMyReferralLink — second call (idempotent)');
  const link2 = await getOrCreateMyReferralLinkHandler({}, ctx, deps);
  check('same code returned', link2.code === link1.code, `${link2.code} vs ${link1.code}`);
  const marketerCount = (await db.collection('marketers').get()).size;
  check('no second marketer minted', marketerCount === 1, `marketers=${marketerCount}`);

  // --- 3. Seed activated/qualified referrals + run the aggregator ---
  console.log('\n[3] aggregateFoundingTailorsLeaderboard');
  // Fully qualified referral, 4 active days → 5 points (activation + 4 days).
  await db.doc('referrals/ref_clean_1').set({
    marketerId, milestone: 'qualified', qualifiedAt: ts('2026-08-02T09:00:00Z'), flags: [],
    observedDayKeys: ['2026-08-01', '2026-08-02', '2026-08-03', '2026-08-04'],
  });
  // Activated referral, 1 active day → 2 points (activation + day-1).
  await db.doc('referrals/ref_clean_2').set({
    marketerId, milestone: 'activated', flags: [], observedDayKeys: ['2026-08-02'],
  });
  // A fraud-flagged one for the same referrer — must NOT count, despite 4 active days.
  await db.doc('referrals/ref_blocked').set({
    marketerId, milestone: 'qualified', qualifiedAt: ts('2026-08-03T10:00:00Z'), flags: ['self_referral'],
    observedDayKeys: ['2026-08-03', '2026-08-04', '2026-08-05', '2026-08-06'],
  });
  // An affiliate (non-program) marketer's qualified referral — must be excluded.
  await db.doc('marketers/mAff').set({ type: 'affiliate', name: 'Paid Affiliate', program: 'affiliate' });
  await db.doc('referrals/ref_affiliate').set({
    marketerId: 'mAff', milestone: 'qualified', qualifiedAt: ts('2026-08-03T11:00:00Z'), flags: [],
    observedDayKeys: ['2026-08-03'],
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => NOW });

  const monthId = monthKeyLagos(NOW.getTime());
  const board = (await db.doc(`leaderboards/${monthId}`).get()).data();
  const myEntry = board && board.entries && board.entries.find((e) => e.marketerId === marketerId);
  check('board doc exists for current month', !!board, `leaderboards/${monthId}`);
  check('referrer has exactly 7 points', myEntry && myEntry.points === 7, myEntry && `points=${myEntry.points}`);
  check('fraud-flagged referral excluded', myEntry && myEntry.points === 7);
  check('affiliate excluded from board', board && !board.entries.some((e) => e.marketerId === 'mAff'));
  const current = (await db.doc('leaderboards/current').get()).data();
  check('leaderboards/current points to this month', current && current.monthId === monthId, current && current.monthId);

  // --- 4. Public read callable ---
  console.log('\n[4] getFoundingTailorsLeaderboard (public read)');
  const res = await getFoundingTailorsLeaderboardHandler({ code: link1.code }, { db });
  check('monthId returned', res.monthId === monthId, res.monthId);
  check('top row has name + points', res.top[0] && res.top[0].name === 'Ada Styles' && res.top[0].points === 7, JSON.stringify(res.top[0]));
  check('top rows leak NO marketerId', res.top.every((r) => r.marketerId === undefined));
  check('you resolves rank + points from code', res.you && res.you.rank === 1 && res.you.points === 7, JSON.stringify(res.you));

  const resUnknown = await getFoundingTailorsLeaderboardHandler({ code: 'NOPE0000' }, { db });
  check('unknown code yields you=null', resUnknown.you === null);

  console.log(`\n${failures === 0 ? 'ALL CHECKS PASSED' : `${failures} CHECK(S) FAILED`}`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => { console.error('SMOKE ERROR:', e); process.exit(1); });
