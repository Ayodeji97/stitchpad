// Founding Tailors leaderboard — daily aggregator.
//
// Awards TIERED points to program (`founding_tailors`) user-referrers from each
// referral's `observedDayKeys` (the grader's ratcheted distinct active Lagos
// days): +1 activation point at the first active day's month, plus +1 per active
// day capped at QUALIFY_DISTINCT_DAYS — 5 points max. Blocking-flagged referrals
// earn 0. Each point is bucketed into the Lagos month it was earned. Writes the
// public `leaderboards/*` docs the app + web read directly. The read-side callable
// lives below in this same file.

import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MARKETERS, REFERRALS, REFERRAL_CODES, ACTIVE_DAY_TIMEZONE, QUALIFY_DISTINCT_DAYS, hasBlockingFlag } from './referralConstants';
import type { ReferralFlag } from './referralConstants';
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

/** The Africa/Lagos calendar month (YYYY-MM) of a 'YYYY-MM-DD' Lagos day-key. */
function monthOfDayKey(dayKey: string): string {
  return dayKey.slice(0, 7);
}

export async function aggregateFoundingTailorsLeaderboardHandler(deps: AggregatorDeps): Promise<void> {
  // 1. Program user-referrers → id -> display name.
  const marketersSnap = await deps.db.collection(MARKETERS).where('program', '==', FOUNDING_TAILORS_PROGRAM).get();
  const names = new Map<string, string>();
  marketersSnap.forEach((d) => names.set(d.id, (d.data().name as string) ?? 'Tailor'));

  // 2. Scan activated + qualified referrals; award TIERED points, each bucketed
  //    into the Lagos month it was earned in. A referral is worth up to 5 points:
  //    +1 activation (at the first active day's month) and +1 per active day
  //    (capped at QUALIFY_DISTINCT_DAYS). Blocking-flagged referrals earn 0.
  //    observedDayKeys are ratcheted 'YYYY-MM-DD' Lagos keys from the grader.
  const referralsSnap = await deps.db.collection(REFERRALS).where('milestone', 'in', ['activated', 'qualified']).get();
  const byMonth = new Map<string, Map<string, LeaderEntry>>();
  const allTime = new Map<string, LeaderEntry>();
  const bump = (map: Map<string, LeaderEntry>, id: string) => {
    const e = map.get(id) ?? { marketerId: id, name: names.get(id) as string, points: 0 };
    e.points += 1; map.set(id, e);
  };
  const award = (monthKey: string, id: string) => {
    if (!byMonth.has(monthKey)) byMonth.set(monthKey, new Map());
    bump(byMonth.get(monthKey) as Map<string, LeaderEntry>, id);
    bump(allTime, id);
  };

  referralsSnap.forEach((d) => {
    const r = d.data() as { marketerId: string; flags?: ReferralFlag[]; observedDayKeys?: string[] };
    if (!names.has(r.marketerId)) return;                 // not a program referrer (e.g. affiliate)
    if (hasBlockingFlag(r.flags)) return;                 // fraud-flagged → 0 points
    const days = [...(r.observedDayKeys ?? [])].sort().slice(0, QUALIFY_DISTINCT_DAYS);
    if (days.length === 0) return;                        // no creditable active day yet → no anchor
    award(monthOfDayKey(days[0]), r.marketerId);          // +1 activation, at the first active day's month
    for (const day of days) award(monthOfDayKey(day), r.marketerId); // +1 per active day (already capped)
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

// ── Public read callable ────────────────────────────────────────────────────
// Reads only the pre-computed public `leaderboards/*` docs written by the
// aggregator above (never re-scans referrals/marketers). No auth guard on
// purpose: the public web page calls this unauthenticated.

export interface PublicRow { rank: number; name: string; points: number }
export interface LeaderboardResponse {
  updatedAt: number;
  monthId: string;
  top: PublicRow[];
  you: { rank: number; points: number } | null;
  youAllTime: { rank: number; points: number } | null;
}
export interface ReadDeps { db: admin.firestore.Firestore }

const TOP_LIMIT = 25;
// Referral codes are Crockford-ish alphanumerics — no slashes, no punctuation.
const REFERRAL_CODE_PATTERN = /^[A-Za-z0-9]+$/;

export async function getFoundingTailorsLeaderboardHandler(
  data: { code?: unknown },
  deps: ReadDeps,
): Promise<LeaderboardResponse> {
  const currentDoc = (await deps.db.doc('leaderboards/current').get()).data() as { monthId?: string } | undefined;
  const monthId = currentDoc?.monthId ?? monthKeyLagos(Date.now());
  const board = (await deps.db.doc(`leaderboards/${monthId}`).get()).data() as
    | { updatedAt?: { toMillis(): number }; entries?: LeaderEntry[] }
    | undefined;
  const entries = board?.entries ?? [];

  const top: PublicRow[] = entries
    .slice(0, TOP_LIMIT)
    .map((e, i) => ({ rank: i + 1, name: e.name, points: e.points }));

  let you: { rank: number; points: number } | null = null;
  let youAllTime: { rank: number; points: number } | null = null;
  const trimmedCode = typeof data?.code === 'string' ? data.code.trim() : '';
  // Referral codes are Crockford-ish alphanumerics. Anything outside that
  // charset (e.g. a slash) can't be a real code — and interpolating it into
  // `db.doc()` below would throw (wrong path-segment count) instead of the
  // intended "unknown code" no-op. Treat it as no code: never look it up,
  // never throw, never leak whether a code exists.
  const code = REFERRAL_CODE_PATTERN.test(trimmedCode) ? trimmedCode : null;
  if (code) {
    const codeDoc = (await deps.db.doc(`${REFERRAL_CODES}/${code}`).get()).data() as { marketerId?: string } | undefined;
    const marketerId = codeDoc?.marketerId;
    if (marketerId) {
      const idx = entries.findIndex((e) => e.marketerId === marketerId);
      you = idx >= 0 ? { rank: idx + 1, points: entries[idx].points } : { rank: 0, points: 0 };

      // Lifetime running total from the pre-computed all-time board (same shape /
      // resolution as `you`). Read here — only when a code resolved a marketer — so
      // anonymous/web reads pay nothing new.
      const allTime = (await deps.db.doc('leaderboards/alltime').get()).data() as
        | { entries?: LeaderEntry[] }
        | undefined;
      const allEntries = allTime?.entries ?? [];
      const aIdx = allEntries.findIndex((e) => e.marketerId === marketerId);
      youAllTime = aIdx >= 0 ? { rank: aIdx + 1, points: allEntries[aIdx].points } : { rank: 0, points: 0 };
    }
    // Unknown code: `you`/`youAllTime` stay null — never leak whether the code exists.
  }

  return { updatedAt: board?.updatedAt?.toMillis?.() ?? 0, monthId, top, you, youAllTime };
}

export const getFoundingTailorsLeaderboard = functions
  .region(REGION)
  .https.onCall(async (data) => getFoundingTailorsLeaderboardHandler(data as { code?: unknown }, { db: admin.firestore() }));
