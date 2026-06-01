# PTSP-21 — Brand logo upload during workshop onboarding

**Status:** Design approved 2026-05-26, ready for plan
**Owner:** Daniel Ayodeji
**Jira:** PTSP-21 — Add Logo during workspace onboarding
**Branch:** `feature/ptsp-21-brand-logo-onboarding` (worktree)

## Summary

Today the "Set up your workshop" onboarding screen renders a disabled logo tile that fires a "Logo upload coming soon" snackbar (commit `0638be1`). The `User` domain model, `UserDto`, `UserRepository`, and Firebase Storage have no brand-logo concept at all yet.

This work enables tailors to upload a business logo during onboarding and surfaces that logo at the touchpoints where customers and the tailor expect to see brand identity: the dashboard header, the Settings profile card, the Edit Profile screen (for change/remove), and the shared order receipt on both Android and iOS.

## Goals

1. A tailor finishing onboarding can pick a logo from their device, see it preview immediately, and continue without ever waiting for a network round-trip.
2. The logo appears on the Dashboard, the Settings hero card, and the receipts shared with customers.
3. The tailor can change or remove the logo from Edit Profile at any time, with the destructive remove gated by a confirmation dialog.
4. No regression for existing users: every surface keeps its current initials-avatar appearance when no logo is set.
5. iOS and Android both render the logo on shared receipts in both light and dark variants.

## Non-goals (deliberately deferred)

- Order detail screen header logo, Customer detail screen header logo, Dashboard hero illustrations, Splash / iOS LaunchScreen logo.
- SVG support — V1 accepts PNG and JPG only.
- On-device image downscaling — relying on Coil's downsampling cache.
- A Storage rules file in repo — current default rules suffice.
- Removing the now-unused `workshop_logo_coming_soon` string — separate follow-up to keep this PR additive.

## Scope locked during brainstorm

| Decision | Choice |
| --- | --- |
| Touchpoints in this PR | Onboarding upload + Dashboard header + Settings card + Edit Profile change/remove + Receipts (Android + iOS) |
| Upload timing | Eager on pick, non-blocking |
| Edit affordance | Edit Profile screen only (Settings card stays read-only) |
| Logo formats | PNG and JPG only, max 2 MB |
| Repository home | Extend existing `UserRepository` |

## Data model

### Domain (`core/domain/model/User.kt`)

```kotlin
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val businessName: String?,
    val phoneNumber: String?,
    val whatsappNumber: String?,
    val avatarColorIndex: Int,
    val bonusCoins: Int? = null,
    val businessLogoUrl: String? = null,        // NEW
    val businessLogoStoragePath: String? = null, // NEW
)
```

### DTO (`core/data/dto/UserDto.kt`)

```kotlin
@Serializable
data class UserDto(
    /* existing fields */,
    @SerialName("businessLogoUrl") val businessLogoUrl: String? = null,             // NEW
    @SerialName("businessLogoStoragePath") val businessLogoStoragePath: String? = null, // NEW
)
```

Mapper copies both fields in both directions. Both default to `null` so existing user documents read back fine — no migration required.

## Storage layout

```
users/{uid}/branding/logo.jpg
```

Nested under `branding/` to leave room for future brand assets (banner, swatch) in the same prefix without scattering. Path is deterministic per user — replacing the logo overwrites the same key, but a `delete-then-upload` sequence handles edge cases cleanly (covered in repository contract below).

## Firestore rules

`users/{uid}` is already owner-read/owner-write. The two new fields are simple strings; no rule change required.

## Repository contract (`UserRepository` / `FirebaseUserRepository`)

```kotlin
interface UserRepository {
    /* existing */
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
    ): EmptyResult<DataError.Network>

    // NEW
    suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network>  // (downloadUrl, storagePath)

    suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network>

    suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network>
}
```

Implementation in `FirebaseUserRepository` mirrors `OrderRepository.uploadStylePhoto`:

```kotlin
override suspend fun uploadUserLogo(userId, bytes) =
    runCatching {
        val path = "users/$userId/branding/logo.jpg"
        val ref = storage.reference.child(path)
        ref.putData(bytes.toStorageData())
        val url = ref.getDownloadUrl()
        Pair(url, path)
    }.toNetworkResult()
```

