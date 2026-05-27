# Order Form — Searchable Garment Picker + Custom Values

**Date:** 2026-05-27
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** Follow-up to the rename slice merged in PR #84. Will land as its own ticket (PTSP-XX, to be filed).

---

## 1. Context

PR #84 renamed the order-form's confusing labels:

- "Garment type" → "What are you making?"
- "Description (optional)" → "Design notes (optional)" with a more useful placeholder

That fixed the read-confusion (the old "Garment type" label competed with the "STYLE REFERENCES" section directly below it), but it left two structural problems in place:

1. **The dropdown is a closed enum.** The current `GarmentType` enum has 17 entries — `Agbada`, `Senator`, `Kaftan`, `Asoebi`, `Bridal Gown`, etc. — but Nigerian tailors regularly make items the enum doesn't cover (`Iro and Buba`, `Senator with cape`, `Kente cape`, regional traditional wear). They currently have to pick the closest preset and stuff the real garment name into "Design notes", which breaks downstream features like receipt grouping and pipeline-row display.

2. **The picker has no search.** Scrolling 17 entries on a phone every time you create an order is friction, especially when the tailor's working garment for the week is at the bottom of the list.

This spec defines the **searchable bottom-sheet picker** that replaces the existing `ExposedDropdownMenuBox`, and the **per-tailor custom-value persistence layer** that lets a tailor type "Iro and Buba" once and have it remembered for future orders.

The visual reference is already on `main` at `preview/garment-picker-redesign.html` (5 phone states showing the full flow).

## 2. Branch & PR shape

- **Branch:** `feature/garment-picker-search-and-customs` (or matching ticket ID once filed)
- **Single PR** covering the picker UI + repository + data model + display propagation
- Spec + plan + the existing HTML mockup all live on the branch

## 3. Design decisions (locked during brainstorm)

| # | Decision | Value |
| --- | --- | --- |
| 1 | Storage of custom values | New Firestore subcollection `users/{uid}/customGarmentTypes/{id}` |
| 2 | Sort order for "My garment types" | `lastUsedAt` desc, alphabetical tiebreak |
| 3 | Case-insensitive dedupe | Typed text that case-insensitively matches an existing custom hides the "Add" CTA and highlights the existing entry |
| 4 | "Custom" badge visibility | Form only — detail screen, pipeline, receipt all show the value as plain text |
| 5 | First-launch empty state | "My garment types" section is hidden entirely until the tailor adds their first custom |
| 6 | What to store on `OrderItem` | Just `customGarmentName: String?` — no doc-id reference. OrderItem stays self-contained; custom-types subcollection is only the picker's suggestion list |
| 7 | Migration | Strictly additive — new nullable field, new `OTHER` enum entry, no backfill |
| 8 | Sheet presentation | Modal bottom sheet (~78% screen height), sticky search at top, scrollable list below |
| 9 | Match algorithm | Case-insensitive substring across both `My garment types` and `Preset types`. No fuzzy / Levenshtein matching |

## 4. Data model

### Domain (`core/domain/model/CustomGarmentType.kt`)

```kotlin
data class CustomGarmentType(
    val id: String,
    val name: String,          // stored as typed by the tailor, e.g. "Iro and Buba"
    val createdAt: Long,       // epoch ms
    val lastUsedAt: Long,      // epoch ms — drives sort, updated on every pick
)
```

### DTO (`core/data/dto/CustomGarmentTypeDto.kt`)

```kotlin
@Serializable
data class CustomGarmentTypeDto(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
)
```

Mapper extensions follow the existing pattern (`toCustomGarmentType()` / `toCustomGarmentTypeDto()`).

### `GarmentType` enum changes

Add one new variant:

```kotlin
enum class GarmentType(@StringRes val labelRes: Int, ...) {
    // existing 17 entries unchanged …
    OTHER(R.string.garment_type_other, ...),
}
```

`OTHER` represents "a custom garment name not in our preset list". It is only ever assigned when `customGarmentName` is also non-null on the same `OrderItem`. Existing items keep their preset enum values.

### `OrderItem` additions

```kotlin
data class OrderItem(
    // existing fields…
    val garmentType: GarmentType,
    val customGarmentName: String? = null,   // NEW — set only when garmentType == OTHER
    // …
)
```

OrderDto gains the matching nullable field with `= null` default. Kotlin's `@Serializable` reads existing Firestore docs (which have no `customGarmentName` field) as `null` without migration.

