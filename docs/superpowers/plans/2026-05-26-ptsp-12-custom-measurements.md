# PTSP-12 — Custom Measurement Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let paid-tier tailors (Pro/Atelier + FREE during First Month) define their own custom measurement fields that appear in a "Custom" section of the measurement form, with rename-as-label-only and soft-archive semantics that preserve data on past measurements.

**Architecture:** New `CustomMeasurementField` domain model + dedicated Firestore subcollection (`users/{uid}/customMeasurementFields/{fieldId}`). `MeasurementFormViewModel` observes the field list and renders it as a new section below the existing paged sections. Stable UUID id + editable label keeps renames cheap; soft-archive (`isArchived: Boolean`) preserves past data. Entitlement check via one derived `UserEntitlements.canUseCustomMeasurements` field; FREE post-welcome routes to the existing `UpgradeRoute`.

**Tech Stack:** Kotlin Multiplatform (Kotlin 2.3.21), Compose Multiplatform, Koin, GitLive Firestore, kotlinx.coroutines + Flow, kotlinx.serialization, kotlin.test + Turbine-style channel reads.

**Spec:** `docs/superpowers/specs/2026-05-26-custom-measurements-design.md`
**Branch:** `feature/ptsp-12-custom-measurements` (already created off `origin/main`; spec already committed as `76046fd`).

---

## File map

**New files (production):**

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomMeasurementField.kt` | Domain model |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/CustomMeasurementFieldRepository.kt` | Repository interface |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomMeasurementFieldDto.kt` | Firestore DTO |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapper.kt` | DTO ↔ domain |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt` | Firestore impl |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/AddCustomFieldSheet.kt` | Bottom sheet composable |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/ManageCustomFieldSheet.kt` | Bottom sheet composable |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/ConfirmArchiveDialog.kt` | Alert dialog composable |

**New files (tests):**

| File | Purpose |
|---|---|
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapperTest.kt` | Mapper round-trip |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeCustomMeasurementFieldRepository.kt` | Test fake |

**Modified files:**

| File | Change |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt` | Add `canUseCustomMeasurements: Boolean` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt` | Derive `canUseCustomMeasurements` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt` | 4 new tier-coverage tests |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt` | Bind new repo + provide to VM |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt` | Add `customFields`, `canUseCustomMeasurements`, `customFieldSheet` + `CustomFieldSheet` sealed |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormAction.kt` | 5 new actions |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormEvent.kt` | `NavigateToUpgrade` event |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt` | Inject repo + entitlements; observe; sheet handlers; create/update/archive; loadMeasurement orphan fix |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt` | 9 new tests |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt` | Render Custom section + sheets + Root nav wiring |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | ~10 new strings |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` | Pass `onNavigateToUpgrade` to `MeasurementFormRoot` |
| `firestore.rules` | Allow read/write on new subcollection |

---

## Task 1: Domain model + Repository interface

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomMeasurementField.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/CustomMeasurementFieldRepository.kt`

- [ ] **Step 1: Create the domain model**

`CustomMeasurementField.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.model

data class CustomMeasurementField(
    val id: String,
    val label: String,
    val genders: Set<CustomerGender>,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 2: Create the repository interface**

`CustomMeasurementFieldRepository.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import kotlinx.coroutines.flow.Flow

interface CustomMeasurementFieldRepository {
    fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>>

    suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network>

    suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network>

    suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network>
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL — no usages yet but both files must compile.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomMeasurementField.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/CustomMeasurementFieldRepository.kt
git commit -m "feat(measurements): add CustomMeasurementField model + repository interface (PTSP-12)"
```

---

## Task 2: DTO + mapper (with round-trip test)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomMeasurementFieldDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapper.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapperTest.kt`

- [ ] **Step 1: Write the failing round-trip test**

`CustomMeasurementFieldMapperTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomMeasurementFieldMapperTest {

    @Test
    fun roundTrip_preservesAllFields() {
        val original = CustomMeasurementField(
            id = "ca3f-7b",
            label = "Sleeve cuff width",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
            isArchived = false,
            createdAt = 1_748_275_200_000L,
            updatedAt = 1_748_275_300_000L,
        )

        val dto = original.toCustomMeasurementFieldDto()
        val back = dto.toCustomMeasurementField()

        assertEquals(original.id, back.id)
        assertEquals(original.label, back.label)
        assertEquals(original.genders, back.genders)
        assertEquals(original.isArchived, back.isArchived)
        assertEquals(original.createdAt, back.createdAt)
        assertEquals(original.updatedAt, back.updatedAt)
    }

    @Test
    fun toDto_serializesGendersAsStringList() {
        val field = CustomMeasurementField(
            id = "x",
            label = "L",
            genders = setOf(CustomerGender.MALE),
            createdAt = 0L,
            updatedAt = 0L,
        )

        val dto = field.toCustomMeasurementFieldDto()

        assertEquals(listOf("MALE"), dto.genders)
    }

    @Test
    fun fromDto_unknownGenderString_isFilteredOut() {
        // Future-proofing: if a future client persists a gender we don't know,
        // older clients should ignore the unknown entry rather than crash.
        val dto = CustomMeasurementFieldDto(
            id = "x",
            label = "L",
            genders = listOf("FEMALE", "ALIEN"),
            isArchived = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

        val field = dto.toCustomMeasurementField()

        assertEquals(setOf(CustomerGender.FEMALE), field.genders)
    }

    @Test
    fun fromDto_emptyGenders_yieldsEmptySet() {
        val dto = CustomMeasurementFieldDto(
            id = "x",
            label = "L",
            genders = emptyList(),
            isArchived = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

        assertTrue(dto.toCustomMeasurementField().genders.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.data.mapper.CustomMeasurementFieldMapperTest"`
Expected: FAIL — unresolved references (`CustomMeasurementFieldDto`, `toCustomMeasurementFieldDto`, `toCustomMeasurementField`).

- [ ] **Step 3: Create the DTO**

`CustomMeasurementFieldDto.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomMeasurementFieldDto(
    val id: String = "",
    val label: String = "",
    val genders: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
```

- [ ] **Step 4: Create the mapper**

`CustomMeasurementFieldMapper.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.CustomMeasurementFieldDto
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender

fun CustomMeasurementFieldDto.toCustomMeasurementField(): CustomMeasurementField =
    CustomMeasurementField(
        id = id,
        label = label,
        genders = genders.mapNotNull { raw ->
            runCatching { CustomerGender.valueOf(raw) }.getOrNull()
        }.toSet(),
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CustomMeasurementField.toCustomMeasurementFieldDto(): CustomMeasurementFieldDto =
    CustomMeasurementFieldDto(
        id = id,
        label = label,
        genders = genders.map { it.name },
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.data.mapper.CustomMeasurementFieldMapperTest"`
Expected: PASS — all 4 tests green.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomMeasurementFieldDto.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapper.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/mapper/CustomMeasurementFieldMapperTest.kt
git commit -m "feat(measurements): add CustomMeasurementField DTO + mapper (PTSP-12)"
```

---

## Task 3: Firebase repository impl + Fake

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeCustomMeasurementFieldRepository.kt`

- [ ] **Step 1: Create the Firebase implementation**

`FirebaseCustomMeasurementFieldRepository.kt` (mirrors `FirebaseMeasurementRepository` exactly):

```kotlin
package com.danzucker.stitchpad.feature.measurement.data

import com.danzucker.stitchpad.core.data.dto.CustomMeasurementFieldDto
import com.danzucker.stitchpad.core.data.mapper.toCustomMeasurementField
import com.danzucker.stitchpad.core.data.mapper.toCustomMeasurementFieldDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private const val TAG = "CustomFieldRepo"

class FirebaseCustomMeasurementFieldRepository(
    private val firestore: FirebaseFirestore,
) : CustomMeasurementFieldRepository {

    private fun collection(userId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("customMeasurementFields")

    override fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>> =
        collection(userId)
            .snapshots()
            .map { snapshot ->
                val fields = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data<CustomMeasurementFieldDto>().toCustomMeasurementField() }.getOrNull()
                    }
                    .sortedBy { it.createdAt }
                Result.Success(fields) as Result<List<CustomMeasurementField>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeFields failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        val docRef = if (field.id.isBlank()) {
            collection(userId).document
        } else {
            collection(userId).document(field.id)
        }
        return try {
            val dto = field.toCustomMeasurementFieldDto().copy(id = docRef.id)
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createField failed fieldId=${docRef.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val dto = field.copy(updatedAt = now).toCustomMeasurementFieldDto()
            collection(userId).document(field.id).set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "updateField failed fieldId=${field.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network> {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            collection(userId).document(fieldId).set(
                mapOf("isArchived" to true, "updatedAt" to now),
                merge = true,
            )
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "archiveField failed fieldId=$fieldId"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
```

- [ ] **Step 2: Create the test fake**

`FakeCustomMeasurementFieldRepository.kt`:

```kotlin
package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeCustomMeasurementFieldRepository : CustomMeasurementFieldRepository {
    var observeError: DataError.Network? = null
    var operationError: DataError.Network? = null
    var lastCreatedField: CustomMeasurementField? = null
    var lastUpdatedField: CustomMeasurementField? = null
    var lastArchivedFieldId: String? = null

    private val _fields = MutableStateFlow<List<CustomMeasurementField>>(emptyList())

    /** Test seed helper — set the initial field list. */
    fun seedFields(fields: List<CustomMeasurementField>) {
        _fields.value = fields
    }

    override fun observeFields(
        userId: String,
    ): Flow<Result<List<CustomMeasurementField>, DataError.Network>> =
        _fields.asStateFlow().map { current ->
            observeError?.let { Result.Error(it) } ?: Result.Success(current)
        }

    override suspend fun createField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastCreatedField = field
        _fields.value = _fields.value + field
        return Result.Success(Unit)
    }

    override suspend fun updateField(
        userId: String,
        field: CustomMeasurementField,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastUpdatedField = field
        _fields.value = _fields.value.map { if (it.id == field.id) field else it }
        return Result.Success(Unit)
    }

    override suspend fun archiveField(
        userId: String,
        fieldId: String,
    ): EmptyResult<DataError.Network> {
        operationError?.let { return Result.Error(it) }
        lastArchivedFieldId = fieldId
        _fields.value = _fields.value.map {
            if (it.id == fieldId) it.copy(isArchived = true) else it
        }
        return Result.Success(Unit)
    }
}
```

- [ ] **Step 3: Compile + run all tests as a smoke check**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — no test regressions.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/data/FirebaseCustomMeasurementFieldRepository.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/repository/FakeCustomMeasurementFieldRepository.kt
git commit -m "feat(measurements): add Firebase + Fake CustomMeasurementField repos (PTSP-12)"
```

---

## Task 4: Koin DI + Firestore rules

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt`
- Modify: `firestore.rules`

- [ ] **Step 1: Wire Koin**

Replace the contents of `MeasurementModule.kt`:

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.measurement.data.FirebaseCustomMeasurementFieldRepository
import com.danzucker.stitchpad.feature.measurement.data.FirebaseMeasurementRepository
import com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val measurementDataModule = module {
    singleOf(::FirebaseMeasurementRepository) bind MeasurementRepository::class
    singleOf(::FirebaseCustomMeasurementFieldRepository) bind CustomMeasurementFieldRepository::class
}

val measurementPresentationModule = module {
    viewModelOf(::MeasurementFormViewModel)
}
```

- [ ] **Step 2: Add Firestore rule**

In `firestore.rules`, find the `match /customers/{customerId}` block (around line 99) and add the new subcollection match alongside it. The full surrounding section should look like:

```
      // ── Custom measurement fields ────────────────────────────────────────
      // PTSP-12 — Per-tailor custom field definitions. Soft-archive only;
      // no hard-delete. Client-side gate on tier; server-side feature gate
      // is deferred (see custom-measurements design doc §7).
      match /customMeasurementFields/{fieldId} {
        allow read, write: if isOwner(uid);
      }

