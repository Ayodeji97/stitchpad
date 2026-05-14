# Debug Menu V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an internal debug menu (reachable from Settings on Android + iOS in debug builds) that lets Daniel + tailor-testers seed realistic Firestore state, reset onboarding, switch test accounts, and wipe their data in two taps.

**Architecture:** All code in `commonMain` gated by a compile-time-evaluable `expect val isDebugBuild` (`BuildConfig.DEBUG` on Android, `Platform.isDebugBinary` on iOS). The Settings entry row is hidden when `isDebugBuild` is false; the menu's classes ship in release binaries but are unreachable. Test-account credentials live in a build-time-generated Kotlin file sourced from a gitignored properties file.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Multiplatform, Koin DI, GitLive Firebase SDK (Firestore + Auth + Storage), Compose Navigation. Tests use `kotlin.test` with fakes from `commonTest/com.danzucker.stitchpad.core.data.repository.Fake*` (no Turbine — project doesn't use it).

**Spec:** `docs/superpowers/specs/2026-05-14-debug-menu-design.md`

---

## File map

### New files (commonMain)
- `core/debug/DebugBuild.kt` — `expect val isDebugBuild: Boolean`
- `core/debug/SeedFixtures.kt` — pure data fixtures
- `core/debug/DebugSeeder.kt` — interface + `DefaultDebugSeeder`
- `core/debug/DebugSessionActions.kt` — reset onboarding flags + account switcher
- `feature/debug/presentation/DebugMenuState.kt`
- `feature/debug/presentation/DebugMenuAction.kt`
- `feature/debug/presentation/DebugMenuEvent.kt`
- `feature/debug/presentation/DebugMenuViewModel.kt`
- `feature/debug/presentation/DebugMenuScreen.kt`
- `feature/debug/presentation/DebugMenuRoot.kt`
- `di/DebugModule.kt`

### New files (platform actuals)
- `androidMain/core/debug/DebugBuild.android.kt`
- `iosMain/core/debug/DebugBuild.ios.kt`

### New files (build / config)
- `composeApp/debug-test-accounts.properties.example` (committed template)
- `composeApp/debug-test-accounts.properties` (gitignored — Daniel creates locally)
- `.gitignore` (modify — add the properties file)
- `composeApp/build.gradle.kts` (modify — Gradle task generates `DebugTestAccounts.kt`)
- Generated at build: `composeApp/build/generated/debugTestAccounts/commonMain/kotlin/.../core/debug/DebugTestAccounts.kt`

### New tests (commonTest)
- `core/debug/DefaultDebugSeederTest.kt`
- `core/debug/DebugSessionActionsTest.kt`
- `feature/debug/presentation/DebugMenuViewModelTest.kt`

### Modified files
- `feature/onboarding/data/OnboardingPreferencesStore.kt` — add `suspend fun resetForDebug()`
- `androidMain/feature/onboarding/data/OnboardingPreferences.android.kt` — actual impl
- `iosMain/feature/onboarding/data/OnboardingPreferences.ios.kt` — actual impl
- `commonTest/feature/onboarding/data/FakeOnboardingPreferences.kt` — fake impl
- `navigation/Routes.kt` — add `DebugMenuRoute`
- `navigation/StitchPadNavHost.kt` — register the route
- `StitchPadApp.kt` (`initKoin`) — include `debugModule` conditionally
- `feature/settings/presentation/home/SettingsAction.kt` — add `OnDebugMenuClick`
- `feature/settings/presentation/home/SettingsEvent.kt` — add `NavigateToDebugMenu`
- `feature/settings/presentation/home/SettingsViewModel.kt` — handle the action
- `feature/settings/presentation/home/SettingsScreen.kt` — gated row at bottom
- `feature/settings/presentation/home/SettingsRoot.kt` — observe the event

---

## Task 1: `isDebugBuild` expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.kt`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.ios.kt`

- [ ] **Step 1: Create the expect declaration**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

expect val isDebugBuild: Boolean
```

- [ ] **Step 2: Create the Android actual**

`composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.android.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.BuildConfig

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
```

- [ ] **Step 3: Create the iOS actual**

`composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.ios.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary
```

(`Platform.isDebugBinary` is currently behind `@ExperimentalNativeApi`. The opt-in is local to this file and stable since Kotlin 1.7.)

- [ ] **Step 4: Verify both targets compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/core/debug/DebugBuild.ios.kt
git commit -m "feat(debug): add isDebugBuild expect/actual for Android + iOS"
```

---

## Task 2: Add `resetForDebug()` to OnboardingPreferencesStore

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferencesStore.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.ios.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/data/FakeOnboardingPreferences.kt`

The existing interface only exposes setters that set the flags to `true`. The debug menu needs a way to clear both flags so the next cold launch routes back through Onboarding → Workshop setup.

- [ ] **Step 1: Add the method to the interface**

In `OnboardingPreferencesStore.kt`, append to the interface body:

```kotlin
    /**
     * Resets all onboarding flags to false. Debug-menu use only — production
     * code should not call this. Idempotent.
     */
    suspend fun resetForDebug()
```

The full file after editing:
```kotlin
package com.danzucker.stitchpad.feature.onboarding.data

interface OnboardingPreferencesStore {
    suspend fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()
    suspend fun hasCompletedWorkshopSetup(): Boolean
    suspend fun setWorkshopSetupCompleted()
    suspend fun resetForDebug()
}
```

- [ ] **Step 2: Add Android actual implementation**

First read the existing Android impl to find where the flags are stored:
```bash
cat composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt
```

Then add `resetForDebug()` that writes `false` to both keys using the same DataStore/SharedPreferences API the existing setters use. Example skeleton (adapt the call sites to whatever pattern that file uses):

```kotlin
override suspend fun resetForDebug() {
    dataStore.edit { prefs ->
        prefs[ONBOARDING_SEEN_KEY] = false
        prefs[WORKSHOP_SETUP_COMPLETED_KEY] = false
    }
}
```

- [ ] **Step 3: Add iOS actual implementation**

Repeat for the iOS impl. Likely uses `NSUserDefaults`:
```kotlin
override suspend fun resetForDebug() {
    defaults.setBool(false, forKey = ONBOARDING_SEEN_KEY)
    defaults.setBool(false, forKey = WORKSHOP_SETUP_COMPLETED_KEY)
}
```

- [ ] **Step 4: Add fake implementation**

In `FakeOnboardingPreferences.kt`, append:
```kotlin
    override suspend fun resetForDebug() {
        onboardingSeen = false
        workshopSetupCompleted = false
    }
```

- [ ] **Step 5: Verify both targets compile + tests still pass**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:allTests
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferencesStore.kt \
        composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.android.kt \
        composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/onboarding/data/OnboardingPreferences.ios.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/onboarding/data/FakeOnboardingPreferences.kt
git commit -m "feat(onboarding): add resetForDebug() to clear onboarding flags"
```

