import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { balanceRemaining, summariseGarments } from './digestDetector';
import {
  DAILY_REMINDERS_CHANNEL_ID,
  deletePushTokens,
  loadPushTokens,
  sendMulticast,
} from './fcm';
import { isStampedMoney, moneyFromDoc } from './orderMoney';
import { isDigestAllowed } from './rollout';

const REGION = 'europe-west1';
const COLLECTIBLE = new Set(['READY', 'DELIVERED']);

export interface CollectNotification {
  customerName: string;
  garmentSummary: string;
  amount: number;               // rounded naira
  status: 'READY' | 'DELIVERED';
}

/**
 * The status half of the check, decidable from the base doc alone — no money needed.
 * Split out so the handler can gate on it BEFORE paying for the money read.
 */
export function isCollectibleTransition(before: unknown, after: unknown): boolean {
  const beforeStatus = (before as { status?: string })?.status ?? '';
  const afterStatus = (after as { status?: string })?.status ?? '';
  return !COLLECTIBLE.has(beforeStatus) && COLLECTIBLE.has(afterStatus);
}

/**
 * First entry into a collectible (READY/DELIVERED) state with a balance owing → notify.
 *
 * `after` MUST already have its /private/money merged in. The base order doc carries no
 * money since Slice 8d-1, so calling this with the raw snapshot makes balanceRemaining
 * return 0 every time and the trigger silently never fires — which is exactly what was
 * happening in production.
 */
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
    // Cheap status gate FIRST — this is the highest-frequency trigger in the system
    // and must not pay a Firestore read on every field change. Only the money is
    // unknown at this point, so gate on the transition, then fetch.
    if (!isCollectibleTransition(change.before.data(), change.after.data())) return;

    // Money lives in /private/money; the base doc that fired this trigger has none.
    const moneySnap = await admin.firestore()
      .doc(`users/${uid}/orders/${orderId}/private/money`).get()
      .catch(() => null);
    // Merge ONLY a STAMPED mirror — the same completeness sentinel the digest's
    // collection-group query applies, and the same rule the client uses. Merely
    // existing is not enough: an unstamped partial mirror (payments only, written by
    // recordPayment on a legacy order) carries no totalPrice, so spreading it would
    // zero the base doc's real price and silence this trigger for exactly the orders
    // it is meant to catch. Without the sentinel the digest and this trigger would
    // also disagree about the same order.
    const money = moneySnap?.data();
    const afterWithMoney = isStampedMoney(money)
      ? { ...change.after.data(), ...moneyFromDoc(money) }
      : change.after.data();

    const n = collectibleTransition(change.before.data(), afterWithMoney);
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
      let email = (u.email as string | undefined) ?? '';
      if (!email) {
        // Older/never-updated user docs can have a blank email field; fall back to the
        // Firebase Auth record (source of truth) so the rollout allowlist gate resolves.
        email = await admin.auth().getUser(uid).then((r) => r.email ?? '').catch(() => '');
      }
      const pushEnabled = u.dailyPushEnabled !== undefined
        ? u.dailyPushEnabled !== false
        : u.dailyDigestEmailEnabled !== false;
      const allowed = isDigestAllowed(uid, email);
      if (!pushEnabled || !allowed) {
        functions.logger.info('onOrderCollectible: push skipped (gated)', { uid, orderId, pushEnabled, allowed, emailPresent: !!email });
        return;
      }

      const tokens = await loadPushTokens(db, uid);
      if (tokens.length === 0) {
        functions.logger.info('onOrderCollectible: push skipped (no tokens)', { uid, orderId });
        return;
      }

      // Routed through fcm.ts like the other two jobs. Inlining its own send meant this
      // one — the MONEY notification — missed the aps.sound fix and stayed silent on
      // iOS, skipped the android tag, hardcoded the channel id, and would have thrown
      // above 500 tokens because sendEachForMulticast caps there.
      const { title, body } = collectPushCopy(n);
      const { successCount, invalidTokens } = await sendMulticast(
        tokens,
        {
          title,
          body,
          data: { target: 'order', orderId },
          androidChannelId: DAILY_REMINDERS_CHANNEL_ID,
          // Per-ORDER tag. Sharing the digest's tag meant Android's background
          // auto-display replaced one collectible order with the next — and the digest
          // replaced both — so an owner could simply never learn order A was ready.
          // The foreground path already separates these by notification id; this makes
          // the background path agree. Same order re-notifying still replaces, which is
          // what you want.
          androidTag: `stitchpad_order_${orderId}`,
        },
        'order-collect push',
      );
      functions.logger.info('onOrderCollectible: push sent', {
        uid, orderId, tokenCount: tokens.length, successCount,
      });
      if (invalidTokens.length > 0) await deletePushTokens(db, uid, invalidTokens);
    } catch (err) {
      functions.logger.error('onOrderCollectible: push failed', { uid, orderId, error: err instanceof Error ? err.message : String(err) });
    }
  });
