import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { DocumentData } from 'firebase-admin/firestore';
import { runDailyDigest } from './runDailyDigest';
import { isDigestAllowed, isDigestTester } from './rollout';
import { sendResendEmail } from '../email/resendClient';
import { buildDigestEmail } from './digestEmailTemplate';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { lagosDateKey } from './lagosTime';
import { notificationDocsFromModel } from './notificationDocs';
import { pushSummary } from './pushSummary';
import { DigestIO, DigestModel, DigestRecipient, OrderScanDoc } from './types';

const REGION = 'europe-west1';
const SCHEDULE = '0 7 * * *';
const TIMEZONE = 'Africa/Lagos';

function digestStateRef(uid: string) {
  return admin.firestore().collection('users').doc(uid).collection('private').doc('digestState');
}

function mapOrder(id: string, d: DocumentData): OrderScanDoc {
  return {
    id,
    customerName: d.customerName ?? '',
    status: d.status ?? 'PENDING',
    deadline: typeof d.deadline === 'number' ? d.deadline : null,
    archivedAt: typeof d.archivedAt === 'number' ? d.archivedAt : null,
    totalPrice: typeof d.totalPrice === 'number' ? d.totalPrice : 0,
    payments: Array.isArray(d.payments) ? d.payments.map((p: any) => ({ amount: Number(p?.amount) || 0 })) : [],
    depositPaid: typeof d.depositPaid === 'number' ? d.depositPaid : 0,
    items: Array.isArray(d.items) ? d.items.map((i: any) => ({
      garmentType: i?.garmentType, customGarmentName: i?.customGarmentName, description: i?.description,
    })) : [],
  };
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
      // Scale path: V1 does one users.get() + a serial admin.auth().getUser(uid)
      // per user (N+1). Before going much beyond ~50 users, switch to
      // admin.auth().listUsers() pagination + a uid→email map to drop the N+1.
      const usersSnap = await db.collection('users').get();
      const recipients: DigestRecipient[] = [];
      for (const doc of usersSnap.docs) {
        const data = doc.data();
        let email: string | undefined;
        try {
          const authUser = await admin.auth().getUser(doc.id);
          if (!authUser.email || !authUser.emailVerified) continue;
          email = authUser.email;
        } catch {
          continue; // doc with no matching/verified auth user — skip
        }
        const name = (data.businessName?.trim() || data.displayName?.trim() || email.split('@')[0]);
        recipients.push({
          uid: doc.id,
          email,
          name,
          digestEnabled: data.dailyDigestEmailEnabled !== false,
          // Push opt-out: honor an explicit dailyPushEnabled; otherwise inherit the email
          // digest preference so users who opted out of the daily summary aren't silently
          // opted into push. New users (both absent) default ON.
          pushEnabled: data.dailyPushEnabled !== undefined
            ? data.dailyPushEnabled !== false
            : data.dailyDigestEmailEnabled !== false,
        });
      }
      return recipients;
    },
    async loadOrders(uid) {
      const snap = await db.collection('users').doc(uid).collection('orders').get();
      return snap.docs.map((d) => mapOrder(d.id, d.data()));
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
    loadPushTokens: async (uid: string): Promise<string[]> => {
      const snap = await db.collection('users').doc(uid).collection('notificationTokens').get();
      return snap.docs.map((d) => d.id);
    },

    sendPush: async (tokens, payload) => {
      const FCM_MULTICAST_LIMIT = 500;
      const invalidTokens: string[] = [];
      for (let i = 0; i < tokens.length; i += FCM_MULTICAST_LIMIT) {
        const batch = tokens.slice(i, i + FCM_MULTICAST_LIMIT);
        const res = await admin.messaging().sendEachForMulticast({
          tokens: batch,
          notification: { title: payload.title, body: payload.body },
          android: { notification: { channelId: 'daily_reminders' } },
          data: { target: 'inbox' },
        });
        res.responses.forEach((r, j) => {
          const code = r.error?.code;
          if (code === 'messaging/registration-token-not-registered' ||
              code === 'messaging/invalid-registration-token') {
            invalidTokens.push(batch[j]);
          }
        });
      }
      return { invalidTokens };
    },

    deletePushTokens: async (uid: string, tokens: string[]): Promise<void> => {
      const col = db.collection('users').doc(uid).collection('notificationTokens');
      const FIRESTORE_BATCH_LIMIT = 500;
      for (let i = 0; i < tokens.length; i += FIRESTORE_BATCH_LIMIT) {
        const batch = db.batch();
        for (const t of tokens.slice(i, i + FIRESTORE_BATCH_LIMIT)) {
          batch.delete(col.doc(t));
        }
        await batch.commit();
      }
    },

    getLastPushDate: async (uid: string): Promise<string | null> => {
      const snap = await digestStateRef(uid).get();
      return (snap.data()?.lastPushDate as string | undefined) ?? null;
    },

    setLastPushDate: async (uid: string, dateKey: string): Promise<void> => {
      await digestStateRef(uid).set({ lastPushDate: dateKey }, { merge: true });
    },
  };
}

export const dailyDigest = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
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
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');

    const db = admin.firestore();
    const userDoc = await db.collection('users').doc(uid).get();
    if (userDoc.data()?.dailyDigestEmailEnabled === false) {
      return { sent: false, reason: 'disabled' };
    }
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email) throw new functions.https.HttpsError('failed-precondition', 'no_email_on_account');
    if (!authUser.emailVerified) {
      throw new functions.https.HttpsError('failed-precondition', 'email_not_verified');
    }
    if (!isDigestTester(authUser.email)) {
      throw new functions.https.HttpsError('permission-denied', 'not_a_tester');
    }

    const now = Date.now();
    const ordersSnap = await db.collection('users').doc(uid).collection('orders').get();
    const model = digestDetector(ordersSnap.docs.map((d) => mapOrder(d.id, d.data())), now);
    await writeNotificationsAdmin(db, uid, model);   // populate the inbox for QA
    if (isDigestEmpty(model)) return { sent: false, reason: 'empty' };

    const data = userDoc.data() || {};
    const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
    const { subject, html, text } = buildDigestEmail(model, name);
    await sendResendEmail(apiKey, { to: authUser.email, subject, html, text });
    await digestStateRef(uid).set({ lastSentDate: lagosDateKey(now) }, { merge: true });

    const io = productionDigestIO(apiKey);
    const pushEnabled = data.dailyPushEnabled !== undefined
      ? data.dailyPushEnabled !== false
      : data.dailyDigestEmailEnabled !== false;
    if (pushEnabled) {
      const pushTokens = await io.loadPushTokens(uid);
      if (pushTokens.length > 0 && !isDigestEmpty(model)) {
        const { invalidTokens } = await io.sendPush(pushTokens, pushSummary(model));
        if (invalidTokens.length > 0) await io.deletePushTokens(uid, invalidTokens);
      }
    }

    return { sent: true };
  });
