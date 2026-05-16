import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  DraftMessageRequest,
  DraftMessageResponse,
  DraftContext,
  UserProfileSummary,
  FreeTierUsageDoc,
} from './types';
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
export interface DraftMessageIO {
  profileGet(): Promise<{ exists: boolean; data(): UserProfileSummary | undefined }>;
  usageGet(): Promise<{ exists: boolean; data(): FreeTierUsageDoc | undefined }>;
  usageSet(doc: FreeTierUsageDoc): Promise<void>;
  customerGet(): Promise<{ exists: boolean; data(): { firstName: string } | undefined }>;
  orderGet(): Promise<{ exists: boolean; data(): {
    customerId: string;
    garmentLabel: string;
    depositFormatted: string;
    balanceFormatted: string;
    deadlineFormatted: string;
  } | undefined }>;
}

/**
 * Raw Firestore document shapes — mirror the Kotlin DTOs in
 * composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/.
 * The user doc IS the profile (no separate `profile` subdoc); customers store
 * `name` (full), not `firstName`; orders store the raw amounts + items, so
 * the formatted strings the prompt needs are computed here.
 */
interface RawUserDoc {
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
  deadline?: number | null;
}

function productionIO(uid: string, customerId: string, orderId: string, db: admin.firestore.Firestore): DraftMessageIO {
  return {
    profileGet: () => db.doc(`users/${uid}`).get().then((snap) => ({
      exists: snap.exists,
      data: (): UserProfileSummary | undefined => {
        const raw = snap.data() as RawUserDoc | undefined;
        if (!raw) return undefined;
        return { tier: raw.tier ?? 'free' };
      },
    })),
    usageGet: () => db.doc(`users/${uid}/usage/smart_drafts`).get().then(
      (snap) => toExistsShape<FreeTierUsageDoc>(snap)),
    usageSet: (doc) => db.doc(`users/${uid}/usage/smart_drafts`).set(doc).then(() => undefined),
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
        const deposit = (raw.payments ?? []).reduce((sum, p) => sum + (p.amount ?? 0), 0);
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
        };
      },
    })),
  };
}

function toExistsShape<T>(snap: admin.firestore.DocumentSnapshot): { exists: boolean; data(): T | undefined } {
  return { exists: snap.exists, data: () => snap.data() as T | undefined };
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

  // 1. Tier check
  const profileSnap = await io.profileGet();
  const tier = profileSnap.exists ? profileSnap.data()?.tier ?? 'free' : 'free';

  let nextUsage: FreeTierUsageDoc | null = null;
  if (tier === 'free') {
    const usageSnap = await io.usageGet();
    const existing = usageSnap.exists ? (usageSnap.data() ?? null) : null;
    nextUsage = reconcileUsage({ existing, now });
    if (existing !== null && isExhausted(existing) && existing.monthYear === nextUsage.monthYear) {
      throw new functions.https.HttpsError('permission-denied', 'free_tier_exhausted');
    }
  }

  // 2. Fetch context (validates ownership)
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
  } catch {
    throw new functions.https.HttpsError('unavailable', 'service_unavailable');
  }

  // 5. Increment counter (free tier only) — happens AFTER Vertex success
  if (tier === 'free' && nextUsage !== null) {
    await io.usageSet(nextUsage);
  }

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
