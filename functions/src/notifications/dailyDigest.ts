import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { DocumentData } from 'firebase-admin/firestore';
import { runDailyDigest } from './runDailyDigest';
import { isDigestAllowed } from './rollout';
import { sendResendEmail } from '../email/resendClient';
import { buildDigestEmail } from './digestEmailTemplate';
import { digestDetector, isDigestEmpty } from './digestDetector';
import { lagosDateKey } from './lagosTime';
import { DigestIO, DigestRecipient, OrderScanDoc } from './types';

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
    items: Array.isArray(d.items) ? d.items.map((i: any) => ({
      garmentType: i?.garmentType, customGarmentName: i?.customGarmentName, description: i?.description,
    })) : [],
  };
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
        recipients.push({ uid: doc.id, email, name, digestEnabled: data.dailyDigestEmailEnabled !== false });
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
    sendEmail(p) {
      return sendResendEmail(apiKey, p);
    },
    isAllowed: isDigestAllowed,
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

    const now = Date.now();
    const ordersSnap = await db.collection('users').doc(uid).collection('orders').get();
    const model = digestDetector(ordersSnap.docs.map((d) => mapOrder(d.id, d.data())), now);
    if (isDigestEmpty(model)) return { sent: false, reason: 'empty' };

    const data = userDoc.data() || {};
    const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
    const { subject, html, text } = buildDigestEmail(model, name);
    await sendResendEmail(apiKey, { to: authUser.email, subject, html, text });
    await digestStateRef(uid).set({ lastSentDate: lagosDateKey(now) }, { merge: true });
    return { sent: true };
  });
