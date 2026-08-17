/**
 * Send ONE engagement push to your own devices, against PRODUCTION.
 *
 * Why this exists: a physical phone cannot reach the local emulators
 * (firebaseEmulatorHost() is 127.0.0.1 / 10.0.2.2), so seeing a real notification on
 * a real device — especially an iPhone — has to go through the production project.
 * The scheduled job only fires Tue/Fri, and `debugSendMyEngagementPush` needs a signed
 * ID token, so neither is convenient for "show me the notification right now".
 *
 * SAFETY:
 *   - Read-only against Firestore. Writes NOTHING: no config doc, no digestState, no
 *     token pruning. It cannot alter cadence or per-user caps.
 *   - Targets exactly one account — the email you pass.
 *   - Lists tokens and exits unless you pass --send. Nothing is delivered by accident.
 *
 * The payload is byte-for-byte what engagementPush.ts sends, so what you see on the
 * device is what the real job produces.
 *
 * Usage (from functions/, with production credentials — `gcloud auth application-default login`):
 *   node scripts/sendTestEngagementPush.js you@example.com            # list devices, send nothing
 *   node scripts/sendTestEngagementPush.js you@example.com --send     # actually deliver
 *   node scripts/sendTestEngagementPush.js you@example.com --send --target inbox
 */
const admin = require('firebase-admin');

const PROJECT_ID = 'stitchpad-30607';
const ANNOUNCEMENTS_CHANNEL_ID = 'announcements';
const ANNOUNCEMENT_NOTIFICATION_TAG = 'stitchpad_announcement';
const VALID_TARGETS = ['inbox', 'to_collect', 'dashboard', 'founding_tailors'];

const args = process.argv.slice(2);
const email = args.find((a) => !a.startsWith('--'));
const doSend = args.includes('--send');
const targetArg = args[args.indexOf('--target') + 1];
const target = args.includes('--target') ? targetArg : 'founding_tailors';

if (!email) {
  console.error('Usage: node scripts/sendTestEngagementPush.js <email> [--send] [--target <t>]');
  process.exit(1);
}
if (!VALID_TARGETS.includes(target)) {
  console.error(`--target must be one of: ${VALID_TARGETS.join(', ')}`);
  process.exit(1);
}
// Guard against a stray emulator env var silently pointing this at a local emulator
// (where it would find no real devices and look mysteriously broken).
for (const v of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST']) {
  if (process.env[v]) {
    console.error(`${v} is set (${process.env[v]}). This script targets PRODUCTION — unset it first:`);
    console.error(`  unset ${v}`);
    process.exit(1);
  }
}

admin.initializeApp({ projectId: PROJECT_ID });

(async () => {
  const user = await admin.auth().getUserByEmail(email).catch(() => null);
  if (!user) {
    console.error(`No production account for ${email}.`);
    process.exit(1);
  }
  console.log(`Account : ${email}\nUid     : ${user.uid}`);

  const snap = await admin.firestore()
    .collection('users').doc(user.uid).collection('notificationTokens').get();
  const tokens = snap.docs.map((d) => ({ token: d.id, platform: d.data().platform || '?' }));

  if (tokens.length === 0) {
    console.error('\nNo registered devices.');
    console.error('Open the app on the device, sign in as this account, and ALLOW notifications.');
    console.error('iOS re-registers on every foreground; Android registers on a new token or sign-in.');
    process.exit(1);
  }

  console.log(`\nRegistered devices (${tokens.length}):`);
  for (const t of tokens) console.log(`  [${t.platform}] ${t.token.slice(0, 28)}…`);

  if (!doSend) {
    console.log('\nDry run — nothing sent. Re-run with --send to deliver.');
    process.exit(0);
  }

  // Byte-for-byte the payload engagementPush.ts builds.
  const message = {
    tokens: tokens.map((t) => t.token),
    notification: {
      title: 'Founding Tailors',
      body: 'Invite a tailor friend. Top 3 each month win a free StitchPad shirt.',
    },
    android: {
      notification: { channelId: ANNOUNCEMENTS_CHANNEL_ID, tag: ANNOUNCEMENT_NOTIFICATION_TAG },
    },
    apns: { payload: { aps: { 'interruption-level': 'passive' } } },
    data: { target },
  };

  console.log(`\nSending (target: ${target})…`);
  const res = await admin.messaging().sendEachForMulticast(message);
  console.log(`success: ${res.successCount}  failure: ${res.failureCount}\n`);

  res.responses.forEach((r, i) => {
    const t = tokens[i];
    if (r.success) { console.log(`  ✓ [${t.platform}] ${t.token.slice(0, 28)}…`); return; }
    console.log(`  ✗ [${t.platform}] ${t.token.slice(0, 28)}…  ${r.error?.code}`);
    console.log(`      ${r.error?.message}`);
    // The two failures worth naming, because both look identical from the device
    // ("nothing arrived") and have completely different fixes.
    if (String(r.error?.code).includes('third-party-auth') ||
        String(r.error?.message || '').toLowerCase().includes('apns')) {
      console.log('      → APNs is not configured for this project. Firebase Console →');
      console.log('        Project settings → Cloud Messaging → Apple app configuration →');
      console.log('        upload the APNs Authentication Key (.p8). Without it iOS push');
      console.log('        NEVER delivers, silently.');
    }
    if (String(r.error?.code).includes('registration-token-not-registered')) {
      console.log('      → Stale token (app uninstalled/reinstalled). Open the app again to');
      console.log('        re-register; the real job prunes these automatically.');
    }
  });

  process.exit(res.successCount > 0 ? 0 : 1);
})().catch((e) => { console.error('ERROR', e); process.exit(2); });
