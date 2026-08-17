/**
 * Engagement-push smoke driver against the LOCAL emulators.
 * Runs the REAL compiled runEngagementPush + productionEngagementIO; only the FCM
 * transport is stubbed (FCM cannot be emulated), so the exact payload is asserted.
 */
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080';
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
process.env.GCLOUD_PROJECT = 'stitchpad-30607';

const admin = require('firebase-admin');
if (!admin.apps.length) admin.initializeApp({ projectId: 'stitchpad-30607' });
const db = admin.firestore();

// Capture every FCM send instead of dispatching it.
//
// Patch the Messaging SINGLETON, not `admin.messaging`: esModuleInterop compiles
// `import * as admin` to __importStar, which hands each module its own COPY of the
// namespace, so reassigning the function here never reaches fcm.js. Every caller of
// admin.messaging() shares this one instance.
const sends = [];
const messaging = admin.messaging();
const REAL_FCM = process.env.ENGAGEMENT_SMOKE_REAL_FCM === '1';
if (REAL_FCM) {
  // Opt-in: actually deliver, so you can SEE the notification land on a device and
  // tap it. Requires real credentials (GOOGLE_APPLICATION_CREDENTIALS or an
  // authenticated gcloud/firebase login) because FCM has no emulator. The payload is
  // still captured for the assertions below.
  const realSend = messaging.sendEachForMulticast.bind(messaging);
  messaging.sendEachForMulticast = async (msg) => { sends.push(msg); return realSend(msg); };
  console.log('!! ENGAGEMENT_SMOKE_REAL_FCM=1 — sending REAL pushes to registered devices');
} else {
  messaging.sendEachForMulticast = async (msg) => {
    sends.push(msg);
    return { successCount: msg.tokens.length, failureCount: 0,
             responses: msg.tokens.map(() => ({ success: true })) };
  };
}

const { runEngagementPush } = require('../lib/notifications/runEngagementPush');
const { productionEngagementIO } = require('../lib/notifications/engagementPush');

const TUESDAY = Date.parse('2026-08-18T09:00:00Z');
let pass = 0, fail = 0;
const check = (label, cond, extra='') => {
  if (cond) { pass++; console.log('  ✓', label); }
  else { fail++; console.log('  ✗', label, extra); }
};

(async () => {
  // 1) Seed the campaign config the operator would paste in.
  await db.doc('config/engagementPush').set({
    enabled: true,
    daysOfWeek: [2, 5],
    minDaysBetween: 3,
    campaigns: [
      { id: '2026-08-first-customer', segment: 'no_customer', title: 'Start with one customer',
        body: 'Add your first customer.', target: 'inbox', priority: 0, maxSendsPerUser: 3 },
      { id: '2026-08-founding-tailors', segment: 'no_referral', title: 'Founding Tailors',
        body: 'Invite a tailor friend. Top 3 each month win a shirt.',
        target: 'founding_tailors', priority: 0, maxSendsPerUser: 2 },
    ],
  });

  // 2) Give every seeded user a device token so loadPushTokens returns something.
  const users = await db.collection('users').get();
  const byEmail = {};
  for (const d of users.docs) {
    // The seeded user docs have no `email` field — resolve through Auth, exactly as
    // productionEngagementIO's fallback does for legacy docs.
    const email = (d.data().email
      || (await admin.auth().getUser(d.id).then((u) => u.email).catch(() => ''))
      || '').toLowerCase();
    byEmail[email] = d.id;
    await db.doc(`users/${d.id}/notificationTokens/tok-${d.id}`)
            .set({ token: `tok-${d.id}`, platform: 'android', updatedAt: Date.now() });
  }
  console.log('\nSeeded users:', Object.keys(byEmail).filter(Boolean).join(', '));

  const folaUid = byEmail['fola@gmail.com'];
  const gabbyUid = byEmail['gabby@gmail.com'];
  // Repeatable: clear the anti-stacking stamp from any earlier run.
  for (const d of users.docs) {
    await db.doc(`users/${d.id}/private/digestState`).delete().catch(() => {});
  }

  // 3) Run the real loop.
  const result = await runEngagementPush(productionEngagementIO(), TUESDAY);
  console.log('\nRun result:', JSON.stringify(result));
  console.log('FCM sends captured:', sends.length);
  for (const s of sends) console.log('  payload:', JSON.stringify(s));

  console.log('\nChecks:');
  const sentTokens = sends.flatMap((s) => s.tokens);
  check('Fola (owner) received a nudge', sentTokens.includes(`tok-${folaUid}`));
  if (REAL_FCM) {
    console.log('  i real-FCM mode: the synthetic tok-* entries fail by design;');
    console.log('    a genuine device token registered by the signed-in app is what delivers.');
  }
  check('Gabby (ACTIVE STAFF) was excluded — the staff-exclusion fix',
        gabbyUid ? !sentTokens.includes(`tok-${gabbyUid}`) : false,
        gabbyUid ? `(gabby token present: ${sentTokens.includes('tok-'+gabbyUid)})` : '(gabby uid not found)');

  const p = sends[0];
  check('posts on the announcements_v2 channel',
        p && p.android && p.android.notification.channelId === 'announcements_v2',
        p ? JSON.stringify(p.android) : '');
  check('carries an android tag so background deliveries collapse',
        p && p.android.notification.tag === 'stitchpad_announcement');
  // Inverted deliberately: passive suppressed the banner AND sound on iOS, so the
  // nudge only reached Notification Centre and was effectively invisible.
  check('does NOT set APNs passive — the nudge must be seen',
        p && !(p.apns && p.apns.payload && p.apns.payload.aps
               && p.apns.payload.aps['interruption-level'] === 'passive'),
        p ? JSON.stringify(p.apns) : '');

  // APNs plays nothing without aps.sound; the top-level notification field only
  // becomes aps.alert. Every iOS push was silent until this was added.
  check('sets APNs sound so iOS is audible',
        p && p.apns && p.apns.payload.aps.sound === 'default',
        p ? JSON.stringify(p.apns) : '');
  check('carries a deep-link target in data', p && typeof p.data.target === 'string',
        p ? JSON.stringify(p.data) : '');
  check('Fola got the no_referral campaign (she has customers+orders, no referral link)',
        p && p.notification.title === 'Founding Tailors',
        p ? p.notification.title : '');

  // 4) State written, and the anti-stacking brake now engaged.
  const st = (await db.doc(`users/${folaUid}/private/digestState`).get()).data() || {};
  check('recordSent stamped lastPushDate (blocks a second push today)',
        st.lastPushDate === '2026-08-18', JSON.stringify(st));
  check('engagement counters written',
        st.engagement && st.engagement.sendCount === 1
        && st.engagement.campaigns['2026-08-founding-tailors'] === 1,
        JSON.stringify(st.engagement));

  // 5) Re-run same day: must send nothing (idempotence / anti-stacking).
  const before = sends.length;
  const second = await runEngagementPush(productionEngagementIO(), TUESDAY);
  check('a second run the same day sends nothing (anti-stacking)',
        sends.length === before && second.skippedPushedToday >= 1,
        JSON.stringify(second));

  console.log(`\n${fail === 0 ? 'ALL CHECKS PASSED' : 'FAILURES: ' + fail} (${pass} passed)`);
  process.exit(fail === 0 ? 0 : 1);
})().catch((e) => { console.error('DRIVER ERROR', e); process.exit(2); });