      // ── Customer subcollection ───────────────────────────────────────────
```

(Place the new block immediately ABOVE the existing `match /customers/{customerId}` block so the read/write rule pattern stays grouped with the other subcollections.)

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt firestore.rules
git commit -m "feat(measurements): wire CustomMeasurementField repo + Firestore rule (PTSP-12)"
```

---

## Task 5: Entitlements — `canUseCustomMeasurements`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `EntitlementsCalculatorTest.kt`:

```kotlin
    // --- canUseCustomMeasurements ---

    @Test
    fun canUseCustomMeasurements_isTrue_forPro() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.PRO,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isTrue_forAtelier() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.ATELIER,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isTrue_forFreeInsideWelcomeWindow() {
        // welcomeBonusAppliedAt now → still well inside the 30-day window
        val signup = Instant.fromEpochMilliseconds(1_748_275_200_000L)
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signup,
            now = signup.plus(5.days),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertTrue(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isFalse_forFreePostWelcome() {
        val signup = Instant.fromEpochMilliseconds(1_748_275_200_000L)
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signup,
            now = signup.plus(40.days),  // welcome window has ended
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertFalse(result.canUseCustomMeasurements)
    }

    @Test
    fun canUseCustomMeasurements_isFalse_forFreeWithNoWelcome() {
        val result = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = null,
            now = Instant.fromEpochMilliseconds(1_748_275_200_000L),
            timeZone = TimeZone.of("Africa/Lagos"),
        )
        assertFalse(result.canUseCustomMeasurements)
    }
```

If `kotlin.time.Duration.Companion.days` isn't already imported in this test file, add `import kotlin.time.Duration.Companion.days` at the top.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculatorTest"`
Expected: FAIL — `canUseCustomMeasurements` is an unresolved reference.

- [ ] **Step 3: Add the field to `UserEntitlements`**

In `UserEntitlements.kt`, append to the data class:

```kotlin
data class UserEntitlements(
    val tier: SubscriptionTier,
    val customerCap: Int,
    val smartCoinAllowance: Int,
    val isInWelcomeWindow: Boolean,
    val welcomeEndsAt: Instant?,
    val isWithinWelcomeEndingWarning: Boolean,
    val welcomeDaysLeft: Int?,
    /**
     * PTSP-12 — custom measurement fields are a paid-tier feature, granted
     * to Pro/Atelier always and to FREE tailors during their First Month
     * welcome window as a taste. Reverts to gated when welcome ends.
     */
    val canUseCustomMeasurements: Boolean,
)
```

- [ ] **Step 4: Derive the value in `EntitlementsCalculator.calculate(...)`**

Replace the final `return UserEntitlements(...)` block with:

```kotlin
        val canUseCustomMeasurements = tier == SubscriptionTier.PRO ||
            tier == SubscriptionTier.ATELIER ||
            isInWelcomeWindow

        return UserEntitlements(
            tier = tier,
            customerCap = customerCap,
            smartCoinAllowance = coinAllowance,
            isInWelcomeWindow = isInWelcomeWindow,
            welcomeEndsAt = welcomeEndsAt,
            isWithinWelcomeEndingWarning = isWithinWelcomeEndingWarning,
            welcomeDaysLeft = welcomeDaysLeft,
            canUseCustomMeasurements = canUseCustomMeasurements,
        )
```

- [ ] **Step 5: Compile and update any existing call sites that construct `UserEntitlements`**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: this build will fail with "no value passed for parameter `canUseCustomMeasurements`" at every `UserEntitlements(...)` construction site outside `EntitlementsCalculator`. There are existing call sites in test fakes (e.g., the `FakeEntitlementsProvider` in `CustomerFormViewModelTest`) and possibly in the freemium debug stubs. For each one, add `canUseCustomMeasurements = false` (the conservative default for tests that don't exercise the feature).

Specifically:

1. `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt` — the inline `FakeEntitlementsProvider` constructs `UserEntitlements(...)`. Add `canUseCustomMeasurements = false`.
2. Search the repo for any other test or runtime constructions:
   ```bash
   grep -rn "UserEntitlements(" composeApp/src --include="*.kt" | grep -v "data class"
   ```
   Add `canUseCustomMeasurements = false` (or `true` for Pro/Atelier tier mocks where present) to each construction site.

Re-run compile until it succeeds:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

- [ ] **Step 6: Run all entitlement + customer-form tests to verify green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.domain.entitlement.*" --tests "com.danzucker.stitchpad.feature.customer.presentation.form.*"`
Expected: PASS — both old and new tests green.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/presentation/form/CustomerFormViewModelTest.kt
git commit -m "feat(entitlements): add canUseCustomMeasurements (Pro + Atelier + welcome) (PTSP-12)"
```

(If the grep in Step 5 turned up additional files, include them in `git add`.)

---

## Task 6: ViewModel state additions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt`

- [ ] **Step 1: Extend the state class**

Replace the contents of `MeasurementFormState.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementSection
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementFormState(
    val gender: CustomerGender? = null,
    val sections: List<MeasurementSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val isCurrentSectionExpanded: Boolean = true,
    val isNotesExpanded: Boolean = false,
    val fields: Map<String, String> = emptyMap(),
    val unit: MeasurementUnit = MeasurementUnit.INCHES,
    val notes: String = "",
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val errorMessage: UiText? = null,
    val originalCreatedAt: Long = 0L,
    val originalDateTaken: Long = 0L,
    // PTSP-12 additions
    val customFields: List<CustomMeasurementField> = emptyList(),
    val canUseCustomMeasurements: Boolean = false,
    val customFieldSheet: CustomFieldSheet? = null,
) {
    /**
     * PTSP-6: Save is gated to mirror what `MeasurementFormViewModel.save()`
     * will actually persist — at least one field that parses to a positive
     * double. The save pipeline drops empty strings, lone `.`, unparsable
     * input, and zero values, so any of those alone would silently produce
     * an empty measurement if the gate didn't agree.
     *
     * Edit-mode entries pre-populate `fields` from the existing measurement,
     * so the gate naturally allows resaves of an existing record.
     */
    val canSave: Boolean
        get() = gender != null &&
            fields.values.any { (it.toDoubleOrNull() ?: 0.0) > 0.0 } &&
            !isLoading
}

sealed interface CustomFieldSheet {
    /** "Add custom field" — empty form, no existing field. */
    data object Adding : CustomFieldSheet

    /** "Edit custom field" — pre-populated from an existing field. */
    data class Editing(val field: CustomMeasurementField) : CustomFieldSheet

    /** "Archive this field?" confirm dialog, holding the field to archive. */
    data class ConfirmArchive(val field: CustomMeasurementField) : CustomFieldSheet
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormState.kt
git commit -m "feat(measurements): add customFields + sheet state to MeasurementFormState (PTSP-12)"
```

---

## Task 7: Actions + events

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormEvent.kt`

- [ ] **Step 1: Add the new actions**

Append to the `MeasurementFormAction` sealed interface in `MeasurementFormAction.kt`:

```kotlin
    // PTSP-12 — custom measurement fields
    data object OnAddCustomFieldClick : MeasurementFormAction
    data object OnLockedCustomFieldClick : MeasurementFormAction
    data class OnEditCustomFieldClick(val fieldId: String) : MeasurementFormAction
    data object OnCustomFieldSheetDismiss : MeasurementFormAction
    data class OnSaveCustomField(
        val id: String?,            // null = create, non-null = update
        val label: String,
        val genders: Set<CustomerGender>,
    ) : MeasurementFormAction
    data class OnArchiveCustomFieldRequest(val fieldId: String) : MeasurementFormAction
    data class OnArchiveCustomFieldConfirm(val fieldId: String) : MeasurementFormAction
```

You may need to add `import com.danzucker.stitchpad.core.domain.model.CustomerGender` at the top if it isn't already imported.

- [ ] **Step 2: Add the new event**

In `MeasurementFormEvent.kt`, append to the sealed interface body:

```kotlin
    /**
     * PTSP-12 — emitted when a non-entitled tailor taps the locked
     * "+ Add custom field" affordance. The Root translates this into a
     * navigation to the existing UpgradeRoute (no new conversion UI).
     */
    data object NavigateToUpgrade : MeasurementFormEvent
```

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: compile FAILURE — the existing `when` over `MeasurementFormAction` inside `MeasurementFormViewModel.onAction(...)` is now non-exhaustive. Same for any `when` over `MeasurementFormEvent` in the Root composable.

This is expected at this step. Add temporary stub branches **only** so the project compiles between Task 7 and Task 8 — they will be replaced in the next task. In `MeasurementFormViewModel.onAction(...)`, add right before `OnSaveClick`:

```kotlin
            // TODO PTSP-12 Task 8: real handlers
            MeasurementFormAction.OnAddCustomFieldClick,
            MeasurementFormAction.OnLockedCustomFieldClick,
            MeasurementFormAction.OnCustomFieldSheetDismiss,
            is MeasurementFormAction.OnEditCustomFieldClick,
            is MeasurementFormAction.OnSaveCustomField,
            is MeasurementFormAction.OnArchiveCustomFieldRequest,
            is MeasurementFormAction.OnArchiveCustomFieldConfirm -> Unit
```

In the `ObserveAsEvents` block of `MeasurementFormRoot` (in `MeasurementFormScreen.kt`), add:

```kotlin
            // TODO PTSP-12 Task 12: wire to onNavigateToUpgrade callback
            MeasurementFormEvent.NavigateToUpgrade -> Unit
```

Re-run `./gradlew :composeApp:compileDebugKotlinAndroid` — expect SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormAction.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormEvent.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(measurements): add custom-field actions + NavigateToUpgrade event (PTSP-12)"
```

---

## Task 8: ViewModel — observe + filter + sheet handlers

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

- [ ] **Step 1: Inject the repo + entitlements into the VM constructor**

Find the existing `MeasurementFormViewModel` constructor in `MeasurementFormViewModel.kt`:

```kotlin
class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val orderRepository: OrderRepository,
) : ViewModel() {
```

Replace with (added `customFieldRepository` + `entitlements`):

```kotlin
class MeasurementFormViewModel(
    savedStateHandle: SavedStateHandle,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val measurementPreferencesStore: MeasurementPreferencesStore,
    private val orderRepository: OrderRepository,
    private val customFieldRepository: CustomMeasurementFieldRepository,
    private val entitlements: EntitlementsProvider,
) : ViewModel() {
```

Add the missing imports near the existing imports:

```kotlin
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.repository.CustomMeasurementFieldRepository
```

- [ ] **Step 2: Observe + filter custom fields**

In the `onStart` block (where `loadMeasurement` is called for edit mode), add a sibling `viewModelScope.launch` that observes the custom-field stream and updates state with the filtered subset. The block becomes:

```kotlin
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                val unit = measurementPreferencesStore.getUnit()
                // PTSP-12: snapshot the entitlement at form-open time so the
                // Custom section can render the right CTA (primary vs locked).
                _state.update {
                    it.copy(
                        unit = unit,
                        canUseCustomMeasurements = entitlements.current().canUseCustomMeasurements,
                    )
                }
                if (measurementId != null) {
                    loadMeasurement(measurementId)
                } else {
                    onGenderChange(CustomerGender.FEMALE)
                }
                observeCustomFields()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MeasurementFormState(isEditMode = measurementId != null)
        )

    private fun observeCustomFields() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            customFieldRepository.observeFields(userId).collect { result ->
                if (result is Result.Success) {
                    _state.update { current ->
                        current.copy(customFields = filterFieldsForCurrentGender(result.data, current.gender))
                    }
                }
                // Errors on the field stream are non-fatal — keep the form
                // functional; tailors can retry by reopening the screen.
            }
        }
    }

    private fun filterFieldsForCurrentGender(
        fields: List<CustomMeasurementField>,
        gender: CustomerGender?,
    ): List<CustomMeasurementField> =
        fields.filter { !it.isArchived && (gender == null || gender in it.genders) }
```

- [ ] **Step 3: Re-filter on gender change**

Find the existing `onGenderChange` function and update it to also re-filter the in-state custom fields against the new gender. Change the body from:

```kotlin
    private fun onGenderChange(gender: CustomerGender) {
        val sections = BodyProfileTemplate.sectionsFor(gender)
        val allKeys = sections.flatMap { it.fields }.map { it.key }
        _state.update {
            it.copy(
                gender = gender,
                sections = sections,
                currentSectionIndex = 0,
                isCurrentSectionExpanded = true,
                fields = allKeys.associateWith { "" }
            )
        }
    }
```

To:

```kotlin
    private fun onGenderChange(gender: CustomerGender) {
        val sections = BodyProfileTemplate.sectionsFor(gender)
        val allKeys = sections.flatMap { it.fields }.map { it.key }
        _state.update { current ->
            // Re-filter customFields against the new gender. We keep the
            // already-observed list cached and just narrow the visible set.
            val visibleCustom = filterFieldsForCurrentGender(current.customFields, gender)
            current.copy(
                gender = gender,
                sections = sections,
                currentSectionIndex = 0,
                isCurrentSectionExpanded = true,
                fields = allKeys.associateWith { "" },
                customFields = visibleCustom,
            )
        }
    }
```

Note: `customFields` in state now holds the **filtered** list. If we ever need the unfiltered cached list, we'd re-collect from the repo — fine for V1 because the observer keeps emitting.

- [ ] **Step 4: Implement the sheet-open/dismiss action handlers**

In the `onAction` `when` block, REPLACE the temporary stub from Task 7 with real handlers:

```kotlin
            MeasurementFormAction.OnAddCustomFieldClick -> {
                if (_state.value.canUseCustomMeasurements) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.Adding) }
                } else {
                    viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
                }
            }
            MeasurementFormAction.OnLockedCustomFieldClick -> {
                viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
            }
            is MeasurementFormAction.OnEditCustomFieldClick -> {
                val field = _state.value.customFields.find { it.id == action.fieldId }
                if (field != null) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.Editing(field)) }
                }
            }
            MeasurementFormAction.OnCustomFieldSheetDismiss -> {
                _state.update { it.copy(customFieldSheet = null) }
            }
            is MeasurementFormAction.OnArchiveCustomFieldRequest -> {
                val field = _state.value.customFields.find { it.id == action.fieldId }
                if (field != null) {
                    _state.update { it.copy(customFieldSheet = CustomFieldSheet.ConfirmArchive(field)) }
                }
            }
            is MeasurementFormAction.OnSaveCustomField -> saveCustomField(action.id, action.label, action.genders)
            is MeasurementFormAction.OnArchiveCustomFieldConfirm -> archiveCustomField(action.fieldId)
```

(`saveCustomField` and `archiveCustomField` are added in Task 9. To keep this step compileable, add temporary stub methods right after the `onAction` function body:)

```kotlin
    @OptIn(ExperimentalUuidApi::class)
    private fun saveCustomField(id: String?, label: String, genders: Set<CustomerGender>) {
        // Real impl in Task 9
        _state.update { it.copy(customFieldSheet = null) }
    }

    private fun archiveCustomField(fieldId: String) {
        // Real impl in Task 9
        _state.update { it.copy(customFieldSheet = null) }
    }
```

- [ ] **Step 5: Write failing tests for the sheet handlers**

In `MeasurementFormViewModelTest.kt`, replace the existing `TestScope.createViewModel(...)` helper so it accepts an optional fake custom-field repo + entitlements. Add at the top:

```kotlin
    private lateinit var customFieldRepository: FakeCustomMeasurementFieldRepository
    private lateinit var fakeEntitlements: FakeEntitlementsProvider
```

In `setUp()`, after the existing fakes:

```kotlin
        customFieldRepository = FakeCustomMeasurementFieldRepository()
        fakeEntitlements = FakeEntitlementsProvider(canUseCustomMeasurements = true)
```

Replace the existing `createViewModel(...)` body with:

```kotlin
    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
        measurementId: String? = null,
    ): MeasurementFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (measurementId != null) put("measurementId", measurementId)
        }
        val vm = MeasurementFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            measurementRepository = measurementRepository,
            authRepository = authRepository,
            measurementPreferencesStore = preferencesStore,
            orderRepository = orderRepository,
            customFieldRepository = customFieldRepository,
            entitlements = fakeEntitlements,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }
```

Add the `FakeEntitlementsProvider` helper (mirror the one already in `CustomerFormViewModelTest`):

```kotlin
    private class FakeEntitlementsProvider(
        private val canUseCustomMeasurements: Boolean,
    ) : EntitlementsProvider {
        private val ents = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = 15,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = canUseCustomMeasurements,
        )
        private val _flow = MutableStateFlow(ents)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = ents
        override suspend fun awaitHydrated(): UserEntitlements = ents
    }
```

Add imports for `UserEntitlements`, `SubscriptionTier`, `EntitlementsProvider`, `FakeCustomMeasurementFieldRepository`, `MutableStateFlow`, `StateFlow`.

Now append the new tests:

```kotlin
    // --- PTSP-12: custom fields ---

    @Test
    fun observeCustomFields_filtersByGenderAndArchive() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE)),
            customField(id = "f2", label = "Lapel", genders = setOf(CustomerGender.MALE)),
            customField(id = "f3", label = "Old", genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE), isArchived = true),
        ))
        val vm = createViewModel()
        // Gender auto-defaults to FEMALE for create mode
        val visibleIds = vm.state.value.customFields.map { it.id }
        assertEquals(listOf("f1"), visibleIds)
    }

    @Test
    fun onAddCustomFieldClick_whenEntitled_opensAddingSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun onAddCustomFieldClick_whenNotEntitled_emitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeEntitlements = FakeEntitlementsProvider(canUseCustomMeasurements = false)
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertNull(vm.state.value.customFieldSheet)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onLockedCustomFieldClick_emitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnLockedCustomFieldClick)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onEditCustomFieldClick_opensEditingSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnEditCustomFieldClick("f1"))
        val sheet = vm.state.value.customFieldSheet
        assertIs<CustomFieldSheet.Editing>(sheet)
        assertEquals("Cuff", sheet.field.label)
    }

    @Test
    fun onArchiveCustomFieldRequest_opensConfirmSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldRequest("f1"))
        val sheet = vm.state.value.customFieldSheet
        assertIs<CustomFieldSheet.ConfirmArchive>(sheet)
    }

    @Test
    fun onCustomFieldSheetDismiss_clearsSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
        vm.onAction(MeasurementFormAction.OnCustomFieldSheetDismiss)
        assertNull(vm.state.value.customFieldSheet)
    }

    private fun customField(
        id: String,
        label: String,
        genders: Set<CustomerGender>,
        isArchived: Boolean = false,
    ) = CustomMeasurementField(
        id = id,
        label = label,
        genders = genders,
        isArchived = isArchived,
        createdAt = 0L,
        updatedAt = 0L,
    )
```

Imports to add at the top of the test file: `import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField`, `import com.danzucker.stitchpad.feature.measurement.presentation.form.CustomFieldSheet`, `kotlin.test.assertNull`.

- [ ] **Step 6: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModelTest"`
Expected: PASS — the temporary stubs from Step 4 are wired correctly, so this set should already pass. If `observeCustomFields_filtersByGenderAndArchive` fails because the auto-default gender flow hasn't surfaced fields, verify `observeCustomFields()` is wired in `onStart`.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt
git commit -m "feat(measurements): VM observes + filters custom fields, sheet open/close (PTSP-12)"
```

---

## Task 9: ViewModel — save / update / archive

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `MeasurementFormViewModelTest.kt`:

```kotlin
    // --- PTSP-12: save/update/archive ---

    @Test
    fun saveCustomField_create_callsRepoCreate_withNewUuid_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "Sleeve cuff width",
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        ))
        val created = customFieldRepository.lastCreatedField
        assertNotNull(created)
        assertTrue(created.id.isNotBlank())
        assertEquals("Sleeve cuff width", created.label)
        assertEquals(setOf(CustomerGender.FEMALE, CustomerGender.MALE), created.genders)
        assertFalse(created.isArchived)
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_edit_callsRepoUpdate_preservesId_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(existing))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnEditCustomFieldClick("f1"))
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = "f1",
            label = "Sleeve cuff",
            genders = setOf(CustomerGender.FEMALE),
        ))
        val updated = customFieldRepository.lastUpdatedField
        assertNotNull(updated)
        assertEquals("f1", updated.id)
        assertEquals("Sleeve cuff", updated.label)
        assertNull(customFieldRepository.lastCreatedField)
        assertNull(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_blankLabel_doesNotCallRepo_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "   ",
            genders = setOf(CustomerGender.FEMALE),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        // Sheet stays open so the user can correct
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_emptyGenders_doesNotCallRepo_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnAddCustomFieldClick)
        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "X",
            genders = emptySet(),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        assertIs<CustomFieldSheet.Adding>(vm.state.value.customFieldSheet)
    }

    @Test
    fun saveCustomField_whenEntitlementLost_emitsUpgrade_andDoesNotCallRepo() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // VM created while entitled
        val vm = createViewModel()
        // Entitlement is lost (e.g., welcome ended mid-session)
        fakeEntitlements = FakeEntitlementsProvider(canUseCustomMeasurements = false)
        // re-inject by recreating? Not possible mid-VM. Use the existing VM and
        // rely on save-time re-check. Adjust the FakeEntitlementsProvider impl
        // to expose a mutable hook OR re-read `current()` at save time.
        // (See Step 2 for the real fix: VM re-checks entitlement at save time.)
        // For this test, swap the entitlement in the SAME provider instance.
        // We do that by exposing a `mutate(...)` helper on the fake.
        fakeEntitlements.setEntitled(false)

        vm.onAction(MeasurementFormAction.OnSaveCustomField(
            id = null,
            label = "X",
            genders = setOf(CustomerGender.FEMALE),
        ))
        assertNull(customFieldRepository.lastCreatedField)
        assertIs<MeasurementFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    @Test
    fun onArchiveCustomFieldConfirm_callsRepoArchive_andClosesSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val f = customField(id = "f1", label = "Cuff", genders = setOf(CustomerGender.FEMALE))
        customFieldRepository.seedFields(listOf(f))
        val vm = createViewModel()
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldRequest("f1"))
        assertIs<CustomFieldSheet.ConfirmArchive>(vm.state.value.customFieldSheet)
        vm.onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm("f1"))
        assertEquals("f1", customFieldRepository.lastArchivedFieldId)
        assertNull(vm.state.value.customFieldSheet)
    }
