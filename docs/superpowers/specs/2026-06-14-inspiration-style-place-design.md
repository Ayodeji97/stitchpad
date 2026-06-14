# Inspiration — a styles place not tied to a customer (PTSP-38, phase 2)

## Context

PTSP-38 (tester feedback) asked for three things on styles: (1) keep per-customer
styles, (2) add a separate **favorites place** for styles not tied to any customer
("just those styles you think your customers will like"), and (3) copy/move styles
between customers and into that favorites place.

Phase 1 (PR #162) shipped per-customer copy/move. **Phase 2 (this spec)** builds the
favorites place — named **"Inspiration"** — and extends copy/move to work both ways
between a customer's closet and Inspiration.

### Decisions (confirmed with Daniel)
- **Name:** "Inspiration".
- **Placement:** an entry/card on the **Dashboard** (keeps the 4-tab bottom bar).
- **Scope:** the Inspiration place + add photos directly (multi-pick) + copy/move
  **both directions** between a customer's closet and Inspiration.
- **Architecture:** a `StyleLocation` abstraction (not a parallel repository or a
  sentinel customerId).
- **Order flow:** the order-creation style picker stays **customer-closet-only**
  this phase. To use an Inspiration look for an order, copy it into the customer's
  closet first (one tap), then pick it. See *Deferred follow-up*.

## Architecture — `StyleLocation`

A style lives in one of two places. Model it explicitly:

```kotlin
sealed interface StyleLocation {
    data class CustomerCloset(val customerId: String) : StyleLocation
    data object Inspiration : StyleLocation
}
```

The repository, gallery ViewModel, and transfer flow key on a `StyleLocation`
instead of a raw `customerId`. The `Style` model's *fields* are unchanged — location
is carried as context (route → VM → repository), so phase-1's image-share / re-upload
(`writeSharedCopy`) and doc-only delete logic generalize without change. For an
Inspiration style, `Style.customerId` is simply the empty string and unused — the
`StyleLocation` (from the route), not the style's `customerId`, drives all behavior.
The mapper `toStyle` is generalized to take a `StyleLocation` (passing `""` for the
customerId on Inspiration) rather than a bare `customerId`.

### Paths (Firestore + Storage)
A single mapper turns a location into its collection + storage prefix:
- `CustomerCloset(cid)` → Firestore `users/{uid}/customers/{cid}/styles`, storage
  `users/{uid}/customers/{cid}/styles/{id}.jpg` (unchanged).
- `Inspiration` → Firestore top-level `users/{uid}/inspiration`, storage
  `users/{uid}/inspiration/{id}.jpg`.

## Repository changes (`StyleRepository` + `FirebaseStyleRepository`)

Replace the `customerId: String` parameter with `location: StyleLocation` on:
`observeStyles`, `createStyle`, `createStyles`, `updateStyle`, `deleteStyle`.
Generalize transfer to:

```kotlin
suspend fun copyStyle(userId, from: StyleLocation, style: Style, to: StyleLocation)
suspend fun moveStyle(userId, from: StyleLocation, style: Style, to: StyleLocation)
```

`stylesCollection`/`storagePath` become `collectionFor(location)` /
`storagePathFor(location, id)`. Everything else (offline-write enqueue, the
share-vs-reupload branch in `writeSharedCopy`, never-eager-delete in `deleteStyle`)
is reused as-is. `FakeStyleRepository` mirrors the new signatures.

## Navigation

- `StyleGalleryRoute(customerId: String? = null)` — `null` means Inspiration
  (avoids a custom NavType). VM derives the location:
  `customerId?.let(StyleLocation::CustomerCloset) ?: StyleLocation.Inspiration`.
- `StyleFormRoute(customerId: String? = null, …)` — same convention, so adding to
  Inspiration reuses the existing multi-pick form.

## Gallery & form reuse

The existing `StyleGalleryScreen`/`StyleGalleryViewModel` and `StyleForm` serve both
places; only the **title** differs ("FOLA'S CLOSET" vs "INSPIRATION") and the
empty-state copy. Long-press → Copy / Move / Delete and multi-pick add are unchanged.

## Both-way transfer + target picker

`StyleGalleryViewModel` builds the transfer target list from the current location:
- From a **customer's closet** → other ACTIVE customers **plus an "Inspiration" row**.
- From **Inspiration** → the ACTIVE customers.

`TransferTarget` carries a `StyleLocation` (a customer id or Inspiration). The picker
sheet renders the customer rows plus, when applicable, a distinct "Inspiration" row.
The existing post-transfer "View" snackbar action navigates to the chosen
destination's gallery (`StyleGalleryRoute(targetCustomerId-or-null)`).

## Dashboard entry

An "Inspiration" card on the Dashboard navigates to `StyleGalleryRoute(null)`. The
exact visual treatment (icon, count, placement among existing dashboard cards) will
be explored as **HTML variants under `Preview/`** before writing Compose, per the
established design workflow.

## Firestore rules + ops

- Add to `firestore.rules` under the user document:
  `match /inspiration/{styleId} { allow read, write: if isOwner(uid) }`.
- **Ops (not in repo):** Firebase **Storage** rules for `users/{uid}/inspiration/`
  are console-managed — add the same owner rule there before testing uploads, or
  Inspiration image uploads will fail. Flag in the PR's go-live notes.

## Deferred follow-up (separate ticket/PR)

Add **Inspiration as a source in the order-creation style picker** (a
"Closet | Inspiration" toggle). Requires the order's `StyleImageRef` resolution
(`OrderFormViewModel.availableStyles`, `OrderForm`/`OrderDetail` rendering) to also
resolve styleIds against the Inspiration collection. Out of scope here.

## Testing

- **Repository:** `collectionFor`/`storagePathFor` map each location to the right
  paths; copy/move across location combinations call the right collections.
- **ViewModel:** target list includes Inspiration when in a closet and customers
  when in Inspiration; copy/move call the repo with the correct `from`/`to`; gallery
  title resolves per location.
- **Fake:** records transfers with their `from`/`to` locations.

## Verification

- Unit tests green (`:composeApp:testDebugUnitTest`), iOS compile
  (`:composeApp:compileKotlinIosSimulatorArm64`), `detekt` clean.
- Manual: Dashboard → Inspiration opens an empty place; add 2+ photos (multi-pick);
  from a customer's closet, copy a style to Inspiration → "View" shows it there and
  the original remains; move a style from Inspiration to a customer → it leaves
  Inspiration and appears in the closet, image renders; offline copy syncs on
  reconnect. Repeat on iOS.
