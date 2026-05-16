import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  DraftMessageRequest,
  DraftMessageResponse,
  DraftContext,
  UserProfileSummary,
  FreeTierUsageDoc,
  IntentType,
  Language,
} from './types';

const SUPPORTED_INTENT_TYPES: readonly IntentType[] = [
  'balance_reminder',
  'pickup_ready',
  'follow_up',
  'custom_note',
];
const SUPPORTED_LANGUAGES: readonly Language[] = ['en', 'pcm'];
// Mirrors NOTES_MAX_CHARS in DraftMessageScreen.kt; keep the two in sync.
const CUSTOM_NOTES_MAX_LENGTH = 200;

function isIntentType(value: unknown): value is IntentType {
  return typeof value === 'string' && (SUPPORTED_INTENT_TYPES as readonly string[]).includes(value);
}

function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}
import { buildSystemPrompt, buildUserPrompt } from './promptBuilder';
import { reconcileUsage, isExhausted } from './freeTierCounter';
import { getVertexClient, VertexClient } from './vertexClient';

/**
 * Test seam — production code uses getVertexClient() directly.
 * Tests inject a fake via __setVertexClientForTests.
 */
let injectedVertexClient: VertexClient | null = null;
export function __setVertexClientForTests(client: VertexClient): void {
  injectedVertexClient = client;
}
function vertex(): VertexClient {
  return injectedVertexClient ?? getVertexClient();
}

/**
 * Test seam — production wraps admin.firestore() docs into an io shim.
 * Tests inject a fake io directly.
 */
export type FreeTierReservation =
  | { exhausted: true }
  | { exhausted: false; usage: FreeTierUsageDoc };

export interface DraftMessageIO {
  profileGet(): Promise<{ exists: boolean; data(): UserProfileSummary | undefined }>;
  /**
   * Atomically reads the current free-tier usage doc, checks whether the
   * caller is exhausted for this month, and (if not exhausted) writes the
   * incremented doc. Runs inside a Firestore transaction in production so
   * two concurrent invocations cannot both pass the limit check and both
   * succeed at writing count = limit. Returns `exhausted: true` when the
   * caller has already used their monthly allowance and no write happened.
   *
   * Trade-off: the reservation is committed BEFORE the Vertex call. If
   * Vertex then fails, the user "loses" one quota slot without getting a
   * draft. Accepted for V1 (small tester cohort, low Vertex flake rate);
   * a compensating decrement can be added in V1.5 if needed.
   */
  reserveFreeTierSlot(now: Date): Promise<FreeTierReservation>;
  customerGet(): Promise<{ exists: boolean; data(): { firstName: string } | undefined }>;
  orderGet(): Promise<{ exists: boolean; data(): {
    customerId: string;
    garmentLabel: string;
    depositFormatted: string;
    balanceFormatted: string;
    deadlineFormatted: string;
    isOpen: boolean;
  } | undefined }>;
}

/**
 * Raw Firestore document shapes — mirror the Kotlin DTOs in
 * composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/.
 * The user doc IS the profile (no separate `profile` subdoc); customers store
 * `name` (full), not `firstName`; orders store the raw amounts + items, so
 * the formatted strings the prompt needs are computed here.
 *
 * The app writes `subscriptionTier`; `tier` is only present on test docs
 * that were manually edited in the Firebase Console before the field was
 * standardized. Read both with `subscriptionTier` preferred so premium
 * users aren't rate-limited as free.
 */
interface RawUserDoc {
  subscriptionTier?: 'free' | 'premium';
  tier?: 'free' | 'premium';
}

interface RawCustomerDoc {
  name?: string;
}

interface RawOrderItemDoc {
  description?: string;
  garmentType?: string;
}

interface RawPaymentDoc {
  amount?: number;
}

