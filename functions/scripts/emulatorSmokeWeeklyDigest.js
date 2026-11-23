#!/usr/bin/env node
// Local Firebase integration smoke. Transport overrides capture all email/FCM;
// this script never invokes Resend or FCM and never connects to production.
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8085';
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
const assert = require('node:assert/strict');
const admin = require('firebase-admin');
admin.initializeApp({ projectId: 'stitchpad-30607' });
const { productionDigestIO } = require('../lib/notifications/dailyDigest');
const { runDailyDigest } = require('../lib/notifications/runDailyDigest');
const { digestEmailWeekday } = require('../lib/notifications/digestEmailPolicy');
const { lagosWeekday, lagosDateKey, DAY_MS } = require('../lib/notifications/lagosTime');
const OWNER = 'weekly-digest-smoke-owner';
const STAFF = 'weekly-digest-smoke-staff';

async function main() {
  const db = admin.firestore();
  const actualNow = Date.now();
  let now = actualNow;
  while (lagosWeekday(now) !== digestEmailWeekday(OWNER)) now += DAY_MS;
  for (const [uid, role] of [[OWNER, 'owner'], [STAFF, 'staff']]) {
    try { await admin.auth().createUser({ uid, email: `${uid}@example.invalid`, emailVerified: true }); }
    catch (error) { if (error.code !== 'auth/uid-already-exists') throw error; }
    await admin.auth().setCustomUserClaims(uid, { role, ...(role === 'staff' ? { workshopUid: OWNER } : {}) });
    await db.doc(`users/${uid}`).set({ businessName: 'Smoke atelier', subscriptionTier: 'atelier',
      grantSource: 'launch_free', subscriptionSource: role === 'staff' ? 'apple' : null,
      subscriptionStatus: 'active', subscriptionEndsAt: actualNow + 40 * DAY_MS });
    await db.doc(`users/${uid}/notificationTokens/${uid}-token`).set({ platform: 'android', updatedAt: actualNow });
  }
  await db.doc(`users/${OWNER}/memberships/${STAFF}`).set({ status: 'active' });
  await db.doc(`users/${OWNER}/orders/smoke-order`).set({ customerName: 'Smoke customer', status: 'IN_PROGRESS',
    deadline: now - DAY_MS, assignedMemberId: STAFF, items: [] });
  await db.doc('config/app').set({}); // Missing audience defaults to paid.
  const io = productionDigestIO('emulator-no-delivery');
  const list = io.listRecipients.bind(io);
  io.listRecipients = async () => (await list()).filter(r => r.uid === OWNER || r.uid === STAFF);
  const emails = [], pushes = [];
  io.sendEmail = async payload => { emails.push(payload); };
  io.sendPush = async (tokens, payload) => { pushes.push({ tokens, payload }); return { successCount: tokens.length, invalidTokens: [] }; };
  const reset = async () => {
    emails.length = 0; pushes.length = 0;
    for (const uid of [OWNER, STAFF]) await db.doc(`users/${uid}/private/digestState`).delete();
  };
  const run = async (time = now) => {
    const countBefore = emails.length;
    const result = await runDailyDigest(io, time);
    assert.equal(result.failed, 0);
    return emails.length - countBefore;
  };

  assert.equal((await io.listRecipients()).find(r => r.uid === STAFF).paid, false, 'paid staff is not a paid owner');
  assert.equal(await run(), 0, 'launch owner receives no email');
  assert.equal(pushes.length, 2, 'owner and active staff still receive push');
  assert.ok((await db.collection(`users/${OWNER}/notifications`).get()).size > 0, 'inbox populated');

  await db.doc(`users/${OWNER}`).update({ subscriptionSource: 'paystack' });
  await reset();
  assert.equal(await run(), 1, 'paid owner receives weekly summary');
  assert.ok(emails[0].subject.includes('weekly'));
  assert.equal(await run(), 0, 'same-day rerun does not send again');
  assert.equal(await run(now + DAY_MS), 0, 'off-day does not email');
  assert.equal(pushes.length, 4, 'owner and staff pushes remain daily');
  assert.equal(await run(now + 7 * DAY_MS), 1, 'next assigned week sends');

  await reset();
  await db.doc(`users/${OWNER}/private/digestState`).set({ lastSentDate: lagosDateKey(now - 5 * DAY_MS) });
  assert.equal(await run(), 0, 'recent manual-send stamp blocks email');

  await reset();
  await db.doc('config/app').set({ digestEmailAudience: 'none' });
  assert.equal(await run(), 0, 'kill switch blocks email');
  assert.equal(pushes.length, 2, 'kill switch leaves push alone');

  await reset();
  await db.doc(`users/${OWNER}`).update({ subscriptionEndsAt: actualNow - DAY_MS });
  await db.doc('config/app').set({ digestEmailAudience: 'paid' });
  assert.equal(await run(), 0, 'expired payment is not paid');
  await db.doc('config/app').set({ digestEmailAudience: 'all' });
  assert.equal(await run(), 1, 'all override broadens the audience');

  await reset();
  await db.doc(`users/${OWNER}/orders/smoke-order`).update({ status: 'DELIVERED' });
  assert.equal(await run(), 0, 'empty summaries never become nudge emails');
  console.log('PASS: real Auth/Firestore recipient mapping, paid/expired/launch/staff classification, weekly cadence, rerun guard, runtime audience changes, daily owner/staff push and inbox. Email/FCM captured locally.');
  await admin.app().delete();
}
main().catch(error => { console.error(error); process.exitCode = 1; });