`updateBrandLogo` writes both fields with `merge=true`. `deleteUserLogo` calls `storage.reference.child(path).delete()`. All three suspend until server ack (per the `gitlive` set-awaits-server-ack memory) — callers that want fire-and-forget must launch in a non-tracked coroutine.

## Onboarding flow

### State extension (`WorkshopSetupState`)

```kotlin
sealed interface LogoUploadState {
    data object Empty : LogoUploadState
    data class Uploading(val previewBytes: ByteArray) : LogoUploadState
    data class Uploaded(val url: String, val path: String) : LogoUploadState
    data class Failed(val previewBytes: ByteArray, val path: String) : LogoUploadState
}

data class WorkshopSetupState(
    val businessName: String,
    val whatsappNumber: String,
    val isLoading: Boolean,
    val logo: LogoUploadState = LogoUploadState.Empty,  // NEW
    val isAwaitingLogo: Boolean = false,                 // NEW — Continue tapped while Uploading
    /* existing error fields */
)
```

### Action additions (`WorkshopSetupAction`)

```kotlin
data class OnLogoPicked(val bytes: ByteArray) : WorkshopSetupAction
data object OnLogoRetry : WorkshopSetupAction
// OnLogoUploadClick is removed (replaced by the picker hook in the screen composable)
```

The disabled-tile + `ShowComingSoon` event is removed from the screen.

### Event additions

```kotlin
data class ShowSnackbar(val message: UiText) : WorkshopSetupEvent  // generic, reused for success/error
```

`ShowComingSoon` is removed.

### Pick handler logic

1. Screen composable opens the Peekaboo picker (`rememberImagePickerLauncher`) on tile tap. Picker callback delivers `ByteArray` → fires `OnLogoPicked(bytes)`.
2. ViewModel `onLogoPicked(bytes)`:
   - Validate MIME (first bytes must match PNG `89 50 4E 47` or JPG `FF D8 FF`) and size (≤ 2 MB). On fail, emit `ShowSnackbar` with the appropriate `UiText` and stay in `Empty`.
   - Cancel any in-flight upload job (`logoUploadJob?.cancel()`).
   - Set `state.logo = Uploading(bytes)`.
   - Launch new `logoUploadJob` in `viewModelScope`:
     ```kotlin
     userRepository.uploadUserLogo(uid, bytes).fold(
         onSuccess = { (url, path) -> state.logo = Uploaded(url, path) },
         onFailure = { state.logo = Failed(bytes, expectedPath); emit ShowSnackbar(...with Retry action...) }
     )
     ```

### Continue handler logic (`onContinue`)

Reads current `state.logo`:

- `Empty` or `Failed` → call `createUserProfile(uid, businessName, whatsappNumber)` (no logo fields). Navigate home.
- `Uploaded(url, path)` → `createUserProfile(...)` + `updateBrandLogo(uid, url, path)`. Navigate home.
- `Uploading` →
  - Set `isAwaitingLogo = true` (UI shows brief "Finishing logo upload…" text below the Continue button).
  - `await` the `logoUploadJob`.
  - Re-read state and proceed as above (`Uploaded` → with logo; `Failed` → without logo + already-shown error snackbar).

### Skip handler logic (`onSkip`)

- `Uploading` → `logoUploadJob.cancel()` + fire-and-forget `deleteUserLogo("users/$uid/branding/logo.jpg")` (the deterministic path — we may have partial bytes uploaded already, and a `delete` on a non-existent object is a safe no-op).
- `Uploaded(_, path)` → fire-and-forget `deleteUserLogo(path)`.
- `Empty` / `Failed` → no cleanup needed.
- Mark workshop setup completed + navigate home in all cases.

### Tile rendering

`Empty` → camera-icon tile (current visual, but now enabled). Tap → open picker.
`Uploading` → tile shows `previewBytes` via `AsyncImage(model = previewBytes)` with `LoadingDots` overlay.
`Uploaded` → tile shows `AsyncImage(model = url)` with `LoadingDots` in the loading slot; tap → re-opens picker to change.
`Failed` → tile shows the preview dimmed with a retry icon overlay; tap dispatches `OnLogoRetry` (re-runs upload with the same bytes).

## Dashboard header

