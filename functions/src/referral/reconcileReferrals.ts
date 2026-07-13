import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { lagosDateKey } from '../notifications/lagosTime';
import {
  REGION,
  REFERRALS,
  MARKETERS,
  QUALIFY_DISTINCT_DAYS,
  QUALIFY_WINDOW_DAYS,
  HOLD_WINDOW_DAYS,
  DAY_MS,
} from './referralConstants';
import { hasBlockingFlag } from './referralConstants';
import type { ReferralMilestone, PayoutState, ReferralFlag } from './referralConstants';

// reconcileReferrals — the nightly grader that walks referred users through the
// payable milestones and opens their payout.
//
//   attributed ──(businessName set AND >=1 customer/order)──▶ activated
//              ──(>=QUALIFY_DISTINCT_DAYS distinct Africa/Lagos write-days, within
//                 QUALIFY_WINDOW_DAYS of attribution)──────▶ qualified → payout pending
//
// "Meaningful write" = a customer, order, or measurement doc (never an app-open).
// Qualification REQUIRES activation first (a genuinely-engaged tailor sets a
// business name and books work), which keeps the funnel counters monotonic
// (installs >= activated >= qualified) and adds a genuineness signal — matching
// the locked "Activated → Qualified" progression. All thresholds are the
// server-only constants in referralConstants.ts; the client never computes this.
//
// A referral carrying any BLOCKING fraud flag still advances milestone (the
// milestone reflects real engagement) but its payout is withheld — payoutState
// stays `none`, so no money is ever queued for it. Advisory flags (e.g.
// missing_device_hash) do NOT withhold — see hasBlockingFlag. Grading is idempotent
// and re-checks the racy milestone/payoutState fields inside a per-referral
// transaction, so a re-run (or the debug callable) never double-counts.

const MILESTONE_RANK: Record<ReferralMilestone, number> = {
  attributed: 0,
  activated: 1,
  qualified: 2,
};

// How long past a referral's qualification-window close the grader keeps
// scanning it, so activity on the final window day (made after that night's run)
// is still graded on a later run. Must exceed the max gap between nightly runs.
const RECONCILE_GRACE_DAYS = 2;

// ── Pure: distinct Lagos write-days within the qualification window ───────────

/**
 * The distinct Africa/Lagos calendar-day keys ('YYYY-MM-DD') among activity
 * timestamps that fall inside [signupMs, windowEndMs). Sorted + de-duplicated so
 * the count is the number of distinct active days and the array is stable to
 * store back on the referral doc.
 *
 * `createdAt` on customers/orders/measurements is a client-set epoch-millis Long
 * (not a server Timestamp), so it is forgeable — but the window is bounded by the
 * server-authoritative signupAt/qualificationWindowEndsAt, which caps the abuse to
 * "spread writes across days you were genuinely within your first 14 days".
 */
export function computeActiveDayKeys(
  activityCreatedAtMs: number[],
  signupMs: number,
  windowEndMs: number,
): string[] {
  const keys = new Set<string>();
  for (const ms of activityCreatedAtMs) {
    if (typeof ms !== 'number' || !Number.isFinite(ms)) continue;
    if (ms < signupMs || ms >= windowEndMs) continue;
    keys.add(lagosDateKey(ms));
  }
  return Array.from(keys).sort();
}

// ── Pure: grade one referral ─────────────────────────────────────────────────

export interface ReferralGradeInput {
  milestone: ReferralMilestone;
  payoutState: PayoutState;
  /** businessName set AND (>=1 customer OR >=1 order). */
  activated: boolean;
  /** distinct in-window write-days reached QUALIFY_DISTINCT_DAYS. */
  qualifiesByActivity: boolean;
  /** any BLOCKING flag present → payout withheld (milestone still advances). */
  hasFlags: boolean;
  /** one-time bounty (kobo) from the marketer doc; only used on qualify. */
  payoutRatePerUser: number;
  nowMs: number;
}

export interface ReferralGradeResult {
  milestone: ReferralMilestone;
  payoutState: PayoutState;
  payoutAmount: number;
  holdEndsAtMs: number | null;
  // Marketer aggregate deltas (0 unless a boundary was crossed this run).
  activatedDelta: number;
  qualifiedDelta: number;
  pendingAmountDelta: number;
}

/**
 * Returns the referral's target state, or null when nothing changes (already at
 * or beyond the milestone the evidence supports). Never regresses a milestone or
 * a payout. Qualification requires activation, so `qualifiesByActivity` only
 * promotes a referral that is also `activated`.
 */
