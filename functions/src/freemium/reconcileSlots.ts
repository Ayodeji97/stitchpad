import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { CustomerSlotInfo, SlotChange, ReconcileSlotsResponse } from './types';

export type { CustomerSlotInfo, SlotChange, ReconcileSlotsResponse };

const DAY_MS = 24 * 60 * 60 * 1000;
const ACTIVITY_WINDOW_DAYS = 30;

/**
 * Pure function — picks which customers to LOCK to bring the active-slot
 * count down to `cap`, using a 50/50 mix of active + inactive (rounded
 * to nearest). Also promotes already-LOCKED customers back to ACTIVE
 * when cap rises (e.g., the user upgrades to Pro).
 *
 * "Active" = last activity within the last 30 days from `now`.
 * "Inactive" = older than 30 days (or never).
 *
 * Within each bucket, oldest-last-touch is locked first.
 */
export function selectSlotsToLock(
  customers: CustomerSlotInfo[],
  cap: number,
  now: Date,
): SlotChange[] {
  const activeCustomers = customers.filter((c) => c.slotState === 'active');
  const lockedCustomers = customers.filter((c) => c.slotState === 'locked');

  // Cap rose enough to fit everyone — promote all locked back to active.
  // (V1.5 may also need to re-lock if user downgrades again; for V1.0 a
  // separate call handles it.)
  if (activeCustomers.length + lockedCustomers.length <= cap) {
    return lockedCustomers.map((c) => ({ id: c.id, toState: 'active' as const }));
  }

  // Cap is finite but rose: promote enough locked customers to reach the
  // new active cap. Within locked, promote MOST-RECENT activity first
  // (mirror image of the lock rule — last-locked-by-activity first to be
  // unlocked).
  if (activeCustomers.length < cap && lockedCustomers.length > 0) {
    const slotsAvailable = cap - activeCustomers.length;
    const toPromote = lockedCustomers
      .slice() // don't mutate input
      .sort((a, b) => b.lastActivityMs - a.lastActivityMs) // newest first
      .slice(0, slotsAvailable);
    return toPromote.map((c) => ({ id: c.id, toState: 'active' as const }));
  }

  if (activeCustomers.length <= cap) {
    return []; // Nothing to do — already at or below cap on active and no locked to promote.
  }

  const toLockCount = activeCustomers.length - cap;
  const nowMs = now.getTime();
  const cutoff = nowMs - ACTIVITY_WINDOW_DAYS * DAY_MS;

  const activeBucket = activeCustomers
    .filter((c) => c.lastActivityMs >= cutoff)
    .sort((a, b) => a.lastActivityMs - b.lastActivityMs); // oldest first
  const inactiveBucket = activeCustomers
    .filter((c) => c.lastActivityMs < cutoff)
    .sort((a, b) => a.lastActivityMs - b.lastActivityMs); // oldest first

  const fromInactive = Math.min(Math.round(toLockCount / 2), inactiveBucket.length);
  const fromActive = Math.min(toLockCount - fromInactive, activeBucket.length);

  // If one bucket is short, fill the gap from the other.
  const remaining = toLockCount - fromInactive - fromActive;
  const extras: CustomerSlotInfo[] = [];
  if (remaining > 0) {
    const fallback = inactiveBucket.length > activeBucket.length
      ? inactiveBucket.slice(fromInactive)
      : activeBucket.slice(fromActive);
    extras.push(...fallback.slice(0, remaining));
  }

  return [
    ...inactiveBucket.slice(0, fromInactive),
    ...activeBucket.slice(0, fromActive),
    ...extras,
  ].map((c) => ({ id: c.id, toState: 'locked' as const }));
}

/**
 * HTTPS callable. The client invokes this on app foreground when it
 * detects a tier or welcome-window change. Idempotent — safe to call
 * any time; only writes when a change is needed.
 */
export const reconcileCustomerSlots = functions
  .region('europe-west1')
  .https.onCall(async (_data, context): Promise<ReconcileSlotsResponse> => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const uid = context.auth.uid;
    const db = admin.firestore();
    const userSnap = await db.doc(`users/${uid}`).get();
    if (!userSnap.exists) {
      throw new functions.https.HttpsError('failed-precondition', 'user_not_found');
    }
    const user = userSnap.data() as {
      subscriptionTier?: string;
      welcomeBonusAppliedAt?: admin.firestore.Timestamp | number;
    };
    const tier = normalizeTier(user.subscriptionTier);
    const welcomeMillis = toEpochMs(user.welcomeBonusAppliedAt);
    const inWelcome = isInWelcomeWindow(welcomeMillis, new Date());
    const cap = effectiveCap(tier, inWelcome);

    const customersSnap = await db.collection(`users/${uid}/customers`).get();
    const infos: CustomerSlotInfo[] = customersSnap.docs.map((d) => {
      const c = d.data() as { slotState?: string; updatedAt?: number; lastActivityAt?: number };
      return {
        id: d.id,
        lastActivityMs: c.lastActivityAt ?? c.updatedAt ?? 0,
        slotState: normalizeSlotState(c.slotState),
      };
    });

    const changes = selectSlotsToLock(infos, cap, new Date());
    if (changes.length === 0) {
      const activeNow = infos.filter((c) => c.slotState === 'active').length;
      return {
        changes: [],
        totalActiveAfter: activeNow,
        totalLockedAfter: infos.length - activeNow,
      };
    }

    const BATCH_LIMIT = 500;
    for (let i = 0; i < changes.length; i += BATCH_LIMIT) {
      const chunk = changes.slice(i, i + BATCH_LIMIT);
      const batch = db.batch();
      for (const ch of chunk) {
        batch.update(db.doc(`users/${uid}/customers/${ch.id}`), {
          slotState: ch.toState,
          lockedAt: ch.toState === 'locked' ? Date.now() : null,
        });
      }
      await batch.commit();
    }

    // Build a map of changes by id for fast lookup
    const changesById = new Map(changes.map((c) => [c.id, c.toState]));
    // Apply the changes to the original states to compute the new totals.
    let activeAfter = 0;
    for (const info of infos) {
      const finalState = changesById.get(info.id) ?? info.slotState;
      if (finalState === 'active') activeAfter += 1;
    }
    return {
      changes,
      totalActiveAfter: activeAfter,
      totalLockedAfter: infos.length - activeAfter,
    };
  });