```

Update `FakeEntitlementsProvider` in this test file to expose `setEntitled(...)`:

```kotlin
    private class FakeEntitlementsProvider(
        initialCanUseCustomMeasurements: Boolean,
    ) : EntitlementsProvider {
        private fun build(can: Boolean) = UserEntitlements(
            tier = SubscriptionTier.FREE,
            customerCap = 15,
            smartCoinAllowance = 5,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = can,
        )
        private val _flow = MutableStateFlow(build(initialCanUseCustomMeasurements))
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value

        fun setEntitled(can: Boolean) {
            _flow.value = build(can)
        }
    }
```

Constructor usage updates from `FakeEntitlementsProvider(canUseCustomMeasurements = true)` → `FakeEntitlementsProvider(initialCanUseCustomMeasurements = true)`. Update the call in `setUp()`.

- [ ] **Step 2: Replace the stub save/archive methods**

In `MeasurementFormViewModel.kt`, replace the temporary stubs from Task 8 step 4 with real implementations:

```kotlin
    @OptIn(ExperimentalUuidApi::class)
    private fun saveCustomField(id: String?, label: String, genders: Set<CustomerGender>) {
        // Defense in depth: VM-side entitlement re-check (welcome window could
        // have elapsed since the form opened; the UI check is the first gate).
        if (!entitlements.current().canUseCustomMeasurements) {
            viewModelScope.launch { _events.send(MeasurementFormEvent.NavigateToUpgrade) }
            return
        }
        // Trim + validate. Empty label or empty gender set keeps the sheet open
        // so the user can correct without losing what they typed.
        val trimmed = label.trim()
        if (trimmed.isEmpty() || genders.isEmpty()) return

        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val now = Clock.System.now().toEpochMilliseconds()
            val isCreate = id == null
            val field = CustomMeasurementField(
                id = id ?: Uuid.random().toString(),
                label = trimmed,
                genders = genders,
                isArchived = false,
                createdAt = if (isCreate) now else (
                    _state.value.customFields.find { it.id == id }?.createdAt ?: now
                ),
                updatedAt = now,
            )
            val result = if (isCreate) {
                customFieldRepository.createField(userId, field)
            } else {
                customFieldRepository.updateField(userId, field)
            }
            if (result is Result.Success) {
                _state.update { it.copy(customFieldSheet = null) }
            } else {
                // Surface via the shared snackbar. Leave the sheet OPEN so the
                // user can retry without re-typing.
                _state.update {
                    it.copy(errorMessage = (result as Result.Error).error.toMeasurementUiText())
                }
            }
        }
    }

    private fun archiveCustomField(fieldId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            val result = customFieldRepository.archiveField(userId, fieldId)
            if (result is Result.Success) {
                _state.update { it.copy(customFieldSheet = null) }
            } else {
                _state.update {
                    it.copy(
                        customFieldSheet = null,  // close confirm — error shown via snackbar
                        errorMessage = (result as Result.Error).error.toMeasurementUiText(),
                    )
                }
            }
        }
    }
