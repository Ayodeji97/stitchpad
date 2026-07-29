import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { balanceRemaining, summariseGarments } from './digestDetector';
import { isDigestAllowed } from './rollout';

const REGION = 'europe-west1';
const COLLECTIBLE = new Set(['READY', 'DELIVERED']);

export interface CollectNotification {
  customerName: string;
  garmentSummary: string;
  amount: number;               // rounded naira
  status: 'READY' | 'DELIVERED';
}

/** First entry into a collectible (READY/DELIVERED) state with a balance owing → notify. */
export function collectibleTransition(before: unknown, after: any): CollectNotification | null {
  const beforeStatus = (before as { status?: string })?.status ?? '';
  if (COLLECTIBLE.has(beforeStatus)) return null;      // already collectible → not a first entry
  if (!COLLECTIBLE.has(after?.status)) return null;
  const bal = balanceRemaining(after);
  if (bal <= 0) return null;
  return {
    customerName: after.customerName ?? '',
    garmentSummary: summariseGarments(after.items ?? []),
    amount: Math.round(bal),
    status: after.status,
  };
}

export function collectPushCopy(n: CollectNotification): { title: string; body: string } {
  const state = n.status === 'READY' ? 'ready' : 'delivered';
  const firstName = (n.customerName.trim().split(/\s+/)[0]) || n.customerName;
  return {
    title: 'StitchPad',
    body: `${firstName}'s ${n.garmentSummary} is ${state} — ₦${n.amount.toLocaleString('en-NG')} to collect`,
  };
}

/**
 * Instant notify: the first time an order enters a collectible (READY/DELIVERED) state with a
 * balance owing, write a deduped in-app inbox doc and send an immediate push (gated by the same
 * pushEnabled + rollout-allowlist rules as the daily digest). Runs on every order update, so the
 * early `if (!n) return` after the pure check keeps non-qualifying updates cheap — no Firestore
 * reads happen before that. The two reads below (user doc, tokens) are strictly inside the
 * qualifying branch.
 */
export const onOrderCollectible = functions
  .region(REGION)
  .firestore.document('users/{uid}/orders/{orderId}')
  .onUpdate(async (change, context) => {
    const uid = context.params.uid as string;
    const orderId = context.params.orderId as string;
    const n = collectibleTransition(change.before.data(), change.after.data());
    if (!n) return;
    functions.logger.info('onOrderCollectible: qualifying transition', { uid, orderId, status: n.status, amount: n.amount });

    const db = admin.firestore();

    // 1) In-app inbox doc — ungated, deduped against the daily digest via the same id.
    // NOTE: isOverdue is pinned false here and stays false for the doc's life. The daily
    // digest writes the SAME deterministic `${orderId}__TO_COLLECT` id via .create(), and
    // since this instant write always lands first, the digest's `.create()` hits
    // ALREADY_EXISTS (swallowed below) and its later, correctly-computed isOverdue never
    // gets applied — this doc is never updated after creation. A future inbox "overdue"
    // badge must NOT read notification.isOverdue for TO_COLLECT; recompute from a live
    // source (e.g. re-run the owedSince/daysOwed check against current order data).
    try {
      await db.collection('users').doc(uid).collection('notifications')
        .doc(`${orderId}__TO_COLLECT`)
        .create({
          orderId, type: 'TO_COLLECT', customerName: n.customerName,
          garmentSummary: n.garmentSummary, amount: n.amount,
          deadline: null, isOverdue: false, isRead: false, createdAt: Date.now(),
        });
    } catch (err) {
      if ((err as { code?: number }).code !== 6) {
        functions.logger.warn('onOrderCollectible: notification write failed', { uid, orderId });
      }
    }

    // 2) Immediate push — gated by pushEnabled + rollout, mirroring dailyDigest.
    try {
      const userSnap = await db.collection('users').doc(uid).get();
      const u = userSnap.data() ?? {};
      const email = (u.email as string | undefined) ?? '';
      const pushEnabled = u.dailyPushEnabled !== undefined
        ? u.dailyPushEnabled !== false
        : u.dailyDigestEmailEnabled !== false;
      if (!pushEnabled || !isDigestAllowed(uid, email)) {
        functions.logger.info('onOrderCollectible: push skipped (gated)', { uid, orderId, pushEnabled, allowed: isDigestAllowed(uid, email) });
        return;
      }

      const tokensSnap = await db.collection('users').doc(uid).collection('notificationTokens').get();
      const tokens = tokensSnap.docs.map((d) => d.id);
      if (tokens.length === 0) {
        functions.logger.info('onOrderCollectible: push skipped (no tokens)', { uid, orderId });
        return;
      }

      const { title, body } = collectPushCopy(n);
      const res = await admin.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        android: { notification: { channelId: 'daily_reminders' } },
        data: { target: 'order', orderId },
      });
      functions.logger.info('onOrderCollectible: push sent', {
        uid, orderId, tokenCount: tokens.length,
        successCount: res.successCount, failureCount: res.failureCount,
        failureCodes: res.responses.filter((r) => !r.success).map((r) => r.error?.code),
      });
      const invalid: string[] = [];
      res.responses.forEach((r, i) => {
        if (!r.success && (r.error?.code === 'messaging/registration-token-not-registered'
          || r.error?.code === 'messaging/invalid-registration-token')) invalid.push(tokens[i]);
      });
      await Promise.all(invalid.map((t) =>
        db.collection('users').doc(uid).collection('notificationTokens').doc(t).delete().catch(() => undefined)));
    } catch (err) {
      functions.logger.error('onOrderCollectible: push failed', { uid, orderId, error: err instanceof Error ? err.message : String(err) });
    }
  });
