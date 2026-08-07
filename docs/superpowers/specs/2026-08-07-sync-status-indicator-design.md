# Sync Status Indicator — Design

Date: 2026-08-07
Status: Approved, not yet implemented

## Goal

Tell the tailor when their data is not yet on the server.

Today the app degrades silently. Reproduced deliberately on 2026-08-07: with the
Firestore backend fully unreachable, the app accepted a customer save, navigated
forward to Add Measurements, and listed the new record in Customers — with no
error, no banner, and no visual difference from a synced record. Writes queue
locally and flush whenever connectivity returns, which can be hours.

For a tailor on poor Nigerian connectivity, that means entering a morning of
orders, seeing every one of them saved, and having none of it on the server.

## What already exists (reused, not rebuilt)

**The silent behaviour is deliberate and stays.** `core/offline/OfflineWriteDispatcher`
fires business writes into an app-lifetime scope precisely so forms do not hang
in airplane mode — its own docstring says awaiting the write task "keeps forms
stuck in airplane mode even though the local document already exists". That is
the right call for these users. This spec does not touch the write path.

The defect is that the dispatcher's fallback is "failures are surfaced through
logs". Logs are not a user interface. This spec adds the missing UI only.

Also reused:

- **GitLive 2.4.0 exposes the metadata we need in `commonMain`** —
  `SnapshotMetadata.hasPendingWrites` and `.isFromCache`, plus
  `snapshots(includeMetadataChanges: Boolean = false)`. Verified in the SDK
  sources; works on both platforms, no expect/actual needed.
- **An always-on user-doc listener** — `FirebaseUserRepository.observeUser`
  (`core/data/repository/FirebaseUserRepository.kt:171`) is already live for
  every signed-in user. Global connection state piggybacks on it, so this
  feature adds **no new Firestore listeners**.

## Domain

New enum in `core/domain/model/SyncStatus.kt`:

```kotlin
enum class SyncStatus { SYNCED, SYNCING, OFFLINE }
```

New field on the `Customer` and `Order` domain models:

```kotlin
val isPendingSync: Boolean = false
```

Defaulted, so no existing construction site changes.

**Why on the domain model rather than a wrapper.** "Is this record confirmed on
the server" is a genuine fact about the record in an offline-first app, not an
incidental persistence detail. The data is already in hand at the mapping site.
The alternative — `Flow<Result<List<Pending<Customer>>, _>>` — ripples through
every consumer of `observeCustomers` for no behavioural gain.

## Global status — `SyncStatusObserver`

New in `core/data/sync/SyncStatusObserver.kt`, provided as a Koin `single`.

- Switches the existing user-doc listener to `snapshots(includeMetadataChanges = true)`.
- Maps each snapshot's metadata to a status via a pure function:

| `isFromCache` | `hasPendingWrites` | Status |
|---|---|---|
| true | any | `OFFLINE` |
| false | true | `SYNCING` |
| false | false | `SYNCED` |

- Exposes `val status: Flow<SyncStatus>`.
- Debounces the transition **into** `OFFLINE` by ~2s, so a cold start (whose
  first snapshot is always `isFromCache = true`) does not flash the banner.
  Transitions into `SYNCING`/`SYNCED` are emitted immediately.
- On upstream error, emits `SYNCED` (hides the banner). Failing to *invisible*
  preserves today's behaviour rather than showing a possibly-false "Offline".

The pure mapping function is extracted so it is unit-testable without Firestore.

## Per-row status

Three call sites, all using the same two-step change:

- `feature/customer/data/FirebaseCustomerRepository.kt` — `observeCustomers` (:112)
- `feature/order/data/FirebaseOrderRepository.kt` — `observeOrders` (:252)
- `feature/order/data/FirebaseOrderRepository.kt` — `observeArchivedOrders` (:264)

1. `.snapshots()` → `.snapshots(includeMetadataChanges = true)`.
2. At the existing `snapshot.documents.mapNotNull { … }` site, read
   `doc.metadata.hasPendingWrites` and pass it into the mapper.

`observeCustomer` / `observeOrder` (single-document) are **not** changed — detail
screens are out of scope per Non-goals.

Step 1 is what makes the flag *clear* when the write confirms — without it the
badge would stick until the next content change.

## UI

**`ui/components/SyncStatusBanner.kt`** — hosted in
`feature/main/presentation/MainScreen.kt`, inside the existing `Scaffold`, above
content so it covers all four tabs. Renders nothing when `SYNCED`; no chrome in
the normal case.

| Status | Copy |
|---|---|
| `OFFLINE` | Offline — saved on this phone, will sync later |
| `SYNCING` | Syncing… |

Copy is reassuring rather than alarming: the data *is* safe locally and the
offline-first behaviour is working as designed. The risk being communicated is
"not on the server yet", not "lost".

**`ui/components/PendingSyncBadge.kt`** — an outline dot plus "Not synced", used
in the customer and order row composables.

Both components:

- take colours from `DesignTokens`, defined for **light and dark**;
- use `compose.resources` string resources, with `&apos;` rather than backslash
  escapes (backslashes render literally on CMP iOS);
- ship a `@Preview` per state.

## Edge cases

- **Signed out** — no user-doc listener, so no status. Banner hidden. Auth
  screens are unaffected.
- **Fresh install, first load** — handled by the `OFFLINE` debounce described
  under `SyncStatusObserver`.
- **Emulator builds** — behave identically; the emulator is on loopback and does
  not silently degrade, which is why it is the E2E target.
- **Recomposition** — `includeMetadataChanges = true` emits on metadata-only
  changes, so the customers and orders flows emit more often than today.
  Functionally harmless. Verify list scrolling still feels right on a low-end
  Android device before merge.

## Testing

- Unit test the pure metadata → `SyncStatus` mapping, including the debounce.
- ViewModel tests asserting rows expose `isPendingSync`, using the existing
  fake-repository pattern in `commonTest`.
- Compose previews for both components in light and dark.
- **E2E regression** — extend `.maestro/` (PR #346): sign in, kill the Firestore
  emulator mid-flow, assert the banner appears and a saved record shows the
  badge. This makes the exact bug that prompted this spec a guarded regression.

## Non-goals

- **No pending count in the banner.** An accurate app-wide count needs global
  observers over every collection; the per-row badges already answer "which".
- **No manual retry.** Firestore retries on its own. A Retry button would imply
  control we do not have.
- **No Settings sync screen.**
- **No badges on measurements, order detail, or dashboard cards.** The banner
  still covers those surfaces.
- **No change to the write path.** Offline-first stays exactly as it is.

## Open question for implementation

The original 2026-08-07 incident — where the simulator could not reach production
Firestore while the host could, in the same minute — was never root-caused; the
failing container was destroyed before it could be isolated. This spec makes that
class of failure *visible*, which is the user-facing fix, but does not explain it.
If it recurs, preserve the container
(`xcrun simctl get_app_container <udid> <bundle> data`) before wiping.
