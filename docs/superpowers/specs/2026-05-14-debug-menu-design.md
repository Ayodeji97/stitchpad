# Debug Menu V1 — Design Spec

**Date:** 2026-05-14
**Branch:** `feature/debug-menu`
**Owner:** Daniel Ogunleye
**Related:** Settings redesign (PR #35, merged 2026-05-14), PM intern tester recruitment, Freemium PR (paused; will absorb Tier 3)

## Goal

Add an internal debug menu, reachable from Settings on **both Android and iOS**, that lets Daniel (QA) and recruited tailor-testers reach realistic app state in two taps instead of laboriously creating data through the app or waiting for time-dependent logic. The menu's entry point is hidden in release builds; the menu code itself ships in release binaries but is unreachable.

## Motivation

Three forces converge on this need now:

1. **Tester recruitment is imminent.** The PM intern is recruiting 5–10 Nigerian tailors. They will receive debug builds — Android APKs primarily but iOS TestFlight builds too. If they open the app and see an empty dashboard, the eval is meaningless — they need realistic data on first launch.
2. **State diversity has outgrown manual setup.** `DashboardUiState` alone has 8 sealed variants (Loading, BrandNew, several Empty / Reconnect heroes, populated). Reaching each currently requires real Firestore data manipulation. Setup checklist and dashboard hero states all hinge on data presence (customers, orders, measurements, styles).
3. **Smoke-test discipline.** Per the QA smoke-test memory, every PR needs manual smoke steps. Without scripted state, smoke testing is slow and inconsistent. The seed actions formalize "the state this PR is supposed to fix."

A debug menu attacks all three at once, and is small enough to ship in one PR before testers arrive.

## Out of scope (V1)

- **Tier 2 — Screens playground** (jump to any screen forced into a chosen `UiState` variant). Compose `@Preview` annotations already cover most of this for self-review. Defer; only revisit if a third PR ends up manually scripting state.
- **Tier 3 — Force flags** (entitlements Free/Trial/Pro toggle, force-`today` offsets, force-network-offline). Co-dependent on the paused freemium PR. Will be added inside that PR when it resumes.
- **Tier 4 — Diagnostics** (build version, current UID, FCM token, copy-last-N-log-lines, intentional crash button). Nice-to-have once testers are in the wild and need a "paste this when reporting" blob. Out of V1.
- **True strip-from-release via source-set splitting.** Considered and rejected for V1 — Android variant source sets would strip the menu from the release APK, and an iOS `iosDebugMain` custom source set would do the same for iOS. Both are ~1 day of Gradle work and risk getting stuck on KMP plugin quirks. V1 ships the menu in `commonMain` gated by a compile-time-evaluable boolean (`isDebugBuild`), accepting that the menu code is present-but-unreachable in release binaries. App Review allows dead code; the principle of stripping is good hygiene we revisit in a hardening pass. See "Hardening — future" below.
- **Persistent seeded state across processes.** Seeds write to the current user's Firestore — they're real data the user can delete via "Delete all my data" or via uninstall.
- **Multi-tenant seed isolation.** Seeds target the currently signed-in user only. If two testers share a Firebase account they'll see each other's seeded data — fine, since they shouldn't be sharing accounts.

## Architecture

### One feature package in `commonMain`, gated by `isDebugBuild`

All debug code — orchestration, ViewModel, screens — lives in `commonMain` and runs identically on Android and iOS. A single compile-time-evaluable `expect val isDebugBuild: Boolean` decides whether the Settings entry row renders. When `false`, the row is hidden and the route is never navigated to — the menu's code is present in the binary but unreachable.

**Why `commonMain` instead of source-set splitting:** See "Out of scope" above and the decision log below. Short version: source-set splitting on iOS requires custom Gradle wiring that risks a multi-day spike; commonMain ships in hours, works identically on both platforms, and preserves the upgrade path.

**Why a compile-time boolean instead of a runtime feature flag:** `BuildConfig.DEBUG` on Android and `kotlin.native.Platform.isDebugBinary` on iOS are both compile-time evaluable. The Kotlin compiler can dead-code-eliminate `if (isDebugBuild) { ... }` blocks in release builds at compile time (when minification is enabled) or at runtime (when it isn't). Either way, the user never sees the row.

### `isDebugBuild` definition

```kotlin
// commonMain/core/debug/DebugBuild.kt
expect val isDebugBuild: Boolean

// androidMain/core/debug/DebugBuild.android.kt
actual val isDebugBuild: Boolean = BuildConfig.DEBUG

// iosMain/core/debug/DebugBuild.ios.kt
import kotlin.native.Platform
actual val isDebugBuild: Boolean = Platform.isDebugBinary
```

`Platform.isDebugBinary` is a Kotlin/Native stdlib property that reflects whether the binary was compiled with `-opt` (release) or without (debug). It's set at the Gradle framework-build level (`linkDebugFrameworkIosX` vs `linkReleaseFrameworkIosX`) which Xcode picks based on its CONFIGURATION setting. No extra wiring needed.

### Package structure

```
composeApp/
├── debug-test-accounts.properties           // GITIGNORED — Daniel's local creds
├── debug-test-accounts.properties.example   // committed template, empty values
│
└── src/
    ├── commonMain/kotlin/com/danzucker/stitchpad/
    │   ├── core/debug/
    │   │   ├── DebugBuild.kt              // expect val isDebugBuild
    │   │   ├── DebugSeeder.kt             // interface + DefaultDebugSeeder
    │   │   ├── SeedFixtures.kt            // brand-new, active-workshop, all-reconnect data
    │   │   └── DebugSessionActions.kt     // sign-out, switch account, reset onboarding flags
    │   ├── feature/debug/presentation/
    │   │   ├── DebugMenuRoot.kt           // real composable, calls into DebugMenuViewModel
    │   │   ├── DebugMenuViewModel.kt
    │   │   ├── DebugMenuScreen.kt
    │   │   ├── DebugMenuState.kt
    │   │   ├── DebugMenuAction.kt
    │   │   └── DebugMenuEvent.kt
    │   ├── navigation/Routes.kt           // + @Serializable data object DebugMenuRoute
    │   └── di/DebugModule.kt              // Koin module
    │
    ├── androidMain/kotlin/com/danzucker/stitchpad/core/debug/
    │   └── DebugBuild.android.kt          // actual: BuildConfig.DEBUG
    │
    └── iosMain/kotlin/com/danzucker/stitchpad/core/debug/
        └── DebugBuild.ios.kt              // actual: Platform.isDebugBinary

# generated at build time (not committed, not under src/):
composeApp/build/generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug/
└── DebugTestAccounts.kt                   // generated from the properties file;
                                           // commonMain.kotlin.srcDir() points here so it
                                           // is visible to commonMain code as if hand-written
```

### Wiring in existing code

**Settings home** (`feature/settings/presentation/home/SettingsScreen.kt`) gets a one-line addition in its last section:

```kotlin
if (isDebugBuild) {
    SettingsRow(
        icon = Icons.Outlined.BugReport,
        label = "Debug menu",
        onClick = { onAction(SettingsAction.OnDebugMenuClick) },
    )
}
```

`SettingsAction.OnDebugMenuClick` emits a `SettingsEvent.NavigateToDebugMenu` consumed by `SettingsRoot`, which calls `navController.navigate(DebugMenuRoute)`.

**Navigation** (`navigation/Routes.kt` + `StitchPadNavHost`): a new `@Serializable data object DebugMenuRoute` is added and registered in `StitchPadNavHost` with a `composable<DebugMenuRoute> { DebugMenuRoot(onClose = { navController.navigateUp() }) }` entry. The route exists for both platforms; the `isDebugBuild` gate on the Settings row prevents non-debug builds from ever navigating to it.

**Koin DI** (`StitchPadApplication.kt` Android + `Koin.ios.kt` iOS startKoin block): the existing `if (isDebuggable) ...` pattern in `StitchPadApplication.kt` is extended to include `debugModule` (defined in `commonMain/di/DebugModule.kt`). On iOS, the equivalent block in `Koin.ios.kt` does the same. Release Koin graphs don't load `debugModule`; release UI never tries to instantiate `DebugMenuViewModel`, so even if the module were loaded, no harm done.

## Tier 1 actions — V1 scope

### Seed: brand-new tailor
Wipes all Firestore data for the current user (customers, orders, measurements, styles). After this, the dashboard renders its `BrandNew` hero. The workshop-setup flag is left set, so we don't bounce the user out of the app.

### Seed: active workshop
Inserts a coherent set:
- 8 customers (varied names, mix of WhatsApp / no-WhatsApp)
- 4 customers each get one measurement
- 2 customers each get one style (with placeholder photos from `composeResources`)
- 4 orders in various states: 1 InProgress, 1 ReadyForPickup, 1 Delivered (last week), 1 Cancelled
- 2 of the orders are 7-day-old, 1 is 3-day-old, 1 is same-day

Fixtures are deterministic — same data every time the seed runs. Lets us write smoke-test scripts that reference "Customer #3" reliably.

### Seed: all-reconnect
6 customers, each with their last order Delivered 100+ days ago. Forces the dashboard `AllReconnect` hero so we can review that promoted state.

### Reset onboarding flags
Clears `hasSeenOnboarding` and `hasCompletedWorkshopSetup` in `OnboardingPreferences`. Next cold launch routes through Onboarding → Workshop setup. Does **not** sign the user out.

### Sign out
Same as Settings' Sign out — duplicated here so testers don't have to back out two screens to switch accounts.

### Switch to Fola / Switch to Gabby
Two buttons that sign out then sign in with the test creds from `reference_test_environment`. Credentials live in a **build-time-generated** `DebugTestAccounts.kt` file:

- A gitignored `composeApp/debug-test-accounts.properties` file holds the actual creds locally on Daniel's machine.
- A committed `composeApp/debug-test-accounts.properties.example` ships as a template with empty values.
- A Gradle task reads `debug-test-accounts.properties` at build time and generates `commonMain/build/generated/debug-test-accounts/kotlin/.../DebugTestAccounts.kt` with the values baked in.
- If the properties file is missing, the task generates `DebugTestAccounts.kt` with empty-string defaults so compile doesn't fail. The "Switch account" buttons appear but display a `"creds not configured"` Snackbar when tapped.

This pattern works for both Android and iOS since the generated Kotlin file is in `commonMain`. **Important: only the properties file is gitignored — never commit real creds.**

### Delete all my data
Calls the same `onAuthUserDeleted` path PR #38 wired up — actually deletes the auth user + Firestore + Storage. After this, the app routes back to Login. Equivalent to Settings' "Delete account" but reachable from the debug menu so it's a one-tap reset for testers.

## State and data flow

`DebugMenuViewModel` holds:

```kotlin
data class DebugMenuState(
    val isWorking: Boolean = false,
    val lastResult: UiText? = null,   // shown in a Snackbar
)

sealed interface DebugMenuAction {
    data object OnSeedBrandNew : DebugMenuAction
    data object OnSeedActiveWorkshop : DebugMenuAction
    data object OnSeedAllReconnect : DebugMenuAction
    data object OnResetOnboardingFlags : DebugMenuAction
    data object OnSignOut : DebugMenuAction
    data class OnSwitchAccount(val account: TestAccount) : DebugMenuAction
    data object OnDeleteAllData : DebugMenuAction
}

sealed interface DebugMenuEvent {
    data class SignedOut(val routeToLogin: Boolean) : DebugMenuEvent
    data object DeletedAccount : DebugMenuEvent  // routes to Login
    data class SeedFailed(val message: UiText) : DebugMenuEvent
}
```

Each action delegates to `DebugSeeder` or `DebugSessionActions`. Result of every action is surfaced as a Snackbar via `DebugMenuState.lastResult` — per the notification-patterns memory, no Toasts.

The ViewModel is registered in `DebugModule.kt`. The Koin startup block conditionally includes it when `isDebugBuild` is true.

## Error handling

Per project convention, all paths return `Result<T, DataError>`. Failures map to `UiText` via existing `toUiText()` extensions and surface as a Snackbar. No crashes — even if a seed action fails halfway through, partial state stays in Firestore and the user can re-run "brand-new" to wipe and start over.

The seeders use `runCatching` boundaries: if one seed write fails, the remaining ones still run (best-effort) and the Snackbar reports `"Seeded N of M items (some failed)"`. Reasoning: testers don't care about strict transactionality; they want to get to a usable state. If we hit a systemic failure (auth lost), the existing repository auth-guard returns `Result.Error(AuthError.NotSignedIn)` and the ViewModel routes to Login.

## Testing

**Smoke test (manual, per QA workflow):**
1. Sign in as Fola on debug Android build.
2. Settings → scroll to bottom → tap **Debug menu**.
3. Tap **Seed: active workshop** → Snackbar shows success → back to dashboard → see 8 customers, 4 orders, populated state with Setup checklist dismissed.
4. Tap **Seed: brand-new tailor** → Snackbar shows success → dashboard reverts to BrandNew hero, onboarding flags **not** reset.
5. Tap **Reset onboarding flags** → kill app → re-open → routes through Onboarding then Workshop setup.
6. Tap **Switch to Gabby** → app signs out, signs in as Gabby, lands on Gabby's dashboard. Repeat **Switch to Fola** to return.
7. Repeat steps 2–6 on iOS debug build (iPhone 17 Pro sim per `reference_test_environment`).
8. Build Android release variant → install → confirm Settings has no Debug menu row at the bottom of the screen.
9. Build iOS release configuration → install on a sim → confirm Settings has no Debug menu row.

**Unit tests:**
- `SeedFixtures` is a pure data file — no tests beyond compile.
- `DefaultDebugSeeder` gets unit tests in `commonTest` (since it's in commonMain) that fake the repositories and assert the right writes happen for each seed scenario.
- `DebugMenuViewModel` follows the standard android-testing pattern: Turbine on state flow, AssertK on emissions, `UnconfinedTestDispatcher`, fake `DebugSeeder`. Tests live in `commonTest`.

## Risks and open questions

- **Debug code lives in release binaries.** This is the explicit V1 trade-off. App Review allows dead code; the symbol-name visibility (`DebugMenuRoot`, `DebugSeeder`, etc. in the release APK / iOS framework) is the cost of avoiding the Gradle spike. Hardening pass (see below) addresses this.
- **`Platform.isDebugBinary` reliability.** It reflects Kotlin/Native's `-opt` flag, which the standard `linkReleaseFramework*` task sets. If we ever introduce a custom Kotlin/Native task that builds without `-opt`, `isDebugBinary` could return true unexpectedly. Not a current concern. Verify during smoke testing on a release-config iOS build.
- **Test-account credentials.** Properties file is gitignored. Daniel will need to recreate `debug-test-accounts.properties` after any fresh clone. Mitigation: the `.example` template is committed, and the README's "Debug builds" section will document the one-line creation. If the properties file is absent, the "Switch account" buttons exist but show "creds not configured" — the app doesn't fail to build.
- **Crashlytics noise from debug seeds.** Crashlytics is already disabled in debug builds per `StitchPadApplication.kt`. No noise risk.
- **What if a tester gets a release build by accident?** They get no debug menu — entry row is hidden. They'd have to manually create data — same as the current state. No regression.

## Hardening — future

Two follow-up improvements when there's time, neither required for V1 to be useful:

1. **Source-set splitting on Android.** Move `core/debug/` and `feature/debug/presentation/` into an AGP debug variant source set (`src/debug/kotlin/`) so the release APK literally doesn't contain the code. ~1 day of work.
2. **iOS source-set splitting via custom `iosDebugMain`.** Mirror the Android approach on iOS by declaring a custom Kotlin source set that participates only in `linkDebugFramework*` tasks. Requires Gradle wiring for the framework variant selection. ~1–2 days, with KMP plugin compatibility risk.

Both can be done as a single hardening PR after we have one or two more KMP-grade Gradle changes under our belt. Neither blocks shipping V1 or onboarding testers.

## Decision log

| Decision | Chosen | Alternative considered | Reason |
|---|---|---|---|
| Strip-from-release mechanism | `isDebugBuild` compile-time-evaluable expect val; code lives in commonMain | Source-set splitting on both Android and iOS | Source-set split on iOS requires a custom Kotlin source set with framework-variant wiring (~1 day with plugin-quirk risk). V1 ships hours sooner with `isDebugBuild`; hardening preserved as a follow-up. App Review allows dead code in release. |
| Where seed orchestration lives | `commonMain/core/debug/` | Android-debug source set only | iOS needs functional debug menu too. CommonMain gives both platforms identical behavior without duplicating orchestration. |
| iOS debug-build detection | `kotlin.native.Platform.isDebugBinary` | Custom Gradle property + generated file, or compile-flag preprocessing | Stdlib-supported, reflects the `-opt` flag automatically. Zero extra wiring. |
| Account-switcher creds storage | Build-time-generated Kotlin file from gitignored properties | Hard-coded in source (committed), or runtime-read from a bundled JSON | Test creds are real Firebase auth — keep out of git history. Build-time generation works on both platforms (KMP-aware). |
| V1 scope | Tier 1 only (seed/reset) | All three tiers (seed/reset + screens playground + force flags) | Tier 2 duplicates `@Preview`; Tier 3 is paywall-coupled and rides freemium PR. Smaller PR ships before testers arrive. |
| Entry point | Row in Settings | Hidden gesture, separate Dev tab in bottom nav | Bottom nav stays production-shaped; Settings row + `isDebugBuild` gate is enough invisibility. |
