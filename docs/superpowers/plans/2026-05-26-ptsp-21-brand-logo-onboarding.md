# PTSP-21 Brand Logo Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable tailors to upload a business logo during workshop onboarding and surface that logo at the dashboard header, Settings profile card, Edit Profile (for change/remove), and shared order receipts on Android + iOS.

**Architecture:** Extend the existing `UserRepository` with three Storage methods (`uploadUserLogo`, `updateBrandLogo`, `deleteUserLogo`) — same pattern as `OrderRepository.uploadStylePhoto`. Add two nullable fields (`businessLogoUrl`, `businessLogoStoragePath`) to `User` + `UserDto`. Share a single `LogoUploadState` sealed interface across the onboarding and edit-profile ViewModels. A new `BrandLogo` composable in `ui/components/` is used at every display surface; it falls back to the existing `UserAvatar` initials circle when no URL is set, so existing users see no change.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform, MVI (State/Action/Event sealed classes, Root/Screen composable split), `Result<T, E>` for typed failures, Koin DI, Firebase via gitlive SDK, Coil v3 (`SubcomposeAsyncImage` + `LoadingDots`), Peekaboo image picker, JUnit5 + Turbine + UnconfinedTestDispatcher for ViewModel tests.

**Spec:** `docs/superpowers/specs/2026-05-26-ptsp-21-brand-logo-onboarding-design.md`
**Branch:** local `worktree-feature+ptsp-21-brand-logo-onboarding`; remote target `feature/ptsp-21-brand-logo-onboarding`

---

## File structure

### Create

- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoError.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidator.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/presentation/BrandLogoErrorMapping.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/presentation/LogoUploadState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/BrandLogo.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidatorTest.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelLogoTest.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileViewModelLogoTest.kt`

### Modify

- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupAction.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/UserAvatar.kt` (only if BrandLogo needs to reuse parts of it; see Task 10)
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/ProfileHeroCard.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileAction.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileEvent.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileScreen.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt`
- `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt`
- `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`
- (call sites that build `ReceiptData` — found via grep in Task 16)
- `composeApp/src/commonMain/composeResources/values/strings.xml`

---

## Task 1: Add `businessLogoUrl` + `businessLogoStoragePath` to the `User` domain model

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt`

- [ ] **Step 1: Add the two nullable fields**

```kotlin
package com.danzucker.stitchpad.core.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val businessName: String?,
    val phoneNumber: String?,
    val whatsappNumber: String?,
    val avatarColorIndex: Int,
    val bonusCoins: Int? = null,
    /** Resolved Firebase Storage download URL for the user's brand logo. Null = no logo set. */
    val businessLogoUrl: String? = null,
    /** Firebase Storage path for the logo. Used for deletion and replacement. Null = no logo set. */
    val businessLogoStoragePath: String? = null,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL (the new fields default to null, no call site needs updating yet).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt
git commit -m "feat(user): add businessLogoUrl + storage path to User domain (PTSP-21)"
```

---

## Task 2: Add the two fields to `UserDto` + mapper

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt`

- [ ] **Step 1: Add DTO fields**

Append the two fields to `UserDto`. Keep the existing field order/comments intact; only add at the end:

```kotlin
@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val businessName: String? = null,
    @SerialName("phone")
    val phoneNumber: String? = null,
    @SerialName("whatsapp")
    val whatsappNumber: String? = null,
    @SerialName("whatsappNumber")
    val legacyWhatsappNumber: String? = null,
    val avatarColorIndex: Int = 0,
    val bonusCoins: Int? = null,
    @SerialName("businessLogoUrl")
    val businessLogoUrl: String? = null,
    @SerialName("businessLogoStoragePath")
    val businessLogoStoragePath: String? = null,
)
```

- [ ] **Step 2: Update mapper — both directions**

```kotlin
fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    whatsappNumber = whatsappNumber ?: legacyWhatsappNumber,
    avatarColorIndex = avatarColorIndex,
    bonusCoins = bonusCoins,
    businessLogoUrl = businessLogoUrl,
    businessLogoStoragePath = businessLogoStoragePath,
)

fun User.toUserDto(): UserDto = UserDto(
    id = id,
    email = email,
    displayName = displayName,
    businessName = businessName,
    phoneNumber = phoneNumber,
    whatsappNumber = whatsappNumber,
    avatarColorIndex = avatarColorIndex,
    bonusCoins = bonusCoins,
    businessLogoUrl = businessLogoUrl,
    businessLogoStoragePath = businessLogoStoragePath,
)
```

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/UserMapper.kt
git commit -m "feat(user): persist businessLogoUrl + storage path via UserDto (PTSP-21)"
```

---

## Task 3: Extend `UserRepository` interface with three logo methods

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt`

- [ ] **Step 1: Add `Result` import + three new methods**

```kotlin
package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUserProfile(
        userId: String,
        businessName: String?,
        whatsappNumber: String?,
    ): EmptyResult<DataError.Network>

    suspend fun deleteUserDoc(userId: String): EmptyResult<DataError.Network>

    suspend fun updateProfile(
        userId: String,
        businessName: String?,
        displayName: String?,
        phoneNumber: String?,
        whatsappNumber: String?,
        avatarColorIndex: Int?
    ): EmptyResult<DataError.Network>

    fun observeUser(userId: String): Flow<User?>

    /**
     * Uploads `bytes` to a deterministic Firebase Storage path (`users/{userId}/branding/logo.jpg`).
     * Overwrites any existing object at that path. Caller is responsible for invoking
     * [updateBrandLogo] afterwards to persist the returned (downloadUrl, storagePath) on the user doc.
     */
    suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network>

    /**
     * Writes both `businessLogoUrl` and `businessLogoStoragePath` to `users/{userId}`. Passing
     * (null, null) explicitly clears the logo. Uses `FieldValue.delete` for clears so the keys
     * actually drop from the Firestore document (matches the `updateProfile` pattern).
     */
    suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network>

    /**
     * Deletes the Storage object at [storagePath]. Safe to call on a non-existent object
     * (treated as success). Does NOT update the user doc — callers must coordinate with
     * [updateBrandLogo] when removing a logo permanently.
     */
    suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network>
}
```

- [ ] **Step 2: Compile (the impl will break — that's expected)**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD FAILED — `FirebaseUserRepository` doesn't override the three new methods. Move on to Task 4 to add them.

- [ ] **Step 3: Commit (interface only — impl follows next task)**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/UserRepository.kt
git commit -m "feat(user): UserRepository contract for brand logo upload/update/delete (PTSP-21)"
```

---

## Task 4: Implement the three methods in `FirebaseUserRepository`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt`

Pattern reference: `FirebaseOrderRepository.uploadStylePhoto` (lines 316–331) + path-builder convention.

- [ ] **Step 1: Add `FirebaseStorage` to the constructor + import**

Update the constructor signature (note the existing impl only injects `FirebaseFirestore`):

```kotlin
package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.data.mapper.toUser
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.style.data.toStorageData
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "UserRepo"
private const val USERS = "users"

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : UserRepository {

    private fun logoStoragePath(userId: String): String =
        "users/$userId/branding/logo.jpg"

    /* existing methods unchanged */
}
```

- [ ] **Step 2: Add `uploadUserLogo` impl (paste at the bottom of the class, above `buildInitialUserDoc`)**

```kotlin
    override suspend fun uploadUserLogo(
        userId: String,
        bytes: ByteArray,
    ): Result<Pair<String, String>, DataError.Network> {
        val path = logoStoragePath(userId)
        return try {
            storage.reference.child(path).putData(bytes.toStorageData())
            val downloadUrl = storage.reference.child(path).getDownloadUrl()
            Result.Success(downloadUrl to path)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "uploadUserLogo failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
```

- [ ] **Step 3: Add `updateBrandLogo` impl (right after `uploadUserLogo`)**

