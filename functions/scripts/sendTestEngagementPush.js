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
 *   node scripts/sendTestEngagementPush.js you@example.com --send --campaign no_customer
 *   node scripts/sendTestEngagementPush.js you@example.com --send --title "Win a shirt" --body "Invite a tailor"
 *   node scripts/sendTestEngagementPush.js you@example.com --send --quiet  # iOS passive
 *   node scripts/sendTestEngagementPush.js --list-campaigns
 */
const admin = require('firebase-admin');

const PROJECT_ID = 'stitchpad-30607';
const ANNOUNCEMENTS_CHANNEL_ID = 'announcements_v2';
const ANNOUNCEMENT_NOTIFICATION_TAG = 'stitchpad_announcement';
const VALID_TARGETS = ['inbox', 'to_collect', 'dashboard', 'founding_tailors'];

/**
 * The canonical copy for each segment, mirroring the runbook's paste-this config, so
 * every nudge can be previewed on a real device before it is ever configured live.
 * Copy is judged on a lock screen, not in a JSON file.
 */
const CAMPAIGNS = {
  no_customer: {
    title: 'Start with one customer',
    body: 'Add your first customer and keep their measurements in one place.',
    target: 'dashboard',
  },
  no_order: {
    title: 'What are you sewing now?',
    body: 'Log it as an order and StitchPad tracks the deadline for you.',
    target: 'dashboard',
  },
  busy_no_team: {
    title: 'Do you have staff?',
    body: 'Add them to StitchPad and assign orders, so you can see who is on what.',
    target: 'dashboard',
  },
  quiet: {
    title: 'Nothing due today',
    body: "Add this week's jobs so nothing slips through.",
    target: 'to_collect',
  },
  no_referral: {
    title: 'Know another tailor?',
    body: 'Invite them. Top 3 each month win a free StitchPad shirt.',
    target: 'founding_tailors',
  },
  welcome_ending: {
    title: '{{businessName}}, 3 days left',
    body: 'Your First Month ends soon. Upgrade to keep every customer on your list.',
    target: 'dashboard',
  },
  dormant: {
    title: "It's been a while",
    body: 'Add the jobs you are working on and StitchPad tracks the deadlines again.',
    target: 'dashboard',
  },
};

/**
 * Stand-in values for {{variables}} so templated copy can be previewed. The real job
 * fills these from the user doc and the leaderboard.
 */
const PREVIEW_VARS = {
  businessName: 'Apeke Couture',
  points: 12,
  customerCount: 7,
  orderCount: 23,
};

function renderPreview(text) {
  return text.replace(/\{\{\s*([A-Za-z0-9_]+)\s*\}\}/g,
    (whole, name) => (name in PREVIEW_VARS ? String(PREVIEW_VARS[name]) : whole));
}

const args = process.argv.slice(2);
const email = args.find((a) => !a.startsWith('--'));
const doSend = args.includes('--send');
// Production sends at normal priority, so this tool does too by default — what you
// see is what the real job produces. --quiet re-adds the APNs passive level if you
// ever want to compare the two tiers on a device again.
const quiet = args.includes('--quiet');
const arg = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);

// --campaign picks a segment's canonical copy; --title/--body override anything, so a
// new line can be tried on a device in seconds without editing the config.
const campaignKey = arg('--campaign');
if (campaignKey && !CAMPAIGNS[campaignKey]) {
  console.error(`--campaign must be one of: ${Object.keys(CAMPAIGNS).join(', ')}`);
  process.exit(1);
}
const campaign = CAMPAIGNS[campaignKey] || CAMPAIGNS.no_referral;
const title = renderPreview(arg('--title') || campaign.title);
const body = renderPreview(arg('--body') || campaign.body);
const target = arg('--target') || campaign.target;

if (args.includes('--list-campaigns')) {
  console.log('Segment campaigns (preview any with --campaign <segment>):\n');
  for (const [k, c] of Object.entries(CAMPAIGNS)) {
    console.log(`  ${k}`);
    console.log(`    title : ${c.title}`);
    console.log(`    body  : ${c.body}`);
    console.log(`    target: ${c.target}\n`);
  }
  process.exit(0);
}
if (!email) {
  console.error('Usage: node scripts/sendTestEngagementPush.js <email> [--send] [--campaign <s>] [--title T] [--body B] [--target t]');
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
    notification: { title, body },
    android: {
      notification: { channelId: ANNOUNCEMENTS_CHANNEL_ID, tag: ANNOUNCEMENT_NOTIFICATION_TAG },
    },
    ...(quiet ? { apns: { payload: { aps: { 'interruption-level': 'passive' } } } } : {}),
    data: { target },
  };

  console.log(`\nSending  title: ${JSON.stringify(title)}`);
  console.log(`         body : ${JSON.stringify(body)}`);
  console.log(`         target: ${target}   iOS level: ${quiet ? 'passive — Notification Centre only' : 'default — banner + sound'}`);
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