`feature/dashboard/presentation/DashboardScreen.kt`, `DashboardHeader()` composable. Today renders a 40dp initials avatar in the top-right. Swap that node for the new `BrandLogo` composable (see below). When `state.businessLogoUrl == null`, `BrandLogo` falls back to the same initials avatar — pixel-identical to today.

`DashboardViewModel` already observes the current user; surface `businessLogoUrl` through the existing state without new flows.

## Settings hero card

`feature/settings/presentation/components/ProfileHeroCard.kt`. Today renders a 56dp initials circle. Same swap to `BrandLogo(size = 56.dp)`. Card stays read-only — tapping anywhere still routes to Edit Profile.

## Edit Profile — change / remove

`feature/settings/presentation/editprofile/EditProfileScreen.kt`. Add a new section composable at the top of the form, above Business name:

```
┌── Business logo ──────────────────────────┐
│  [BrandLogo, 64dp]                         │
│  [Change logo]            [Remove]         │
└────────────────────────────────────────────┘
```

The Edit Profile ViewModel grows the same `LogoUploadState` field used in onboarding. Action additions: `OnLogoPicked(bytes)`, `OnRemoveLogoClick`, `OnRemoveLogoConfirm`.

- **Change logo** → picker → eager upload (overwrites the existing object at the deterministic path) → on success, `updateBrandLogo(uid, newUrl, newPath)`. No explicit delete is needed: `users/{uid}/branding/logo.jpg` is a deterministic key, so `putData` overwrites in place. Snackbar: `edit_profile_logo_updated`.
- **Remove** → confirmation dialog (per the `feedback_notification_patterns` memory — destructive = Dialog) → on confirm, `updateBrandLogo(uid, null, null)` first (so any in-flight UI subscribers stop pointing at the URL), then `deleteUserLogo(previousPath)`. Snackbar: `edit_profile_logo_removed`. If the delete fails, log it but consider the remove successful for the user — the Firestore field is already null.
- The Remove button is hidden when `state.logo == Empty` AND `currentUser.businessLogoUrl == null` (no logo to remove).

## Receipts (Android + iOS)

### `ReceiptData` (`core/sharing/ReceiptData.kt`)

```kotlin
data class ReceiptData(
    /* existing fields */,
    val businessLogoBytes: ByteArray?,   // NEW — pre-decoded for sync rendering
)
```

We prefetch the bytes once at share time. Renderers draw onto `Canvas` / `CGContext` synchronously and can't await Coil, so URL alone isn't enough. Bytes are pulled via `ImageLoader.execute(ImageRequest)` → `Bitmap.compress(PNG)` (Android) / `UIImage.pngData()` (iOS). Cached in-memory for the session inside the receipt-builder (small, throwaway).

### Receipt layout change

Both renderers update the header row:

```
┌──────────────────────────────────────────┐
│ [logo 40×40, 6dp rounded]  Business name │
│                            Phone number   │
├──────────────────────────────────────────┤
│ items, totals, etc...                     │
└──────────────────────────────────────────┘
```

`businessLogoBytes == null` → skip the left column and the text shifts left. Light and dark variants both place the logo on the existing card surface — we don't tint it (it's the user's asset). Per the `feedback_spec_both_color_modes` memory, both variants are mocked and tested.

iOS impl uses `UIImage(data: bytes.toNSData())` → `image.draw(in: rect)`. Android uses `BitmapFactory.decodeByteArray` → `canvas.drawBitmap`.

## Shared composable: `BrandLogo`

New file: `ui/components/BrandLogo.kt`.

```kotlin
@Composable
fun BrandLogo(
    logoUrl: String?,
    fallbackInitials: String,
    fallbackColorIndex: Int,
    size: Dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
)
```

- `logoUrl != null` → `AsyncImage` (Coil v3), `ContentScale.Crop`, clipped to `shape`, `LoadingDots` in the loading slot (mandatory per the `feedback_image_loading_dots` memory), content description = `brand_logo_content_description`.
- `logoUrl == null` → existing initials avatar component (same one ProfileHeroCard uses today, extracted into a helper if needed).
- Three `@Preview` composables: with URL, without URL (initials), and loading state.

## Strings (`commonMain/composeResources/values/strings.xml`)