---

## Task 3: Gitignored test-account properties + committed template

**Files:**
- Create: `composeApp/debug-test-accounts.properties.example`
- Modify: `.gitignore`

- [ ] **Step 1: Create the template**

`composeApp/debug-test-accounts.properties.example`:
```properties
# Local-only credentials for debug "Switch account" buttons.
# Copy this file to debug-test-accounts.properties and fill in real values.
# NEVER commit the .properties file (only this .example is tracked).
fola.email=
fola.password=
gabby.email=
gabby.password=
```

- [ ] **Step 2: Add to .gitignore**

Append to `.gitignore`:
```
# Debug menu test creds — local only, never committed
composeApp/debug-test-accounts.properties
```

- [ ] **Step 3: Verify .gitignore works**

```bash
echo "fola.email=test@example.com" > composeApp/debug-test-accounts.properties
git status
```
Expected: `composeApp/debug-test-accounts.properties` does NOT appear in untracked files. Delete the test file: `rm composeApp/debug-test-accounts.properties`.

- [ ] **Step 4: Commit**

```bash
git add .gitignore composeApp/debug-test-accounts.properties.example
git commit -m "chore(debug): gitignore test-account properties + ship template"
```

---

## Task 4: Gradle task to generate `DebugTestAccounts.kt`

**Files:**
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Add the generation task**

In `composeApp/build.gradle.kts`, near the bottom (after the `android { }` block, before any `dependencies { }` block), add:

```kotlin
// Generates DebugTestAccounts.kt from debug-test-accounts.properties at build
// time. The properties file is gitignored; this task tolerates its absence by
// producing empty-string defaults (Switch-account buttons render but show a
// "creds not configured" Snackbar at runtime). See debug-menu-design.md.
val generateDebugTestAccounts by tasks.registering {
    val propsFile = layout.projectDirectory.file("debug-test-accounts.properties").asFile
    val outputDir = layout.buildDirectory.dir(
        "generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug"
    )

    inputs.file(propsFile).optional(true)
    outputs.dir(outputDir)

    doLast {
        val props = java.util.Properties().apply {
            if (propsFile.exists()) {
                propsFile.inputStream().use { load(it) }
            }
        }
        val folaEmail = props.getProperty("fola.email", "").trim()
        val folaPassword = props.getProperty("fola.password", "").trim()
        val gabbyEmail = props.getProperty("gabby.email", "").trim()
        val gabbyPassword = props.getProperty("gabby.password", "").trim()

        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("DebugTestAccounts.kt").writeText(
            """
            |// GENERATED — do not edit. Source: composeApp/debug-test-accounts.properties
            |package com.danzucker.stitchpad.core.debug
            |
            |internal object DebugTestAccounts {
            |    const val FOLA_EMAIL: String = ${'"'}$folaEmail${'"'}
            |    const val FOLA_PASSWORD: String = ${'"'}$folaPassword${'"'}
            |    const val GABBY_EMAIL: String = ${'"'}$gabbyEmail${'"'}
            |    const val GABBY_PASSWORD: String = ${'"'}$gabbyPassword${'"'}
            |
            |    val isConfigured: Boolean
            |        get() = FOLA_EMAIL.isNotBlank() && FOLA_PASSWORD.isNotBlank() &&
            |                GABBY_EMAIL.isNotBlank() && GABBY_PASSWORD.isNotBlank()
            |}
            |
            """.trimMargin()
        )
    }
}

// Wire the generated source into commonMain so DebugTestAccounts is visible
// from commonMain code as if it were authored there.
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateDebugTestAccounts)
}
```

- [ ] **Step 2: Run the task and verify the generated file exists**

```bash
./gradlew :composeApp:generateDebugTestAccounts
ls composeApp/build/generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug/
```
Expected output: `DebugTestAccounts.kt` exists.