```kotlin
    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun updateBrandLogo(
        userId: String,
        logoUrl: String?,
        logoStoragePath: String?,
    ): EmptyResult<DataError.Network> {
        return try {
            val data = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp,
                // Nullable URL/path: when the user removes the logo, drop the keys
                // entirely (FieldValue.delete) so stale URLs don't survive on the doc.
                "businessLogoUrl" to (logoUrl ?: FieldValue.delete),
                "businessLogoStoragePath" to (logoStoragePath ?: FieldValue.delete),
            )
            firestore.collection(USERS).document(userId).set(data, merge = true)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "updateBrandLogo failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
```

- [ ] **Step 4: Add `deleteUserLogo` impl (right after `updateBrandLogo`)**

```kotlin
    override suspend fun deleteUserLogo(
        storagePath: String,
    ): EmptyResult<DataError.Network> {
        return try {
            storage.reference.child(storagePath).delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // A delete on a non-existent object throws; treat as success so callers can
            // fire-and-forget on Skip without surfacing a benign 404 to the user.
            AppLogger.w(tag = TAG, throwable = e) { "deleteUserLogo treated as no-op path=$storagePath" }
            Result.Success(Unit)
        }
    }
```

- [ ] **Step 5: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD FAILED — Koin DI is still constructing `FirebaseUserRepository` with one arg. Fix in next task.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt
git commit -m "feat(user): FirebaseUserRepository brand logo upload/update/delete (PTSP-21)"
```

---

## Task 5: Wire `FirebaseStorage` into Koin

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt`

`FirebaseStorage` is already injected into `FirebaseOrderRepository` elsewhere, so the binding exists somewhere in the DI graph. `singleOf(::FirebaseUserRepository)` automatically resolves both constructor params once `FirebaseStorage` is available in scope.

- [ ] **Step 1: Verify `FirebaseStorage` is already registered in the DI graph**

Run: `grep -rn "FirebaseStorage" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/`
Expected: at least one `single { Firebase.storage }` or similar binding (registered for `FirebaseOrderRepository`).

If no binding is found, add this to `AuthModule.kt` (or the existing `firebase` module) at the top of `authDataModule`:

```kotlin
single { dev.gitlive.firebase.Firebase.storage }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL — Koin's `singleOf(::FirebaseUserRepository)` now wires both `FirebaseFirestore` and `FirebaseStorage` automatically.

- [ ] **Step 3: Commit (only if a binding was added)**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/AuthModule.kt
git commit -m "feat(di): wire FirebaseStorage into UserRepository (PTSP-21)"
```

If no DI change was needed, skip the commit.

---

## Task 6: Add `BrandLogoError` + `toUiText()` mapping

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoError.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/presentation/BrandLogoErrorMapping.kt`

Pattern reference: `feature/auth/presentation/AuthErrorMapping.kt`.

- [ ] **Step 1: Create the error type**

```kotlin
package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Error

sealed interface BrandLogoError : Error {
    data object TooLarge : BrandLogoError
    data object UnsupportedFormat : BrandLogoError
    data class Network(val cause: DataError.Network) : BrandLogoError
}
```

- [ ] **Step 2: Create the `toUiText()` mapping**

```kotlin
package com.danzucker.stitchpad.feature.branding.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.workshop_logo_invalid_format
import stitchpad.composeapp.generated.resources.workshop_logo_too_large
import stitchpad.composeapp.generated.resources.workshop_logo_upload_failed

fun BrandLogoError.toUiText(): UiText = when (this) {
    BrandLogoError.TooLarge -> UiText.StringResourceText(Res.string.workshop_logo_too_large)
    BrandLogoError.UnsupportedFormat -> UiText.StringResourceText(Res.string.workshop_logo_invalid_format)
    is BrandLogoError.Network -> when (cause) {
        com.danzucker.stitchpad.core.domain.error.DataError.Network.NO_INTERNET ->
            UiText.StringResourceText(Res.string.error_no_internet)
        else -> UiText.StringResourceText(Res.string.workshop_logo_upload_failed)
    }
}
```

- [ ] **Step 3: Commit (build will fail because string IDs don't exist yet — that's fine; Task 7 adds them)**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/
git commit -m "feat(branding): BrandLogoError + UiText mapping (PTSP-21)"
```

---

## Task 7: Add the new strings + update the helper

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Update the existing helper text (V1 is raster only)**

Find the line `<string name="workshop_logo_upload_sub">PNG, JPG or SVG · Max 2MB</string>` and replace with:

```xml
<string name="workshop_logo_upload_sub">PNG or JPG · Max 2MB</string>
```

- [ ] **Step 2: Add new entries (paste right after the existing `workshop_logo_*` block, around line 985)**

```xml
<!-- PTSP-21 brand logo upload -->
<string name="workshop_logo_uploading">Uploading logo…</string>
<string name="workshop_logo_upload_failed">Couldn&apos;t upload logo. Retry?</string>
<string name="workshop_logo_too_large">Logo must be 2MB or smaller</string>
<string name="workshop_logo_invalid_format">Use a PNG or JPG image</string>
<string name="workshop_logo_finishing">Finishing logo upload…</string>
<string name="workshop_logo_uploaded">Logo uploaded</string>
<string name="workshop_logo_retry">Retry</string>

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

`workshop_logo_coming_soon` stays in place (deliberately unused; removed in a follow-up).

**No backslash apostrophes** — use `&apos;` (per the `feedback_strings_no_backslash_escape` memory).

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL. The `BrandLogoErrorMapping.kt` from Task 6 now resolves all referenced string IDs.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(strings): brand logo onboarding + edit profile copy (PTSP-21)"
```

---

## Task 8: Add `BrandLogoValidator` + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidator.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidatorTest.kt`

- [ ] **Step 1: Write failing tests first**

```kotlin
package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandLogoValidatorTest {

    private val validator = BrandLogoValidator()

    @Test
    fun `rejects bytes larger than 2MB`() {
        // 2MB + 1 byte of valid PNG magic followed by junk
        val tooBig = ByteArray(2 * 1024 * 1024 + 1).apply {
            this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4E; this[3] = 0x47
        }
        val result = validator.validate(tooBig)
        assertEquals(Result.Error(BrandLogoError.TooLarge), result)
    }

    @Test
    fun `rejects bytes with no PNG or JPG magic`() {
        // GIF magic: 47 49 46 38
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val result = validator.validate(gif)
        assertEquals(Result.Error(BrandLogoError.UnsupportedFormat), result)
    }

    @Test
    fun `accepts valid PNG bytes`() {
        // PNG magic: 89 50 4E 47
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            ByteArray(100)
        val result = validator.validate(png)
        assertTrue(result is Result.Success)
    }

    @Test
    fun `accepts valid JPG bytes`() {
        // JPG magic: FF D8 FF
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(100)
        val result = validator.validate(jpg)
        assertTrue(result is Result.Success)
    }

    @Test
    fun `rejects empty bytes`() {
        val result = validator.validate(ByteArray(0))
        assertEquals(Result.Error(BrandLogoError.UnsupportedFormat), result)
    }
}
```

- [ ] **Step 2: Run the tests — expect failure**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidatorTest"`
Expected: compilation failure — `BrandLogoValidator` does not exist yet.

- [ ] **Step 3: Implement the validator**

```kotlin
package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result

/**
 * Validates that a picked image is small enough to upload and is one of the formats
 * we render natively on both Android and iOS (PNG + JPG). SVG is intentionally rejected
 * in V1; coil-svg + an iOS SVG decoder would be required to render it on the receipt.
 */
class BrandLogoValidator(
    private val maxBytes: Int = MAX_BYTES,
) {
    fun validate(bytes: ByteArray): EmptyResult<BrandLogoError> {
        if (bytes.size > maxBytes) return Result.Error(BrandLogoError.TooLarge)
        if (!hasSupportedMagic(bytes)) return Result.Error(BrandLogoError.UnsupportedFormat)
        return Result.Success(Unit)
    }

    private fun hasSupportedMagic(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_MAGIC_BYTES) return false
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        val isPng = bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        // JPG: FF D8 FF
        val isJpg = bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        return isPng || isJpg
    }

    companion object {
        const val MAX_BYTES: Int = 2 * 1024 * 1024
        private const val MIN_MAGIC_BYTES = 4
    }
}
```

