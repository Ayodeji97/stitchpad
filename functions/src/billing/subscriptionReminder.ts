import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { sendResendEmail } from '../email/resendClient';
import { isDigestTester } from '../notifications/rollout';
import { BillingCadence, BillingTier } from './paystackBilling';
import { buildRenewalReminderEmail } from './subscriptionReminderTemplate';

const REGION = 'europe-west1';
const SCHEDULE = '0 9 * * *'; // 09:00 Africa/Lagos, after the daily digest (07:00)
const TIMEZONE = 'Africa/Lagos';
const DAY_MS = 24 * 60 * 60 * 1000;

/** How far ahead of expiry to remind. One reminder per prepaid period. */
export const REMINDER_WINDOW_MS = 3 * DAY_MS;

/**
 * Link into the app's Upgrade screen, used as the renewal email's "Renew" button.
 * An https Universal Link / App Link (NOT the stitchpad:// custom scheme) because
 * Gmail's iOS app refuses custom-scheme hrefs — it would render as dead text. The
 * apps verify this host via associated-domains (iOS) / assetlinks.json (Android) and
 * route it to UpgradeRoute; if the app isn't installed it falls back to a web page.
 * Kept in sync with applinks:link.getstitchpad.com and the Android intent-filter.
 */
export const PAY_DEEP_LINK = 'https://link.getstitchpad.com/upgrade';

/**
 * Deep link carrying the plan to renew, so the Upgrade screen pre-selects the
 * tailor's current tier + cadence (one tap from paying) instead of defaulting to
 * the Atelier upsell. The app reads ?tier= and ?cadence= from the URL.
 */
export function buildUpgradeDeepLink(tier: BillingTier, cadence: BillingCadence): string {
  return `${PAY_DEEP_LINK}?tier=${tier}&cadence=${cadence}`;
}

export interface ReminderRecipient {
  uid: string;
  email: string;
  name: string;
  tier: BillingTier;
  cadence: BillingCadence;
  subscriptionEndsAt: Date;
}

export interface SubscriptionReminderIO {
  /** Paid, non-renewing users whose period ends within (now, now+windowMs], with email+name resolved. */
  listExpiring(nowMs: number, windowMs: number): Promise<ReminderRecipient[]>;
  /** The subscriptionEndsAt (epoch ms) we last reminded this user for, or null. */
  getRemindedForEndsAt(uid: string): Promise<number | null>;
  setRemindedForEndsAt(uid: string, endsAtMs: number): Promise<void>;
  sendEmail(params: { to: string; subject: string; html: string; text: string }): Promise<void>;
}

export interface ReminderRunResult {
  considered: number;
  sent: number;
  skippedAlreadyReminded: number;
  failed: number;
}

/**
 * Pure run loop. Sends one renewal reminder per prepaid period, keyed on the
 * exact subscriptionEndsAt so it re-arms automatically when the user renews
 * (new end date). Production wraps this with productionIO; tests inject fakes.
 */
