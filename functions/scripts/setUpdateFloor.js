#!/usr/bin/env node
/*
 * Slice 8c — set the force-update floor in config/app so every ACTIVE user is on
 * a build that reads money/contact from the /private sub-docs (Slice 8a) before
 * the base-strip (Slice 8d). The app's AppGate force-updates any build whose
 * versionCode / CFBundleVersion is BELOW the floor (fail-open: a null floor never
 * gates). See core/config/domain/AppGate.kt + AppConfig.kt.
 *
 * This is a HARD gate: below-floor users are blocked from the app until they
 * update. Set it only when you are comfortable forcing the remaining tail — ideally
 * after the 8a release has had time to reach most users organically, so few people
 * actually hit the wall.
 *
 * The floor values are the BUILD NUMBERS of the 8a release:
 *   - Android: versionCode of the 8a release APK/AAB
 *   - iOS:     CURRENT_PROJECT_VERSION (CFBundleVersion) of the 8a release
 *
 * Usage:
 *   # dry run (default) — prints current vs new, writes nothing:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 ANDROID_FLOOR=NNN IOS_FLOOR=NNN \
 *     node scripts/setUpdateFloor.js
 *   # apply:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 ANDROID_FLOOR=NNN IOS_FLOOR=NNN \
 *     node scripts/setUpdateFloor.js --commit
 *
 * To LOWER/REMOVE the floor again (rollback), pass 0 or `null`:
 *   ANDROID_FLOOR=null IOS_FLOOR=null ... --commit
 *
 * Auth: application-default credentials with Firestore access, e.g.
 *   gcloud auth application-default login
 */
const admin = require('firebase-admin');

function parseFloor(name) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') {
    throw new Error(`Missing env ${name} (build number, or "null" to clear the floor).`);
  }
  if (raw === 'null') return null;
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 0) {
    throw new Error(`${name} must be a non-negative integer or "null", got: ${raw}`);
  }
  return n;
}

async function main() {
  const commit = process.argv.includes('--commit');
  const androidFloor = parseFloor('ANDROID_FLOOR');
  const iosFloor = parseFloor('IOS_FLOOR');

  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = admin.firestore();
  const ref = db.doc('config/app');

  const before = (await ref.get()).data() || {};
  console.log('current config/app floors:', {
    minSupportedBuildAndroid: before.minSupportedBuildAndroid ?? null,
    minSupportedBuildIos: before.minSupportedBuildIos ?? null,
  });
  console.log('new floors:', {
    minSupportedBuildAndroid: androidFloor,
    minSupportedBuildIos: iosFloor,
  });

  // Warn if the update screen would have no button / copy — an easy mistake that
  // shows a blocking screen with no way forward.
  if (androidFloor !== null && !before.updateUrlAndroid) {
    console.warn('WARN: updateUrlAndroid is unset — the Android update button will be hidden.');
  }
  if (iosFloor !== null && !before.updateUrlIos) {
    console.warn('WARN: updateUrlIos is unset — the iOS update button will be hidden.');
  }
  if ((androidFloor !== null || iosFloor !== null) && !before.forceUpdateMessage) {
    console.warn('WARN: forceUpdateMessage is unset — the screen shows default copy only.');
  }

  if (!commit) {
    console.log('DRY RUN — re-run with --commit to apply.');
    return;
  }

  await ref.set(
    { minSupportedBuildAndroid: androidFloor, minSupportedBuildIos: iosFloor },
    { merge: true },
  );
  console.log('COMMITTED floor to config/app.');
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err.message || err);
    process.exit(1);
  },
);