```xml
<!-- Updated -->
<string name="workshop_logo_upload_sub">PNG or JPG · Max 2MB</string>

<!-- New -->
<string name="workshop_logo_uploading">Uploading logo…</string>
<string name="workshop_logo_upload_failed">Couldn&apos;t upload logo. Retry?</string>
<string name="workshop_logo_too_large">Logo must be 2MB or smaller</string>
<string name="workshop_logo_invalid_format">Use a PNG or JPG image</string>
<string name="workshop_logo_finishing">Finishing logo upload…</string>

<string name="edit_profile_logo_title">Business logo</string>
<string name="edit_profile_logo_change">Change logo</string>
<string name="edit_profile_logo_remove">Remove</string>
<string name="edit_profile_logo_remove_confirm_title">Remove business logo?</string>
<string name="edit_profile_logo_remove_confirm_body">Your logo will no longer appear on the dashboard or receipts.</string>
<string name="edit_profile_logo_remove_confirm_cta">Remove</string>
<string name="edit_profile_logo_updated">Logo updated</string>
<string name="edit_profile_logo_removed">Logo removed</string>

<string name="brand_logo_content_description">Business logo</string>
```

`workshop_logo_coming_soon` stays in place (deliberately unused; removed in a follow-up). No backslash apostrophes per the `feedback_strings_no_backslash_escape` memory.

## Error handling

Feature-local error type:

```kotlin
sealed interface BrandLogoError : Error {
    data object TooLarge : BrandLogoError
    data object UnsupportedFormat : BrandLogoError
    data class Network(val cause: DataError.Network) : BrandLogoError
}
```

`toUiText()` extensions live next to each ViewModel that surfaces these errors. Validation runs in the ViewModel before any upload kicks off — invalid input never reaches the repository.

## DI

No DI changes. `FirebaseUserRepository` is already bound to `UserRepository` in `AuthModule`; the new methods ride that existing binding.

## Testing strategy

| Layer | Test | Tool |
| --- | --- | --- |
| `WorkshopSetupViewModel` | Pick → Uploading → Uploaded; Pick → upload fails → Failed + snackbar event; Skip while Uploaded triggers delete; Skip while Uploading cancels job + delete; Continue while Uploading awaits then proceeds; validation rejects oversize / non-PNG-JPG | JUnit5 + Turbine + UnconfinedTestDispatcher + FakeUserRepository |
| `EditProfileViewModel` | Change → updateBrandLogo + delete previous path; Remove → confirm path → null fields + delete | Same kit |
| Validation | Pure-function tests on the MIME + size validator | JUnit5 |
| Compose previews | `BrandLogo` with/without URL/loading | `@Preview` |
| `FirebaseUserRepository` | Not unit-tested (consistent with existing Firebase impls); covered by manual smoke | — |

## QA smoke test (PR description must include this list)

1. Fresh signup → workshop setup → pick logo → tile shows LoadingDots → preview appears → Continue → dashboard shows the logo top-right.
2. Fresh signup → pick logo → upload fails (kill wifi during upload) → snackbar offers retry → reconnect → retry succeeds.
3. Fresh signup → pick logo → wait for upload → Skip → logo no longer anywhere, Storage object gone.
4. Existing user with no logo → dashboard + settings + receipt all look identical to today.
5. Existing user → Edit Profile → Change logo → settings hero card + dashboard update on next snapshot.
6. Edit Profile → Remove → confirm → all surfaces revert to initials avatar; Storage object deleted.
7. Share receipt with logo: logo appears in receipt header on Android (light + dark) and iOS (light + dark).
8. Run all of the above on a real iPhone (per the recurring iOS-JVM-skew memories — `feedback_kmp_jvm_only_apis`, `feedback_gitlive_ios_nonnull_tokens`, `feedback_ios_modal_bottom_sheet_timing`).
9. Validation: try to upload a 3MB file → blocked with `workshop_logo_too_large`; try to upload a GIF → blocked with `workshop_logo_invalid_format`.

## Worktree

This work happens in the worktree `feature/ptsp-21-brand-logo-onboarding` (off `main`). The spec lands as the first commit on the branch; implementation commits follow.

## Open follow-ups (post-merge)

- Remove `workshop_logo_coming_soon` string entry.
- Decide whether to surface the logo on order detail header, customer detail header, and dashboard hero illustrations.
- Path-scoped Firebase Storage rules file in repo.
- On-device downscaling if perf becomes an issue (Coil downsampling cache covers V1).