export function gradeReferral(input: ReferralGradeInput): ReferralGradeResult | null {
  const currentRank = MILESTONE_RANK[input.milestone];

  // Highest milestone the evidence supports (qualified implies activated).
  let targetMilestone: ReferralMilestone = input.milestone;
  if (input.activated && input.qualifiesByActivity) {
    targetMilestone = 'qualified';
  } else if (input.activated && currentRank < MILESTONE_RANK.activated) {
    targetMilestone = 'activated';
  }
  const targetRank = MILESTONE_RANK[targetMilestone];

  // Counters bump once, when a boundary is first crossed (jumping straight to
  // qualified counts toward activated too, keeping the funnel monotonic).
  const activatedDelta = targetRank >= MILESTONE_RANK.activated && currentRank < MILESTONE_RANK.activated ? 1 : 0;
  const qualifiedDelta = targetRank >= MILESTONE_RANK.qualified && currentRank < MILESTONE_RANK.qualified ? 1 : 0;

  // Open the payout only when qualification is first reached, the referral is
  // clean, and no payout has started yet. Flags advance the milestone but leave
  // payoutState `none` — nothing is ever queued for a flagged install.
  let payoutState = input.payoutState;
  let payoutAmount = 0;
  let holdEndsAtMs: number | null = null;
  let pendingAmountDelta = 0;
  if (qualifiedDelta === 1 && !input.hasFlags && input.payoutState === 'none') {
    payoutState = 'pending';
    payoutAmount = input.payoutRatePerUser;
    holdEndsAtMs = input.nowMs + HOLD_WINDOW_DAYS * DAY_MS;
    pendingAmountDelta = input.payoutRatePerUser;
  }

  const unchanged =
    targetMilestone === input.milestone &&
    payoutState === input.payoutState &&
    activatedDelta === 0 &&
    qualifiedDelta === 0;
  if (unchanged) return null;

  return { milestone: targetMilestone, payoutState, payoutAmount, holdEndsAtMs, activatedDelta, qualifiedDelta, pendingAmountDelta };
}

// ── Handler (deps-injected for testability) ──────────────────────────────────

export interface ReconcileReferralsDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
}

export interface ReconcileReferralsResult {
  scanned: number;
  activated: number;
  qualified: number;
}

interface CandidateSignals {
  activated: boolean;
  activeDayKeys: string[];
}

/**
 * Reads the referred user's business name + activity and derives the activation
 * and distinct-active-day signals. Activity `createdAt` is a plain epoch-millis
 * Long (see computeActiveDayKeys), so we read arrays and compute in memory rather
 * than range-query. Measurements are nested under each customer and are only
 * scanned when customers+orders alone haven't already cleared the distinct-day
 * bar — bounding the extra subcollection reads to the referrals that need them.
 */
async function gatherSignals(
  db: admin.firestore.Firestore,
  uid: string,
  signupMs: number,
  windowEndMs: number,
): Promise<CandidateSignals> {
  const userSnap = await db.doc(`users/${uid}`).get();
  const businessName = (userSnap.data()?.businessName ?? '') as string;
  const businessNameSet = typeof businessName === 'string' && businessName.trim().length > 0;

  const [customersSnap, ordersSnap] = await Promise.all([
    db.collection(`users/${uid}/customers`).get(),
    db.collection(`users/${uid}/orders`).get(),
  ]);
  const activated = businessNameSet && (customersSnap.size > 0 || ordersSnap.size > 0);

  const activityMs: number[] = [];
  for (const d of customersSnap.docs) activityMs.push(d.data()?.createdAt as number);
  for (const d of ordersSnap.docs) activityMs.push(d.data()?.createdAt as number);

  let dayKeys = computeActiveDayKeys(activityMs, signupMs, windowEndMs);

  // Only pay for the (potentially many) per-customer measurement reads if we
  // still need more distinct days to qualify — and fetch them concurrently.
  if (dayKeys.length < QUALIFY_DISTINCT_DAYS) {
    const measurementSnaps = await Promise.all(
      customersSnap.docs.map((c) => db.collection(`users/${uid}/customers/${c.id}/measurements`).get()),
    );
    for (const mSnap of measurementSnaps) {
      for (const m of mSnap.docs) activityMs.push(m.data()?.createdAt as number);
    }
    dayKeys = computeActiveDayKeys(activityMs, signupMs, windowEndMs);
  }

  return { activated, activeDayKeys: dayKeys };
}

