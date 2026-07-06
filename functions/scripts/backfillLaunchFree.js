#!/usr/bin/env node
/*
 * One-off backfill: place every EXISTING user on the launch-free grant
 * (tier=atelier, tagged grantSource=launch_free) so no one hits a paywall
 * during the 2026 free-for-everyone period. Idempotent and safe to re-run.
 *
 * Skips anyone already on an ACTIVE paid tier — real Apple/Paystack/gift
 * subscribers and manual tester grants are left untouched.
 *
 * Field shape + predicate MUST stay in lockstep with
 * functions/src/freemium/launchGrant.ts.
 *
 * Usage:
 *   # dry run (default) — prints what WOULD change, writes nothing:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js
 *   # apply:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js --commit
 *
 * Auth: application-default credentials with Firestore access, e.g.
 *   gcloud auth application-default login
 */
const admin = require('firebase-admin');

const LAUNCH_GRANT_SOURCE = 'launch_free';

function shouldGrant(data) {
  const tier = data.subscriptionTier;
  const isPaidTier = tier === 'pro' || tier === 'atelier';
  const isActive = data.subscriptionStatus === 'active';
  return !(isPaidTier && isActive);
}

async function main() {
  const commit = process.argv.includes('--commit');
  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = admin.firestore();

  const snap = await db.collection('users').get();
  const now = admin.firestore.Timestamp.now();

  let granted = 0;
  let skipped = 0;
  let batch = db.batch();
  let pending = 0;

  for (const doc of snap.docs) {
    if (!shouldGrant(doc.data())) {
      skipped += 1;
      continue;
    }
    granted += 1;
    if (commit) {
      batch.set(
        doc.ref,
        {
          subscriptionTier: 'atelier',
          subscriptionStatus: 'active',
          grantSource: LAUNCH_GRANT_SOURCE,
          grantedAt: now,
          updatedAt: now,
        },
        { merge: true },
      );
      pending += 1;
      if (pending === 400) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
  }
  if (commit && pending > 0) {
    await batch.commit();
  }

  console.log(
    `${commit ? 'COMMITTED' : 'DRY RUN'} — total=${snap.size} granted=${granted} skipped=${skipped}`,
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