```bash
cat composeApp/build/generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugTestAccounts.kt
```
Expected: Generated file with empty-string constants (the properties file doesn't exist locally yet).

- [ ] **Step 3: Verify compile picks up the generated file**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify with creds populated**

Create `composeApp/debug-test-accounts.properties` (gitignored) with real values:
```properties
fola.email=fola@example.com
fola.password=somepassword
gabby.email=gabby@example.com
gabby.password=anotherpassword
```

Re-run:
```bash
./gradlew :composeApp:generateDebugTestAccounts
cat composeApp/build/generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugTestAccounts.kt
```
Expected: Constants now contain the real values, and `isConfigured` would be true.

- [ ] **Step 5: Commit**

```bash
git add composeApp/build.gradle.kts
git commit -m "build(debug): generate DebugTestAccounts.kt from gitignored props"
```

---

## Task 5: Seed fixtures (pure data)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/SeedFixtures.kt`

This is a pure data file — no logic, no tests beyond compile. Defines deterministic fixture customers, measurements, styles, and orders that downstream seed scenarios assemble.

- [ ] **Step 1: Inspect domain models to confirm field names**

Run these reads to confirm exact field names (the plan may have drifted):
```bash
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Customer.kt
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Order.kt
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Measurement.kt
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomerGender.kt
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/GarmentType.kt
```

- [ ] **Step 2: Create the file with fixtures**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/SeedFixtures.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.DeliveryPreference
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange

/**
 * Deterministic fixture data used by [DebugSeeder]. Fixed IDs let smoke tests
 * reference "seed-customer-1" reliably. createdAt is supplied by the caller
 * (typically a `now()` function from the ViewModel) so fixtures don't bake in
 * stale times when the device clock changes.
 */
internal object SeedFixtures {

    /** Eight customers with Nigerian-style names + phones. */
    fun customers(userId: String, now: Long): List<Customer> = listOf(
        Customer("seed-customer-1", userId, "Adaeze Okafor", "+2348012345601",
            email = "adaeze@example.ng", address = "12 Awolowo Way, Ikeja",
            deliveryPreference = DeliveryPreference.PICKUP, notes = null, createdAt = now),
        Customer("seed-customer-2", userId, "Folake Adebayo", "+2348012345602",
            email = null, address = null,
            deliveryPreference = DeliveryPreference.PICKUP, notes = "Prefers WhatsApp",
            createdAt = now),
        Customer("seed-customer-3", userId, "Chinedu Eze", "+2348012345603",
            email = null, address = "5 Marina, Lagos Island",
            deliveryPreference = DeliveryPreference.DELIVERY, notes = null, createdAt = now),
        Customer("seed-customer-4", userId, "Tunde Bakare", "+2348012345604",
            email = "tunde@example.ng", address = null,
            deliveryPreference = DeliveryPreference.PICKUP, notes = null, createdAt = now),
        Customer("seed-customer-5", userId, "Ngozi Iwu", "+2348012345605",
            email = null, address = null,
            deliveryPreference = DeliveryPreference.PICKUP, notes = null, createdAt = now),
        Customer("seed-customer-6", userId, "Oluwaseun Adesina", "+2348012345606",
            email = null, address = "27 Allen Avenue, Ikeja",
            deliveryPreference = DeliveryPreference.DELIVERY, notes = null, createdAt = now),
        Customer("seed-customer-7", userId, "Hauwa Bello", "+2348012345607",
            email = null, address = null,
            deliveryPreference = DeliveryPreference.PICKUP, notes = null, createdAt = now),
        Customer("seed-customer-8", userId, "Femi Akinola", "+2348012345608",
            email = "femi@example.ng", address = null,
            deliveryPreference = DeliveryPreference.PICKUP, notes = null, createdAt = now),
    )

    /** Measurement for first four seeded customers. */
    fun measurementsFor(customer: Customer, now: Long): Measurement = Measurement(
        id = "seed-measurement-${customer.id.substringAfterLast('-')}",
        customerId = customer.id,
        gender = CustomerGender.FEMALE,
        fields = mapOf(
            "bust" to 36.0,
            "waist" to 28.0,
            "hip" to 38.0,
            "shoulder" to 15.0,
            "sleeveLength" to 22.5,
        ),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = now,
        createdAt = now,
    )

    /**
     * Order fixtures for the [activeWorkshop] seed. Each fixture is built
     * against a customer the seeder has already inserted. The caller supplies
     * `now`; per-order `createdAt` is offset to produce the spec'd day-old
     * distribution.
     *
     * Returns: list of (customer index 0-3, Order). Indexed so the seeder can
     * pair an order to its customer without coupling to actual IDs.
     */
    fun activeOrders(customers: List<Customer>, now: Long): List<Order> {
        val dayMs = 24L * 60 * 60 * 1000
        return listOf(
            // 7-day-old IN_PROGRESS
            Order(
                id = "seed-order-1",
                userId = customers[0].userId,
                customerId = customers[0].id,
                customerName = customers[0].name,
                items = listOf(OrderItem(
                    id = "seed-item-1",
                    garmentType = GarmentType.AGBADA,
                    description = "Cream agbada with embroidery",
                    price = 25_000.0,
                )),
                status = OrderStatus.IN_PROGRESS,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.IN_PROGRESS, now - 7 * dayMs)),
                totalPrice = 25_000.0,
                payments = emptyList(),
                deadline = now + 5 * dayMs,
                notes = null,
                createdAt = now - 7 * dayMs,
                updatedAt = now - 7 * dayMs,
            ),
            // 7-day-old READY
            Order(
                id = "seed-order-2",
                userId = customers[1].userId,
                customerId = customers[1].id,
                customerName = customers[1].name,
                items = listOf(OrderItem(
                    id = "seed-item-2",
                    garmentType = GarmentType.GOWN,
                    description = "Navy ankara gown",
                    price = 18_000.0,
                )),
                status = OrderStatus.READY,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(
                    StatusChange(OrderStatus.IN_PROGRESS, now - 7 * dayMs),
                    StatusChange(OrderStatus.READY, now - 1 * dayMs),
                ),
                totalPrice = 18_000.0,
                payments = emptyList(),
                deadline = now - 1 * dayMs,
                notes = null,
                createdAt = now - 7 * dayMs,
                updatedAt = now - 1 * dayMs,
            ),
            // 3-day-old DELIVERED (last week)
            Order(
                id = "seed-order-3",
                userId = customers[2].userId,
                customerId = customers[2].id,
                customerName = customers[2].name,
                items = listOf(OrderItem(
                    id = "seed-item-3",
                    garmentType = GarmentType.SHIRT,
                    description = "White cotton dress shirt",
                    price = 8_500.0,
                )),
                status = OrderStatus.DELIVERED,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(
                    StatusChange(OrderStatus.IN_PROGRESS, now - 3 * dayMs),
                    StatusChange(OrderStatus.READY, now - 2 * dayMs),
                    StatusChange(OrderStatus.DELIVERED, now - 1 * dayMs),
                ),
                totalPrice = 8_500.0,
                payments = emptyList(),
                deadline = now - 1 * dayMs,
                notes = null,
                createdAt = now - 3 * dayMs,
                updatedAt = now - 1 * dayMs,
            ),
            // Same-day PENDING (deadline 14d out)
            Order(
                id = "seed-order-4",
                userId = customers[3].userId,
                customerId = customers[3].id,
                customerName = customers[3].name,
                items = listOf(OrderItem(
                    id = "seed-item-4",
                    garmentType = GarmentType.KAFTAN,
                    description = "Sky-blue brocade kaftan",
                    price = 15_000.0,
                )),
                status = OrderStatus.PENDING,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.PENDING, now)),
                totalPrice = 15_000.0,
                payments = emptyList(),
                deadline = now + 14 * dayMs,
                notes = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Six all-reconnect customers (100+ days since last order). */
    fun allReconnectCustomers(userId: String, now: Long): List<Customer> {
        val dayMs = 24L * 60 * 60 * 1000
        return List(6) { i ->
            Customer(
                id = "seed-reconnect-${i + 1}",
                userId = userId,
                name = "Reconnect Tester ${i + 1}",
                phone = "+234801234${(7000 + i).toString().padStart(4, '0')}",
                email = null,
                address = null,
                deliveryPreference = DeliveryPreference.PICKUP,
                notes = null,
                createdAt = now - (120 + i * 5) * dayMs,
            )
        }
    }

    /** One Delivered order per all-reconnect customer, 100+ days ago. */
    fun allReconnectOrders(customers: List<Customer>, now: Long): List<Order> {
        val dayMs = 24L * 60 * 60 * 1000
        return customers.mapIndexed { i, c ->
            val daysAgo = 100L + i * 10
            Order(
                id = "seed-reconnect-order-${i + 1}",
                userId = c.userId,
                customerId = c.id,
                customerName = c.name,
                items = listOf(OrderItem(
                    id = "seed-reconnect-item-${i + 1}",
                    garmentType = GarmentType.GOWN,
                    description = "Old order — needs reconnect",
                    price = 12_000.0,
                )),
                status = OrderStatus.DELIVERED,
                subStatus = null,
                priority = OrderPriority.NORMAL,
                statusHistory = listOf(StatusChange(OrderStatus.DELIVERED, now - daysAgo * dayMs)),
                totalPrice = 12_000.0,
                payments = emptyList(),
                deadline = now - daysAgo * dayMs,
                notes = null,
                createdAt = now - daysAgo * dayMs,
                updatedAt = now - daysAgo * dayMs,
            )
        }
    }
}
```

**Note:** `GarmentType` values (`AGBADA`, `GOWN`, `SHIRT`, `KAFTAN`) — verify against the enum in step 1. If names differ (e.g., `BUBU`, `BOUBOU`, etc.), substitute the closest matching enum value.

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/SeedFixtures.kt
git commit -m "feat(debug): seed fixtures — customers, measurements, orders"
```