```

Add the missing import:

```kotlin
import kotlin.time.Clock
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModelTest"`
Expected: PASS — all new tests + all prior tests green.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt
git commit -m "feat(measurements): VM saves/updates/archives custom fields with entitlement guard (PTSP-12)"
```

---

## Task 10: `loadMeasurement` — preserve custom + orphan keys

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

Append to `MeasurementFormViewModelTest.kt`:

```kotlin
    // --- PTSP-12: loadMeasurement key preservation ---

    @Test
    fun loadMeasurement_withCustomFieldValues_populatesAllKeys() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customFieldRepository.seedFields(listOf(
            customField(id = "custom-1", label = "Cuff", genders = setOf(CustomerGender.FEMALE)),
        ))
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf(
                "bust_circumference" to 92.0,
                "custom-1" to 12.5,
            ),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")

        assertEquals("12.5", vm.state.value.fields["custom-1"])
        assertEquals("92", vm.state.value.fields["bust_circumference"])
    }

    @Test
    fun loadMeasurement_withOrphanKeys_preservesValuesOnRoundtrip() = runTest {
        // A measurement stored with a custom-field key whose definition the
        // user has archived (or that was imported from another source). The
        // form must NOT drop the value when saving.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // No matching definition in customFieldRepository — that's the point
        val measurement = Measurement(
            id = "m1",
            customerId = "customer-1",
            gender = CustomerGender.FEMALE,
            fields = mapOf("orphan-key-99" to 7.0),
            unit = MeasurementUnit.CM,
            notes = null,
            dateTaken = 0L,
            createdAt = 0L,
        )
        measurementRepository.measurementsList = listOf(measurement)
        val vm = createViewModel(measurementId = "m1")
        // Save without modification
        vm.onAction(MeasurementFormAction.OnSaveClick)

        val updated = measurementRepository.lastUpdatedMeasurement
        assertNotNull(updated)
        assertEquals(7.0, updated.fields["orphan-key-99"])
    }
