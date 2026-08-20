import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { runDailyDigest } from './runDailyDigest';
import { isDigestAllowed, isDigestTester } from './rollout';
import { sendResendEmail } from '../email/resendClient';
import { buildDigestEmail } from './digestEmailTemplate';
import { buildUnsubscribeUrl } from './emailUnsubscribe';
import { buildNudgeEmail } from './nudgeEmailTemplate';
import { digestDetector, isDigestEmpty } from './digestDetector';
import {
  DAILY_REMINDERS_CHANNEL_ID,
  DAILY_REMINDER_NOTIFICATION_TAG,
  deletePushTokens,
  loadPushTokens,
  sendMulticast,
} from './fcm';
import { lagosDateKey } from './lagosTime';
import { notificationDocsFromModel } from './notificationDocs';
import { loadMoneyByOrderId, withMoney } from './orderMoney';
import { loadWorkshopAudience } from './workshopAudience';
import { mapOrderScanDoc } from './orderScan';
import { pushSummary } from './pushSummary';
import { DigestIO, DigestModel, DigestRecipient } from './types';

const REGION = 'europe-west1';
const SCHEDULE = '0 7 * * *';
const TIMEZONE = 'Africa/Lagos';
/** Deployed URL of the `emailUnsubscribe` HTTPS function (same project + region). */
const UNSUBSCRIBE_BASE = 'https://europe-west1-stitchpad-30607.cloudfunctions.net/emailUnsubscribe';

/** Trimmed string, or '' for anything that is not a string. Type-safe, unlike `?.trim()`. */
function text(v: unknown): string {
  return typeof v === 'string' ? v.trim() : '';
}

function digestStateRef(uid: string) {
  return admin.firestore().collection('users').doc(uid).collection('private').doc('digestState');
}

async function writeNotificationsAdmin(db: admin.firestore.Firestore, uid: string, model: DigestModel): Promise<void> {
  const col = db.collection('users').doc(uid).collection('notifications');
  const createdAt = Date.now();
  for (const spec of notificationDocsFromModel(model)) {
    try {
      // .create() throws ALREADY_EXISTS if the deterministic-id doc exists →
      // dedup: first time only, and read-state on the existing doc is preserved.
      await col.doc(spec.id).create({ ...spec.data, isRead: false, createdAt });
    } catch (err) {
      const code = (err as { code?: number }).code;
      // gRPC ALREADY_EXISTS = 6. We compare the numeric code directly because
      // firebase-admin does not expose admin.firestore.GrpcStatus as a RUNTIME
      // value in this version (it's a .d.ts type only — using it throws at runtime).
      if (code !== 6) {
        functions.logger.warn('writeNotification failed', { uid, id: spec.id, error: err instanceof Error ? err.message : String(err) });
      }
    }
  }
}