## 5. Repository layer

### Interface (`core/domain/repository/CustomGarmentTypeRepository.kt`)

```kotlin
interface CustomGarmentTypeRepository {

    /** Subscribe to the tailor's saved customs. Sorted by lastUsedAt desc, alpha tiebreak. */
    fun observe(userId: String): Flow<Result<List<CustomGarmentType>, DataError.Network>>

    /**
     * Create a new custom OR return the existing one if a case-insensitive name
     * match is found. Always increments lastUsedAt on the resolved doc.
     */
    suspend fun upsert(userId: String, name: String): Result<CustomGarmentType, DataError.Network>

    /** Update lastUsedAt on an existing custom (called when an already-saved custom is picked). */
    suspend fun touch(userId: String, id: String): EmptyResult<DataError.Network>
}
```

### Implementation: `FirebaseCustomGarmentTypeRepository`

Firestore path: `users/{uid}/customGarmentTypes/{id}`

- `observe` — `snapshots()` flow with client-side sort (Firestore's `orderBy(lastUsedAt, DESC)` works, but client-side sort + alpha tiebreak in one pass is cleaner and the dataset is small)
- `upsert` — reads the full subcollection, case-insensitively compares `name`. If match found, updates `lastUsedAt` and returns the existing doc. If no match, creates a new doc with a generated id, `createdAt = lastUsedAt = now`. Wrapped in a single Firestore transaction to prevent race-on-double-tap.
- `touch` — single `update` call with `{ lastUsedAt: now }`. Fire-and-forget from the form.

### Koin module

Add `singleOf(::FirebaseCustomGarmentTypeRepository) bind CustomGarmentTypeRepository::class` to the data module. `OrderFormViewModel` constructor gains the dependency.

## 6. Picker UX flow

Bottom sheet (`ModalBottomSheet`), height capped at 78% of screen. Sticky header area: title + sub + search field. Scrollable list below.

### Sections in order

1. **Add-as-custom row (conditional)** — green dashed-border row that appears at the very top when the search text has no case-insensitive match in either list. Tapping it: calls `upsert(name = searchText)`, picks the result, dismisses the sheet, shows a snackbar `Saved "X" to your garment types.`
2. **My garment types (conditional)** — only visible if the tailor has ≥ 1 saved custom. Section header shows `n` count. Rows are filtered by the search query.
3. **Preset types** — always visible. Section header shows `n` count. Rows are filtered by the search query.

### Interaction matrix

| Search text | Existing customs | Behavior |
| --- | --- | --- |
| Empty | 0 | "My garment types" section hidden. Preset section shows all 17 entries. |
| Empty | ≥ 1 | Both sections visible, unfiltered. |
| Non-empty, no matches anywhere | any | "Add 'X' as a new garment type" row appears at top. Both sections show empty state with a one-line hint. |
| Non-empty, matches in preset only | any | "Add" row hidden (typed text might be a preset variant). Preset section filtered. |
| Non-empty, case-insensitive match in customs | ≥ 1 | "Add" row **hidden** (dedupe). Custom section shows highlighted match. |
| Non-empty, matches in both | ≥ 1 | "Add" row hidden. Both sections filtered. |

### Empty-state copy (search has no matches)

- Top "Add" row: `Add "<typed>" as a new garment type` + sub: `Saved for next time`
- Preset section empty: `No preset matches "<typed>" — that's OK, add it as your own.` (the "Add" row above is the affordance)

### Filtering algorithm

Pure client-side. `name.lowercase().contains(query.lowercase())` for both lists. The dataset is small (~17 presets + ≤ 30 customs per tailor in practice), so no debouncing needed.

## 7. Form-side changes

### `OrderFormScreen` Step 2 (Items)

- The "What are you making?" field becomes a tap target (no inline dropdown).
- Tapping it opens the picker, prefilled with the current item's `garmentType` / `customGarmentName` highlighted.
- When `garmentType == OTHER && customGarmentName != null`, the field renders the custom name with a `Custom` chip (Material `AssistChip` style, secondary color, 10px font with caps).
- All other field behavior unchanged.

### `OrderFormState` additions

```kotlin
data class OrderFormState(
    // existing fields…
    val customGarmentTypes: List<CustomGarmentType> = emptyList(),
    val activePickerItemId: String? = null,    // which item is opening the picker (null = sheet closed)
    val pickerSearchQuery: String = "",
)
```

### `OrderFormAction` additions

Replaces `OnItemGarmentTypeChange` with:

```kotlin
sealed interface OrderFormAction {
    // existing actions…

    /** Open the picker for a specific item row. */
    data class OnOpenGarmentPicker(val itemId: String) : OrderFormAction

    /** Pick a preset or existing custom garment value. */
    data class OnPickGarmentType(
        val itemId: String,
        val garmentType: GarmentType,
        val customName: String?  // non-null iff garmentType == OTHER
    ) : OrderFormAction

    /** Add and pick a brand-new custom value. Calls upsert, then dispatches OnPickGarmentType. */
    data class OnAddCustomGarmentType(val itemId: String, val name: String) : OrderFormAction

    data class OnPickerSearchChange(val query: String) : OrderFormAction

    data object OnDismissPicker : OrderFormAction
}
```

`OnItemGarmentTypeChange` is replaced by the actions above — the new shape covers all paths (open, pick existing, add custom, search, dismiss). The plan will handle the rename cleanly.

### `OrderFormEvent` additions

```kotlin
sealed interface OrderFormEvent {
    // existing events…
    data class ShowCustomSavedSnackbar(val name: String) : OrderFormEvent
}
```

### `OrderFormViewModel`

- Constructor gains `private val customGarmentTypeRepository: CustomGarmentTypeRepository`.
- In `loadInitialData`, after resolving `userId`, launches a coroutine that collects `customGarmentTypeRepository.observe(userId)` and updates `state.customGarmentTypes`.
- `OnAddCustomGarmentType` flow:
  1. `viewModelScope.launch { … }`
  2. `upsert(userId, name)` → suspends
  3. On success: dispatch `OnPickGarmentType(itemId, OTHER, result.name)` + emit `ShowCustomSavedSnackbar(result.name)`
  4. On error: emit a generic error event (existing `OrderFormEvent.ShowError` pattern)
- `OnPickGarmentType` flow:
  1. Updates `state.items[itemId]` with new `garmentType` + `customGarmentName`
  2. If `garmentType == OTHER` and the picked custom already exists in `state.customGarmentTypes`: fire-and-forget `touch(userId, customId)`. (We don't suspend on this — the snapshot listener will re-emit the new sort order.)

## 8. Display rules — applied everywhere garment name shows

Single pure-domain extension function used by all display sites. The caller passes a resolver so the helper stays composable-agnostic and unit-testable:

```kotlin
// core/domain/model/OrderItem.kt — pure domain helper
fun OrderItem.displayGarmentName(resolveLabel: (GarmentType) -> String): String =
    if (garmentType == GarmentType.OTHER && !customGarmentName.isNullOrBlank()) {
        customGarmentName
    } else {
        resolveLabel(garmentType)
    }
```

Compose call site:

```kotlin
val displayName = item.displayGarmentName { stringResource(it.labelRes) }
```

Non-Compose call site (e.g. receipt text builders that pre-resolve labels):

```kotlin
val displayName = item.displayGarmentName { resolvedLabels[it] ?: "" }
```

Call sites to update:
- `OrderHeroCard` (order detail header)
- `OrderGarmentDetailsCard` (order detail body)
- Pipeline row composable (dashboard pipeline)
- Dashboard previews ("Next up" / "Due soon" cards)
- Receipt sharing renderer
- Status sheet subtitles

The **only** place that additionally renders the "Custom" pill is `OrderFormScreen`'s tap-target field. Every other site renders plain text.

## 9. Receipt grouping

The existing receipt groups items by their displayed garment name. With `displayGarmentName` returning the custom name when present, custom values group correctly as standalone groups (one custom name = one group). No special handling needed.

## 10. Migration

Strictly additive. Three changes, all backward-compatible:

1. **New enum entry** `GarmentType.OTHER` — only assigned to new items. Existing items keep their preset values.
2. **New `OrderItem` field** `customGarmentName: String?` — Kotlin defaults to `null`. Existing Firestore docs that don't contain this field deserialize fine.
3. **New subcollection** `users/{uid}/customGarmentTypes/` — empty for all existing tailors. Picker's "My garment types" section is hidden when empty (decision #5), so the absence is invisible.

No backfill, no migration script, no version flag.

## 11. Testing strategy

Following the existing project pattern: pure-domain helpers get unit tests, ViewModels get Fake-repository-backed tests, and Firebase implementations get smoke coverage (not unit tests — they integrate against GitLive's SDK which is hard to mock cleanly).

### New fake repository (`FakeCustomGarmentTypeRepository`)

Lives in `commonTest`. In-memory map keyed by user id. Implements the full repository contract for use across `OrderFormViewModelTest` and any future consumers.

### Display rule unit test (`OrderItemDisplayTest`, commonTest)
- `displayGarmentName` returns custom name when `garmentType == OTHER && customGarmentName != null`.
- `displayGarmentName` returns the resolved enum label for preset items.
- `displayGarmentName` falls through to the enum resolver when `garmentType == OTHER` but `customGarmentName` is null or blank (defensive — should never happen in practice, but proves the fallback).

### ViewModel test (`OrderFormViewModelTest`, commonTest)
- `OnAddCustomGarmentType` calls `upsert` on the fake repo, then updates the item state with the result and emits `ShowCustomSavedSnackbar`.
- `OnPickGarmentType(itemId, OTHER, "Iro and Buba")` updates item state. If "Iro and Buba" is in `state.customGarmentTypes`, `touch` is also called on the fake.
- `OnPickGarmentType` with a preset enum does NOT call `touch`.
- Picker state (`activePickerItemId`, `pickerSearchQuery`) updates correctly across open / pick / dismiss.
- Search filter helper (extracted into a domain pure function `filterGarmentOptions`) returns expected matches for various queries — case-insensitive substring, empty query, no match, match in customs only, match in presets only.

### Firebase smoke (manual)

`FirebaseCustomGarmentTypeRepository` is exercised by the smoke test below — no unit test layer.

### Smoke test (manual, included in PR body)
- Open New Order, tap "What are you making?" → picker opens.
- Type "Iro" → both sections filter live.
- Type something new ("Kente cape") → green "Add" row appears → tap → field updates with "Custom" pill, snackbar confirms.
- Close, reopen the picker → "Kente cape" appears in "My garment types".
- Type the same custom case-differently ("KENTE CAPE") → existing entry highlighted, "Add" row hidden.
- Save order → order detail shows "Kente cape" as plain text, no Custom pill (form-only).
- Edit existing order with a preset garment type → field shows preset, picker opens with preset highlighted.

## 12. What's NOT in V1 (deferred)

- **Settings → "My garment types" management screen** (rename / delete / merge). Filed as project memory `project_premium_tier_candidates` candidate; lives behind Settings, not on the picker.
- **Splitting the existing enum.** `Asoebi`, `Bridal Gown`, `Corporate Wear` are really occasion tags, not garment items. Separate refactor.
- **Cross-tailor sharing of customs.** Each tailor has their own list. No marketplace.
- **"Did you mean…?" near-duplicate warnings.** Strict case-insensitive equality only — no fuzzy / Levenshtein matching.
- **Empty-state fallback to suggested-from-history.** If a brand-new tailor has zero customs, we just don't show the "My garment types" section. We don't infer customs from past order names.
- **Usage-count analytics.** We track `lastUsedAt` for sort, not `usageCount` for analytics.

## 13. Risk & open questions

### Known risks

1. **GitLive `set()` awaits server ACK** ([feedback memory `gitlive_firestore_set_awaits_server_ack`][gitlive]). `upsert` could hang if the tailor adds a custom while offline. Mitigation: fire `upsert` in `viewModelScope` but **don't** block the form — optimistically pick `OTHER + typed name` into form state immediately, let the snapshot listener reconcile the subcollection later. The snackbar message can change to "Saved when back online" if we detect offline, but V1 ships with the optimistic-only path.

2. **Pick-time race on rapid double-tap of the "Add" row.** Mitigated by:
   - Disabling the "Add" row for ~300ms after tap
   - `upsert` doing a case-insensitive scan before write inside a Firestore transaction

3. **Search performance on large custom lists.** Not actually a risk for V1 — even prolific tailors are unlikely to have > 50 customs. Client-side filtering on a small list is instant. Re-evaluate if we ever see > 100 customs per user.

### Open questions

None remaining. All six original brainstorm questions resolved during the question-and-answer flow that produced this spec.

---

[gitlive]: ../../../.claude/projects/-Users-danzucker-Desktop-Project-StitchPad/memory/feedback_gitlive_firestore_set_awaits_server_ack.md
