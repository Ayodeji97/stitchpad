#!/usr/bin/env node
/*
 * One-off: grant (or revoke) the Firebase Auth `admin: true` custom claim used
 * by the referral admin callables (createMarketer, getReferralDashboard,
 * exportReferralsCsv, markReferralPaid).
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/serviceAccount.json \
 *     node scripts/setAdminClaim.js <email|uid> [--revoke]
 *
 * Notes:
 *   - Requires a service-account key with the "Firebase Authentication Admin"
 *     role (or an env with matching application-default credentials).
 *   - The claim takes effect on the user's NEXT ID-token refresh — have them
 *     sign out/in to pick it up immediately (otherwise up to ~1h).
 *   - This is intentionally NOT a Cloud Function: granting admin from a callable
 *     would itself need an admin gate, which is the chicken-and-egg we avoid by
 *     bootstrapping out-of-band.
 */
const admin = require('firebase-admin');

async function main() {
  const arg = process.argv[2];
  const revoke = process.argv.includes('--revoke');
  if (!arg) {
    console.error('Usage: node scripts/setAdminClaim.js <email|uid> [--revoke]');
    process.exit(1);
  }

  admin.initializeApp();
  const auth = admin.auth();

  const user = arg.includes('@') ? await auth.getUserByEmail(arg) : await auth.getUser(arg);
  const claims = { ...(user.customClaims || {}) };
  if (revoke) {
    delete claims.admin;
  } else {
    claims.admin = true;
  }
  await auth.setCustomUserClaims(user.uid, claims);

  console.log(
    `${revoke ? 'Revoked' : 'Granted'} admin for ${user.email || user.uid} (uid=${user.uid}).`,
  );
  console.log('Effect: next ID-token refresh — have them sign out/in to pick it up now.');
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
