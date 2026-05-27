# Custom measurement fields — PTSP-12

**Date:** 2026-05-26
**Status:** Approved (brainstorm) — awaiting implementation plan
**Tickets:** PTSP-12 (primary)
**Branch:** `feature/ptsp-12-custom-measurements`
**Tier:** Paid (Pro + Atelier), with First Month bonus access for FREE tailors

---

## 1. Context

Nigerian tailoring practice varies by region, style specialism, and individual tailor — no fixed measurement list covers every workflow. If a tailor opens StitchPad and the field they need isn't there, the app stops being usable for that order. PTSP-12 lets paid-tier tailors define their own custom measurement fields that appear alongside the default template.

This was already scoped as a backlog item with open questions (`memory: project_custom_measurements`); the brainstorm resolved each one. The feature is the second paid-tier candidate to ship (after the offline-first V1.5 surface that's still in backlog) and the gating point is kept clean — one entitlement check, one upgrade route — so future paid features can plug into the same pattern.

## 2. Scope & decisions

| Decision | Choice | Rationale |
|---|---|---|
| Field scope | **Per-tailor** (define once, reuse across all customers) | Matches the paid-tier value prop; minimises re-entry friction. |
| Gender association | **Tailor picks per field** (Female / Male / Both) | Lets shop-specific fields target the right form; "Both" is the default chip. |
| Section placement | **Single "Custom" section at the end** of the existing paged form | No section picker required at add time; clear visual separation from defaults. |
| Entry point | **Single bottom sheet from the measurement form** (add/edit/archive all in one) | Discoverable where the tailor needs it; defers a heavier Settings management screen until usage warrants. |
| Field shape | **Numeric, global unit** (same as default fields) | Zero schema change to `Measurement.fields: Map<String, Double>`; free-text observations stay in `Measurement.notes`. |
| Rename | **Label-only; stable internal id** | Past `Measurement.fields["<id>"]` keep working after rename. |
| Delete | **Soft-archive** (`isArchived = true`) | "We never delete your data" — past values keep rendering on past measurements. Un-archive can ship in V1.1. |
| FREE-tier UX | **Show with Pro lock badge → upgrade bottom sheet on tap** | Drives discovery + conversion; matches existing cap-reached pattern. |
| First Month | **Custom measurements ARE unlocked during welcome window** | "Taste the feature before you decide to upgrade." Same window as the customer-cap bonus. |
| Persistence | **`users/{uid}/customMeasurementFields/{fieldId}` subcollection** | Mirrors existing pattern (`customers/`, `measurements/`, `orders/`). Easier to evolve than an embedded array. |

## 3. UX flow

```
Customer detail → "Add measurement" → MeasurementForm (existing)
                                            │
                                            ├─ Default sections (paged, unchanged):
                                            │     Upper Body → Body Lengths → Trouser
                                            │
                                            └─ NEW: Custom section (appended after defaults)
                                                  ├─ Custom fields the tailor has defined that
                                                  │   apply to the current customer's gender
                                                  ├─ "+ Add custom field" affordance
                                                  │   ├─ Pro/Atelier or First Month: opens AddCustomFieldSheet
                                                  │   └─ FREE post-welcome: Pro lock pill → upgrade sheet
                                                  └─ Long-press a custom field → ManageCustomFieldSheet
```

### `AddCustomFieldSheet` (bottom sheet)

- **Label** text field (max ~30 chars, required, non-blank trim)
- **Genders** chip group: Female / Male / Both (Both selected by default)
- `[ Save ]` primary button + `[ Cancel ]` text button

On Save: field is persisted, sheet dismisses, field appears in the Custom section immediately.

### `ManageCustomFieldSheet` (long-press existing field)

- Edit label (same constraints)
- Edit gender visibility
- `[ Archive field ]` destructive action → confirm dialog:
  - Title: "Archive this field?"
  - Body: "This field will stop appearing on new measurements. Values already recorded stay visible on past measurements."
  - `[ Cancel ]` + `[ Archive ]`

### Restoration

Archived fields are not surfaced in V1. Un-archive (a "Show archived" toggle in the manage sheet) can ship in V1.1 if usage warrants.

## 4. Architecture

### 4.1 New domain model

```kotlin
// core/domain/model/CustomMeasurementField.kt
@Serializable
data class CustomMeasurementField(
    val id: String,                    // stable UUID; never displayed
    val label: String,                 // tailor-editable; what shows in UI
    val genders: Set<CustomerGender>,  // non-empty
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
```

The **`id` is stable, the `label` is editable** — that's what makes "rename = label-only" safe across past records. Past `Measurement.fields["<id>"]` keep resolving after a rename.

### 4.2 Firestore

```
users/{uid}/customMeasurementFields/{fieldId}
```

**Document shape (`CustomMeasurementFieldDto`):**
```json
{
  "id": "ca3f-7b...",
  "label": "Sleeve cuff width",
  "genders": ["FEMALE", "MALE"],
  "isArchived": false,
  "createdAt": 1748275200000,
  "updatedAt": 1748275200000
}
```

**Security rule** (`firestore.rules`):
```
match /users/{uid}/customMeasurementFields/{fieldId} {
  allow read, write: if request.auth.uid == uid;
}
```

Server-side feature-tier rule enforcement is **out of scope for V1** — consistent with `customerCap` being enforced client-side today. Flagged as a gap (see `memory: project_freemium_v1` "server/client cap drift" pattern).

### 4.3 Repository

```kotlin
// core/domain/repository/CustomMeasurementFieldRepository.kt
interface CustomMeasurementFieldRepository {
    fun observeFields(userId: String): Flow<Result<List<CustomMeasurementField>, DataError.Network>>
    suspend fun createField(userId: String, field: CustomMeasurementField): EmptyResult<DataError.Network>
    suspend fun updateField(userId: String, field: CustomMeasurementField): EmptyResult<DataError.Network>
    suspend fun archiveField(userId: String, fieldId: String): EmptyResult<DataError.Network>
}
```

`archiveField` is sugar for `updateField(field.copy(isArchived = true))` — named for intent at call sites. No hard-delete in V1.

Implementation: `feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt` — mirrors `FirebaseCustomerRepository` pattern (mapper, DTO, GitLive Firestore client).

### 4.4 No change to `Measurement.fields`

`Measurement.fields: Map<String, Double>` already accepts any string key. A custom field's value is stored under its UUID key (e.g., `"ca3f-7b..." -> 14.5`). The Firestore document survives label edits and archives unchanged — only the display layer needs to know about the rename.

### 4.5 Entitlements

Add one derived capability:

```kotlin
data class UserEntitlements(
    val tier: SubscriptionTier,
    val customerCap: Int,
    val smartCoinAllowance: Int,
    // ... existing welcome-window fields unchanged
    val canUseCustomMeasurements: Boolean,  // NEW
)
```

In `EntitlementsCalculator`:
```kotlin
val canUseCustomMeasurements = tier == SubscriptionTier.PRO ||
    tier == SubscriptionTier.ATELIER ||
    isInWelcomeWindow  // First Month taste
```

Every call site goes through `entitlements.current().canUseCustomMeasurements`. Per `memory: project_premium_tier_candidates`: *keep the gating point clean (one Koin-injected Entitlement check)*.

### 4.6 What happens when welcome ends on a FREE tailor

Match `project_freemium_v1`'s "we never delete your data" principle:

- **Past measurements** with recorded custom-field values: **always visible**, on every tier, forever.
- **Custom-field definitions** in Firestore: **kept** even when welcome ends. Don't auto-archive on tier downgrade.
- **The Custom section in new-measurement forms**: gated behind `canUseCustomMeasurements`. Post-welcome FREE → "+ Add custom field" shows the Pro lock and routes to the upgrade sheet.
- **On later upgrade**: all previously-defined custom fields re-activate (definitions were never deleted).

Net: workflow data survives downgrade. The feature gate is on *active use*, not *data ownership*. Same model as locked customers.

### 4.7 State / Actions / Events

#### `MeasurementFormState` additions

```kotlin
data class MeasurementFormState(
    // ... existing fields unchanged
    val customFields: List<CustomMeasurementField> = emptyList(),
    val canUseCustomMeasurements: Boolean = false,
    val customFieldSheet: CustomFieldSheet? = null,
)

sealed interface CustomFieldSheet {
    data object Adding : CustomFieldSheet
    data class Editing(val field: CustomMeasurementField) : CustomFieldSheet
    data class ConfirmArchive(val field: CustomMeasurementField) : CustomFieldSheet
}
```

Sheet state lives in VM state (not local Compose state) so destructive flows (archive confirm) survive config changes.

#### New `MeasurementFormAction` entries

```kotlin
sealed interface MeasurementFormAction {
    // ... existing actions unchanged
    data object OnAddCustomFieldClick : MeasurementFormAction
    data object OnLockedCustomFieldClick : MeasurementFormAction
    data class OnEditCustomFieldClick(val fieldId: String) : MeasurementFormAction
    data object OnCustomFieldSheetDismiss : MeasurementFormAction
    data class OnSaveCustomField(
        val id: String?,  // null = create, non-null = update
        val label: String,
        val genders: Set<CustomerGender>,
    ) : MeasurementFormAction
    data class OnArchiveCustomFieldConfirm(val fieldId: String) : MeasurementFormAction
}
```

#### New `MeasurementFormEvent` entry

```kotlin
data object NavigateToUpgrade : MeasurementFormEvent
```

#### VM stream

The VM observes `customMeasurementFieldRepository.observeFields(userId)` and stores the filtered subset in `state.customFields` (filter: `gender ∈ field.genders && !field.isArchived`). Recomputes whenever gender changes.

### 4.8 Bundled fix: `loadMeasurement` orphan handling

`MeasurementFormViewModel.loadMeasurement` at line 130 currently does:

```kotlin
val allKeys = sections.flatMap { it.fields }.map { it.key }
val fieldsAsString = allKeys.associateWith { key -> ... }
```

This only repopulates *template* keys. If a custom-field value exists on the stored `Measurement`, it would be silently dropped on save. Replace with:

```kotlin
val templateKeys = sections.flatMap { it.fields }.map { it.key }.toSet()
val customKeys = currentCustomFields.map { it.id }.toSet()
val recordedKeys = measurement.fields.keys
val allKeys = templateKeys + customKeys + recordedKeys
val fieldsAsString = allKeys.associateWith { key -> measurement.fields[key]?.let { /* format */ } ?: "" }
```

This preserves:
- Template values (existing behavior)
- Currently-defined custom-field values (new)
- **Orphan values** — keys that no longer match any known field (data imported elsewhere, deleted definitions). Orphans round-trip cleanly without rendering as inputs.

## 5. Testing

### 5.1 Unit tests

**`EntitlementsCalculatorTest`** (extends existing):
1. `canUseCustomMeasurements_isTrue_forPro`
2. `canUseCustomMeasurements_isTrue_forAtelier`
3. `canUseCustomMeasurements_isTrue_forFreeInsideWelcomeWindow`
4. `canUseCustomMeasurements_isFalse_forFreePostWelcome`

**`CustomMeasurementFieldRepositoryTest`** (new, follows `FakeCustomerRepository` pattern):
5. `createField_persists`
6. `updateField_changesLabelOnly_keepsId` — proves the rename-safety guarantee
7. `archiveField_setsIsArchivedTrue`
8. `observeFields_emitsActiveAndArchived_callerFilters`

**`MeasurementFormViewModelTest`** (extends existing):
9. `customFields_filteredByGenderAndArchive`
10. `onAddCustomFieldClick_whenEntitled_opensAddingSheet`
11. `onLockedCustomFieldClick_emitsNavigateToUpgrade_andDoesNotOpenSheet`
12. `onSaveCustomField_create_callsRepoCreate_withNewUuid`
13. `onSaveCustomField_edit_callsRepoUpdate_preservesId`
14. `onSaveCustomField_whenEntitlementLost_emitsNavigateToUpgrade_repoNotCalled` (defense in depth)
15. `onArchiveCustomFieldConfirm_callsRepoArchive_andClosesSheet`
16. `loadMeasurement_withCustomFieldValues_populatesAllKeys`
17. `loadMeasurement_withOrphanKeys_preservesValuesOnRoundtrip`

### 5.2 Smoke test (Android + iOS, light + dark)

**Setup:**
- Pro test account (always entitled)
- FREE test account, fresh signup (in welcome window)
- FREE test account, post-welcome (use debug menu's time-advance if available, otherwise an aged account)

**Happy path (Pro)**

1. Sign in as Pro → create customer → add measurement. Custom section appears empty with primary "+ Add custom field" button.
2. Tap "+ Add custom field" → sheet appears. Type `Sleeve cuff width`, pick `Both`, Save. Field appears immediately in the Custom section.
3. Enter `12.5` → save measurement. Customer detail shows "Sleeve cuff width: 12.5 cm".
4. Edit the measurement → confirm `12.5` is pre-populated. Change to `13.0` → save. Value updates, no silent drop.

**Rename & archive**

5. Long-press `Sleeve cuff width` → manage → rename to `Cuff` → save. Past measurement now reads "Cuff: 13.0".
6. Long-press `Cuff` → Archive field → confirm. Field disappears from Custom section; past measurement still shows "Cuff: 13.0".
7. Re-add a field named `Cuff` → succeeds (new UUID, no collision).

**Gender filtering**

8. Add "Bra cup separation" with Female-only. Add "Lapel width" with Male-only. Female customer's form shows only "Bra cup separation"; male customer's form shows only "Lapel width".

**Free tier — welcome window**

9. Sign in as fresh FREE account (welcome active) → add measurement. "+ Add custom field" appears as primary button (no lock badge). Adding a field works identically to Pro.

**Free tier — post-welcome**

10. Sign in as FREE account whose welcome window expired → add measurement. "+ Add custom field" shows with Pro lock pill. Tap → upgrade bottom sheet. Past measurements with custom-field values still render correctly.

**Cross-cutting**

11. iOS compile check ([[feedback_kmp_jvm_only_apis]]).
12. Process-death survival: open the add-custom-field sheet, background+kill, reopen — sheet state should restore (lives in VM, not local Compose).
13. Cursor + codex review per [[feedback_review_rotation]].

## 6. Review

- Cursor + `codex review` before merge per `memory: feedback_review_rotation`.
- Pre-emptive watch for cross-side constant drift (`canUseCustomMeasurements` Kotlin only; no server-side counterpart yet).

## 7. Deferred / out of scope

- **Server-side rule enforcement** of `canUseCustomMeasurements`. The Firestore rule currently allows any signed-in user to write to their `customMeasurementFields/` subcollection. Client-side gate is the only guard for V1.
- **Un-archive UX** ("Show archived" toggle). Archived fields stay in Firestore but are invisible to the user in V1.
- **Settings → Workshop → Custom measurements** dedicated management screen. V1 ships add/edit/archive from the measurement form only.
- **Custom-field analytics** (which fields are most used across the tailor's customers).
- **Soft per-tailor cap** (e.g., max 100 custom fields). No artificial cap in V1; doc-count is small.
- **Free-text custom fields**. Use `Measurement.notes` for observations.
- **Per-field unit override**. All fields share the form's global unit.
- **Section assignment picker**. All custom fields land in the single "Custom" section.