---

## Task 6: `DebugSeeder` interface + `DefaultDebugSeeder` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSeeder.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DefaultDebugSeederTest.kt`

- [ ] **Step 1: Write the failing test**

`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DefaultDebugSeederTest.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultDebugSeederTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences

    private val fixedNow = 1_715_700_000_000L  // 2026-05-14 11:20:00 UTC

    @BeforeTest
    fun setUp() {
        customerRepository = FakeCustomerRepository()
        orderRepository = FakeOrderRepository()
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository().apply {
            currentUser = com.danzucker.stitchpad.core.domain.model.User(
                id = "test-uid",
                email = "test@example.com",
                displayName = "Test Tailor",
                businessName = "Test Workshop",
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        onboardingPreferences = FakeOnboardingPreferences()
    }

    private fun createSeeder(): DefaultDebugSeeder = DefaultDebugSeeder(
        customerRepository = customerRepository,
        orderRepository = orderRepository,
        measurementRepository = measurementRepository,
        authRepository = authRepository,
        onboardingPreferences = onboardingPreferences,
        now = { fixedNow },
    )

    @Test
    fun `seedBrandNew wipes existing customer and order data`() = runTest {
        customerRepository.customersList = listOf(
            com.danzucker.stitchpad.core.domain.model.Customer(
                id = "pre-existing", userId = "test-uid", name = "Pre", phone = "+1",
                createdAt = 0,
            )
        )

        val result = createSeeder().seedBrandNew()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(0, customerRepository.customersList.size)
    }

    @Test
    fun `seedActiveWorkshop creates 8 customers + 4 orders`() = runTest {
        val result = createSeeder().seedActiveWorkshop()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(8, customerRepository.customersList.size)
        // FakeOrderRepository exposes a writableOrders or similar field. Verify:
        // (Adapt to the actual fake's API once read.)
    }

    @Test
    fun `seedAllReconnect creates 6 customers and 6 delivered orders`() = runTest {
        val result = createSeeder().seedAllReconnect()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(6, customerRepository.customersList.size)
    }

    @Test
    fun `seed returns Failure when not signed in`() = runTest {
        authRepository.currentUser = null

        val result = createSeeder().seedBrandNew()

        assertTrue(result is SeedResult.Failure, "expected Failure, got $result")
    }
}
```

**Note:** Inspect `FakeOrderRepository` to discover the right field name for asserting orders were created. The plan above leaves a comment — fill it in based on what the fake exposes. Common patterns: `ordersList`, `lastCreatedOrder`, `createInvocationCount`.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :composeApp:compileTestKotlinDesktop
```
Expected: COMPILATION FAILED — `DefaultDebugSeeder` and `SeedResult` not defined.

- [ ] **Step 3: Create the interface + implementation**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSeeder.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore

sealed interface SeedResult {
    data object Success : SeedResult
    data class Failure(val reason: String) : SeedResult
}

interface DebugSeeder {
    suspend fun seedBrandNew(): SeedResult
    suspend fun seedActiveWorkshop(): SeedResult
    suspend fun seedAllReconnect(): SeedResult
    suspend fun wipeAllData(): SeedResult
}

class DefaultDebugSeeder(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
    private val now: () -> Long,
) : DebugSeeder {

    override suspend fun seedBrandNew(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        return SeedResult.Success
    }

    override suspend fun seedActiveWorkshop(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        val t = now()
        val customers = SeedFixtures.customers(userId, t)
        customers.forEach { customerRepository.createCustomer(userId, it) }
        // Measurements for first 4 customers
        customers.take(4).forEach { c ->
            measurementRepository.createMeasurement(userId, c.id, SeedFixtures.measurementsFor(c, t))
        }
        // Orders for first 4 customers
        SeedFixtures.activeOrders(customers, t).forEach { orderRepository.createOrder(userId, it) }
        return SeedResult.Success
    }

    override suspend fun seedAllReconnect(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        val t = now()
        val customers = SeedFixtures.allReconnectCustomers(userId, t)
        customers.forEach { customerRepository.createCustomer(userId, it) }
        SeedFixtures.allReconnectOrders(customers, t).forEach { orderRepository.createOrder(userId, it) }
        return SeedResult.Success
    }

    override suspend fun wipeAllData(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        return SeedResult.Success
    }

    private suspend fun wipeForUser(userId: String) {
        // Fetch current customers + orders and delete them. We can't trivially
        // wipe sub-collections from the client; the seeder cleans only what
        // surfaces via the observe flows. Real cleanup is the Cloud Function
        // (onAuthUserDeleted) — this is a tester convenience.
        val customers = currentCustomers(userId)
        val orders = currentOrders(userId)
        orders.forEach { orderRepository.deleteOrder(userId, it.id) }
        customers.forEach { customerRepository.deleteCustomer(userId, it.id) }
    }

    private suspend fun currentCustomers(userId: String):
            List<com.danzucker.stitchpad.core.domain.model.Customer> {
        return when (val r = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> emptyList()
        }
    }

    private suspend fun currentOrders(userId: String):
            List<com.danzucker.stitchpad.core.domain.model.Order> {
        return when (val r = orderRepository.observeOrders(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> emptyList()
        }
    }
}
```

Add the import at the top: `import kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Run the test — verify it passes**

```bash
./gradlew :composeApp:allTests --tests "*DefaultDebugSeederTest*"
```
Expected: PASS for all four tests. If a test fails, adapt the fake API references.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSeeder.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DefaultDebugSeederTest.kt
git commit -m "feat(debug): DebugSeeder interface + DefaultDebugSeeder with tests"
```

---

## Task 7: `DebugSessionActions` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActionsTest.kt`

- [ ] **Step 1: Write the failing test**