function productionDigestIO(apiKey: string): DigestIO {
  const db = admin.firestore();
  return {
    async listRecipients(): Promise<DigestRecipient[]> {
      // Enumerates FIREBASE AUTH, not the `users` collection. The old users-collection
      // walk silently excluded every account that never finished workshop setup — 41 of
      // 154 on 2026-08-20 — which is precisely the group that most needs a nudge. The
      // users docs, opt-out flags and token platforms are each fetched in ONE query and
      // joined in memory, so this is cheaper than the per-user getUser() it replaces.
      //
      // Still serial per recipient downstream (orders, money, FCM, Resend): ~0.5s each,
      // inside the 540s timeout at this size. Past roughly 700 accounts, add bounded
      // concurrency — recipient order is stable, so a timeout starves the same tail
      // every morning with no symptom beyond a missing "run complete" log line.
      const secret = process.env.EMAIL_UNSUB_SECRET ?? '';
      const [usersSnap, prefsSnap, tokensSnap] = await Promise.all([
        db.collection('users').get(),
        db.collection('emailPrefs').get(),
        db.collectionGroup('notificationTokens').get(),
      ]);
      const userDocs = new Map(usersSnap.docs.map((d) => [d.id, d.data()]));
      const prefs = new Map(prefsSnap.docs.map((d) => [d.id, d.data()]));

      // Newest token per uid decides which store the CTA points at.
      const platforms = new Map<string, { platform: string; updatedAt: number }>();
      for (const t of tokensSnap.docs) {
        const owner = t.ref.parent.parent?.id;
        const platform = text(t.data().platform).toLowerCase();
        if (!owner || (platform !== 'ios' && platform !== 'android')) continue;
        const updatedAt = Number(t.data().updatedAt ?? 0);
        const prev = platforms.get(owner);
        if (!prev || updatedAt > prev.updatedAt) platforms.set(owner, { platform, updatedAt });
      }

      const recipients: DigestRecipient[] = [];
      let pageToken: string | undefined;
      do {
        const page = await admin.auth().listUsers(1000, pageToken);
        for (const authUser of page.users) {
          if (authUser.disabled) continue;
          const uid = authUser.uid;
          const email = authUser.email ?? '';
          const data = userDocs.get(uid) ?? {};
          const pref = prefs.get(uid) ?? {};
          const plat = platforms.get(uid)?.platform;
          // `?.` guards null/undefined but NOT type: a numeric or map businessName from a
          // console edit would throw inside listRecipients, which runs OUTSIDE the
          // per-recipient try/catch — one bad doc would silence the digest for everyone.
          const name = (text(data.businessName) || text(data.displayName)
            || text(authUser.displayName) || email.split('@')[0] || 'Tailor');
          recipients.push({
            uid,
            email,
            emailVerified: Boolean(authUser.email && authUser.emailVerified),
            name,
            digestEnabled: data.dailyDigestEmailEnabled !== false,
            // Push opt-out: honor an explicit dailyPushEnabled; otherwise inherit the email
            // digest preference so users who opted out of the daily summary aren't silently
            // opted into push. New users (both absent) default ON.
            pushEnabled: data.dailyPushEnabled !== undefined
              ? data.dailyPushEnabled !== false
              : data.dailyDigestEmailEnabled !== false,
            emailOptOut: pref.optOut === true,
            hardBounce: pref.hardBounce === true,
            platform: plat === 'ios' || plat === 'android' ? plat : null,
            unsubscribeUrl: secret ? buildUnsubscribeUrl(uid, secret, UNSUBSCRIBE_BASE) : '',
          });
        }
        pageToken = page.pageToken;
      } while (pageToken);
      return recipients;
    },
    async hasCustomers(uid) {
      const snap = await db.collection('users').doc(uid).collection('customers').limit(1).get();
      return !snap.empty;
    },
    async markHardBounce(uid) {
      await db.collection('emailPrefs').doc(uid).set(
        { hardBounce: true, hardBounceAt: admin.firestore.FieldValue.serverTimestamp() },
        { merge: true },
      );
    },
    async loadOrders(uid) {
      // Money lives in /private/money since Slice 8d-1 — reading only the base doc
      // made every balance compute to zero. See orderMoney.ts.
      const [snap, money] = await Promise.all([
        db.collection('users').doc(uid).collection('orders').get(),
        loadMoneyByOrderId(db, uid),
      ]);
      return withMoney(snap.docs.map((d) => mapOrderScanDoc(d.id, d.data())), money);
    },
    async getLastSentDate(uid) {
      const snap = await digestStateRef(uid).get();
      return (snap.exists && snap.data()?.lastSentDate) || null;
    },
    async setLastSentDate(uid, dateKey) {
      await digestStateRef(uid).set({ lastSentDate: dateKey }, { merge: true });
    },
    writeNotifications(uid, model) {
      return writeNotificationsAdmin(db, uid, model);
    },
    sendEmail(p) {
      return sendResendEmail(apiKey, p);
    },
    isAllowed: isDigestAllowed,
    loadPushTokens: (uid: string) => loadPushTokens(db, uid),

    sendPush: (tokens, payload) => sendMulticast(
      tokens,
      {
        title: payload.title,
        body: payload.body,
        data: { target: payload.target ?? 'to_collect' },
        androidChannelId: DAILY_REMINDERS_CHANNEL_ID,
        // A daily summary is meant to collapse to one notification. Without a tag it
        // only did so in the foreground; backgrounded deliveries stacked.
        androidTag: DAILY_REMINDER_NOTIFICATION_TAG,
      },
      'digest push',
    ),

    deletePushTokens: (uid: string, tokens: string[]) => deletePushTokens(db, uid, tokens),

    getLastPushDate: async (uid: string): Promise<string | null> => {
      const snap = await digestStateRef(uid).get();
      return (snap.data()?.lastPushDate as string | undefined) ?? null;
    },

    setLastPushDate: async (uid: string, dateKey: string): Promise<void> => {
      await digestStateRef(uid).set({ lastPushDate: dateKey }, { merge: true });
    },

    listStaffUids: async (ownerUid: string): Promise<string[]> => {
      const { staffUids } = await loadWorkshopAudience(db, ownerUid);
      return staffUids;
    },

    /**
     * Staff opt-out. Resolved from the staff member's OWN user doc — they control
     * their notifications, not the workshop owner. Same inheritance as the owner's
     * push flag, so an existing opt-out is honoured without a migration.
     */
    isStaffPushEnabled: async (staffUid: string): Promise<boolean> => {
      const snap = await db.collection('users').doc(staffUid).get();
      const u = snap.data();
      if (!u) return false;
      if (u.dailyPushEnabled !== undefined) return u.dailyPushEnabled !== false;
      return u.dailyDigestEmailEnabled !== false;
    },
  };
}

