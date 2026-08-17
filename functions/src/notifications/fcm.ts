/**
 * Shared FCM send + token hygiene.
 *
 * Lifted verbatim out of dailyDigest.ts's productionDigestIO so the daily digest,
 * the order-collect trigger, and the engagement push all batch, log, and prune
 * identically. Before this, the same 40 lines were duplicated per call site and
 * drifted (one logged successes, one logged failures, one batched its token
 * deletes and one did not).
 *
 * Deliberately dependency-injected on `db` rather than calling
 * admin.firestore() internally, so callers share one Firestore handle.
 */
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import type { Firestore } from 'firebase-admin/firestore';

/** Order deadlines and money owed — the channel users must never mute by accident. */
export const DAILY_REMINDERS_CHANNEL_ID = 'daily_reminders';
/**
 * Tips, feature discovery, announcements. Separately mutable from order reminders.
 *
 * The `_v2` suffix is load-bearing. Android locks a channel's importance the first
 * time the app creates it — a later code change cannot raise it, so anyone who had
 * installed the original `announcements` channel would have been stuck at
 * IMPORTANCE_LOW forever. A new id is the only way to ship a different importance.
 * Never edit this in place again; mint another id instead.
 */
export const ANNOUNCEMENTS_CHANNEL_ID = 'announcements_v2';

/**
 * Android notification tags. A tag makes a new push REPLACE the previous one with
 * the same tag instead of stacking beside it.
 *
 * These matter specifically for the background/killed case: our own notification
 * ids in StitchPadMessagingService only apply when the app is in the FOREGROUND and
 * we post the notification ourselves. When the app is backgrounded the FCM SDK
 * auto-displays the payload, ignores those ids, and — with no tag — creates a
 * brand-new notification per message. Without these, an untouched Friday nudge and
 * Tuesday's would both sit in the shade.
 */
export const ANNOUNCEMENT_NOTIFICATION_TAG = 'stitchpad_announcement';
export const DAILY_REMINDER_NOTIFICATION_TAG = 'stitchpad_daily_reminder';

const FCM_MULTICAST_LIMIT = 500;
const FIRESTORE_BATCH_LIMIT = 500;

/** FCM error codes that mean "this token is dead" — anything else is transient. */
const DEAD_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

export interface PushPayload {
  title: string;
  body: string;
  /** Tap-routing extras, e.g. { target: 'order', orderId }. */
  data?: Record<string, string>;
  /** Android channel to post on. Defaults to daily reminders. */
  androidChannelId?: string;
  /** Android notification tag — successive pushes with the same tag replace, not stack. */
  androidTag?: string;
  /**
   * Deliver without a sound. Default is OFF — everything we send is worth hearing.
   *
   * iOS ONLY. Android takes its sound from the channel's importance, which the user
   * controls and which a message payload cannot override on API 26+.
   */
  silent?: boolean;
}

export interface PushResult {
  successCount: number;
  /** Dead tokens the caller should delete — see {@link deletePushTokens}. */
  invalidTokens: string[];
}

/**
 * Sends to every token in batches of 500 (the FCM multicast cap).
 *
 * Every failure is logged, not just the two token-invalid codes we prune on:
 * other failures (APNs auth/credential, propagation, internal) used to be
 * silently swallowed, which left "no push arrived" undiagnosable. Tokens are
 * truncated in logs — not PII, but no reason to write them in full.
 *
 * `logLabel` identifies the caller in logs, e.g. 'digest push'.
 */
export async function sendMulticast(
  tokens: string[],
  payload: PushPayload,
  logLabel: string,
): Promise<PushResult> {
  const invalidTokens: string[] = [];
  let successCount = 0;

  for (let i = 0; i < tokens.length; i += FCM_MULTICAST_LIMIT) {
    const batch = tokens.slice(i, i + FCM_MULTICAST_LIMIT);
    const res = await admin.messaging().sendEachForMulticast({
      tokens: batch,
      notification: { title: payload.title, body: payload.body },
      android: {
        notification: {
          channelId: payload.androidChannelId ?? DAILY_REMINDERS_CHANNEL_ID,
          ...(payload.androidTag ? { tag: payload.androidTag } : {}),
        },
      },
      // APNs plays NOTHING unless aps.sound is set — the top-level `notification`
      // field only becomes aps.alert. Without this every iOS push we have ever sent,
      // digest included, arrived silently. Android needs no equivalent: its sound
      // comes from the channel's importance, which a payload cannot override on
      // API 26+ and which the user owns.
      ...(payload.silent ? {} : { apns: { payload: { aps: { sound: 'default' } } } }),
      ...(payload.data ? { data: payload.data } : {}),
    });

    res.responses.forEach((r, j) => {
      if (r.success) {
        successCount++;
        return;
      }
      const code = r.error?.code;
      functions.logger.error(`${logLabel}: FCM send failed`, {
        code,
        message: r.error?.message,
        tokenPrefix: batch[j].slice(0, 24),
      });
      if (code && DEAD_TOKEN_CODES.has(code)) invalidTokens.push(batch[j]);
    });
  }

  return { successCount, invalidTokens };
}

/**
 * Every registered token for a user. The doc id IS the token (the `token` field
 * is duplicated inside for collectionGroup lookups), so ids are all we read.
 */
export async function loadPushTokens(db: Firestore, uid: string): Promise<string[]> {
  const snap = await db.collection('users').doc(uid).collection('notificationTokens').get();
  return snap.docs.map((d) => d.id);
}

/** Batch-deletes dead tokens so a stale device stops costing a send every run. */
export async function deletePushTokens(db: Firestore, uid: string, tokens: string[]): Promise<void> {
  const col = db.collection('users').doc(uid).collection('notificationTokens');
  for (let i = 0; i < tokens.length; i += FIRESTORE_BATCH_LIMIT) {
    const batch = db.batch();
    for (const t of tokens.slice(i, i + FIRESTORE_BATCH_LIMIT)) {
      batch.delete(col.doc(t));
    }
    await batch.commit();
  }
}