`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActionsTest.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugSessionActionsTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences
    private lateinit var sessionActions: DebugSessionActions

    @BeforeTest
    fun setUp() {
        authRepository = FakeAuthRepository()
        onboardingPreferences = FakeOnboardingPreferences().apply {
            onboardingSeen = true
            workshopSetupCompleted = true
        }
        sessionActions = DebugSessionActions(authRepository, onboardingPreferences)
    }

    @Test
    fun `resetOnboardingFlags clears both flags`() = runTest {
        sessionActions.resetOnboardingFlags()

        assertFalse(onboardingPreferences.onboardingSeen)
        assertFalse(onboardingPreferences.workshopSetupCompleted)
    }

    @Test
    fun `switchAccount returns ConfigurationMissing when creds blank`() = runTest {
        val result = sessionActions.switchAccount(email = "", password = "")

        assertTrue(result is SessionActionResult.ConfigurationMissing)
    }

    @Test
    fun `switchAccount signs out then signs in with given creds`() = runTest {
        authRepository.currentUser = com.danzucker.stitchpad.core.domain.model.User(
            id = "old-uid", email = "old@example.com", displayName = "Old",
            businessName = null, phoneNumber = null, whatsappNumber = null,
            avatarColorIndex = 0,
        )

        val result = sessionActions.switchAccount("fola@example.com", "password")

        assertTrue(result is SessionActionResult.Success, "expected Success, got $result")
        // FakeAuthRepository's signInWithEmail should record the email.
        // Adapt assertion to whatever the fake exposes.
    }
}
```

- [ ] **Step 2: Verify it fails to compile**

```bash
./gradlew :composeApp:compileTestKotlinDesktop
```
Expected: COMPILATION FAILED.

- [ ] **Step 3: Create the implementation**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt`:
```kotlin
package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore

sealed interface SessionActionResult {
    data object Success : SessionActionResult
    data class Failure(val reason: String) : SessionActionResult
    /** Test-account creds aren't filled in `debug-test-accounts.properties`. */
    data object ConfigurationMissing : SessionActionResult
}

class DebugSessionActions(
    private val authRepository: AuthRepository,
    private val onboardingPreferences: OnboardingPreferencesStore,
) {
    suspend fun resetOnboardingFlags() {
        onboardingPreferences.resetForDebug()
    }

    suspend fun signOut(): SessionActionResult = when (val r = authRepository.signOut()) {
        is Result.Success -> SessionActionResult.Success
        is Result.Error -> SessionActionResult.Failure(r.error.toString())
    }

    suspend fun switchAccount(email: String, password: String): SessionActionResult {
        if (email.isBlank() || password.isBlank()) return SessionActionResult.ConfigurationMissing
        authRepository.signOut()
        return when (val r = authRepository.signInWithEmail(email, password)) {
            is Result.Success -> SessionActionResult.Success
            is Result.Error -> SessionActionResult.Failure(r.error.toString())
        }
    }

    suspend fun deleteCurrentAccount(): SessionActionResult =
        when (val r = authRepository.deleteAccount()) {
            is Result.Success -> SessionActionResult.Success
            is Result.Error -> SessionActionResult.Failure(r.error.toString())
        }
}
```

- [ ] **Step 4: Run the test — verify it passes**

```bash
./gradlew :composeApp:allTests --tests "*DebugSessionActionsTest*"
```
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActions.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/debug/DebugSessionActionsTest.kt
git commit -m "feat(debug): DebugSessionActions for reset/switch/sign-out/delete"
```

---

## Task 8: `DebugMenuState` + `DebugMenuAction` + `DebugMenuEvent`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuState.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuAction.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuEvent.kt`

- [ ] **Step 1: Create the state**

`DebugMenuState.kt`:
```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.presentation.UiText

data class DebugMenuState(
    val isWorking: Boolean = false,
    val lastResult: UiText? = null,
    val testAccountsConfigured: Boolean = false,
)
```

- [ ] **Step 2: Create the action**

`DebugMenuAction.kt`:
```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

sealed interface DebugMenuAction {
    data object OnBackClick : DebugMenuAction
    data object OnSeedBrandNewClick : DebugMenuAction
    data object OnSeedActiveWorkshopClick : DebugMenuAction
    data object OnSeedAllReconnectClick : DebugMenuAction
    data object OnResetOnboardingClick : DebugMenuAction
    data object OnSignOutClick : DebugMenuAction
    data object OnSwitchToFolaClick : DebugMenuAction
    data object OnSwitchToGabbyClick : DebugMenuAction
    data object OnDeleteAllDataClick : DebugMenuAction
}
```

- [ ] **Step 3: Create the event**

`DebugMenuEvent.kt`:
```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface DebugMenuEvent {
    data object NavigateBack : DebugMenuEvent
    data object NavigateToLogin : DebugMenuEvent
    data class ShowSnackbar(val message: UiText) : DebugMenuEvent
}
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/
git commit -m "feat(debug): DebugMenu state/action/event types"
```

---