interface RawOrderDoc {
  customerId: string;
  customerName?: string;
  items?: RawOrderItemDoc[];
  totalPrice?: number;
  payments?: RawPaymentDoc[];
  // Legacy field on orders created before the payments[] migration. The
  // Kotlin OrderRepository absorbs this into payments[] on the next write,
  // but read paths still need to honor it standalone.
  depositPaid?: number;
  deadline?: number | null;
  // Used to filter out delivered/archived orders server-side so a stale
  // client-side picker selection can't generate a draft for a closed order.
  status?: string;
  archivedAt?: number | null;
}

function productionIO(uid: string, customerId: string, orderId: string, db: admin.firestore.Firestore): DraftMessageIO {
  return {
    profileGet: () => db.doc(`users/${uid}`).get().then((snap) => ({
      exists: snap.exists,
      data: (): UserProfileSummary | undefined => {
        const raw = snap.data() as RawUserDoc | undefined;
        if (!raw) return undefined;
        return { tier: raw.subscriptionTier ?? raw.tier ?? 'free' };
      },
    })),
    reserveFreeTierSlot: async (now: Date): Promise<FreeTierReservation> => {
      const ref = db.doc(`users/${uid}/usage/smart_drafts`);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const existing = snap.exists ? (snap.data() as FreeTierUsageDoc) : null;
        const next = reconcileUsage({ existing, now });
        if (existing !== null && isExhausted(existing) && existing.monthYear === next.monthYear) {
          return { exhausted: true } as const;
        }
        tx.set(ref, next);
        return { exhausted: false, usage: next } as const;
      });
    },
    customerGet: () => db.doc(`users/${uid}/customers/${customerId}`).get().then((snap) => ({
      exists: snap.exists,
      data: (): { firstName: string } | undefined => {
        const raw = snap.data() as RawCustomerDoc | undefined;
        if (!raw) return undefined;
        const name = (raw.name ?? '').trim();
        const first = name.split(/\s+/)[0] || 'Customer';
        return { firstName: first };
      },
    })),
    orderGet: () => db.doc(`users/${uid}/orders/${orderId}`).get().then((snap) => ({
      exists: snap.exists,
      data: () => {
        const raw = snap.data() as RawOrderDoc | undefined;
        if (!raw) return undefined;
        const total = raw.totalPrice ?? 0;
        const paymentsSum = (raw.payments ?? []).reduce(
          (sum, p) => sum + (p.amount ?? 0),
          0,
        );
        // Fall back to legacy depositPaid when payments[] hasn't been written
        // yet — otherwise existing orders draft with the full total as the
        // outstanding balance.
        const deposit = paymentsSum > 0 ? paymentsSum : (raw.depositPaid ?? 0);
        const balance = Math.max(0, total - deposit);
        const firstItem = raw.items?.[0];
        const garmentLabel =
          (firstItem?.description?.trim() ||
            (firstItem?.garmentType ? humanizeGarmentType(firstItem.garmentType) : '') ||
            'Garment');
        return {
          customerId: raw.customerId,
          garmentLabel,
          depositFormatted: formatNaira(deposit),
          balanceFormatted: formatNaira(balance),
          deadlineFormatted: raw.deadline ? formatDeadline(raw.deadline) : 'No deadline set',
          isOpen: raw.archivedAt == null && (raw.status ?? 'PENDING') !== 'DELIVERED',
        };
      },
    })),
  };
}

function formatNaira(amount: number): string {
  const whole = Math.round(amount).toString();
  const grouped = whole.split('').reverse().reduce<string[]>((acc, ch, i) => {
    if (i > 0 && i % 3 === 0) acc.push(',');
    acc.push(ch);
    return acc;
  }, []).reverse().join('');
  return `₦${grouped}`;
}

function formatDeadline(epochMillis: number): string {
  const d = new Date(epochMillis);
  const day = d.toLocaleDateString('en-GB', { weekday: 'long' });
  const month = d.toLocaleDateString('en-GB', { month: 'long' });
  return `${day}, ${month} ${d.getDate()}`;
}