```

- [ ] **Step 2: Replace `loadMeasurement` key-build logic**

In `MeasurementFormViewModel.kt`, find the existing `loadMeasurement(...)` function. Locate this block inside the `Result.Success` branch:

```kotlin
                    if (measurement != null) {
                        val sections = BodyProfileTemplate.sectionsFor(measurement.gender)
                        val allKeys = sections.flatMap { it.fields }.map { it.key }
                        val fieldsAsString = allKeys.associateWith { key ->
                            val v = measurement.fields[key]
                            if (v != null) {
                                if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                            } else {
                                ""
                            }
                        }
                        _state.update {
                            it.copy(
                                gender = measurement.gender,
                                sections = sections,
                                fields = fieldsAsString,
                                unit = measurement.unit,
                                notes = measurement.notes ?: "",
                                originalCreatedAt = measurement.createdAt,
                                originalDateTaken = measurement.dateTaken,
                                isLoading = false
                            )
                        }
                    }
```

Replace with (union of template ∪ visible custom ∪ recorded keys):

```kotlin
                    if (measurement != null) {
                        val sections = BodyProfileTemplate.sectionsFor(measurement.gender)
                        val templateKeys = sections.flatMap { it.fields }.map { it.key }.toSet()
                        val customKeys = _state.value.customFields.map { it.id }.toSet()
                        val recordedKeys = measurement.fields.keys
                        // Union: template + visible custom + anything actually
                        // recorded on the doc (orphans included so save round-
                        // trips them cleanly, even if no definition exists).
                        val allKeys = templateKeys + customKeys + recordedKeys
                        val fieldsAsString = allKeys.associateWith { key ->
                            val v = measurement.fields[key]
                            if (v != null) {
                                if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                            } else {
                                ""
                            }
                        }
                        _state.update {
                            it.copy(
                                gender = measurement.gender,
                                sections = sections,
                                fields = fieldsAsString,
                                unit = measurement.unit,
                                notes = measurement.notes ?: "",
                                originalCreatedAt = measurement.createdAt,
                                originalDateTaken = measurement.dateTaken,
                                isLoading = false,
                            )
                        }
                    }
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.measurement.presentation.form.MeasurementFormViewModelTest"`
Expected: PASS — both new tests + all prior tests green.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt
git commit -m "fix(measurements): loadMeasurement preserves custom + orphan keys (PTSP-12)"
```

---

## Task 11: Strings + Custom section UI

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt`