## Task 9: `DebugMenuViewModel` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugMenuViewModelTest {

    private lateinit var seeder: FakeDebugSeeder
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeOnboarding: FakeOnboardingPreferences
    private lateinit var sessionActions: DebugSessionActions

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        seeder = FakeDebugSeeder()
        fakeAuth = FakeAuthRepository().apply {
            currentUser = com.danzucker.stitchpad.core.domain.model.User(
                id = "test-uid", email = "test@example.com", displayName = "Test",
                businessName = null, phoneNumber = null, whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        fakeOnboarding = FakeOnboardingPreferences()
        sessionActions = DebugSessionActions(fakeAuth, fakeOnboarding)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(testAccountsConfigured: Boolean = true): DebugMenuViewModel {
        val vm = DebugMenuViewModel(
            seeder = seeder,
            sessionActions = sessionActions,
            testAccountsConfigured = testAccountsConfigured,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `initial state has testAccountsConfigured propagated`() = runTest {
        val vm = createViewModel(testAccountsConfigured = false)
        val state = vm.state.first()
        assertFalse(state.testAccountsConfigured)
    }

    @Test
    fun `OnSeedActiveWorkshopClick delegates to seeder and emits Snackbar on success`() = runTest {
        val vm = createViewModel()
        seeder.seedActiveWorkshopResult = SeedResult.Success

        vm.onAction(DebugMenuAction.OnSeedActiveWorkshopClick)

        assertEquals(1, seeder.seedActiveWorkshopCalls)
        assertNotNull(vm.state.first().lastResult)
    }

    @Test
    fun `OnSeedBrandNewClick reports failure via Snackbar`() = runTest {
        val vm = createViewModel()
        seeder.seedBrandNewResult = SeedResult.Failure("boom")

        vm.onAction(DebugMenuAction.OnSeedBrandNewClick)

        assertNotNull(vm.state.first().lastResult)
    }

    @Test
    fun `OnResetOnboardingClick clears onboarding flags`() = runTest {
        fakeOnboarding.onboardingSeen = true
        fakeOnboarding.workshopSetupCompleted = true
        val vm = createViewModel()

        vm.onAction(DebugMenuAction.OnResetOnboardingClick)

        assertFalse(fakeOnboarding.onboardingSeen)
        assertFalse(fakeOnboarding.workshopSetupCompleted)
    }

    @Test
    fun `OnSignOutClick on success emits NavigateToLogin event`() = runTest {
        val vm = createViewModel()

        val events = mutableListOf<DebugMenuEvent>()
        backgroundScope.launch(Dispatchers.Main) {
            vm.events.collect { events.add(it) }
        }
        vm.onAction(DebugMenuAction.OnSignOutClick)

        assertTrue(events.any { it is DebugMenuEvent.NavigateToLogin })
    }

    private class FakeDebugSeeder : DebugSeeder {
        var seedBrandNewResult: SeedResult = SeedResult.Success
        var seedActiveWorkshopResult: SeedResult = SeedResult.Success
        var seedAllReconnectResult: SeedResult = SeedResult.Success
        var wipeResult: SeedResult = SeedResult.Success
        var seedBrandNewCalls = 0
        var seedActiveWorkshopCalls = 0

        override suspend fun seedBrandNew(): SeedResult { seedBrandNewCalls++; return seedBrandNewResult }
        override suspend fun seedActiveWorkshop(): SeedResult { seedActiveWorkshopCalls++; return seedActiveWorkshopResult }
        override suspend fun seedAllReconnect(): SeedResult = seedAllReconnectResult
        override suspend fun wipeAllData(): SeedResult = wipeResult
    }
}
```

The test uses the real `DebugSessionActions` class (not a shim) wired to `FakeAuthRepository` + `FakeOnboardingPreferences` — fewer moving parts, no interface extraction needed.

- [ ] **Step 2: Verify it fails to compile**

```bash
./gradlew :composeApp:compileTestKotlinDesktop
```
Expected: COMPILATION FAILED — `DebugMenuViewModel` not defined.

- [ ] **Step 3: Create the ViewModel**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModel.kt`:
```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DebugTestAccounts
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.core.debug.SessionActionResult
import com.danzucker.stitchpad.core.presentation.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DebugMenuViewModel(
    private val seeder: DebugSeeder,
    private val sessionActions: DebugSessionActions,
    private val testAccountsConfigured: Boolean = DebugTestAccounts.isConfigured,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DebugMenuState(testAccountsConfigured = testAccountsConfigured)
    )
    val state = _state.asStateFlow()

    private val _events = Channel<DebugMenuEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: DebugMenuAction) {
        when (action) {
            DebugMenuAction.OnBackClick -> emit(DebugMenuEvent.NavigateBack)
            DebugMenuAction.OnSeedBrandNewClick -> runSeed { seeder.seedBrandNew() }
            DebugMenuAction.OnSeedActiveWorkshopClick -> runSeed { seeder.seedActiveWorkshop() }
            DebugMenuAction.OnSeedAllReconnectClick -> runSeed { seeder.seedAllReconnect() }
            DebugMenuAction.OnResetOnboardingClick -> runJob {
                sessionActions.resetOnboardingFlags()
                _state.update { it.copy(lastResult = UiText.DynamicString("Onboarding flags reset")) }
            }
            DebugMenuAction.OnSignOutClick -> runJob {
                val r = sessionActions.signOut()
                if (r is SessionActionResult.Success) {
                    emit(DebugMenuEvent.NavigateToLogin)
                } else {
                    _state.update { it.copy(lastResult = UiText.DynamicString("Sign-out failed")) }
                }
            }
            DebugMenuAction.OnSwitchToFolaClick -> runJob {
                handleSwitch(sessionActions.switchAccount(
                    DebugTestAccounts.FOLA_EMAIL, DebugTestAccounts.FOLA_PASSWORD,
                ))
            }
            DebugMenuAction.OnSwitchToGabbyClick -> runJob {
                handleSwitch(sessionActions.switchAccount(
                    DebugTestAccounts.GABBY_EMAIL, DebugTestAccounts.GABBY_PASSWORD,
                ))
            }
            DebugMenuAction.OnDeleteAllDataClick -> runJob {
                val r = sessionActions.deleteCurrentAccount()
                if (r is SessionActionResult.Success) {
                    emit(DebugMenuEvent.NavigateToLogin)
                } else {
                    _state.update { it.copy(lastResult = UiText.DynamicString("Delete failed")) }
                }
            }
        }
    }

    private fun runSeed(block: suspend () -> SeedResult) = runJob {
        val r = block()
        _state.update {
            it.copy(lastResult = when (r) {
                SeedResult.Success -> UiText.DynamicString("Seed complete")
                is SeedResult.Failure -> UiText.DynamicString("Seed failed: ${r.reason}")
            })
        }
    }

    private fun runJob(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun handleSwitch(r: SessionActionResult) {
        when (r) {
            SessionActionResult.Success -> {
                _state.update { it.copy(lastResult = UiText.DynamicString("Switched accounts")) }
                emit(DebugMenuEvent.NavigateToLogin)
            }
            SessionActionResult.ConfigurationMissing -> _state.update {
                it.copy(lastResult = UiText.DynamicString("Test creds not configured"))
            }
            is SessionActionResult.Failure -> _state.update {
                it.copy(lastResult = UiText.DynamicString("Switch failed: ${r.reason}"))
            }
        }
    }

    private fun emit(event: DebugMenuEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
```


- [ ] **Step 4: Run the test — verify it passes**

```bash
./gradlew :composeApp:allTests --tests "*DebugMenuViewModelTest*"
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModel.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuViewModelTest.kt
git commit -m "feat(debug): DebugMenuViewModel with seeding + session-action handlers"
```

---

## Task 10: `DebugMenuScreen` (stateless UI)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuScreen.kt`

No tests for the pure-Compose stateless screen — covered by manual smoke test.

- [ ] **Step 1: Inspect existing Screen pattern for conventions**

```bash
sed -n '1,80p' composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt
```
This shows the project's standard Scaffold + TopAppBar + LazyColumn structure with shared `SettingsRow`/`SettingsSectionCard` components.

- [ ] **Step 2: Write the screen**

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuScreen.kt`:
```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRow
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    state: DebugMenuState,
    snackbarHostState: SnackbarHostState,
    onAction: (DebugMenuAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Debug menu") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.lg),
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxSize())
            }

            SettingsSectionCard(title = "Seed data") {
                SettingsRow(icon = Icons.Outlined.BugReport, label = "Brand-new tailor",
                    onClick = { onAction(DebugMenuAction.OnSeedBrandNewClick) })
                SettingsRow(icon = Icons.Outlined.BugReport, label = "Active workshop",
                    onClick = { onAction(DebugMenuAction.OnSeedActiveWorkshopClick) })
                SettingsRow(icon = Icons.Outlined.BugReport, label = "All-reconnect",
                    onClick = { onAction(DebugMenuAction.OnSeedAllReconnectClick) })
            }

            SettingsSectionCard(title = "Session") {
                SettingsRow(icon = Icons.Outlined.Refresh, label = "Reset onboarding flags",
                    onClick = { onAction(DebugMenuAction.OnResetOnboardingClick) })
                SettingsRow(icon = Icons.AutoMirrored.Outlined.Logout, label = "Sign out",
                    onClick = { onAction(DebugMenuAction.OnSignOutClick) })
            }

            SettingsSectionCard(title = "Switch account") {
                SettingsRow(
                    icon = Icons.Outlined.SwapHoriz,
                    label = if (state.testAccountsConfigured) "Switch to Fola"
                            else "Switch to Fola (not configured)",
                    onClick = { onAction(DebugMenuAction.OnSwitchToFolaClick) },
                )
                SettingsRow(
                    icon = Icons.Outlined.SwapHoriz,
                    label = if (state.testAccountsConfigured) "Switch to Gabby"
                            else "Switch to Gabby (not configured)",
                    onClick = { onAction(DebugMenuAction.OnSwitchToGabbyClick) },
                )
            }

            SettingsSectionCard(title = "Danger zone") {
                SettingsRow(icon = Icons.Outlined.DeleteForever, label = "Delete all my data",
                    onClick = { onAction(DebugMenuAction.OnDeleteAllDataClick) })
            }
        }
    }
}

