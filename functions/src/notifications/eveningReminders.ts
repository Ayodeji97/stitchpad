/**
 * Evening reminder push — 19:00 Lagos (Daniel, 2026-11-24), server-only (no app release needed).
 *
 * One push per owner per evening at most, combining, in priority order:
 *   due tomorrow & not Ready  >  delivered & still owed  >  Ready & not collected
 * and otherwise, for an active tailor who recorded nothing today, a short
 * "any new jobs today?" nudge. Rules: reminderDetector.ts. Loop: runEveningReminders.ts.
 *
 * Separate from the 07:00 digest on purpose: its own schedule, its own
 * once-per-evening stamp (`users/{uid}/private/reminderState`), its own Android tag
 * so it does not replace the morning notification in the shade.
 *
 * Emergency brake: set `config/app.reminderPushEnabled` to false. No deploy needed.
 */
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { DocumentData, Firestore } from 'firebase-admin/firestore';
import {
  DAILY_REMINDERS_CHANNEL_ID, deletePushTokens, loadPushTokens, sendMulticast,
} from './fcm';
import { loadActiveStaffUids } from './engagementPush';
import { reminderInboxDocs } from './notificationDocs';
import { loadMoneyByOrderId, withMoney } from './orderMoney';
import { mapOrderScanDoc } from './orderScan';
import { isDigestAllowed, isDigestTester } from './rollout';
import { runEveningReminders } from './runEveningReminders';
import {
  ReminderEntry, ReminderIO, ReminderItem, ReminderPushPayload, ReminderRecipient, ReminderState,
} from './reminderTypes';

const REGION = 'europe-west1';
const SCHEDULE = '0 19 * * *';
const TIMEZONE = 'Africa/Lagos';

/**
 * Its own tag, NOT DAILY_REMINDER_NOTIFICATION_TAG: sharing the digest's tag would
 * make the evening push silently replace an unread morning digest in the shade.
 */
export const EVENING_REMINDER_NOTIFICATION_TAG = 'stitchpad_evening_reminder';

function reminderStateRef(db: Firestore, uid: string) {
  return db.collection('users').doc(uid).collection('private').doc('reminderState');
}

function digestStateRef(db: Firestore, uid: string) {
  return db.collection('users').doc(uid).collection('private').doc('digestState');
}

function str(v: unknown): string | null {
  return typeof v === 'string' && v.length > 0 ? v : null;
}

function finite(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
}

/** Defensive parse: a hand-edited or half-written doc must not throw for this user. */
export function parseReminderState(reminder: DocumentData | undefined, digest: DocumentData | undefined): ReminderState {
  const entries: Record<string, ReminderEntry> = {};
  const raw = reminder?.entries;
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    for (const [key, e] of Object.entries(raw as Record<string, DocumentData>)) {
      const count = finite(e?.count);
      const lastSentAt = finite(e?.lastSentAt);
      const anchor = finite(e?.anchor);
      if (count !== null && lastSentAt !== null && anchor !== null) entries[key] = { count, lastSentAt, anchor };
    }
  }
  return {
    lastEveningPushDate: str(reminder?.lastEveningPushDate),
    lastNudgeDayIndex: finite(reminder?.lastNudgeDayIndex),
    entries,
    engagementLastSentDate: str((digest?.engagement as DocumentData | undefined)?.lastSentDate),
  };
}

/** Same push-opt-out inheritance as dailyDigest.listRecipients. */
export function recipientFromUserDoc(uid: string, u: DocumentData): ReminderRecipient {
  return {
    uid,
    email: typeof u.email === 'string' ? u.email : '',
    locale: typeof u.locale === 'string' ? u.locale : undefined,
    pushEnabled: u.dailyPushEnabled !== undefined
      ? u.dailyPushEnabled !== false
      : u.dailyDigestEmailEnabled !== false,
    activityPushEnabled: u.activityPushEnabled !== false,
  };
}

async function writeInboxAdmin(db: Firestore, uid: string, items: ReminderItem[]): Promise<void> {
  const col = db.collection('users').doc(uid).collection('notifications');
  const createdAt = Date.now();
  for (const spec of reminderInboxDocs(items)) {
    try {
      // create-once: an existing row (and its read state) is left alone.
      await col.doc(spec.id).create({ ...spec.data, isRead: false, createdAt });
    } catch (err) {
      // gRPC ALREADY_EXISTS = 6 — see dailyDigest.writeNotificationsAdmin.
      if ((err as { code?: number }).code !== 6) {
        functions.logger.warn('evening reminders: inbox write failed', {
          uid, id: spec.id, error: err instanceof Error ? err.message : String(err),
        });
      }
    }
  }
}