- [ ] **Step 1: Add string resources**

Append to `strings.xml`, adjacent to the other `measurement_*` strings:

```xml
    <!-- PTSP-12 — custom measurement fields -->
    <string name="custom_field_section_title">Custom</string>
    <string name="custom_field_section_pill_pro">Your fields</string>
    <string name="custom_field_section_pill_locked">Pro</string>
    <string name="custom_field_section_pill_first_month">First Month</string>
    <string name="custom_field_empty_caption">Add measurements that aren’t on the default list — like cuff width, lapel, or gown sweep. They’ll appear here for every customer.</string>
    <string name="custom_field_locked_caption">Define measurements that don’t appear on the default list. Available on Pro.</string>
    <string name="custom_field_add_button">Add custom field</string>
    <string name="custom_field_sheet_add_title">Add custom field</string>
    <string name="custom_field_sheet_add_subtitle">Define a measurement you’d like to track for your customers.</string>
    <string name="custom_field_sheet_edit_title">Edit field</string>
    <string name="custom_field_sheet_label">Field name</string>
    <string name="custom_field_sheet_label_placeholder">e.g. Sleeve cuff width</string>
    <string name="custom_field_sheet_genders_label">Show on</string>
    <string name="custom_field_sheet_gender_female">Female</string>
    <string name="custom_field_sheet_gender_male">Male</string>
    <string name="custom_field_sheet_gender_both">Both</string>
    <string name="custom_field_sheet_save">Save</string>
    <string name="custom_field_sheet_save_changes">Save changes</string>
    <string name="custom_field_sheet_cancel">Cancel</string>
    <string name="custom_field_sheet_archive">Archive field</string>
    <string name="custom_field_archive_dialog_title">Archive this field?</string>
    <string name="custom_field_archive_dialog_body">“%1$s” will stop appearing on new measurements. Values already recorded stay visible on past measurements.</string>
    <string name="custom_field_archive_dialog_confirm">Archive</string>
```

Per [[feedback_strings_no_backslash_escape]], the typographic apostrophe `’` (U+2019) is used instead of `\'` — verify nothing got auto-corrected to a backslash escape.

- [ ] **Step 2: Render the Custom section in `MeasurementFormScreen`**

Find the existing measurement form layout in `MeasurementFormScreen.kt` — specifically the section that paged through `state.sections` via a HorizontalPager (or the section list). Locate the end of the paged section block, just BEFORE the existing notes/save bar.

Add a `CustomFieldsSection` composable below all the existing private composables. Then render it after the section pager. The composable:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomFieldsSection(
    fields: List<CustomMeasurementField>,
    fieldValues: Map<String, String>,
    unitSuffix: String,
    canUseCustomMeasurements: Boolean,
    isInWelcomeWindow: Boolean,
    onFieldValueChange: (String, String) -> Unit,
    onAddClick: () -> Unit,
    onLockedAddClick: () -> Unit,
    onEditField: (String) -> Unit,
    onArchiveRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.custom_field_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val pillRes = when {
                canUseCustomMeasurements && isInWelcomeWindow -> Res.string.custom_field_section_pill_first_month
                canUseCustomMeasurements -> Res.string.custom_field_section_pill_pro
                else -> Res.string.custom_field_section_pill_locked
            }
            Text(
                text = stringResource(pillRes).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (canUseCustomMeasurements) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(horizontal = DesignTokens.space2, vertical = 4.dp),
            )
        }

        if (fields.isEmpty()) {
            val captionRes = if (canUseCustomMeasurements) {
                Res.string.custom_field_empty_caption
            } else {
                Res.string.custom_field_locked_caption
            }
            Text(
                text = stringResource(captionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            fields.forEach { field ->
                // Long-press on the label row (NOT the text field) opens the
                // manage sheet. The text field's own gesture detector would
                // otherwise swallow the long-press and break text selection.
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = field.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onEditField(field.id) },
                            ),
                    )
                    MeasurementTextField(
                        value = fieldValues[field.id] ?: "",
                        onValueChange = { onFieldValueChange(field.id, it) },
                        label = "",  // label rendered above by the long-pressable Text
                        placeholder = "0",
                        suffix = unitSuffix,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }

        AddCustomFieldButton(
            enabled = canUseCustomMeasurements,
            onClick = if (canUseCustomMeasurements) onAddClick else onLockedAddClick,
        )
    }
}