- [ ] **Step 4: Run tests again — expect pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidatorTest"`
Expected: 5 tests, all pass.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidator.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/branding/domain/BrandLogoValidatorTest.kt
git commit -m "feat(branding): BrandLogoValidator with TDD (PTSP-21)"
```

---

## Task 9: Add `LogoUploadState` sealed interface

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/presentation/LogoUploadState.kt`

This is shared between `WorkshopSetupState` and `EditProfileState` so the same `BrandLogoSection` composable can drive both screens.

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.branding.presentation

/**
 * Shared transient state for the brand-logo upload tile. Drives the visual treatment
 * of the picker zone (empty/icon, loading-with-preview, uploaded preview, failed).
 *
 * `Uploaded.url` is the resolved Firebase Storage download URL — safe to feed Coil.
 * `Uploaded.path` is the Storage path, needed for delete on Skip / Remove.
 *
 * `Failed` carries the picked bytes so the user can retry without re-opening the picker.
 */
sealed interface LogoUploadState {
    data object Empty : LogoUploadState
    data class Uploading(val previewBytes: ByteArray) : LogoUploadState {
        override fun equals(other: Any?): Boolean =
            other is Uploading && previewBytes.contentEquals(other.previewBytes)
        override fun hashCode(): Int = previewBytes.contentHashCode()
    }
    data class Uploaded(val url: String, val path: String) : LogoUploadState
    data class Failed(val previewBytes: ByteArray) : LogoUploadState {
        override fun equals(other: Any?): Boolean =
            other is Failed && previewBytes.contentEquals(other.previewBytes)
        override fun hashCode(): Int = previewBytes.contentHashCode()
    }
}
```

(The custom `equals`/`hashCode` overrides on `Uploading` and `Failed` are required because Kotlin's data-class default uses reference equality for `ByteArray`. Without these, MutableStateFlow's "skip identical emissions" check breaks.)

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/branding/presentation/LogoUploadState.kt
git commit -m "feat(branding): LogoUploadState shared sealed interface (PTSP-21)"
```

---

## Task 10: Create the `BrandLogo` composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/BrandLogo.kt`

`UserAvatar` (`feature/dashboard/presentation/components/UserAvatar.kt`) is the existing initials-avatar fallback. We don't modify it — `BrandLogo` simply delegates to it when no URL is set.

- [ ] **Step 1: Create the composable**

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.brand_logo_content_description

/**
 * The canonical brand-logo render. When `logoUrl` is non-null, renders the user's
 * uploaded image (with [LoadingDots] in the Coil loading slot — required per the
 * project's image-loading convention). When null, falls back to an initials circle
 * that visually matches the legacy `UserAvatar`, so existing users see no change.
 */
@Composable
fun BrandLogo(
    logoUrl: String?,
    fallbackInitials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val description = stringResource(Res.string.brand_logo_content_description)
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    val initial = fallbackInitials.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { LoadingDots(dotSize = (size.value / 8f).dp.coerceAtLeast(4.dp)) },
                error = {
                    InitialsFallback(initial, textColor, size)
                },
            )
        } else {
            InitialsFallback(initial, textColor, size)
        }
    }
}