function humanizeGarmentType(raw: string): string {
  const lower = raw.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/**
 * Pure handler for testing. Production wraps this in functions.https.onCall.
 */
export async function draftMessageHandler(
  data: DraftMessageRequest,
  context: functions.https.CallableContext,
  io: DraftMessageIO,
  now: Date = new Date(),
): Promise<DraftMessageResponse> {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }

  // 0. Validate enum fields up front. The TypeScript types only constrain
  // trusted callers; an outdated or direct client can pass any string here.
  // Without this guard, buildUserPrompt would index its enum maps with the
  // unknown value, produce an `undefined` label, and we'd burn quota on a
  // garbage Vertex call.
  if (!isIntentType(data.intentType)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported intentType');
  }
  if (!isLanguage(data.language)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported language');
  }
  // customNotes is optional per the type — undefined/null are fine. Anything
  // else must be a string under the UI's 200-char cap. Otherwise a direct
  // caller could send arbitrary data, burn a quota slot, and only then crash
  // inside buildUserPrompt's .trim().
  if (data.customNotes != null) {
    if (typeof data.customNotes !== 'string' || data.customNotes.length > CUSTOM_NOTES_MAX_LENGTH) {
      throw new functions.https.HttpsError('invalid-argument', 'invalid_input: customNotes too long or wrong type');
    }
  }

  // 1. Tier check
  const profileSnap = await io.profileGet();
  const tier = profileSnap.exists ? profileSnap.data()?.tier ?? 'free' : 'free';

  // 2. Validate customer + order BEFORE reserving the free-tier slot — a
  // stale selection or deleted order should fail loudly without burning
  // one of the caller's five monthly drafts.
  const [customerSnap, orderSnap] = await Promise.all([io.customerGet(), io.orderGet()]);
  if (!customerSnap.exists) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: customer not found');
  }
  const customer = customerSnap.data()!;
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: order not found');
  }
  const order = orderSnap.data()!;
  if (order.customerId !== data.customerId) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: order does not belong to customer');
  }
  // The client picker only shows open orders, but the callable is open to
  // direct invocation — reject delivered/archived orders here so a stale
  // selection doesn't generate a draft for an already-closed order.
  if (!order.isOpen) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: order is not open');
  }

  // 3. Reserve the free-tier slot now that inputs are known good.
  let nextUsage: FreeTierUsageDoc | null = null;
  if (tier === 'free') {
    const reservation = await io.reserveFreeTierSlot(now);
    if (reservation.exhausted) {
      throw new functions.https.HttpsError('permission-denied', 'free_tier_exhausted');
    }
    nextUsage = reservation.usage;
  }

  // 3. Build prompts
  const draftCtx: DraftContext = {
    customerFirstName: customer.firstName,
    garmentLabel: order.garmentLabel,
    depositFormatted: order.depositFormatted,
    balanceFormatted: order.balanceFormatted,
    deadlineFormatted: order.deadlineFormatted,
  };
  const systemPrompt = buildSystemPrompt();
  const userPrompt = buildUserPrompt({
    intentType: data.intentType,
    language: data.language,
    context: draftCtx,
    customNotes: data.customNotes,
  });

  // 4. Call Vertex
  let draftText: string;
  try {
    draftText = await vertex().generateText({ systemPrompt, userPrompt });
  } catch (err) {
    // Log the real Vertex failure so operators can diagnose it from Cloud
    // Logging without changing the client-facing error code.
    functions.logger.error('vertex_call_failed', {
      message: err instanceof Error ? err.message : String(err),
      stack: err instanceof Error ? err.stack : undefined,
    });
    throw new functions.https.HttpsError('unavailable', 'service_unavailable');
  }

  // Counter is already incremented via reserveFreeTierSlot above — no
  // additional write needed here. See the type doc on that method for the
  // trade-off explanation.

  return {
    draftText,
    remainingFreeQuota: tier === 'premium' ? null : (nextUsage!.limit - nextUsage!.count),
  };
}

/**
 * Production callable export.
 */
export const smartDraftMessage = functions
  .region('europe-west1')
  .https.onCall(async (data: DraftMessageRequest, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const db = admin.firestore();
    const io = productionIO(context.auth.uid, data.customerId, data.orderId, db);
    return draftMessageHandler(data, context, io);
  });