@Preview
@Composable
private fun DebugMenuScreenPreview() {
    StitchPadTheme {
        DebugMenuScreen(
            state = DebugMenuState(testAccountsConfigured = true),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}
```

**Note on SettingsSectionCard / SettingsRow:** verify they live at `feature/settings/presentation/components/` per the earlier file listing. If their signatures differ from `(title: String, content: @Composable -> Unit)` or `(icon, label, onClick)`, adapt.

- [ ] **Step 3: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuScreen.kt
git commit -m "feat(debug): DebugMenuScreen stateless UI with section cards"
```

---

## Task 11: `DebugMenuRoot`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuRoot.kt`

- [ ] **Step 1: Write the Root composable**

```kotlin
package com.danzucker.stitchpad.feature.debug.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DebugMenuRoot(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: DebugMenuViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DebugMenuEvent.NavigateBack -> onNavigateBack()
            DebugMenuEvent.NavigateToLogin -> onNavigateToLogin()
            is DebugMenuEvent.ShowSnackbar -> {
                scope.launch {
                    val msg = when (val t = event.message) {
                        is UiText.DynamicString -> t.value
                        is UiText.StringResourceText -> getString(t.id)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    DebugMenuScreen(state = state, snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction)
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/debug/presentation/DebugMenuRoot.kt
git commit -m "feat(debug): DebugMenuRoot wiring VM + ObserveAsEvents"
```

---

## Task 12: `DebugModule` (Koin)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DefaultDebugSeeder
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuViewModel
import kotlinx.datetime.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val debugModule = module {
    single<DebugSeeder> {
        DefaultDebugSeeder(
            customerRepository = get(),
            orderRepository = get(),
            measurementRepository = get(),
            authRepository = get(),
            onboardingPreferences = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    single { DebugSessionActions(authRepository = get(), onboardingPreferences = get()) }
    // Explicit `viewModel { }` factory rather than viewModelOf(::DebugMenuViewModel) because
    // the VM takes a defaulted Boolean param (testAccountsConfigured) — see
    // feedback_ios_clock_injection memory for why viewModelOf can't skip lambda defaults.
    viewModel { DebugMenuViewModel(seeder = get(), sessionActions = get()) }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/DebugModule.kt
git commit -m "feat(debug): Koin module for seeder, session actions, ViewModel"
```

---

## Task 13: Add `DebugMenuRoute` and wire into NavHost

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/StitchPadNavHost.kt`

- [ ] **Step 1: Add the route**

Append to `Routes.kt`:
```kotlin
@Serializable
data object DebugMenuRoute
```

- [ ] **Step 2: Register the route in NavHost**

Read the existing structure first:
```bash
sed -n '1,40p' composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/StitchPadNavHost.kt
```

Add this import:
```kotlin
import com.danzucker.stitchpad.feature.debug.presentation.DebugMenuRoot
```

Inside the `NavHost { }` block (e.g., next to the other `composable<>` registrations near the bottom):

```kotlin
composable<DebugMenuRoute> {
    DebugMenuRoot(
        onNavigateBack = { navController.navigateUp() },
        onNavigateToLogin = {
            navController.navigate(LoginRoute) {
                popUpTo(HomeRoute) { inclusive = true }
            }
        },
    )
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/
git commit -m "feat(debug): wire DebugMenuRoute into nav graph"
```

---

## Task 14: Include `debugModule` in `initKoin`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt`

- [ ] **Step 1: Add the conditional**

In `initKoin`, near the bottom of the module list, before the closing `)` of `modules(...)`:

```kotlin
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.di.debugModule
// ... in the modules(...) call, last line:
            platformModule,
        )
        if (isDebugBuild) {
            modules(debugModule)
        }
    }
}
```

(The `if (isDebugBuild) { modules(debugModule) }` line goes between the closing `)` of `modules(...)` and the closing `}` of `startKoin { }`.)

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt
git commit -m "feat(debug): conditionally include debugModule in Koin graph"
```

---

## Task 15: Add Settings entry row + handler

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsEvent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsRoot.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/StitchPadNavHost.kt`

- [ ] **Step 1: Add the action**

In `SettingsAction.kt`, append before the closing `}`:
```kotlin
    data object OnDebugMenuClick : SettingsAction
```

- [ ] **Step 2: Add the event**

In `SettingsEvent.kt`, append before the closing `}` of the sealed interface:
```kotlin
    data object NavigateToDebugMenu : SettingsEvent
```

- [ ] **Step 3: Handle the action in the ViewModel**

In `SettingsViewModel.kt` `onAction`'s `when`:
```kotlin
            SettingsAction.OnDebugMenuClick -> emit(SettingsEvent.NavigateToDebugMenu)
```

- [ ] **Step 4: Render the gated row in the Screen**

In `SettingsScreen.kt`, after all other section cards, add a gated section:
```kotlin
import com.danzucker.stitchpad.core.debug.isDebugBuild
import androidx.compose.material.icons.outlined.BugReport
// ... at the end of the Column, after the last SettingsSectionCard:
            if (isDebugBuild) {
                SettingsSectionCard(title = "Debug") {
                    SettingsRow(
                        icon = Icons.Outlined.BugReport,
                        label = "Debug menu",
                        onClick = { onAction(SettingsAction.OnDebugMenuClick) },
                    )
                }
            }
```

- [ ] **Step 5: Observe the event in the Root and pass through to nav**

In `SettingsRoot.kt`, add a new parameter `onNavigateToDebugMenu: () -> Unit` and handle the event:
```kotlin
@Composable
fun SettingsRoot(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onNavigateToDebugMenu: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // ... existing body, then in the ObserveAsEvents when:
    when (event) {
        // ... existing branches ...
        SettingsEvent.NavigateToDebugMenu -> onNavigateToDebugMenu()
        // ...
    }
}
```

- [ ] **Step 6: Wire SettingsRoot's new callback in NavHost**

In `StitchPadNavHost.kt`, find the existing `SettingsRoot(...)` call (likely inside the `MainRoot` graph nested somewhere — adapt to actual structure) and pass:
```kotlin
onNavigateToDebugMenu = { navController.navigate(DebugMenuRoute) },
```

If `SettingsRoot` is reached through `MainRoot`'s internal nav rather than the top-level `StitchPadNavHost`, propagate the callback through `MainRoot` too.

- [ ] **Step 7: Verify compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run all tests**

```bash
./gradlew :composeApp:allTests
```
Expected: All PASS (no regressions).

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/ \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/StitchPadNavHost.kt
git commit -m "feat(settings): debug menu entry row (gated by isDebugBuild)"
```

---

## Task 16: Manual smoke test — Android

Per the QA workflow memory, every PR documents manual smoke steps. Run these on a debug Android build before opening the PR.

- [ ] **Step 1: Build and install on a real device or simulator**

```bash
./gradlew :composeApp:installDebug
```
Open the app, sign in as Fola (or any test account).

- [ ] **Step 2: Verify Debug menu entry exists**

Navigate: Bottom nav → Settings → scroll to the bottom.
Expected: A "Debug" section card with one row labeled "Debug menu". Tap it.

- [ ] **Step 3: Test each seed action**

In the Debug menu:
- Tap **Active workshop** → wait for Snackbar "Seed complete".
- Navigate back to Dashboard → verify the dashboard shows the populated state (8 customers, multiple orders, populated heroes).
- Return to Debug menu → tap **Brand-new tailor** → back to Dashboard → verify the dashboard reverts to the `BrandNew` hero.
- Return → tap **All-reconnect** → back to Dashboard → verify the `AllReconnect` hero appears.

- [ ] **Step 4: Test reset onboarding flags**

- Debug menu → **Reset onboarding flags** → Snackbar "Onboarding flags reset".
- Force-kill the app, re-open.
- Expected: app routes through Onboarding → Workshop setup (the post-signup screen) before reaching Home.

- [ ] **Step 5: Test account switching (if configured)**

Only if you have `composeApp/debug-test-accounts.properties` set up with real creds:
- Debug menu → **Switch to Gabby** → app routes to Login, then signs in as Gabby.
- Debug menu → **Switch to Fola** → back to Fola.

If creds aren't configured, the buttons should show "(not configured)" in their label and tapping shows a "Test creds not configured" Snackbar.

- [ ] **Step 6: Test sign out**

- Debug menu → **Sign out** → app routes to Login.

- [ ] **Step 7: Test delete all data**

- Sign back in. Debug menu → **Delete all my data** → app routes to Login. Verify in Firebase console that the auth user and Firestore data for that account are gone.

- [ ] **Step 8: Verify release build does NOT show the menu**

```bash
./gradlew :composeApp:assembleRelease
# Install the resulting APK manually (release builds need signing config — may not be available locally)
```
If release signing isn't configured locally, skip this step and rely on Play Store internal-testing track verification post-merge.

If the release APK can be installed: open Settings → confirm no "Debug" section appears.

---

## Task 17: Manual smoke test — iOS

- [ ] **Step 1: Build and run on iOS Simulator (iPhone 17 Pro per test-environment memory)**

Open `iosApp/iosApp.xcodeproj` in Xcode. Select iPhone 17 Pro simulator. Run.

- [ ] **Step 2: Repeat steps 2–6 from Task 16**

Same flows: Settings → Debug menu → each seed action → reset onboarding → sign out → switch account.

- [ ] **Step 3: Verify release configuration does NOT show the menu**

In Xcode → Edit Scheme → Run → set Build Configuration to **Release**. Run on simulator (no codesigning required).

Expected: Settings has NO Debug section row at the bottom. (`Platform.isDebugBinary` should return `false` because Kotlin/Native compiled with `-opt`.)

If the row appears in the release configuration, `Platform.isDebugBinary` isn't being set correctly. Diagnose:
- Check the Gradle iOS framework task that ran (`linkReleaseFrameworkIosSimulatorArm64` vs `linkDebugFrameworkIosSimulatorArm64`)
- Verify by adding a temp `println(Platform.isDebugBinary)` in `MainViewController.kt` and reading the Xcode console.

---

## Task 18: Open PR with full smoke documentation

- [ ] **Step 1: Push branch**

```bash
git push -u origin feature/debug-menu
```

- [ ] **Step 2: Open PR**

```bash
gh pr create --title "feat(debug): Debug menu V1 — seed/reset for Daniel + testers" --body "$(cat <<'EOF'
## Summary
- Internal debug menu reachable from Settings → Debug menu (gated by `isDebugBuild`)
- Tier 1 actions: 3 seed scenarios (brand-new, active workshop, all-reconnect), reset onboarding, sign out, switch test account, delete all data
- Works on both Android (BuildConfig.DEBUG) and iOS (Platform.isDebugBinary)
- Test-account creds via gitignored `debug-test-accounts.properties` + build-time generation

Spec: `docs/superpowers/specs/2026-05-14-debug-menu-design.md`
Plan: `docs/superpowers/plans/2026-05-14-debug-menu.md`

## Test plan
- [ ] Android debug: Settings → Debug menu visible
- [ ] Android: each seed action produces expected dashboard state
- [ ] Android: Reset onboarding routes through Onboarding+Workshop setup on next launch
- [ ] Android: Switch to Fola/Gabby works (if creds configured) or shows "not configured"
- [ ] Android: Sign out and Delete all data route to Login
- [ ] iOS debug (iPhone 17 Pro sim): same flows above pass
- [ ] iOS release configuration: Debug section row does NOT appear in Settings
- [ ] No regression in existing settings flows (sign out, edit profile, etc.)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-review checklist (for the implementer)

Before marking the PR ready:

- [ ] All `compileDebugKotlinAndroid` + `compileKotlinIosSimulatorArm64` pass
- [ ] `./gradlew :composeApp:allTests` passes (no regressions in existing tests)
- [ ] `./gradlew detekt` passes
- [ ] No `TODO` / `FIXME` left in new files
- [ ] `debug-test-accounts.properties` is NOT in the diff (verify with `git diff main --stat | grep -i properties`)
- [ ] All seed actions produce the expected dashboard state per spec
- [ ] iOS release configuration genuinely hides the Debug section row
- [ ] The PR description's test plan is checked off
