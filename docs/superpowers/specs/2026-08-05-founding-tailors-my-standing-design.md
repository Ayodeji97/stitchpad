# Founding Tailors — In-App "Your Standing" Design

**Date:** 2026-08-05
**Status:** Approved (brainstorming), pending implementation plan
**Builds on:** the tiered Founding Tailors points (`2026-08-04-founding-tailors-tiered-points-design.md`) and the existing `leaderboards/*` docs.

## Goal

Let a tailor see their **own** Founding Tailors standing inside the app: points earned **this month** and a **lifetime running total** (never reset). The lifetime total is the data source for a future "annual top 3" awards strategy — that strategy is **not** built here.

## What already exists (reused, not rebuilt)

- The daily aggregator already writes `leaderboards/alltime` = `{ updatedAt, entries: [{ marketerId, name, points }] }` — the lifetime cumulative total per program referrer. **No new aggregation is needed.**
- The public read callable `getFoundingTailorsLeaderboard({ code })` already resolves the viewer from their referral `code` and returns their current-month `you: { rank, points } | null`.
- The full ranked leaderboard already renders on the web (`getstitchpad.com/founding-tailors`); the app's "View leaderboard" button links to it. **The in-app view shows only the viewer's own points, not the full board.**

## Backend — extend the read callable

Add the viewer's lifetime figure to `getFoundingTailorsLeaderboard`, mirroring how `you` is computed.

Response shape (add `youAllTime`; everything else unchanged):
```ts
export interface LeaderboardResponse {
  updatedAt: number;
  monthId: string;
  top: PublicRow[];
  you: { rank: number; points: number } | null;         // current month (unchanged)
  youAllTime: { rank: number; points: number } | null;  // NEW: lifetime running total
}
```

Handler change (in `getFoundingTailorsLeaderboardHandler`, `foundingTailorsLeaderboard.ts`): inside the existing `if (marketerId) { ... }` block — after computing `you` — read `leaderboards/alltime` and resolve `youAllTime` the same way:
```ts
const allTime = (await deps.db.doc('leaderboards/alltime').get()).data() as
  | { entries?: LeaderEntry[] } | undefined;
const allEntries = allTime?.entries ?? [];
const aIdx = allEntries.findIndex((e) => e.marketerId === marketerId);
youAllTime = aIdx >= 0 ? { rank: aIdx + 1, points: allEntries[aIdx].points } : { rank: 0, points: 0 };
```
`youAllTime` defaults to `null` (declared alongside `you`) and stays `null` for no-code / unknown-code — same privacy rule as `you` (never leak whether a code exists). The extra `alltime` read happens only when a `marketerId` resolved, so anonymous/web reads pay nothing new. `top` and the current-month logic are untouched — the web page ignores the new field (backward compatible).

## App — a "Your standing" card

### Domain + repository
- New domain model (in `feature/referral/domain/ReferralRepository.kt`, beside `ReferralLink`):
  ```kotlin
  data class FoundingTailorsStanding(
      val monthPoints: Int,
      val monthRank: Int,     // 0 = has a code but no points/rank this month
      val allTimePoints: Int,
      val allTimeRank: Int,   // 0 = no lifetime points/rank yet
  )
  ```
- New repository method:
  ```kotlin
  suspend fun getFoundingTailorsStanding(code: String): Result<FoundingTailorsStanding, DataError.Network>
  ```
- `CloudFunctionsReferralRepository` implements it by calling `functions.httpsCallable("getFoundingTailorsLeaderboard").invoke(<code DTO>).data<LeaderboardResponseDto>()`, mapping `you`/`youAllTime` (null → 0/0) into `FoundingTailorsStanding`. It reuses the existing GitLive callable + `try/catch → Result.Error(DataError.Network.UNKNOWN)` pattern already used by `getOrCreateMyReferralLink`/`recordAttribution`. Only `you` + `youAllTime` are read from the response DTO; `top` is ignored.

### State + ViewModel
- `FoundingTailorsState` adds `val standing: FoundingTailorsStanding? = null` (null = not loaded / fetch failed → card hidden).
- In `FoundingTailorsViewModel.loadLink()`, after `code` is resolved (both branches — existing code on the user doc AND freshly minted), fetch the standing and update state. A failed standing fetch does **not** set `error` or block the screen — it just leaves `standing = null` (the link + share + view-leaderboard still work). This is a secondary, best-effort load.

### UI
- `FoundingTailorsScreen` renders a **"Your standing"** card when `state.standing != null`, placed right after the subtitle (above the share/view buttons). It shows two rows:
  - **This month:** `{monthPoints} points` and, when `monthRank > 0`, `#{monthRank}`.
  - **All time:** `{allTimePoints} points` and, when `allTimeRank > 0`, `#{allTimeRank}`.
  - When both point totals are 0: a hint line "Share your link to start earning." instead of ranks.
- Uses `compose.resources` strings (no hardcoded text, no em dashes); the card follows the screen's existing design-token style. The Screen keeps a `@Preview` with a populated `standing`.

New strings (keys illustrative): `founding_tailors_standing_title` ("Your standing"), `_this_month` ("This month"), `_all_time` ("All time"), `_points` ("%1$d points"), `_rank` ("#%1$d"), `_empty` ("Share your link to start earning.").

## Edge cases
- **Brand-new founding tailor** (has a code, no points yet): `you`/`youAllTime` = `{rank:0, points:0}` → card shows "0 points" with the "share your link to start earning" hint.
- **Standing fetch fails / offline:** card hidden; link, share, and view-leaderboard remain functional (never block on the secondary load).
- **No code yet** (mint still pending or failed): no standing fetch; card hidden.
- **Plurals:** "%1$d points" is used for all counts (including 1) — acceptable; not worth a plural resource here.

## Testing
- Backend (`foundingTailorsLeaderboard.test.ts`): add read-callable cases — `youAllTime` resolves rank+points from the `alltime` board; `youAllTime` = `{rank:0,points:0}` when the code is valid but absent from `alltime`; `youAllTime` = `null` for no-code and unknown-code. Existing `you`/`top` tests stay green.
- App ViewModel test: after `LoadLink`, `state.standing` is populated from the repo; a repo `Result.Error` leaves `standing = null` and does **not** set `error`.
- App repo test: maps a `LeaderboardResponse` (with `you` + `youAllTime`) to `FoundingTailorsStanding`; null `you`/`youAllTime` → 0/0.

## Deploy / rollout
1. Update + test the read callable; redeploy **only** `getFoundingTailorsLeaderboard` (the aggregator is unchanged). Web is unaffected (ignores the new field).
2. Ship the app change (repo + ViewModel + screen + strings) through the normal PR + store pipeline.
3. No data migration — `leaderboards/alltime` is already maintained.

## Non-goals
- No annual/yearly competition or awards flow — the lifetime `alltime` board is the data source for that future strategy; yearly buckets can be added when needed.
- No full in-app leaderboard — the ranked board stays on the web.
- No change to scoring, the aggregator, the grader, or the fraud rules.