// Customer-cap constants. MUST stay in lockstep with Kotlin's
// EntitlementsCalculator (composeApp/.../core/domain/entitlement/EntitlementsCalculator.kt):
//
//   FIRST_MONTH_CUSTOMER_CAP = 200  // matches WELCOME_CUSTOMER_CAP on the client
//   FREE_CUSTOMER_CAP        = 15   // matches FREE_CUSTOMER_CAP on the client
//
// If these diverge, reconcileSlots will lock customers the client just allowed —
// a real data-corruption flow on First Month users with 31–199 customers, which
// is exactly what the PR-1 review caught when this server constant lagged 30.
const FIRST_MONTH_CUSTOMER_CAP = 200;
const FREE_CUSTOMER_CAP = 15;

export function effectiveCap(tier: string, inWelcome: boolean): number {
  if (tier === 'pro' || tier === 'atelier') return Number.MAX_SAFE_INTEGER;
  return inWelcome ? FIRST_MONTH_CUSTOMER_CAP : FREE_CUSTOMER_CAP;
}

/**
 * Coerce any client-supplied slotState string to one of the two valid buckets.
 *
 * Firestore rules deliberately leave `customers/{id}.slotState` client-writable
 * for V1.0 (the SwapSheet flow batches two slotState writes from the client).
 * A malicious or buggy client could write an arbitrary value like `"fancy"` or
 * `""` — without normalization here, `selectSlotsToLock` filters by exact
 * `=== 'active'` / `=== 'locked'` equality, so unknown values escape both
 * buckets and the customer becomes permanently invisible to reconcile (durable
 * cap bypass).
 *
 * Mirrors the Kotlin `CustomerSlotState.fromWire` mapping (anything-not-locked
 * → active) so reconcile always counts the customer toward the cap.
 */
export function normalizeSlotState(raw: string | undefined): 'active' | 'locked' {
  return raw?.toLowerCase() === 'locked' ? 'locked' : 'active';
}

// Legacy accounts may carry subscriptionTier === "premium" from pre-V1.0 writes.
// draftMessage.ts normalizes the same way; keep both in lockstep so a "premium"
// account doesn't silently fall through to the free cap and lock its customers.
function normalizeTier(raw: string | undefined): string {
  if (raw === 'premium') return 'pro';
  return raw ?? 'free';
}

function toEpochMs(value: admin.firestore.Timestamp | number | undefined): number | undefined {
  if (value === undefined) return undefined;
  if (typeof value === 'number') return value;
  return value.toMillis(); // firestore.Timestamp
}

/**
 * Length of the rolling First Month window in days, measured from
 * `welcomeBonusAppliedAt`. MUST stay in lockstep with Kotlin's
 * `EntitlementsCalculator.WELCOME_WINDOW_DAYS`. Tests on both sides pin
 * the literal value — see `effectiveCap` tests and EntitlementsCalculatorTest.
 */
export const WELCOME_WINDOW_DAYS = 30;
const MS_PER_DAY = 24 * 60 * 60 * 1000;

export function isInWelcomeWindow(welcomeAppliedAtMs: number | undefined, now: Date): boolean {
  if (!welcomeAppliedAtMs) return false;
  return now.getTime() < welcomeEndsAtMs(welcomeAppliedAtMs);
}

/**
 * Given the welcome-application instant, returns the millisecond instant when
 * the rolling First Month window expires — exactly `WELCOME_WINDOW_DAYS` after
 * the application. Mirrors the client-side EntitlementsCalculator so the
 * welcome window is computed identically on both ends.
 *
 * Previously this was calendar-month-aligned (end of the Lagos signup month).
 * Switched to rolling on 2026-05-22 so every signup gets a fair 30 days
 * regardless of which day of the month they signed up.
 */
export function welcomeEndsAtMs(welcomeAppliedAtMs: number): number {
  return welcomeAppliedAtMs + WELCOME_WINDOW_DAYS * MS_PER_DAY;
}
