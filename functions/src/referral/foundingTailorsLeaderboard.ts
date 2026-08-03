// Founding Tailors leaderboard — daily aggregator.
//
// Reads only qualified, non-blocked referrals of program (`founding_tailors`)
// user-referrers and buckets each into the Africa/Lagos calendar month of its
// `qualifiedAt` stamp (added by reconcileReferrals — see
// referralConstants.ts / reconcileReferrals.ts). Writes public leaderboard
// docs the app can read directly (no callable round-trip needed to view the
// board). The read-side callable is added in a later slice to this same file.

import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MARKETERS, REFERRALS, ACTIVE_DAY_TIMEZONE, hasBlockingFlag } from './referralConstants';
import type { ReferralFlag, ReferralMilestone } from './referralConstants';
import { FOUNDING_TAILORS_PROGRAM } from './getOrCreateMyReferralLink';

export interface LeaderEntry { marketerId: string; name: string; points: number }
export interface AggregatorDeps { db: admin.firestore.Firestore; now: () => Date }

/** YYYY-MM in Africa/Lagos for the given epoch-ms. */
export function monthKeyLagos(ms: number): string {
  // en-CA formats as YYYY-MM-DD; slice to YYYY-MM. timeZone shifts the day/month boundary.
  const ymd = new Intl.DateTimeFormat('en-CA', {
    timeZone: ACTIVE_DAY_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(ms));
  return ymd.slice(0, 7);
}

function sortEntries(map: Map<string, LeaderEntry>): LeaderEntry[] {
  return [...map.values()].sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

export async function aggregateFoundingTailorsLeaderboardHandler(deps: AggregatorDeps): Promise<void> {
  // 1. Program user-referrers → id -> display name.
  const marketersSnap = await deps.db.collection(MARKETERS).where('program', '==', FOUNDING_TAILORS_PROGRAM).get();
  const names = new Map<string, string>();
  marketersSnap.forEach((d) => names.set(d.id, (d.data().name as string) ?? 'Tailor'));

  // 2. Scan qualified referrals; keep only program referrers with no blocking flag.
  const qualifiedSnap = await deps.db.collection(REFERRALS).where('milestone', '==', 'qualified').get();
  const byMonth = new Map<string, Map<string, LeaderEntry>>();
  const allTime = new Map<string, LeaderEntry>();
  const bump = (map: Map<string, LeaderEntry>, id: string) => {
    const e = map.get(id) ?? { marketerId: id, name: names.get(id) as string, points: 0 };
    e.points += 1; map.set(id, e);
  };

  qualifiedSnap.forEach((d) => {
    const r = d.data() as { marketerId: string; flags?: ReferralFlag[]; qualifiedAt?: { toMillis(): number }; milestone: ReferralMilestone };
    if (!names.has(r.marketerId)) return;                 // not a program referrer (e.g. affiliate)
    if (hasBlockingFlag(r.flags)) return;                 // fraud-flagged → no point
    const ms = r.qualifiedAt?.toMillis?.();
    if (typeof ms !== 'number') return;                   // needs qualifiedAt (Task 1); pre-stamp docs skipped until next reconcile
    const mk = monthKeyLagos(ms);
    if (!byMonth.has(mk)) byMonth.set(mk, new Map());
    bump(byMonth.get(mk) as Map<string, LeaderEntry>, r.marketerId);
    bump(allTime, r.marketerId);
  });

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const currentMonth = monthKeyLagos(deps.now().getTime());
  const batch = deps.db.batch();
  for (const [mk, map] of byMonth) {
    batch.set(deps.db.doc(`leaderboards/${mk}`), { monthId: mk, updatedAt: nowTs, entries: sortEntries(map) });
  }
  // Ensure the current month doc exists even with zero points (page renders an empty board, not an error).
  if (!byMonth.has(currentMonth)) {
    batch.set(deps.db.doc(`leaderboards/${currentMonth}`), { monthId: currentMonth, updatedAt: nowTs, entries: [] });
  }
  batch.set(deps.db.doc('leaderboards/current'), { monthId: currentMonth, updatedAt: nowTs });
  batch.set(deps.db.doc('leaderboards/alltime'), { updatedAt: nowTs, entries: sortEntries(allTime) });
  await batch.commit();
}

// ── Export: nightly schedule ──────────────────────────────────────────────
export const aggregateFoundingTailorsLeaderboard = functions
  .region(REGION)
  // 04:00 Africa/Lagos daily — after reconcileReferrals (03:30) has stamped
  // the night's newly-qualified referrals with qualifiedAt.
  .pubsub.schedule('0 4 * * *')
  .timeZone(ACTIVE_DAY_TIMEZONE)
  .onRun(async () => {
    await aggregateFoundingTailorsLeaderboardHandler({ db: admin.firestore(), now: () => new Date() });
  });