export async function reconcileReferralsHandler(
  deps: ReconcileReferralsDeps,
): Promise<ReconcileReferralsResult> {
  const { db } = deps;
  const nowMs = deps.now().getTime();
  const nowTs = admin.firestore.Timestamp.fromMillis(nowMs);

  // Grade referrals still inside their qualification window (plus a short grace)
  // that haven't qualified yet — a naturally-rolling ~14-day set. The grace
  // matters: the grader runs once nightly, so a user's writes on the FINAL window
  // day AFTER that run — but still before qualificationWindowEndsAt — aren't seen
  // until the next night, by which point a hard `>= now` cutoff would already
  // exclude them and a genuine 4th distinct day (→ payout) would be lost. Scanning
  // for RECONCILE_GRACE_DAYS past window-close catches that final-day activity;
  // the distinct-day math itself still counts only in-window writes (windowEndMs),
  // so the grace never over-qualifies. Pre-indexed by the
  // (milestone, qualificationWindowEndsAt) composite.
  const graceCutoffTs = admin.firestore.Timestamp.fromMillis(nowMs - RECONCILE_GRACE_DAYS * DAY_MS);
  const snap = await db
    .collection(REFERRALS)
    .where('milestone', 'in', ['attributed', 'activated'])
    .where('qualificationWindowEndsAt', '>=', graceCutoffTs)
    .get();

  let activatedCount = 0;
  let qualifiedCount = 0;

  for (const doc of snap.docs) {
    const uid = doc.id;
    const data = doc.data();
    // Active-day counting is bounded by the qualification window. New referrals
    // anchor the window on attribution time (qualificationWindowStartsAt); older
    // referrals predate that field, so fall back to signupAt to preserve their
    // original bounds. Only in-window writes count either way.
    const windowStartMs: number =
      data.qualificationWindowStartsAt?.toMillis?.() ?? data.signupAt?.toMillis?.() ?? nowMs;
    const windowEndMs: number = data.qualificationWindowEndsAt?.toMillis?.() ?? (windowStartMs + QUALIFY_WINDOW_DAYS * DAY_MS);

    const signals = await gatherSignals(db, uid, windowStartMs, windowEndMs);
    const qualifiesByActivity = signals.activeDayKeys.length >= QUALIFY_DISTINCT_DAYS;

    // Re-read the racy fields (milestone/payoutState/flags) inside the transaction
    // so a concurrent run or a manual debug invocation can't double-count.
    const applied = await db.runTransaction(async (tx) => {
      // ALL reads first — Firestore transactions reject a read after any write.
      // We read both the referral (racy fields) and its marketer (rate +
      // counters) up front, before deciding the writes below.
      const fresh = await tx.get(doc.ref);
      if (!fresh.exists) return null;
      const f = fresh.data() as {
        milestone: ReferralMilestone;
        payoutState: PayoutState;
        flags?: ReferralFlag[];
        marketerId: string;
        activeDayKeys?: string[];
      };
      const marketerRef = db.doc(`${MARKETERS}/${f.marketerId}`);
      const m = (await tx.get(marketerRef)).data() ?? {};

      const grade = gradeReferral({
        milestone: f.milestone,
        payoutState: f.payoutState,
        activated: signals.activated,
        qualifiesByActivity,
        hasFlags: hasBlockingFlag(f.flags),
        payoutRatePerUser: (m.payoutRatePerUser as number) ?? 0,
        nowMs,
      });

      // Refresh the cached day-count even when no milestone changes — a referral
      // can gain active days while still under the bar. Skip the write only when
      // truly nothing changed.
      const prevKeys = f.activeDayKeys ?? [];
      const daysChanged =
        prevKeys.length !== signals.activeDayKeys.length ||
        prevKeys.some((k, i) => k !== signals.activeDayKeys[i]);
      if (!grade && !daysChanged) return null;

      const update: Record<string, unknown> = {
        activeDays: signals.activeDayKeys.length,
        activeDayKeys: signals.activeDayKeys,
        updatedAt: nowTs,
      };
      if (grade) {
        update.milestone = grade.milestone;
        update.payoutState = grade.payoutState;
        if (grade.payoutState === 'pending' && f.payoutState === 'none') {
          update.payoutAmount = grade.payoutAmount;
          update.holdEndsAt = admin.firestore.Timestamp.fromMillis(grade.holdEndsAtMs as number);
        }
      }
      tx.set(doc.ref, update, { merge: true });

      // Roll up marketer aggregates via read-modify-write inside the same
      // transaction (race-safe, and testable without FieldValue.increment).
      if (grade && (grade.activatedDelta || grade.qualifiedDelta || grade.pendingAmountDelta)) {
        tx.set(marketerRef, {
          activated: ((m.activated as number) ?? 0) + grade.activatedDelta,
          qualified: ((m.qualified as number) ?? 0) + grade.qualifiedDelta,
          pendingAmount: ((m.pendingAmount as number) ?? 0) + grade.pendingAmountDelta,
          updatedAt: nowTs,
        }, { merge: true });
      }
      return grade;
    });

    if (applied) {
      if (applied.activatedDelta) activatedCount++;
      if (applied.qualifiedDelta) qualifiedCount++;
    }
  }

  const result: ReconcileReferralsResult = { scanned: snap.size, activated: activatedCount, qualified: qualifiedCount };
  functions.logger.info('reconcileReferrals complete', { ...result });
  return result;
}

// ── Exports: nightly schedule + admin-only debug trigger ─────────────────────

export const reconcileReferrals = functions
  .region(REGION)
  // 03:30 Africa/Lagos daily — after midnight so the prior Lagos day is fully
  // counted, off the 01:00–02:30 sweep contention. timeZone MUST match
  // ACTIVE_DAY_TIMEZONE so day boundaries line up with the distinct-day math.
  .pubsub.schedule('30 3 * * *')
  .timeZone('Africa/Lagos')
  .onRun(async () => {
    await reconcileReferralsHandler({ db: admin.firestore(), now: () => new Date() });
  });

/** Admin-only manual trigger for the grader (same handler), for QA + backfills. */
export const debugReconcileReferrals = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<ReconcileReferralsResult> => {
    if (context.auth?.token?.admin !== true) {
      throw new functions.https.HttpsError('permission-denied', 'admin_only');
    }
    return reconcileReferralsHandler({ db: admin.firestore(), now: () => new Date() });
  });