@Composable
private fun AddCustomFieldButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (enabled) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.outline
    val content = if (enabled) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
    ) {
        if (!enabled) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(DesignTokens.space1))
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(DesignTokens.space1))
        }
        Text(
            text = stringResource(Res.string.custom_field_add_button),
            fontWeight = FontWeight.SemiBold,
        )
        if (!enabled) {
            Spacer(Modifier.width(DesignTokens.space2))
            Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
```

You'll need the additional imports at the top of `MeasurementFormScreen.kt`:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.KeyboardType
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import stitchpad.composeapp.generated.resources.custom_field_add_button
import stitchpad.composeapp.generated.resources.custom_field_empty_caption
import stitchpad.composeapp.generated.resources.custom_field_locked_caption
import stitchpad.composeapp.generated.resources.custom_field_section_pill_first_month
import stitchpad.composeapp.generated.resources.custom_field_section_pill_locked
import stitchpad.composeapp.generated.resources.custom_field_section_pill_pro
import stitchpad.composeapp.generated.resources.custom_field_section_title
```

`MeasurementTextField` is the existing private composable in this file (around line 567) that renders the underlying numeric input with a suffix (`cm` / `in`). The Custom-section row reuses it directly — no new shared input wrapper needed.

- [ ] **Step 3: Insert `CustomFieldsSection` into the screen**

In the `MeasurementFormScreen` composable body, find where the existing paged sections finish rendering (before the notes-toggle and save bar). Insert:

```kotlin
            CustomFieldsSection(
                fields = state.customFields,
                fieldValues = state.fields,
                unitSuffix = unitSuffix,  // existing local in this composable
                canUseCustomMeasurements = state.canUseCustomMeasurements,
                isInWelcomeWindow = false,  // wired in Task 12; harmless 'false' for now
                onFieldValueChange = { key, value ->
                    onAction(MeasurementFormAction.OnFieldChange(key, value))
                },
                onAddClick = { onAction(MeasurementFormAction.OnAddCustomFieldClick) },
                onLockedAddClick = { onAction(MeasurementFormAction.OnLockedCustomFieldClick) },
                onEditField = { id -> onAction(MeasurementFormAction.OnEditCustomFieldClick(id)) },
                onArchiveRequest = { id -> onAction(MeasurementFormAction.OnArchiveCustomFieldRequest(id)) },
            )
```

- [ ] **Step 4: Compile + verify previews build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt
git commit -m "feat(measurements): render Custom section in measurement form (PTSP-12)"
```

---

## Task 12: Add/Manage sheets + ConfirmArchive dialog + nav-to-upgrade wiring

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/AddCustomFieldSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/ManageCustomFieldSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/ConfirmArchiveDialog.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: AddCustomFieldSheet**

`AddCustomFieldSheet.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_sheet_add_subtitle
import stitchpad.composeapp.generated.resources.custom_field_sheet_add_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_cancel
import stitchpad.composeapp.generated.resources.custom_field_sheet_edit_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_both
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_female
import stitchpad.composeapp.generated.resources.custom_field_sheet_gender_male
import stitchpad.composeapp.generated.resources.custom_field_sheet_genders_label
import stitchpad.composeapp.generated.resources.custom_field_sheet_label
import stitchpad.composeapp.generated.resources.custom_field_sheet_label_placeholder
import stitchpad.composeapp.generated.resources.custom_field_sheet_save
import stitchpad.composeapp.generated.resources.custom_field_sheet_save_changes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFieldSheet(
    initial: CustomMeasurementField?,  // null = create flow, non-null = edit
    onDismiss: () -> Unit,
    onSave: (id: String?, label: String, genders: Set<CustomerGender>) -> Unit,
    modifier: Modifier = Modifier,
    bottomExtra: @Composable (() -> Unit)? = null,  // optional destructive footer (Archive)
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var selected by remember {
        mutableStateOf(initial?.genders ?: setOf(CustomerGender.FEMALE, CustomerGender.MALE))
    }

    val titleRes = if (initial == null) Res.string.custom_field_sheet_add_title
                   else Res.string.custom_field_sheet_edit_title
    val saveRes = if (initial == null) Res.string.custom_field_sheet_save
                  else Res.string.custom_field_sheet_save_changes

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (initial == null) {
                Text(
                    text = stringResource(Res.string.custom_field_sheet_add_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = { if (it.length <= 30) label = it },
                label = { Text(stringResource(Res.string.custom_field_sheet_label)) },
                placeholder = { Text(stringResource(Res.string.custom_field_sheet_label_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(Res.string.custom_field_sheet_genders_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_female),
                    selected = selected == setOf(CustomerGender.FEMALE),
                    onClick = { selected = setOf(CustomerGender.FEMALE) },
                    modifier = Modifier.weight(1f),
                )
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_male),
                    selected = selected == setOf(CustomerGender.MALE),
                    onClick = { selected = setOf(CustomerGender.MALE) },
                    modifier = Modifier.weight(1f),
                )
                GenderChip(
                    label = stringResource(Res.string.custom_field_sheet_gender_both),
                    selected = selected == setOf(CustomerGender.FEMALE, CustomerGender.MALE),
                    onClick = { selected = setOf(CustomerGender.FEMALE, CustomerGender.MALE) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.custom_field_sheet_cancel)) }
                Button(
                    onClick = { onSave(initial?.id, label, selected) },
                    enabled = label.isNotBlank() && selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) { Text(stringResource(saveRes)) }
            }

            // Optional destructive footer — only the Editing-flow caller passes
            // a non-null bottomExtra (the "Archive field" button).
            bottomExtra?.let {
                Spacer(Modifier.height(DesignTokens.space2))
                it()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = modifier,
    )
}
```

- [ ] **Step 2: ManageCustomFieldSheet**

`ManageCustomFieldSheet.kt` — same shape as Add but adds an "Archive field" destructive entry at the bottom:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_sheet_archive

@Composable
fun ManageCustomFieldSheet(
    field: CustomMeasurementField,
    onDismiss: () -> Unit,
    onSave: (id: String?, label: String, genders: Set<CustomerGender>) -> Unit,
    onArchiveRequest: (fieldId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AddCustomFieldSheet(
        initial = field,
        onDismiss = onDismiss,
        onSave = onSave,
        modifier = modifier,
    )
    // Note: the destructive "Archive" entry is exposed via long-press → manage
    // → footer button. For sheet-stacking simplicity, the Add sheet handles
    // both flows; the Archive trigger is invoked from outside this composable
    // (the screen wires a footer button when state.customFieldSheet == Editing).
}
```

> Practical wiring: rather than stacking two sheets, the **measurement screen** decides which composable to render based on `state.customFieldSheet`:
> - `Adding` → `AddCustomFieldSheet(initial = null, ...)`
> - `Editing(field)` → `AddCustomFieldSheet(initial = field, ...)` + a fixed-position "Archive field" `OutlinedButton` rendered below the sheet's primary actions. Add a `bottomExtra: @Composable (() -> Unit)? = null` parameter to `AddCustomFieldSheet` and render it after the save row when non-null. The screen passes the archive button only in the `Editing` case.
> - `ConfirmArchive(field)` → `ConfirmArchiveDialog(...)`.

(Update `AddCustomFieldSheet` to accept the optional `bottomExtra` slot. If you skipped that in Step 1, add it now: a `bottomExtra: @Composable (() -> Unit)? = null` parameter rendered at the end of the `Column`.)

- [ ] **Step 3: ConfirmArchiveDialog**

`ConfirmArchiveDialog.kt`:

```kotlin
package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CustomMeasurementField
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_body
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_confirm
import stitchpad.composeapp.generated.resources.custom_field_archive_dialog_title
import stitchpad.composeapp.generated.resources.custom_field_sheet_cancel

@Composable
fun ConfirmArchiveDialog(
    field: CustomMeasurementField,
    onDismiss: () -> Unit,
    onConfirm: (fieldId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_field_archive_dialog_title)) },
        text = {
            Text(stringResource(Res.string.custom_field_archive_dialog_body, field.label))
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(field.id) },
            ) {
                Text(
                    text = stringResource(Res.string.custom_field_archive_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.custom_field_sheet_cancel))
            }
        },
    )
}
```

- [ ] **Step 4: Render the sheets from `MeasurementFormScreen`**

In `MeasurementFormScreen` composable (inside `MeasurementFormScreen.kt`), after the main Scaffold content, add:

```kotlin
    when (val sheet = state.customFieldSheet) {
        CustomFieldSheet.Adding -> AddCustomFieldSheet(
            initial = null,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onSave = { id, label, genders ->
                onAction(MeasurementFormAction.OnSaveCustomField(id, label, genders))
            },
        )
        is CustomFieldSheet.Editing -> AddCustomFieldSheet(
            initial = sheet.field,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onSave = { id, label, genders ->
                onAction(MeasurementFormAction.OnSaveCustomField(id, label, genders))
            },
            bottomExtra = {
                OutlinedButton(
                    onClick = { onAction(MeasurementFormAction.OnArchiveCustomFieldRequest(sheet.field.id)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.custom_field_sheet_archive))
                }
            },
        )
        is CustomFieldSheet.ConfirmArchive -> ConfirmArchiveDialog(
            field = sheet.field,
            onDismiss = { onAction(MeasurementFormAction.OnCustomFieldSheetDismiss) },
            onConfirm = { id -> onAction(MeasurementFormAction.OnArchiveCustomFieldConfirm(id)) },
        )
        null -> Unit
    }
```

Add the imports:

```kotlin
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.AddCustomFieldSheet
import com.danzucker.stitchpad.feature.measurement.presentation.form.components.ConfirmArchiveDialog
import stitchpad.composeapp.generated.resources.custom_field_sheet_archive
```

- [ ] **Step 5: Wire `onNavigateToUpgrade` through the Root**

In `MeasurementFormRoot` (top of `MeasurementFormScreen.kt`), add a callback parameter and replace the temporary `NavigateToUpgrade -> Unit` stub:

```kotlin
@Composable
fun MeasurementFormRoot(
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: MeasurementFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            MeasurementFormEvent.NavigateBack -> onNavigateBack()
            MeasurementFormEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }

    // ... rest of the Root body unchanged
}
```

- [ ] **Step 6: Pass the callback in `MainScreen.kt`**

In `MainScreen.kt`, find the `composable<MeasurementFormRoute> { ... }` block (around line 215). Update:

```kotlin
        composable<MeasurementFormRoute> {
            MeasurementFormRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToUpgrade = { navController.navigate(UpgradeRoute) },
            )
        }
```

- [ ] **Step 7: Compile (Android + iOS) and run all tests**

Run:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid \
          :composeApp:compileKotlinIosSimulatorArm64 \
          :composeApp:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL on all three. Per [[feedback_kmp_jvm_only_apis]], confirm iOS link is clean.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/components/ \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(measurements): add/manage/archive sheets + upgrade nav wiring (PTSP-12)"
```

---

## Task 13: Cross-platform verification + detekt

**Files:** None (verification only)

- [ ] **Step 1: Android compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: iOS simulator compile**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full unit test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: all tests pass — no regressions.

- [ ] **Step 4: Detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If any new finding lands on touched files, run the `/format` skill to auto-fix.

---

## Task 14: Manual smoke test (Daniel)

Run through every step in §5.2 of `docs/superpowers/specs/2026-05-26-custom-measurements-design.md` on Android and iOS (iPhone 17 Pro sim per [[reference_test_environment]]), light + dark.

- [ ] PTSP-12 happy path 1 — Empty Custom section appears for Pro
- [ ] Happy path 2 — Add a custom field via the sheet; appears immediately
- [ ] Happy path 3 — Enter a value, save measurement, value persists
- [ ] Happy path 4 — Edit the measurement, value pre-populated, change & save
- [ ] Rename & archive 5 — Rename a field; past measurement updates label
- [ ] Rename & archive 6 — Archive a field; past measurement still shows the value
- [ ] Rename & archive 7 — Re-add a field with the same name; succeeds (new UUID)
- [ ] Gender filtering 8 — Female-only / Male-only fields surface correctly per customer gender
- [ ] FREE welcome 9 — Fresh FREE in welcome window: "+ Add custom field" is primary, no lock
- [ ] FREE post-welcome 10 — Expired FREE: "+ Add custom field" shows Pro pill, tap → upgrade sheet; past data still visible
- [ ] iOS string render — no `\'` artifacts (per [[feedback_strings_no_backslash_escape]])
- [ ] Process-death survival — open Add sheet, background+kill, reopen → sheet state restores

---

## Task 15: Push branch + open PR

- [ ] **Step 1: Push**

```bash
git push -u origin feature/ptsp-12-custom-measurements
```

- [ ] **Step 2: Open PR**

```bash
gh pr create --title "feat(measurements): custom measurement fields (PTSP-12)" --body "$(cat <<'EOF'
## Summary
- Paid-tier custom measurement fields (Pro + Atelier + FREE during First Month).
- Per-tailor scope, gender-tagged, numeric-only, rendered in a new Custom section at the end of the measurement form.
- Stable UUID id + editable label → renames safe across past records; soft-archive preserves data.
- Bundled fix: `loadMeasurement` now preserves custom-field and orphan values on edit (previously silently dropped non-template keys).

Spec: docs/superpowers/specs/2026-05-26-custom-measurements-design.md
Plan: docs/superpowers/plans/2026-05-26-ptsp-12-custom-measurements.md

## Test plan
- [ ] Pro: full happy path + rename + archive (Android + iOS)
- [ ] FREE in welcome window: add a field, take a measurement
- [ ] FREE post-welcome: locked CTA → upgrade sheet; past data still visible
- [ ] Gender filtering: female-only / male-only fields render correctly per customer
- [ ] iOS string render check
- [ ] Cursor review (bugbot)
- [ ] codex review

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Cursor + codex review**

Per [[feedback_review_rotation]], wait for Bugbot to post on the new PR, then run `/code-review --comment` for codex findings. Address any P1/P2.

---

## Notes

- **`@OptIn(ExperimentalUuidApi::class)`** is required where `Uuid.random()` is called. Already used in `OrderFormViewModel` / `CustomerFormViewModel` — same pattern.
- **`@OptIn(ExperimentalFoundationApi::class)`** is required on the long-press handler (`Modifier.combinedClickable`).
- **GitLive Firestore `set(map, merge = true)`** for the archive partial-update — equivalent to a `merge: true` write. If the precise method signature in this codebase's GitLive version differs (e.g., `setData(...)` or `update(mapOf(...))`), match the surrounding pattern in `FirebaseUserRepository`.
- **No server-side enforcement** of `canUseCustomMeasurements` is shipped in V1 (consistent with how `customerCap` is client-only). Server-side tier rule is in §7 of the spec as deferred.
- **Strings file:** the apostrophe-as-typographic rule from [[feedback_strings_no_backslash_escape]] applies. Spot-check `strings.xml` after the IDE auto-formats.