export async function runSubscriptionReminder(
  io: SubscriptionReminderIO,
  nowMs: number,
  windowMs: number = REMINDER_WINDOW_MS,
): Promise<ReminderRunResult> {
  const recipients = await io.listExpiring(nowMs, windowMs);
  const result: ReminderRunResult = {
    considered: recipients.length, sent: 0, skippedAlreadyReminded: 0, failed: 0,
  };

  for (const r of recipients) {
    try {
      const endsAtMs = r.subscriptionEndsAt.getTime();
      if ((await io.getRemindedForEndsAt(r.uid)) === endsAtMs) {
        result.skippedAlreadyReminded++;
        continue;
      }
      const daysLeft = Math.max(1, Math.ceil((endsAtMs - nowMs) / DAY_MS));
      const { subject, html, text } = buildRenewalReminderEmail({
        name: r.name,
        tier: r.tier,
        daysLeft,
        renewalDate: r.subscriptionEndsAt,
        payUrl: buildUpgradeDeepLink(r.tier, r.cadence),
      });
      // Stamp AFTER a successful send (at-least-once): a failure stamping after the
      // email went out may re-send next run — preferred over losing the reminder.
      await io.sendEmail({ to: r.email, subject, html, text });
      await io.setRemindedForEndsAt(r.uid, endsAtMs);
      result.sent++;
    } catch (err) {
      result.failed++;
      functions.logger.error('subscription reminder: recipient failed', {
        uid: r.uid, error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  functions.logger.info('subscription reminder run complete', { ...result });
  return result;
}

function reminderStateRef(uid: string) {
  return admin.firestore().collection('users').doc(uid).collection('private').doc('renewalReminderState');
}

function toDate(value: unknown): Date | null {
  if (value instanceof Date) return value;
  if (typeof value === 'number') return new Date(value);
  if (typeof value === 'string') {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  if (value && typeof value === 'object' && 'toDate' in value &&
      typeof (value as { toDate: () => Date }).toDate === 'function') {
    return (value as { toDate: () => Date }).toDate();
  }
  return null;
}

/**
 * Cadence (monthly/annual) of the user's most recent paid checkout, so the renewal
 * deep link can pre-select it. The user doc only stores tier, not cadence. Uses a
 * single-field `status == 'paid'` filter (auto-indexed; no composite index) and
 * picks the latest by paidAt in code. Defaults to monthly when none is found.
 */
async function getLatestPaidCadence(db: admin.firestore.Firestore, uid: string): Promise<BillingCadence> {
  const snap = await db.collection('users').doc(uid).collection('billingTransactions')
    .where('status', '==', 'paid').get();
  let latestMs = -1;
  let cadence: BillingCadence = 'monthly';
  for (const d of snap.docs) {
    const data = d.data();
    const paidAtMs = toDate(data.paidAt)?.getTime() ?? 0;
    if (paidAtMs > latestMs) {
      latestMs = paidAtMs;
      cadence = data.cadence === 'annual' ? 'annual' : 'monthly';
    }
  }
  return cadence;
}

function productionIO(apiKey: string): SubscriptionReminderIO {
  const db = admin.firestore();
  return {
    async listExpiring(nowMs, windowMs) {
      const snap = await db.collection('users')
        .where('subscriptionTier', 'in', ['pro', 'atelier'])
        .where('subscriptionRenews', '==', false)
        .where('subscriptionEndsAt', '>', admin.firestore.Timestamp.fromDate(new Date(nowMs)))
        .where('subscriptionEndsAt', '<=', admin.firestore.Timestamp.fromDate(new Date(nowMs + windowMs)))
        .get();
      // Resolve email/verified status concurrently — the candidate set is small
      // (paid users expiring within the window), but the per-user admin.auth()
      // lookup is an N+1, so parallelize it rather than awaiting serially.
      const resolved = await Promise.all(snap.docs.map(async (doc): Promise<ReminderRecipient | null> => {
        const data = doc.data();
        const endsAt = toDate(data.subscriptionEndsAt);
        if (!endsAt) {
          functions.logger.warn('subscription reminder: unparseable subscriptionEndsAt', { uid: doc.id });
          return null;
        }
        try {
          const authUser = await admin.auth().getUser(doc.id);
          if (!authUser.email || !authUser.emailVerified) return null;
          const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
          const cadence = await getLatestPaidCadence(db, doc.id);
          return {
            uid: doc.id,
            email: authUser.email,
            name,
            tier: data.subscriptionTier as BillingTier,
            cadence,
            subscriptionEndsAt: endsAt,
          };
        } catch {
          return null;
        }
      }));
      return resolved.filter((r): r is ReminderRecipient => r !== null);
    },
    async getRemindedForEndsAt(uid) {
      const snap = await reminderStateRef(uid).get();
      const v = snap.data()?.remindedForEndsAtMs;
      return typeof v === 'number' ? v : null;
    },
    async setRemindedForEndsAt(uid, endsAtMs) {
      await reminderStateRef(uid).set({ remindedForEndsAtMs: endsAtMs }, { merge: true });
    },
    sendEmail(p) {
      return sendResendEmail(apiKey, p);
    },
  };
}

export const prepaidSubscriptionReminder = functions
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
    await runSubscriptionReminder(productionIO(apiKey), Date.now());
  });

/**
 * Debug/QA trigger: sends a renewal reminder to the CALLER on demand, bypassing the
 * schedule, the 3-day window, and the per-period dedup, so a tester can preview the
 * email and exercise the renewal app link without hand-editing Firestore.
 * Tester-allowlisted (same gate as debugSendMyDigest). Uses the caller's real tier when
 * they're on a paid plan, otherwise a representative Pro sample.
 */
export const debugSendMyRenewalReminder = functions
  .region(REGION)
  .runWith({ secrets: ['RESEND_API_KEY'] })
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    const apiKey = process.env.RESEND_API_KEY;
    if (!apiKey) throw new functions.https.HttpsError('failed-precondition', 'email_not_configured');

    const db = admin.firestore();
    const userDoc = await db.collection('users').doc(uid).get();
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email) throw new functions.https.HttpsError('failed-precondition', 'no_email_on_account');
    if (!authUser.emailVerified) throw new functions.https.HttpsError('failed-precondition', 'email_not_verified');
    if (!isDigestTester(authUser.email)) throw new functions.https.HttpsError('permission-denied', 'not_a_tester');

    const data = userDoc.data() || {};
    const tier: BillingTier = data.subscriptionTier === 'atelier' ? 'atelier' : 'pro';
    const cadence = await getLatestPaidCadence(db, uid);
    const name = (data.businessName?.trim() || data.displayName?.trim() || authUser.email.split('@')[0]);
    const { subject, html, text } = buildRenewalReminderEmail({
      name,
      tier,
      daysLeft: 3,
      renewalDate: new Date(Date.now() + REMINDER_WINDOW_MS),
      payUrl: buildUpgradeDeepLink(tier, cadence),
    });
    await sendResendEmail(apiKey, { to: authUser.email, subject, html, text });
    return { sent: true, to: authUser.email };
  });