@Composable
private fun InitialsFallback(initial: String, textColor: Color, size: Dp) {
    Text(
        text = initial,
        color = textColor,
        fontSize = (size.value * 0.4f).sp,
        fontWeight = FontWeight.Bold,
    )
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun BrandLogoFallbackPreview() {
    StitchPadTheme {
        BrandLogo(logoUrl = null, fallbackInitials = "Esther", size = 56.dp)
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/BrandLogo.kt
git commit -m "feat(ui): BrandLogo composable with initials fallback (PTSP-21)"
```

---

## Task 11: Extend `WorkshopSetupState` + Action + Event

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt`

- [ ] **Step 1: Update state**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import org.jetbrains.compose.resources.StringResource

data class WorkshopSetupState(
    val businessName: String = "",
    val whatsappNumber: String = "",
    val isLoading: Boolean = false,
    val businessNameError: StringResource? = null,
    val whatsappError: StringResource? = null,
    val logo: LogoUploadState = LogoUploadState.Empty,
    /** True between Continue tap and in-flight upload completion. UI shows "Finishing logo upload…". */
    val isAwaitingLogo: Boolean = false,
)
```

- [ ] **Step 2: Update actions — remove `OnLogoUploadClick`, add picker + retry**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

sealed interface WorkshopSetupAction {
    data class OnBusinessNameChange(val name: String) : WorkshopSetupAction
    data class OnWhatsAppNumberChange(val raw: String) : WorkshopSetupAction
    data object OnBusinessNameBlur : WorkshopSetupAction
    data object OnWhatsAppNumberBlur : WorkshopSetupAction
    data object OnContinueClick : WorkshopSetupAction
    data object OnSkipClick : WorkshopSetupAction
    data class OnLogoPicked(val bytes: ByteArray) : WorkshopSetupAction
    data object OnLogoRetry : WorkshopSetupAction
}
```

- [ ] **Step 3: Update events — remove `ShowComingSoon`, add `ShowSnackbar`**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface WorkshopSetupEvent {
    data object NavigateToHome : WorkshopSetupEvent
    data object NavigateToLogin : WorkshopSetupEvent
    data class ShowError(val message: UiText) : WorkshopSetupEvent
    data class ShowSnackbar(val message: UiText) : WorkshopSetupEvent
}
```

- [ ] **Step 4: Compile (ViewModel + Screen will still reference `OnLogoUploadClick` / `ShowComingSoon` — expected; Task 12 + 13 fix them)**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD FAILED with unresolved references in `WorkshopSetupViewModel.kt` + `WorkshopSetupScreen.kt`. Move on.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupState.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupAction.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupEvent.kt
git commit -m "feat(onboarding): WorkshopSetup state/action/event for logo upload (PTSP-21)"
```

---

## Task 12: Write `WorkshopSetupViewModel` logo tests (TDD)

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelLogoTest.kt`

You'll likely need a `FakeUserRepository`. If one already exists in `commonTest`, reuse it; otherwise build a minimal in-test fake (see Step 1).

- [ ] **Step 1: Write the failing tests (pattern: JUnit5 + Turbine + UnconfinedTestDispatcher)**

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelLogoTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun validPngBytes() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(100)

    @Test
    fun `picking valid logo transitions Empty -- Uploading -- Uploaded`() = runTest {
        val repo = FakeUserRepository(
            uploadResult = Result.Success("https://x/logo.jpg" to "users/u1/branding/logo.jpg"),
        )
        val vm = WorkshopSetupViewModel(
            userRepository = repo,
            authRepository = FakeAuthRepository(userId = "u1"),
            onboardingPreferences = FakeOnboardingPreferences(),
        )

        vm.state.test {
            assertEquals(LogoUploadState.Empty, awaitItem().logo)
            vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
            val uploading = awaitItem().logo
            assertIs<LogoUploadState.Uploading>(uploading)
            val uploaded = awaitItem().logo
            assertIs<LogoUploadState.Uploaded>(uploaded)
            assertEquals("https://x/logo.jpg", uploaded.url)
        }
    }

    @Test
    fun `picking oversize bytes emits TooLarge snackbar and stays Empty`() = runTest {
        val repo = FakeUserRepository()
        val vm = WorkshopSetupViewModel(repo, FakeAuthRepository(userId = "u1"), FakeOnboardingPreferences())

        vm.events.test { /* drain events */
            vm.onAction(WorkshopSetupAction.OnLogoPicked(ByteArray(3 * 1024 * 1024)))
            val event = awaitItem()
            assertIs<WorkshopSetupEvent.ShowSnackbar>(event)
        }
        assertEquals(LogoUploadState.Empty, vm.state.value.logo)
    }

    @Test
    fun `upload failure transitions to Failed and emits retry snackbar`() = runTest {
        val repo = FakeUserRepository(uploadResult = Result.Error(DataError.Network.UNKNOWN))
        val vm = WorkshopSetupViewModel(repo, FakeAuthRepository(userId = "u1"), FakeOnboardingPreferences())

        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        assertIs<LogoUploadState.Failed>(vm.state.value.logo)
    }

    @Test
    fun `Skip after Uploaded deletes the Storage object`() = runTest {
        val repo = FakeUserRepository(uploadResult = Result.Success("u" to "users/u1/branding/logo.jpg"))
        val vm = WorkshopSetupViewModel(repo, FakeAuthRepository(userId = "u1"), FakeOnboardingPreferences())

        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        vm.onAction(WorkshopSetupAction.OnSkipClick)

        assertTrue("users/u1/branding/logo.jpg" in repo.deletedPaths)
    }

    @Test
    fun `Continue while Uploaded writes URL via updateBrandLogo`() = runTest {
        val repo = FakeUserRepository(uploadResult = Result.Success("https://x" to "users/u1/branding/logo.jpg"))
        val vm = WorkshopSetupViewModel(repo, FakeAuthRepository(userId = "u1"), FakeOnboardingPreferences())

        vm.onAction(WorkshopSetupAction.OnBusinessNameChange("Esther"))
        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        vm.onAction(WorkshopSetupAction.OnContinueClick)

        assertEquals("https://x" to "users/u1/branding/logo.jpg", repo.lastBrandLogoUpdate)
    }
}

/** Minimal fake — extend in-place if any test needs more behaviour. */
private class FakeUserRepository(
    var uploadResult: Result<Pair<String, String>, DataError.Network> = Result.Error(DataError.Network.UNKNOWN),
) : UserRepository {
    val deletedPaths: MutableList<String> = mutableListOf()
    var lastBrandLogoUpdate: Pair<String?, String?>? = null

    override suspend fun createUserProfile(userId: String, businessName: String?, whatsappNumber: String?): EmptyResult<DataError.Network> = Result.Success(Unit)
    override suspend fun deleteUserDoc(userId: String) = Result.Success<Unit, DataError.Network>(Unit)
    override suspend fun updateProfile(userId: String, businessName: String?, displayName: String?, phoneNumber: String?, whatsappNumber: String?, avatarColorIndex: Int?) = Result.Success<Unit, DataError.Network>(Unit)
    override fun observeUser(userId: String): Flow<User?> = flowOf(null)
    override suspend fun uploadUserLogo(userId: String, bytes: ByteArray) = uploadResult
    override suspend fun updateBrandLogo(userId: String, logoUrl: String?, logoStoragePath: String?): EmptyResult<DataError.Network> {
        lastBrandLogoUpdate = logoUrl to logoStoragePath
        return Result.Success(Unit)
    }
    override suspend fun deleteUserLogo(storagePath: String): EmptyResult<DataError.Network> {
        deletedPaths += storagePath
        return Result.Success(Unit)
    }
}
```

You'll also need a `FakeAuthRepository` (returning a User with `id = "u1"` from `getCurrentUser()`) and a `FakeOnboardingPreferences`. If similar fakes exist in `commonTest`, reuse them; otherwise create local stubs in this test file.

- [ ] **Step 2: Run — expect failure**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModelLogoTest"`
Expected: compilation failure — `OnLogoPicked`, `Empty`, `Uploading`, `Uploaded`, `Failed`, `uploadUserLogo` references in the VM don't exist yet.

- [ ] **Step 3: Commit (tests-only, currently failing)**

```bash
git add composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModelLogoTest.kt
git commit -m "test(onboarding): WorkshopSetup logo upload TDD scaffolding (PTSP-21)"
```

---

## Task 13: Implement the logo flow in `WorkshopSetupViewModel`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt`

- [ ] **Step 1: Replace the file body with the extended version**

Key changes:
1. Add `BrandLogoValidator` (default instance) + a tracked `logoUploadJob`.
2. Replace `OnLogoUploadClick` branch with `OnLogoPicked` + `OnLogoRetry`.
3. Implement `onLogoPicked()`, `onLogoRetry()`.
4. Extend `onContinue()` to await an in-flight job, then call `updateBrandLogo` after `createUserProfile`.
5. Extend `OnSkipClick` to cancel any in-flight upload and delete any uploaded object.

```kotlin
package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.applyImpliedNigerianCountryCode
import com.danzucker.stitchpad.core.sharing.normaliseNigerianPhone
import com.danzucker.stitchpad.core.sharing.validateNigerianMobileE164
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoError
import com.danzucker.stitchpad.feature.branding.domain.BrandLogoValidator
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.feature.branding.presentation.toUiText
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_session_expired
import stitchpad.composeapp.generated.resources.error_unknown
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid
import stitchpad.composeapp.generated.resources.workshop_logo_uploaded

class WorkshopSetupViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val logoValidator: BrandLogoValidator = BrandLogoValidator(),
) : ViewModel() {

    private val _state = MutableStateFlow(WorkshopSetupState())
    val state = _state.asStateFlow()

    private val _events = Channel<WorkshopSetupEvent>()
    val events = _events.receiveAsFlow()

    private var logoUploadJob: Job? = null

    fun onAction(action: WorkshopSetupAction) {
        when (action) {
            is WorkshopSetupAction.OnBusinessNameChange ->
                _state.update { it.copy(businessName = action.name, businessNameError = null) }
            is WorkshopSetupAction.OnWhatsAppNumberChange ->
                _state.update { it.copy(whatsappNumber = capWhatsAppDigits(action.raw), whatsappError = null) }
            WorkshopSetupAction.OnBusinessNameBlur ->
                if (_state.value.businessName.isNotBlank()) validateBusinessName()
            WorkshopSetupAction.OnWhatsAppNumberBlur ->
                if (_state.value.whatsappNumber.isNotBlank()) validateWhatsAppNumber()
            WorkshopSetupAction.OnContinueClick -> onContinue()
            WorkshopSetupAction.OnSkipClick -> onSkip()
            is WorkshopSetupAction.OnLogoPicked -> onLogoPicked(action.bytes)
            WorkshopSetupAction.OnLogoRetry -> onLogoRetry()
        }
    }

    private fun onLogoPicked(bytes: ByteArray) {
        when (val validation = logoValidator.validate(bytes)) {
            is Result.Error -> {
                viewModelScope.launch {
                    _events.send(WorkshopSetupEvent.ShowSnackbar(validation.error.toUiText()))
                }
                return
            }
            is Result.Success -> Unit
        }
        logoUploadJob?.cancel()
        _state.update { it.copy(logo = LogoUploadState.Uploading(bytes)) }
        logoUploadJob = viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                return@launch
            }
            when (val result = userRepository.uploadUserLogo(userId, bytes)) {
                is Result.Success -> _state.update {
                    it.copy(logo = LogoUploadState.Uploaded(result.data.first, result.data.second))
                }
                is Result.Error -> {
                    _state.update { it.copy(logo = LogoUploadState.Failed(bytes)) }
                    _events.send(WorkshopSetupEvent.ShowSnackbar(BrandLogoError.Network(result.error).toUiText()))
                }
            }
        }
    }

    private fun onLogoRetry() {
        val failed = _state.value.logo as? LogoUploadState.Failed ?: return
        onLogoPicked(failed.previewBytes)
    }

    private fun onSkip() {
        val current = _state.value.logo
        logoUploadJob?.cancel()
        when (current) {
            is LogoUploadState.Uploaded -> viewModelScope.launch { userRepository.deleteUserLogo(current.path) }
            is LogoUploadState.Uploading,
            is LogoUploadState.Failed -> viewModelScope.launch {
                userRepository.deleteUserLogo("users/${authRepository.getCurrentUser()?.id}/branding/logo.jpg")
            }
            LogoUploadState.Empty -> Unit
        }
        viewModelScope.launch {
            onboardingPreferences.setWorkshopSetupCompleted()
            _events.send(WorkshopSetupEvent.NavigateToHome)
        }
    }

    private fun validateBusinessName(): Boolean {
        val name = _state.value.businessName.trim()
        if (name.length < MIN_BUSINESS_NAME_LEN) {
            _state.update { it.copy(businessNameError = Res.string.error_business_name_too_short) }
            return false
        }
        return true
    }

    private fun validateWhatsAppNumber(): Boolean {
        val raw = _state.value.whatsappNumber.trim()
        if (raw.isBlank()) return true
        val withCountry = applyImpliedNigerianCountryCode(raw)
        return if (validateNigerianMobileE164(withCountry)) true
        else {
            _state.update { it.copy(whatsappError = Res.string.error_whatsapp_invalid) }
            false
        }
    }

    @Suppress("LongMethod")
    private fun onContinue() {
        if (!validateBusinessName() || !validateWhatsAppNumber()) return

        viewModelScope.launch {
            // If a logo upload is still in flight, await it so we can persist the URL.
            val pending = logoUploadJob
            if (pending != null && pending.isActive) {
                _state.update { it.copy(isAwaitingLogo = true) }
                pending.join()
                _state.update { it.copy(isAwaitingLogo = false) }
            }

            val s = _state.value
            _state.update { it.copy(isLoading = true) }
            try {
                val user = authRepository.getCurrentUser() ?: run {
                    _events.send(WorkshopSetupEvent.ShowError(UiText.StringResourceText(Res.string.error_session_expired)))
                    _events.send(WorkshopSetupEvent.NavigateToLogin)
                    return@launch
                }
                val whatsappE164 = s.whatsappNumber.trim().takeIf { it.isNotBlank() }
                    ?.let { "+" + normaliseNigerianPhone(applyImpliedNigerianCountryCode(it)) }

                val profileResult = userRepository.createUserProfile(
                    userId = user.id,
                    businessName = s.businessName.trim().ifBlank { null },
                    whatsappNumber = whatsappE164,
                )
                if (profileResult is Result.Error) {
                    _events.send(WorkshopSetupEvent.ShowError(UiText.StringResourceText(Res.string.error_unknown)))
                    return@launch
                }

                val logo = s.logo
                if (logo is LogoUploadState.Uploaded) {
                    val r = userRepository.updateBrandLogo(user.id, logo.url, logo.path)
                    if (r is Result.Success) {
                        _events.send(WorkshopSetupEvent.ShowSnackbar(UiText.StringResourceText(Res.string.workshop_logo_uploaded)))
                    }
                    // A failure here doesn't block onboarding — the user has a profile.
                    // We just don't surface the success snackbar; they'll set the logo from Edit Profile.
                }

                onboardingPreferences.setWorkshopSetupCompleted()
                _events.send(WorkshopSetupEvent.NavigateToHome)
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private companion object {
        const val MAX_WHATSAPP_DIGITS = 13
        const val MIN_BUSINESS_NAME_LEN = 2

        fun capWhatsAppDigits(raw: String): String = buildString {
            var digits = 0
            raw.forEach { c ->
                val isDigit = c.isDigit()
                val isAcceptedFormatting = c in "+- ()"
                val keep = when {
                    isDigit && digits < MAX_WHATSAPP_DIGITS -> true.also { digits++ }
                    isAcceptedFormatting -> true
                    else -> false
                }
                if (keep) append(c)
            }
        }
    }
}
```

- [ ] **Step 2: Run the logo tests — expect pass**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.onboarding.presentation.workshop.WorkshopSetupViewModelLogoTest"`
Expected: 5 tests passing.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupViewModel.kt
git commit -m "feat(onboarding): WorkshopSetupViewModel eager logo upload (PTSP-21)"
```

---

## Task 14: Update `WorkshopSetupScreen` — replace disabled tile with picker

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt`

- [ ] **Step 1: Update the `WorkshopSetupRoot` event handler**

Replace the `ShowComingSoon` arm with `ShowSnackbar`. Drop the `comingSoon` local. Drop the `workshop_logo_coming_soon` import.

```kotlin
ObserveAsEvents(viewModel.events) { event ->
    when (event) {
        WorkshopSetupEvent.NavigateToHome -> onNavigateToHome()
        WorkshopSetupEvent.NavigateToLogin -> onNavigateToLogin()
        is WorkshopSetupEvent.ShowError -> {
            scope.launch {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(text.id)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
        is WorkshopSetupEvent.ShowSnackbar -> {
            scope.launch {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(text.id)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
}
```

- [ ] **Step 2: Hoist a Peekaboo picker into `WorkshopSetupRoot`**

Right after the `state` collect, add:

```kotlin
val pickerScope = rememberCoroutineScope()
val logoPicker = com.preat.peekaboo.image.picker.rememberImagePickerLauncher(
    selectionMode = com.preat.peekaboo.image.picker.SelectionMode.Single,
    scope = pickerScope,
    onResult = { byteArrays ->
        byteArrays.firstOrNull()?.let {
            viewModel.onAction(WorkshopSetupAction.OnLogoPicked(it))
        }
    }
)
```

Pass `onLaunchPicker = { logoPicker.launch() }` into `WorkshopSetupScreen`.

- [ ] **Step 3: Update `WorkshopSetupScreen` signature + logo tile rendering**

Change signature:

```kotlin
fun WorkshopSetupScreen(
    state: WorkshopSetupState,
    snackbarHostState: SnackbarHostState,
    onAction: (WorkshopSetupAction) -> Unit,
    onLaunchPicker: () -> Unit,
)
```

Replace the existing logo tile `Box { /* PhotoCamera icon */ }` (lines ~298–370) with a state-driven version. The tile is now always tappable (opens the picker) and renders one of four visual treatments based on `state.logo`:

```kotlin
// 5. Logo upload tile
Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.workshop_logo_label),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F2ED)),
        )
        Text(
            text = stringResource(Res.string.workshop_logo_optional),
            style = TextStyle(fontSize = 13.sp, color = Color(0xFFA8A49D)),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .border(1.5.dp, Color(0xFF3A3731), RoundedCornerShape(10.dp))
            .background(Color(0xFF1F1D1A), RoundedCornerShape(10.dp))
            .clickable {
                val logo = state.logo
                if (logo is com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState.Failed) {
                    onAction(WorkshopSetupAction.OnLogoRetry)
                } else {
                    onLaunchPicker()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        WorkshopLogoTileContent(state.logo)
    }

    if (state.isAwaitingLogo) {
        Text(
            text = stringResource(Res.string.workshop_logo_finishing),
            style = TextStyle(fontSize = 12.sp, color = Color(0xFFA8A49D)),
        )
    }
}
```

Add a sibling private composable:

```kotlin
@Composable
private fun WorkshopLogoTileContent(
    logo: com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState,
) {
    when (logo) {
        com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState.Empty -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(LocalStitchPadColors.current.brandAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = LocalStitchPadColors.current.brandAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_title),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F2ED)),
                )
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_sub),
                    style = TextStyle(fontSize = 11.5.sp, color = Color(0xFFA8A49D)),
                )
            }
        }
        is com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState.Uploading -> {
            Box(contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = logo.previewBytes,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    loading = { LoadingDots() },
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { LoadingDots(color = Color.White) }
            }
        }
        is com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState.Uploaded -> {
            SubcomposeAsyncImage(
                model = logo.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                loading = { LoadingDots() },
            )
        }
        is com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState.Failed -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.workshop_logo_upload_failed),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F2ED)),
                )
                Text(
                    text = stringResource(Res.string.workshop_logo_retry),
                    style = TextStyle(fontSize = 11.5.sp, color = LocalStitchPadColors.current.brandAccent),
                )
            }
        }
    }
}
```

Add imports as needed: `coil3.compose.SubcomposeAsyncImage`, `androidx.compose.ui.layout.ContentScale`, `com.danzucker.stitchpad.ui.components.LoadingDots`.

- [ ] **Step 4: Update the two previews to pass a no-op `onLaunchPicker`**

```kotlin
WorkshopSetupScreen(
    state = WorkshopSetupState(),
    snackbarHostState = remember { SnackbarHostState() },
    onAction = {},
    onLaunchPicker = {},
)
```

Add a third preview showing `LogoUploadState.Uploading` with a 1×1 transparent PNG byte array, and a fourth for `Uploaded` with a placeholder URL (Coil will just show the loading slot in the preview — that's fine).

- [ ] **Step 5: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt
git commit -m "feat(onboarding): WorkshopSetupScreen state-driven logo tile (PTSP-21)"
```

---

## Task 15: Surface `businessLogoUrl` in `DashboardState` + swap header avatar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

- [ ] **Step 1: Add `businessLogoUrl` to `DashboardState`**

Add the field next to `businessName`:

```kotlin
val businessName: String? = null,
val businessLogoUrl: String? = null,
```

- [ ] **Step 2: Wire it into the VM observation**

Find the block (around line 340) that emits `_state.update { it.copy(uiState = ..., firstName = ..., businessName = ...) }`. Add `businessLogoUrl = user.businessLogoUrl` to the copy. (`user` is the `User` already in scope.)

- [ ] **Step 3: Swap `UserAvatar` for `BrandLogo` in `DashboardHeader`**

In `DashboardScreen.kt` around line 1198 — the `Row { BellButton(); UserAvatar(name = firstName, onClick = onAvatarClick) }`. Replace `UserAvatar` with:

```kotlin
Box(
    modifier = Modifier
        .size(48.dp)
        .clickable(onClick = onAvatarClick)
        .semantics { contentDescription = stringResource(Res.string.cd_open_settings) },
    contentAlignment = Alignment.Center,
) {
    BrandLogo(
        logoUrl = businessLogoUrl,
        fallbackInitials = firstName,
        size = 36.dp,
    )
}
```

Add `businessLogoUrl: String?` to the `DashboardHeader` parameter list, alongside `firstName`. At the call site of `DashboardHeader`, pass `businessLogoUrl = state.businessLogoUrl`.

- [ ] **Step 4: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/
git commit -m "feat(dashboard): brand logo in header when set (PTSP-21)"
```

---

## Task 16: Swap the avatar in `ProfileHeroCard` for `BrandLogo`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/ProfileHeroCard.kt`

- [ ] **Step 1: Read the file's current avatar render**

Open `ProfileHeroCard.kt` and locate the 56dp initials circle render (~lines 62–169). It currently looks like a manual `Box { Text(initial) }` block.

- [ ] **Step 2: Add a `logoUrl: String?` parameter**

Add to the `ProfileHeroCard` composable signature:

```kotlin
@Composable
fun ProfileHeroCard(
    businessName: String,
    logoUrl: String?,
    /* existing params */
)
```

- [ ] **Step 3: Replace the manual initials Box with `BrandLogo`**

```kotlin
BrandLogo(
    logoUrl = logoUrl,
    fallbackInitials = businessName,
    size = 56.dp,
)
```

Add `import com.danzucker.stitchpad.ui.components.BrandLogo`.

- [ ] **Step 4: Update the call site (likely `SettingsScreen.kt`)**

Find every call to `ProfileHeroCard(...)` and pass `logoUrl = state.businessLogoUrl`. If `SettingsState` doesn't have that field yet, add it and populate from the same user observation flow already feeding `businessName`.

- [ ] **Step 5: Compile**

Run: `./gradlew :composeApp:compileKotlinMetadata -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/
git commit -m "feat(settings): brand logo in profile hero card (PTSP-21)"
```

---

## Task 17: Edit Profile — add the logo section (change/remove)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileViewModelLogoTest.kt`

- [ ] **Step 1: State extension**

Add to `EditProfileState`:

```kotlin
val logo: LogoUploadState = LogoUploadState.Empty,
val originalLogoUrl: String? = null,
val originalLogoStoragePath: String? = null,
val showRemoveLogoDialog: Boolean = false,
```

Import `com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState`.

- [ ] **Step 2: Action additions**

```kotlin
data class OnLogoPicked(val bytes: ByteArray) : EditProfileAction
data object OnLogoRemoveClick : EditProfileAction
data object OnLogoRemoveConfirm : EditProfileAction
data object OnLogoRemoveDismiss : EditProfileAction
```

- [ ] **Step 3: Event additions**

```kotlin
// Reuse the existing ShowSnackbar event if present; otherwise add it.
data class ShowSnackbar(val message: UiText) : EditProfileEvent
```

- [ ] **Step 4: VM logic**

In `EditProfileViewModel`:

1. In the initial load (`loadCurrentProfile()`), populate `originalLogoUrl` + `originalLogoStoragePath` from the observed `User`. If `businessLogoUrl != null`, set `state.logo = LogoUploadState.Uploaded(url, path)` so the section renders with the existing logo on first paint.

2. Add a `BrandLogoValidator` instance + `logoUploadJob: Job?`.

3. `onLogoPicked(bytes)`: same shape as `WorkshopSetupViewModel.onLogoPicked`, **plus** on Uploaded transition, call `userRepository.updateBrandLogo(uid, url, path)` and emit `ShowSnackbar(workshop_logo_uploaded)` (or `edit_profile_logo_updated`). No need to delete a previous path — the deterministic path means the new upload already overwrote it.

4. `OnLogoRemoveClick` → `state.copy(showRemoveLogoDialog = true)`.

5. `OnLogoRemoveDismiss` → `state.copy(showRemoveLogoDialog = false)`.

6. `OnLogoRemoveConfirm`:
   ```kotlin
   _state.update { it.copy(showRemoveLogoDialog = false) }
   viewModelScope.launch {
       val uid = authRepository.getCurrentUser()?.id ?: return@launch
       val pathToDelete = _state.value.let { it.originalLogoStoragePath ?: (it.logo as? LogoUploadState.Uploaded)?.path }
       userRepository.updateBrandLogo(uid, null, null)
       pathToDelete?.let { userRepository.deleteUserLogo(it) }
       _state.update { it.copy(logo = LogoUploadState.Empty, originalLogoUrl = null, originalLogoStoragePath = null) }
       _events.send(EditProfileEvent.ShowSnackbar(UiText.StringResourceText(Res.string.edit_profile_logo_removed)))
   }
   ```

- [ ] **Step 5: Write the EditProfileViewModelLogoTest**

```kotlin
package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.repository.UserRepository
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelLogoTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun validPngBytes() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(100)

    private fun buildVm(repo: FakeUserRepository): EditProfileViewModel =
        EditProfileViewModel(
            userRepository = repo,
            authRepository = FakeAuthRepository(userId = "u1"),
            // Add any other constructor dependencies the real VM requires;
            // mirror the actual constructor signature when this test is written.
        )

    @Test
    fun `picking valid logo invokes updateBrandLogo with new url and path`() = runTest {
        val repo = FakeUserRepository(
            uploadResult = Result.Success("https://x/logo.jpg" to "users/u1/branding/logo.jpg"),
        )
        val vm = buildVm(repo)

        vm.onAction(EditProfileAction.OnLogoPicked(validPngBytes()))

        assertEquals("https://x/logo.jpg" to "users/u1/branding/logo.jpg", repo.lastBrandLogoUpdate)
        val logo = vm.state.value.logo
        assertIs<LogoUploadState.Uploaded>(logo)
        assertEquals("https://x/logo.jpg", logo.url)
    }

    @Test
    fun `OnLogoRemoveClick opens confirmation dialog without touching logo state`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)

        vm.onAction(EditProfileAction.OnLogoRemoveClick)

        assertTrue(vm.state.value.showRemoveLogoDialog)
        assertEquals(LogoUploadState.Empty, vm.state.value.logo)
        assertEquals(null, repo.lastBrandLogoUpdate)
        assertTrue(repo.deletedPaths.isEmpty())
    }

    @Test
    fun `OnLogoRemoveDismiss closes dialog without clearing the logo`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)

        vm.onAction(EditProfileAction.OnLogoRemoveClick)
        vm.onAction(EditProfileAction.OnLogoRemoveDismiss)

        assertFalse(vm.state.value.showRemoveLogoDialog)
        assertEquals(null, repo.lastBrandLogoUpdate)
        assertTrue(repo.deletedPaths.isEmpty())
    }

    @Test
    fun `OnLogoRemoveConfirm clears logo state and deletes storage object`() = runTest {
        // Seed the VM as if a logo was already loaded from the user doc.
        val repo = FakeUserRepository(
            seededUser = User(
                id = "u1", email = "u@x", displayName = "U", businessName = "Esther",
                phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
                businessLogoUrl = "https://x/logo.jpg",
                businessLogoStoragePath = "users/u1/branding/logo.jpg",
            ),
        )
        val vm = buildVm(repo)
        // Allow the initial load to populate state.originalLogo*
        kotlinx.coroutines.test.runCurrent()

        vm.onAction(EditProfileAction.OnLogoRemoveClick)
        vm.onAction(EditProfileAction.OnLogoRemoveConfirm)

        assertFalse(vm.state.value.showRemoveLogoDialog)
        assertEquals(LogoUploadState.Empty, vm.state.value.logo)
        assertEquals(null to null, repo.lastBrandLogoUpdate)
        assertTrue("users/u1/branding/logo.jpg" in repo.deletedPaths)
    }
}

/** Local copy of the fake — extract to shared commonTest utilities if it appears in a third test. */
private class FakeUserRepository(
    var uploadResult: Result<Pair<String, String>, DataError.Network> = Result.Error(DataError.Network.UNKNOWN),
    private val seededUser: User? = null,
) : UserRepository {
    val deletedPaths: MutableList<String> = mutableListOf()
    var lastBrandLogoUpdate: Pair<String?, String?>? = null

    override suspend fun createUserProfile(userId: String, businessName: String?, whatsappNumber: String?): EmptyResult<DataError.Network> = Result.Success(Unit)
    override suspend fun deleteUserDoc(userId: String) = Result.Success<Unit, DataError.Network>(Unit)
    override suspend fun updateProfile(userId: String, businessName: String?, displayName: String?, phoneNumber: String?, whatsappNumber: String?, avatarColorIndex: Int?) = Result.Success<Unit, DataError.Network>(Unit)
    override fun observeUser(userId: String): Flow<User?> = flowOf(seededUser)
    override suspend fun uploadUserLogo(userId: String, bytes: ByteArray) = uploadResult
    override suspend fun updateBrandLogo(userId: String, logoUrl: String?, logoStoragePath: String?): EmptyResult<DataError.Network> {
        lastBrandLogoUpdate = logoUrl to logoStoragePath
        return Result.Success(Unit)
    }
    override suspend fun deleteUserLogo(storagePath: String): EmptyResult<DataError.Network> {
        deletedPaths += storagePath
        return Result.Success(Unit)
    }
}
```

`FakeAuthRepository(userId = "u1")` returns a minimal `User` from `getCurrentUser()` — define it locally if a shared one doesn't exist (~5 lines following the `AuthRepository` interface).

- [ ] **Step 6: Screen — add the section**

In `EditProfileScreen`, above the Business name field, add:

```kotlin
BrandLogoSection(
    logo = state.logo,
    fallbackInitials = state.businessName,
    onChangeClick = { logoPicker.launch() },
    onRemoveClick = { onAction(EditProfileAction.OnLogoRemoveClick) },
)
```

where `BrandLogoSection` is a new private composable in the same file:

```kotlin
@Composable
private fun BrandLogoSection(
    logo: LogoUploadState,
    fallbackInitials: String,
    onChangeClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.edit_profile_logo_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val logoUrl = (logo as? LogoUploadState.Uploaded)?.url
            BrandLogo(logoUrl = logoUrl, fallbackInitials = fallbackInitials, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChangeClick) {
                    Text(stringResource(Res.string.edit_profile_logo_change))
                }
                if (logo is LogoUploadState.Uploaded) {
                    TextButton(onClick = onRemoveClick) {
                        Text(
                            text = stringResource(Res.string.edit_profile_logo_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
```

Add the destructive confirmation dialog at the top of the Screen (rendered when `state.showRemoveLogoDialog`):

```kotlin
if (state.showRemoveLogoDialog) {
    AlertDialog(
        onDismissRequest = { onAction(EditProfileAction.OnLogoRemoveDismiss) },
        title = { Text(stringResource(Res.string.edit_profile_logo_remove_confirm_title)) },
        text = { Text(stringResource(Res.string.edit_profile_logo_remove_confirm_body)) },
        confirmButton = {
            TextButton(onClick = { onAction(EditProfileAction.OnLogoRemoveConfirm) }) {
                Text(
                    text = stringResource(Res.string.edit_profile_logo_remove_confirm_cta),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(EditProfileAction.OnLogoRemoveDismiss) }) {
                Text(stringResource(Res.string.cancel)) // reuse existing 'cancel' string
            }
        },
    )
}
```

(Find the existing `cancel` string in `strings.xml`; if it doesn't exist as a top-level resource, look for `dialog_cancel` or similar. Use whatever the project's existing dialog "Cancel" string is. As a fallback, add `<string name="cancel">Cancel</string>`.)

- [ ] **Step 7: Hoist the Peekaboo picker into `EditProfileRoot`** (same pattern as Task 14, Step 2).

- [ ] **Step 8: Run the EditProfile tests**

Run: `./gradlew :composeApp:jvmTest --tests "com.danzucker.stitchpad.feature.settings.presentation.editprofile.EditProfileViewModelLogoTest"`
Expected: all pass.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/ composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/
git commit -m "feat(settings): edit profile change/remove brand logo (PTSP-21)"
```

---

## Task 18: Add logo to `ReceiptData` + thread bytes through the call sites

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt`
- Modify: (call sites — grep `ReceiptData(` to find them)

- [ ] **Step 1: Add `businessLogoBytes` to `ReceiptData`**

```kotlin
data class ReceiptData(
    val businessName: String,
    val businessPhone: String?,
    val documentTypeLabel: String,
    val customerName: String,
    val dateFormatted: String,
    val items: List<ReceiptItem>,
    val totalFormatted: String,
    val depositFormatted: String,
    val balanceFormatted: String,
    val isFullyPaid: Boolean,
    val statusLabel: String,
    val statusColorHex: String,
    val deadlineFormatted: String?,
    val priorityLabel: String?,
    val orderIdShort: String,
    val attribution: String?,
    /**
     * Pre-decoded PNG bytes of the user's brand logo, or `null` if none set.
     * Pre-decoded because both renderers draw synchronously and can't await Coil.
     * Decoded via Coil + Bitmap.compress(PNG) on Android / UIImage.pngData() on iOS
     * at the share-trigger call site.
     */
    val businessLogoBytes: ByteArray?,
)
```

- [ ] **Step 2: Find every call site**

Run: `grep -rn "ReceiptData(" composeApp/src/`
Expected: at least one — likely in `OrderDetailViewModel.kt` and its tests.

- [ ] **Step 3: For each call site, fetch + pass the bytes**

In the production call site (likely something like `OrderDetailViewModel.buildReceipt()`), inject a way to fetch the logo bytes. Inject `ImageLoader` (Coil v3) via Koin, then:

```kotlin
private suspend fun fetchLogoBytes(url: String?): ByteArray? {
    if (url == null) return null
    val request = coil3.request.ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
        .data(url)
        .build()
    val result = (imageLoader.execute(request) as? coil3.request.SuccessResult)?.image ?: return null
    // Convert the platform-native image to PNG bytes via a small expect/actual.
    return result.toPngBytes()
}
```

You will need to add a tiny expect/actual:

```kotlin
// commonMain
expect fun coil3.Image.toPngBytes(): ByteArray?

// androidMain: convert to AndroidBitmapImage -> Bitmap.compress(PNG)
// iosMain: convert via UIImagePNGRepresentation
```

Add `imageLoader: ImageLoader` to the `OrderDetailViewModel` constructor and wire in Koin (`get()` will resolve the singleton already registered for Coil).

Pass `businessLogoBytes = fetchLogoBytes(currentUser.businessLogoUrl)` when constructing `ReceiptData`.

In test call sites, pass `businessLogoBytes = null`.

- [ ] **Step 4: Compile both platforms**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosArm64 -q`
Expected: BUILD SUCCESSFUL on both. iOS skew (per `feedback_kmp_jvm_only_apis`) is the recurring risk — fix any iOS-only compile errors before moving on.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/sharing/ReceiptData.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/
git commit -m "feat(sharing): plumb business logo bytes through ReceiptData (PTSP-21)"
```

---

## Task 19: Android receipt — draw logo in header

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt`

- [ ] **Step 1: Decode bytes to Bitmap at the top of the dark + light renderers**

In both `renderDarkBitmap` and `renderLightBitmap` (or whatever the existing render functions are named), at the top:

```kotlin
val logoBitmap = data.businessLogoBytes?.let { bytes ->
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
```

- [ ] **Step 2: Update the header band to draw the logo on the left**

Inside the header drawing block (around line 37–60 in the existing impl), if `logoBitmap != null`, draw it at `(headerHPad, (headerHeight - 40) / 2)` clipped to a 6dp rounded square. Shift the existing `canvas.drawText(data.businessName, ...)` so that it no longer centers across the whole width but instead aligns left of the logo's right edge + a gap, or center across the remaining width to the right of the logo.

The exact draw call:

```kotlin
if (logoBitmap != null) {
    val logoSize = 40f
    val logoLeft = 32f
    val logoTop = (headerHeight - logoSize) / 2f
    val logoRect = android.graphics.RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
    val clipPath = android.graphics.Path().apply { addRoundRect(logoRect, 6f, 6f, android.graphics.Path.Direction.CW) }
    canvas.save()
    canvas.clipPath(clipPath)
    canvas.drawBitmap(logoBitmap, null, logoRect, null)
    canvas.restore()
}
// Then either keep the existing centered text or shift it; pick whichever reads cleaner with the logo.
```

Apply the same treatment in the light-mode renderer.

- [ ] **Step 3: Compile + run any existing Android tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.android.kt
git commit -m "feat(sharing): render brand logo in Android receipt header (PTSP-21)"
```

---

## Task 20: iOS receipt — draw logo in header

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt`

- [ ] **Step 1: Decode bytes to `UIImage` at the top of both renderers**

```kotlin
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.useContents

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toUIImage(): UIImage? {
    if (isEmpty()) return null
    val nsData = this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
    return UIImage.imageWithData(nsData)
}

val logoImage = data.businessLogoBytes?.toUIImage()
```

- [ ] **Step 2: Draw the logo inside the existing header band**

In `renderDarkImage` (around line 84) and the light equivalent, after filling the header band:

```kotlin
if (logoImage != null) {
    val logoSize = 40.0
    val logoLeft = 32.0
    val logoTop = (headerHeight - logoSize) / 2.0
    val logoRect = platform.CoreGraphics.CGRectMake(logoLeft, logoTop, logoSize, logoSize)
    // Clip to a rounded path
    val path = platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(logoRect, 6.0)
    platform.UIKit.UIGraphicsGetCurrentContext()?.let { ctx ->
        platform.CoreGraphics.CGContextSaveGState(ctx)
        path.addClip()
        logoImage.drawInRect(logoRect)
        platform.CoreGraphics.CGContextRestoreGState(ctx)
    }
}
```

(All references match the existing impl's import + API style.)

- [ ] **Step 3: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosArm64 -q && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: BUILD SUCCESSFUL on both. This is the iOS skew checkpoint — per the `feedback_kmp_jvm_only_apis` memory, any JVM-only stdlib call (`String.format`, `LocalDate.toEpochDays() returns Int`) will only surface here. Fix any iOS-only failures before moving on.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/sharing/OrderReceiptSharer.ios.kt
git commit -m "feat(sharing): render brand logo in iOS receipt header (PTSP-21)"
```

---

## Task 21: Run the full test suite + detekt

**Files:** none modified

- [ ] **Step 1: Run all common JVM tests**

Run: `./gradlew :composeApp:jvmTest -q`
Expected: all green. If anything else broke (e.g. a snapshot of `WorkshopSetupViewModel` behaviour now drifts), fix the cause, not the test.

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt -q`
Expected: zero violations. Fix any issues (most likely: unused imports from the screens, long-method on `WorkshopSetupViewModel.onContinue` — add `@Suppress("LongMethod")` if the threshold is exceeded for a clear reason).

- [ ] **Step 3: Commit detekt fixes if any**

```bash
git add -A
git commit -m "chore: detekt cleanup for PTSP-21"
```

---

## Task 22: iOS real-device verification

This is non-negotiable per the recurring iOS skew memories (`feedback_kmp_jvm_only_apis`, `feedback_gitlive_ios_nonnull_tokens`, `feedback_ios_modal_bottom_sheet_timing`, `feedback_kmp_native_serializer_any`, `feedback_kotlin_native_epoch_days`).

- [ ] **Step 1: Build iOS in Xcode**

Open `iosApp/iosApp.xcodeproj`, select an iPhone 17 / 17 Pro simulator OR a real iPhone, build (⌘B), then run (⌘R).
Expected: app launches without runtime crashes.

- [ ] **Step 2: Manual smoke on iPhone**

Run all 9 items from the spec's QA smoke test section (search the spec for "QA smoke"). The critical ones for iOS specifically:

- Pick a logo from the photo library, see it preview with LoadingDots, Continue, see it in the dashboard top-right.
- Pick a logo, kill wifi mid-upload, see the Retry snackbar, reconnect, retry succeeds.
- Pick a logo, Skip — confirm the logo is removed in Firebase Storage console.
- Edit Profile → Change logo → confirm dashboard + Settings hero card refresh on snapshot.
- Edit Profile → Remove → dialog appears → confirm → all surfaces revert to initials.
- Share a receipt with a logo set → logo appears on the receipt in both dark and light variants on iOS.
- Validation: try to share-extension-import a 3MB photo → snackbar `workshop_logo_too_large`. Try a GIF (if the picker allows) → `workshop_logo_invalid_format`.

- [ ] **Step 3: Note any iOS-only bugs found and fix them**

If a fix is needed, create a follow-up commit with the iOS-specific change and re-run Steps 1–2.

- [ ] **Step 4: No code change → no commit. Move on.**

---

## Task 23: Push branch and open PR

- [ ] **Step 1: Push to the conventional remote branch name**

Run:
```bash
git push -u origin worktree-feature+ptsp-21-brand-logo-onboarding:feature/ptsp-21-brand-logo-onboarding
```

- [ ] **Step 2: Open PR**

Use `gh pr create` with a title that follows project convention (recent commits show `feat(area): description (PTSP-21)`):

```bash
gh pr create --title "feat(onboarding): brand logo upload during workshop setup (PTSP-21)" --body "$(cat <<'EOF'
## Summary
- Enables logo upload on the "Set up your workshop" onboarding screen (eager, non-blocking)
- Surfaces the brand logo on the dashboard header, Settings profile card, Edit Profile (change/remove), and shared order receipts (Android + iOS)
- Adds `businessLogoUrl` + `businessLogoStoragePath` to `User` / `UserDto`; extends `UserRepository` with upload / update / delete

Design: docs/superpowers/specs/2026-05-26-ptsp-21-brand-logo-onboarding-design.md
Plan: docs/superpowers/plans/2026-05-26-ptsp-21-brand-logo-onboarding.md

## Test plan
- [ ] Fresh signup → workshop setup → pick logo → tile shows LoadingDots → preview appears → Continue → dashboard shows the logo top-right
- [ ] Fresh signup → pick logo → upload fails (kill wifi) → snackbar offers retry → reconnect → retry succeeds
- [ ] Fresh signup → pick logo → wait → Skip → logo gone from all surfaces, Storage object deleted
- [ ] Existing user with no logo → dashboard + settings + receipt all visually identical to today
- [ ] Existing user → Edit Profile → Change logo → dashboard + Settings hero card update on snapshot
- [ ] Edit Profile → Remove → confirm dialog → confirm → all surfaces revert to initials, Storage object deleted
- [ ] Share receipt with logo on Android (light + dark) and iOS (light + dark) — logo appears in header
- [ ] All of the above on a real iPhone
- [ ] Validation: 3MB file → `workshop_logo_too_large`; GIF → `workshop_logo_invalid_format`

## Reviewer notes
- Per the review-rotation memory, please run both Cursor review and `codex review` before merging.
- This PR is the FULL set of touchpoints from the design spec; out-of-scope items (order detail header, customer detail header, dashboard hero illustrations, splash) are NOT touched.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Return the PR URL.**

---

## Out of scope (must not creep in)

- Order detail header logo, Customer detail header logo, Dashboard hero illustrations, Splash / iOS LaunchScreen logo.
- SVG support.
- On-device downscaling.
- A Storage rules file in repo.
- Removing the now-unused `workshop_logo_coming_soon` string.

These are tracked in the spec's "Open follow-ups" section and should not be silently added during implementation.
