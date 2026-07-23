# Referral activity server-timestamp (Lane B) — design

Date: 2026-07-23
Status: approved design, pending implementation plan
Lane: **B** (client + rules + functions — needs an app release; only protects users on the new binary)

## Relationship to Lane A

Lane A (PR #286, merged `376917d3`) made `reconcileReferrals` credit only *server-observed
completed days* via a monotonic `observedDayKeys` ratchet, capping forgery at one credited
day per nightly grader run. It does **not** make the underlying timestamp trustworthy, and
it must **remain in place after Lane B ships** — old binaries never write the new field, so
the ratchet stays their only protection. Lane B narrows the trust surface for users on the
new binary; it does not replace Lane A.

## Problem

`createdAt` on `customers` / `orders` / `measurements` is a client-set epoch-millis `Long`,
unconstrained by `firestore.rules`. `reconcileReferrals` derives paid "distinct active days"
from it. `computeActiveDayKeys` never rejects future-dated values, so absent Lane A a single
session could forge four distinct in-window days and qualify a marketer payout instantly.
`createdAt` is client-set deliberately — offline-first writes must carry a real creation
date even though they reach the server much later — so the field itself cannot simply be
constrained to `request.time` without breaking offline UX.

## The inherent limit (stated up front)

A document that reached the server exactly once **cannot prove it existed on multiple
earlier days**. "Created offline across four days, then synced in one burst" and "created in
one session with four backdated timestamps" are byte-for-byte identical. No scheme credits
the first while denying the second when both sync in one burst. Lane B therefore does **not**
promise "offline users fully preserved." It promises: *trust `createdAt`, but only within a
server-verified freshness window.* A tailor who syncs less often than that window
under-counts and is genuinely indistinguishable from an attacker — accepted, unfixable.

## Approach: dual-signal (client day-key gated by a server freshness proof)

Add a second, server-authoritative timestamp to each activity document and require the
human-meaningful `createdAt` day to be corroborated by it.

### Client (Kotlin, commonMain — both platforms)

On **create** of a `customer`, `order`, or `measurement`, write a new
`serverCreatedAt = FieldValue.serverTimestamp` alongside the existing client `createdAt`.
`createdAt` is untouched: it still carries the client's real creation instant for display
and ordering, so offline UX does not change.

**Serialization wrinkle (must be handled in the plan):** these creates currently call
`docRef.set(dto)` with a `@Serializable` DTO (e.g. `FirebaseCustomerRepository.kt:147`).
`FieldValue.serverTimestamp` is a write sentinel, **not** serializable — it cannot be a DTO
field. The established pattern is `FirebaseUserRepository.kt:73`, which writes `updatedAt`
through a `Map<String, Any?>`. The plan chooses one of:
- (a) keep `set(dto)`, then a second `docRef.set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)` enqueued through the same `OfflineWriteDispatcher` (ordered after the create); or
- (b) switch the create to a single map-based write.
Option (a) is smaller and keeps the typed DTO; the rules (below) must then allow the
`serverCreatedAt` field to be set on that follow-up merge, not only on create. iOS-serializer
caution applies — see `[[feedback_kmp_native_serializer_any]]`. Both create writes go through
`offlineWrites.enqueue(...)` so offline ordering and the cap checks are unchanged.

### Rules (`firestore.rules`)

These collections currently use a blanket `allow read, write: if isOwner(uid)`
(`firestore.rules` customers/orders/measurements). The plan splits `write` into `create` and
`update`, adding constraints on **create** only and leaving `update` as `if isOwner(uid)` so
edit flows are untouched. On create (and, for option (a), the follow-up merge):
- `serverCreatedAt == request.time` — the exact pattern `welcomeBonusAppliedAt` already uses;
- `createdAt <= request.time + SKEW` — rejects future-dating outright (SKEW a small clock-skew
  allowance, e.g. 5 min, so an honestly slightly-ahead device clock is not rejected).

`createdAt` backdating stays permitted (offline writes legitimately backdate); the freshness
check below, not the rule, is what bounds backdating for payout purposes.

### Reconcile (`functions/src/referral/reconcileReferrals.ts`)

A doc contributes its `createdAt`-day `D` to the active-day set **only when**
`serverCreatedAt` falls within `[createdAt, createdAt + FRESHNESS_DAYS]` (both in Lagos-day
terms). Interpretation:
- Honest online / frequently-syncing: `serverCreatedAt ≈ createdAt` → within window → credited.
- Week-old backdated burst (attack) or infrequently-synced legit work: `serverCreatedAt` is
  many days after the claimed day → outside window → **not** credited.

**Fallback for old binaries:** a doc with no `serverCreatedAt` (any write from a pre-Lane-B
client, and every historical doc) is graded exactly as today — its `createdAt`-day flows into
the **Lane A ratchet** unchanged. So Lane B is purely additive: new-binary docs get the
stronger check, everyone else keeps the Lane A behavior. No migration, no backfill.

`FRESHNESS_DAYS = 3` (tunable constant beside `QUALIFY_*` in `referralConstants.ts`; matches
the reconcile-grace generosity). Larger = kinder to infrequent syncers, weaker anti-fraud;
smaller = the reverse.

## Components touched

| File | Change |
|---|---|
| `composeApp/.../feature/customer/data/FirebaseCustomerRepository.kt` | write `serverCreatedAt` on create |
| `composeApp/.../feature/order/data/FirebaseOrderRepository.kt` | write `serverCreatedAt` on create |
| `composeApp/.../feature/measurement/data/FirebaseMeasurementRepository.kt` | write `serverCreatedAt` on create |
| `firestore.rules` | `serverCreatedAt == request.time` + `createdAt <= request.time + SKEW` on the three collections' creates (+ follow-up merge if option (a)) |
| `functions/src/referral/referralConstants.ts` | add `FRESHNESS_DAYS = 3` |
| `functions/src/referral/reconcileReferrals.ts` | gate each activity day-key on the `serverCreatedAt` freshness window; missing field → existing Lane A path |
| `functions/src/__tests__/referral/reconcileReferrals.test.ts` | freshness-window cases |
| `functions/src/__tests__/firestore.rules.test.ts` | rule cases for the new create constraints |
| `composeApp/.../*Test.kt` (repo/mapper) | assert `serverCreatedAt` written on create; `createdAt` unchanged |

The reconcile change lands as an extension of the existing pure functions
(`gatherSignals` supplies `(createdAtMs, serverCreatedAtMs?)` pairs; a pure
`isServerFresh(createdAtMs, serverCreatedAtMs, freshnessDays)` decides inclusion), mirroring
how `computeActiveDayKeys` / `ratchetObservedDayKeys` / `gradeReferral` are already split out.

## Test plan (TDD — pure functions first)

Reconcile / pure:
1. `serverCreatedAt == createdAt` → day credited.
2. `serverCreatedAt` = `createdAt + FRESHNESS_DAYS` (boundary, inclusive) → credited.
3. `serverCreatedAt` = `createdAt + FRESHNESS_DAYS + 1 day` → **not** credited.
4. `serverCreatedAt` absent → falls through to the Lane A ratchet unchanged (regression pin).
5. Mixed doc set (some fresh, some stale, some legacy) → only fresh + legacy-via-ratchet days
   count; interaction with `observedDayKeys` monotonicity holds.
6. Attack replay: four docs, one session, `createdAt` = days 1–4, all `serverCreatedAt` = day 5,
   `FRESHNESS_DAYS = 3` → at most days 3–4 fresh, and the ratchet still caps observed credit.

Rules (emulator, `firestore.rules.test.ts`):
7. create with `serverCreatedAt == request.time` + `createdAt <= now` → allowed.
8. create with `serverCreatedAt` set to a client literal (≠ request.time) → denied.
9. create with future `createdAt` beyond SKEW → denied.
10. update that does not touch `createdAt`/`serverCreatedAt` → still allowed (no regression to
    edit flows).

Client (commonTest):
11. `createCustomer` / `createOrder` / `createMeasurement` enqueue a write carrying
    `serverCreatedAt`; `createdAt` value preserved; cap-check and offline-ordering behavior
    unchanged. Gate `commonTest` with `:composeApp:compileTestKotlinIosSimulatorArm64`
    (K/N backtick-name + serializer landmines).

## Release & sequencing

- Ships in the next app release train (Lane B = new binary). Rules deploy can precede the
  binary safely: the new `serverCreatedAt == request.time` create-constraint is only asserted
  when the client sends the field, and `createdAt <= request.time + SKEW` is satisfied by every
  honest existing client (they never future-date). **Verify the future-date rule does not
  reject current-binary writes** in the emulator before deploying rules ahead of the binary.
- Keep Lane A deployed and unchanged. Do not gate qualification solely on `serverCreatedAt`.
- After release, spot-check a new-binary account: create activity online → `serverCreatedAt`
  present and ≈ `createdAt`; confirm reconcile still qualifies a genuine 4-day user.

## Explicitly out of scope

- Backfilling `serverCreatedAt` onto historical docs (impossible to do truthfully; Lane A
  covers them).
- Constraining `createdAt` to `request.time` (breaks offline backdating).
- Any change to `updateCustomer` / edit flows beyond leaving them working.
- `whatsappConfirmed` server-verification — separate backlog item
  (`[[project_whatsapp_confirm_server_verification]]`).
