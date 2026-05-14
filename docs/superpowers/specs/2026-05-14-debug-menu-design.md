# Debug Menu V1 — Design Spec

**Date:** 2026-05-14
**Branch:** `feature/debug-menu`
**Owner:** Daniel Ogunleye
**Related:** Settings redesign (PR #35, merged 2026-05-14), PM intern tester recruitment, Freemium PR (paused; will absorb Tier 3)

## Goal

Add an internal debug menu, reachable from Settings, that lets Daniel (QA) and recruited tailor-testers reach realistic app state in two taps instead of by laboriously creating data through the app or waiting for time-dependent logic. The menu must not exist in App Store / Play Store release binaries.

## Motivation

Three forces converge on this need now:

1. **Tester recruitment is imminent.** The PM intern is recruiting 5–10 Nigerian tailors. They will receive debug builds. If they open the app and see an empty dashboard, the eval is meaningless — they need realistic data on first launch.
2. **State diversity has outgrown manual setup.** `DashboardUiState` alone has 8 sealed variants (Loading, BrandNew, several Empty / Reconnect heroes, populated). Reaching each currently requires real Firestore data manipulation. Setup checklist and dashboard hero states all hinge on data presence (customers, orders, measurements, styles).
3. **Smoke-test discipline.** Per the QA smoke-test memory, every PR needs manual smoke steps. Without scripted state, smoke testing is slow and inconsistent. The seed actions formalize "the state this PR is supposed to fix."

A debug menu attacks all three at once, and is small enough to ship in one PR before testers arrive.

## Out of scope (V1)

- **Tier 2 — Screens playground** (jump to any screen forced into a chosen `UiState` variant). Compose `@Preview` annotations already cover most of this for self-review. Defer; only revisit if a third PR ends up manually scripting state.
- **Tier 3 — Force flags** (entitlements Free/Trial/Pro toggle, force-`today` offsets, force-network-offline). Co-dependent on the paused freemium PR. Will be added inside that PR when it resumes.
- **Tier 4 — Diagnostics** (build version, current UID, FCM token, copy-last-N-log-lines, intentional crash button). Nice-to-have once testers are in the wild and need a "paste this when reporting" blob. Out of V1.
- **iOS strip-from-release via custom source set.** See "Strip from release" section — Android gets a true source-set split; iOS gets an `expect/actual` no-op fallback, which is sufficient for App Review but does leave dead code in the iOS release binary. A future hardening can introduce a custom `iosDebugMain` Kotlin source set if/when needed.
- **Persistent seeded state across processes.** Seeds write to the current user's Firestore — they're real data the user can delete via "Delete all my data" or via uninstall.
- **Multi-tenant seed isolation.** Seeds target the currently signed-in user only. If two testers share a Firebase account they'll see each other's seeded data — fine, since they shouldn't be sharing accounts.

## Architecture

### One feature package, source-set-split on Android, expect/actual seam for iOS

**Why source-set split on Android, not `if (BuildConfig.DEBUG)`:** The latter compiles debug class names + seed strings into the release APK; even though unreachable, they leak attack surface and grow the binary. AGP debug source sets are dropped at the variant boundary — release builds genuinely don't see them. This was the explicit choice over a runtime check.

**Android mechanism — spec commits to "Android debug source-set strips from release"; plan picks the exact wiring after a 30-min spike.** Three approaches in order of preference:

1. **AGP variant source sets** (`src/debug/kotlin/` and `src/release/kotlin/` with same FQN, different impl) — native AGP support, no reflection. Verify KMP plugin compatibility during planning.
2. **Explicitly registered Kotlin source sets** for the Android target (`src/androidDebug/kotlin/` declared in `build.gradle.kts`) — more setup, but explicit.
3. **Reflection-based dynamic loading** (last resort): commonMain calls `Class.forName("...DebugBridge")` with a no-op fallback. Ugly; only if 1 and 2 prove incompatible.

**iOS approach — `expect/actual` with no-op iOS actual:** KMP doesn't have a first-class `iosDebugMain` source set the way Android variants work. Adding one is a Gradle project in its own right. For V1:

- `expect val isDebugBuild: Boolean` and `expect fun startDebugMenu(...)` (or similar entry seam) in `commonMain`
- `actual` in `iosMain`: `isDebugBuild = false` and a no-op `startDebugMenu`

Net result on iOS release: a no-op `startDebugMenu` symbol exists in the framework but Settings home checks `isDebugBuild` (which is `false` on iOS) and never reaches it. No reachable debug UI, no entry point.

**Seed orchestration placement — Android debug source set, not `commonMain`:** Putting orchestration in `commonMain` would land seed string literals (`"Customer #1"`, `"Sample order — agbada"`), fixture data, and the seeder class itself into the iOS release binary as dead code. Not an App Review violation, but unnecessary bloat and surface. Moving orchestration into the Android debug source set means iOS release contains zero seed code. The future iOS-debug-source-set hardening would re-add iOS orchestration; until then, Android-only is cleaner.

### Package structure

```
composeApp/src/
├── commonMain/kotlin/com/danzucker/stitchpad/
│   ├── core/debug/
│   │   └── DebugBuild.kt           // expect val isDebugBuild: Boolean
│   ├── feature/debug/presentation/
│   │   └── DebugMenuRoot.kt        // expect @Composable fun DebugMenuRoot(onClose: () -> Unit)
│   └── navigation/Routes.kt        // + @Serializable data object DebugMenuRoute
│
├── <android debug variant source set>/kotlin/com/danzucker/stitchpad/
│   ├── core/debug/
│   │   ├── DebugSeeder.kt          // interface + DefaultDebugSeeder
│   │   ├── SeedFixtures.kt         // brand-new, active-workshop, all-reconnect data sets
│   │   ├── DebugSessionActions.kt  // sign-out, switch account, reset onboarding flags
│   │   └── DebugTestAccounts.kt    // GITIGNORED — Fola/Gabby creds
│   ├── feature/debug/presentation/
│   │   ├── DebugMenuRoot.android.kt    // actual — real impl, calls into DebugMenuViewModel
│   │   ├── DebugMenuViewModel.kt
│   │   ├── DebugMenuScreen.kt
│   │   ├── DebugMenuState.kt
│   │   ├── DebugMenuAction.kt
│   │   └── DebugMenuEvent.kt
│   └── di/DebugModule.kt           // Koin module
│
├── <android release variant source set>/kotlin/com/danzucker/stitchpad/feature/debug/presentation/
│   └── DebugMenuRoot.android.kt    // actual — no-op (`LaunchedEffect { onClose() }` to immediately pop)
│
├── androidMain/kotlin/com/danzucker/stitchpad/core/debug/
│   └── DebugBuild.android.kt       // actual val isDebugBuild = BuildConfig.DEBUG
│
└── iosMain/kotlin/com/danzucker/stitchpad/
    ├── core/debug/DebugBuild.ios.kt        // actual val isDebugBuild = false
    └── feature/debug/presentation/DebugMenuRoot.ios.kt
                                             // actual — no-op (`LaunchedEffect { onClose() }`)
```

The `<android debug variant source set>` and `<android release variant source set>` placeholders resolve during planning to whichever of the three mechanisms (AGP variant source sets, registered Kotlin sources, reflection) proves viable. In all three cases, the real impl lives in a folder that doesn't compile into release; the release variant provides a no-op `actual`.

**Why two `actual` declarations on Android (debug + release) instead of one + a runtime check:** Kotlin `expect/actual` requires exactly one `actual` per platform per compilation. AGP variant source sets satisfy this because only one of `src/debug` / `src/release` participates in a given variant compilation — Kotlin sees exactly one `actual DebugMenuRoot` either way.

**Why route `DebugMenuRoute` and the `expect` composable still need to live in commonMain:** `StitchPadNavHost` is in commonMain, so any route it registers must be compilable on every target. The expect composable's *body* is platform-supplied; commonMain just declares the seam. The `isDebugBuild` check on the Settings row prevents non-debug builds from ever navigating to the route, so the no-op actuals stay unreached in normal use.

Settings home gets a one-line addition:

```kotlin
// In SettingsScreen.kt, last section
if (isDebugBuild) {
    SettingsRow(
        icon = Icons.Outlined.BugReport,
        label = "Debug menu",
        onClick = { onAction(SettingsAction.OnDebugMenuClick) },
    )
}
```

`isDebugBuild` is an `expect val Boolean`:
- `actual` in `androidMain`: `BuildConfig.DEBUG`
- `actual` in `iosMain`: `false` (hard-coded — iOS gets debug menu only via Xcode `DEBUG` configuration, which would flip this via a generated header; deferred)

Navigation: a new `DebugMenuRoute` data object added to `navigation/Routes.kt` and wired in `StitchPadNavHost`.

## Tier 1 actions — V1 scope

### Seed: brand-new tailor
Wipes all Firestore data for the current user (customers, orders, measurements, styles) and resets onboarding flags. After this, the dashboard renders its `BrandNew` hero. The workshop-setup flag is left set, so we don't bounce the user out of the app.

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
Two buttons that sign out then sign in with the test creds from `reference_test_environment`. Credentials live in a `DebugTestAccounts.kt` constants file inside `androidDebug/` (NOT `commonMain`) — they don't ship in iOS release because there is no iOS debug source set, and they don't ship in Android release because they're in the debug source set. **Important: this file must be excluded from version control** via `.gitignore` since the creds are sensitive (test accounts but real Firebase auth). The file is created locally by Daniel and re-created when checking out fresh; a `.gitignored` `DebugTestAccounts.kt.example` template ships instead.

### Delete all my data
Calls the same `onAuthUserDeleted` path PR #38 wired up — actually deletes the auth user + Firestore + Storage. After this, the app routes back to Login. Equivalent to Settings' "Delete account" but reachable from the debug menu so it's a one-tap reset for testers.

## State and data flow

`DebugMenuViewModel` (Android-debug actual) holds:

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

Each action delegates to `DebugSeeder` or `DebugSessionActions` (both in the Android debug source set, alongside the ViewModel). Result of every action is surfaced as a Snackbar via `DebugMenuState.lastResult` — per the notification-patterns memory, no Toasts.

The ViewModel is registered in `DebugModule.kt` (also Android-debug-only). Koin's `:app` setup picks up the module only when the debug source set compiles, so the release Koin graph never sees it.

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
6. Build release variant → install → confirm Settings has no Debug menu row. Verify with `apkanalyzer dex packages release.apk` that none of `DebugMenuViewModel`, `DefaultDebugSeeder`, `SeedFixtures`, `DebugSessionActions`, `DebugTestAccounts` appear in the APK. (The no-op `DebugMenuRoot` actual *is* expected to appear — it's the `expect` seam — but its body is empty.)
7. Build iOS release configuration → confirm Settings has no Debug menu row → ship signoff.

**Unit tests:**
- `SeedFixtures` is a pure data file — no tests needed beyond compile.
- `DefaultDebugSeeder` gets unit tests in `androidUnitTest` (since the debug source set hosts the consumer) that fake the repositories and assert the right writes happen for each seed scenario.
- `DebugMenuViewModel` follows the standard android-testing pattern: Turbine on state flow, AssertK on emissions, `UnconfinedTestDispatcher`, fake `DebugSeeder`.

## Risks and open questions

- **iOS dead-code in release binary.** The `expect/actual` no-op pattern still compiles the no-op stubs into the iOS release framework. They're 0-line Composables but they exist. If iOS App Review ever flags the presence of a `DebugMenuRoot` symbol (extremely unlikely — symbol names alone aren't a violation), we'd need to introduce a custom `iosDebugMain` source set. Accepted risk for V1.
- **Test-account credentials in source.** `DebugTestAccounts.kt` is gitignored. Daniel will need to recreate it after any fresh clone. Mitigation: ship a `DebugTestAccounts.kt.example` template (committed) and a one-line note in the README's "Debug builds" section.
- **Crashlytics noise from debug seeds.** Crashlytics is already disabled in debug builds per `StitchPadApplication.kt`. No noise risk.
- **What if a tester gets a release build by accident?** They get no debug menu. They'd have to manually create data — same as the current state. No regression.

## Decision log

| Decision | Chosen | Alternative considered | Reason |
|---|---|---|---|
| Strip-from-release mechanism | Android source-set split (`androidDebug/`) + iOS expect/actual no-op | Runtime `if (BuildConfig.DEBUG)` guard | Source-set drops symbols at compile time; runtime guard leaks class names into release APK |
| Where seed orchestration lives | Android debug source set | `commonMain/core/debug/` | commonMain would land seed string literals + fixture data into the iOS release binary as dead code. Android-only keeps iOS release clean; future iOS-debug-source-set hardening would re-add iOS orchestration. |
| V1 scope | Tier 1 only (seed/reset) | All three tiers (seed/reset + screens playground + force flags) | Tier 2 duplicates `@Preview`; Tier 3 is paywall-coupled and rides freemium PR. Smaller PR ships before testers arrive. |
| Entry point | Row in Settings | Hidden gesture, separate Dev tab in bottom nav | Bottom nav stays production-shaped; Settings row + source-set split is enough invisibility. |
| Account-switcher creds storage | Gitignored `DebugTestAccounts.kt` + `.example` template | Hard-coded in source (committed) | Test creds are real Firebase auth — keep them out of git history |