export const dailyDigest = functions
  .region(REGION)
  // Opening STAGING made this loop span every user, and it is serial: one
  // admin.auth().getUser() AND one Resend HTTP call per recipient. The v1 default 60s
  // would kill the run mid-loop, and since recipient order is stable the same tail
  // would be starved every morning — surfacing only as a missing "run complete" log.
  .runWith({ secrets: ['RESEND_API_KEY', 'EMAIL_UNSUB_SECRET'], timeoutSeconds: 540, memory: '512MB' })
  .pubsub.schedule(SCHEDULE)
  .timeZone(TIMEZONE)
  .onRun(async () => {
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) {
      functions.logger.error('RESEND_API_KEY secret is not configured');
      return;
    }
    await runDailyDigest(productionDigestIO(apiKey), Date.now());
  });

/**
 * Debug/QA trigger: runs the digest for the CALLER only, ignoring the
 * already-sent stamp and the rollout allowlist, so a tester can verify content
 * on demand. Still respects suppress-when-empty and the opt-out flag.
 */
export const debugSendMyDigest = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY', 'EMAIL_UNSUB_SECRET'] })
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');

    const db = admin.firestore();
    const userDoc = await db.collection('users').doc(uid).get();
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email) throw new functions.https.HttpsError('failed-precondition', 'no_email_on_account');
    if (!authUser.emailVerified) {
      throw new functions.https.HttpsError('failed-precondition', 'email_not_verified');
    }
    if (!isDigestTester(authUser.email)) {
      throw new functions.https.HttpsError('permission-denied', 'not_a_tester');
    }

    const now = Date.now();
    const data = userDoc.data() || {};
    const [ordersSnap, money] = await Promise.all([
      db.collection('users').doc(uid).collection('orders').get(),
      loadMoneyByOrderId(db, uid),
    ]);
    const model = digestDetector(
      withMoney(ordersSnap.docs.map((d) => mapOrderScanDoc(d.id, d.data())), money),
      now,
    );

    // Inbox always populated for QA (ungated, same as production runDailyDigest)
    await writeNotificationsAdmin(db, uid, model);

    // NOTE: no early return on an empty model any more — an empty morning is exactly
    // when the nudge email fires in production, so a tester must be able to preview it.
    // Push stays gated on non-empty: there is nothing urgent to interrupt someone for.

    // Push — gated on its own resolved pushEnabled flag (bypass stamp/allowlist for debug)
    const io = productionDigestIO(apiKey);
    const pushEnabled = data.dailyPushEnabled !== undefined
      ? data.dailyPushEnabled !== false
      : data.dailyDigestEmailEnabled !== false;
    let pushSent = false;
    if (pushEnabled && !isDigestEmpty(model)) {
      const pushTokens = await io.loadPushTokens(uid);
      if (pushTokens.length > 0) {
        const { successCount, invalidTokens } = await io.sendPush(pushTokens, pushSummary(model));
        if (invalidTokens.length > 0) await io.deletePushTokens(uid, invalidTokens);
        pushSent = successCount > 0;
      }
    }

    // Email — only when dailyDigestEmailEnabled is not explicitly false
    const emailEnabled = data.dailyDigestEmailEnabled !== false;
    let emailSent = false;
    if (emailEnabled) {
      const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
      // Mirror the real run's variant choice so a tester previewing on a quiet day
      // sees the nudge users actually receive, not a blank digest.
      const secret = process.env.EMAIL_UNSUB_SECRET ?? '';
      const opts = {
        // Same platform-neutral App Link the real run uses — see ctaUrl() in
        // runDailyDigest.ts for why this must not be a hardcoded store URL.
        ctaUrl: 'https://link.getstitchpad.com/r',
        unsubscribeUrl: secret ? buildUnsubscribeUrl(uid, secret, UNSUBSCRIBE_BASE) : '',
      };
      let payload;
      if (isDigestEmpty(model)) {
        const custSnap = await db.collection('users').doc(uid).collection('customers').limit(1).get();
        const kind = ordersSnap.size > 0 ? 'quiet' : (custSnap.empty ? 'setup' : 'first_order');
        payload = buildNudgeEmail(kind, name, opts);
      } else {
        payload = buildDigestEmail(model, name, opts);
      }
      const { subject, html, text: body } = payload;
      await sendResendEmail(apiKey, { to: authUser.email, subject, html, text: body });
      await digestStateRef(uid).set({ lastSentDate: lagosDateKey(now) }, { merge: true });
      emailSent = true;
    }

    return { sent: emailSent || pushSent, emailSent, pushSent };
  });