function sendReminderPush(tokens: string[], p: ReminderPushPayload) {
  return sendMulticast(
    tokens,
    {
      title: p.title,
      body: p.body,
      data: { target: p.target, ...(p.orderId ? { orderId: p.orderId } : {}) },
      androidChannelId: DAILY_REMINDERS_CHANNEL_ID,
      androidTag: EVENING_REMINDER_NOTIFICATION_TAG,
    },
    'evening reminder push',
  );
}

export function productionReminderIO(onlyUid?: string): ReminderIO {
  const db = admin.firestore();
  return {
    isEnabled: async () => (await db.collection('config').doc('app').get()).data()?.reminderPushEnabled !== false,

    listRecipients: async () => {
      if (onlyUid) {
        const doc = await db.collection('users').doc(onlyUid).get();
        return [recipientFromUserDoc(onlyUid, doc.data() ?? {})];
      }
      const [snap, staffUids] = await Promise.all([
        db.collection('users').get(),
        loadActiveStaffUids(db),
      ]);
      const out: ReminderRecipient[] = [];
      for (const doc of snap.docs) {
        // Owners only for now: staff work inside the owner's workshop and their
        // own orders collection is empty.
        if (staffUids.has(doc.id)) continue;
        out.push(recipientFromUserDoc(doc.id, doc.data()));
      }
      return out;
    },

    isAllowed: isDigestAllowed,

    loadState: async (uid) => {
      const [reminder, digest] = await Promise.all([
        reminderStateRef(db, uid).get(),
        digestStateRef(db, uid).get(),
      ]);
      return parseReminderState(reminder.data(), digest.data());
    },

    // Full overwrite: the doc is server-only and holds nothing but this state, and
    // overwriting is what lets pruned entries actually disappear.
    saveState: async (uid, s) => {
      await reminderStateRef(db, uid).set({
        lastEveningPushDate: s.lastEveningPushDate,
        lastNudgeDayIndex: s.lastNudgeDayIndex,
        entries: s.entries,
        updatedAt: Date.now(),
      });
    },

    loadOrders: async (uid) => {
      const [snap, money] = await Promise.all([
        db.collection('users').doc(uid).collection('orders').get(),
        loadMoneyByOrderId(db, uid),
      ]);
      return withMoney(snap.docs.map((d) => mapOrderScanDoc(d.id, d.data())), money);
    },

    hasCustomerActivitySince: async (uid, sinceMs) => {
      // updatedAt is set on create as well, so one single-field query covers both.
      const snap = await db.collection('users').doc(uid).collection('customers')
        .where('updatedAt', '>=', sinceMs).limit(1).get();
      return !snap.empty;
    },

    writeInbox: (uid, items) => writeInboxAdmin(db, uid, items),
    loadPushTokens: (uid) => loadPushTokens(db, uid),
    sendPush: sendReminderPush,
    deletePushTokens: (uid, tokens) => deletePushTokens(db, uid, tokens),
  };
}

export const eveningReminders = functions
  .region(REGION)
  // Serial over every owner (orders + money + state + tokens each), same cost
  // profile as the digest — the v1 60s default would starve the tail.
  .runWith({ timeoutSeconds: 540, memory: '512MB' })
  .pubsub.schedule(SCHEDULE)
  .timeZone(TIMEZONE)
  .onRun(async () => {
    await runEveningReminders(productionReminderIO(), Date.now());
  });

/**
 * Debug/QA trigger for allowlisted testers: runs tonight's logic for the CALLER
 * only, ignoring the once-per-evening cap. Opt-outs, the kill switch and the
 * per-order caps still apply, and a real send is stamped like a scheduled one.
 */
export const debugSendMyEveningReminder = functions
  .region(REGION)
  .https.onCall(async (_data, context) => {
    const uid = context.auth?.uid;
    if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    const authUser = await admin.auth().getUser(uid);
    if (!authUser.email || !isDigestTester(authUser.email)) {
      throw new functions.https.HttpsError('permission-denied', 'not_a_tester');
    }
    const result = await runEveningReminders(productionReminderIO(uid), Date.now(), { ignoreDailyCap: true });
    return { ...result, delivered: result.sent > 0 };
  });
