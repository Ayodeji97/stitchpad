# Rebrand PR-B — Screen Migration Sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all 103 remaining `DesignTokens.primary*` callsites across 34 feature files to `MaterialTheme.colorScheme.*`, replace the lone `Color(0xFFE8A800)` literal in `WorkshopSetupScreen.kt`, audit four dark-mode contrast spots flagged during PR-A, then delete the 11 `@Deprecated` aliases from `DesignTokens.kt`.

**Architecture:** Pure mechanical refactor with audit. PR-A already aliased every `DesignTokens.primary*` symbol to the new indigo ramp, so the app already renders the new palette via the alias chain. This PR replaces those deprecated references with their Material3 equivalents per the mapping table in `docs/rebrand-pr-b-checklist.md`, then deletes the aliases so the compiler catches any miss. Saffron survives only as `LocalStitchPadColors.current.heritageAccent` for rare heritage moments (PRO badges, Verified Tailor chips, achievement bursts).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.7, Material3, detekt + ktlint. Target platforms: Android (`assembleDebug`) and iOS Native (`compileKotlinIosSimulatorArm64`).

---

## File Structure

This PR touches existing files only — **no new files, no new modules**. Files split by feature area into seven mechanical-migration tasks plus one audit task plus one branch / one deletion / one smoke task.

**Modified files (34 + 1 token file + 1 doc-update if needed):**

```
composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  feature/auth/presentation/
    components/AuthTextField.kt
    components/StitchPadLogo.kt
    forgotpassword/ForgotPasswordScreen.kt
    login/LoginScreen.kt
    signup/SignUpScreen.kt
  feature/onboarding/presentation/
    OnboardingScreen.kt
    SplashScreen.kt
    components/StitchPadLogo.kt
    workshop/WorkshopSetupScreen.kt          ← also fixes 0xFFE8A800 literal
  feature/dashboard/presentation/DashboardScreen.kt
  feature/customer/presentation/list/CustomerListScreen.kt
  feature/main/presentation/MainScreen.kt
  feature/order/presentation/
    form/OrderFormScreen.kt
    list/OrderListScreen.kt
  feature/measurement/presentation/form/MeasurementFormScreen.kt
  feature/reports/presentation/components/
    CustomRangePickerDialog.kt
    KpiGrid.kt
    OutstandingBalancesCard.kt
    ReportsEmptyState.kt
    ReportsPaywallCard.kt
    ReportsTabRow.kt
    SelectedRangeChip.kt
    Sparkline.kt
    TopCustomersCard.kt
  feature/settings/presentation/
    changeemail/ChangeEmailScreen.kt
    changepassword/ChangePasswordScreen.kt
    components/PlanCard.kt
    components/ProfileHeroCard.kt
    components/SettingsRow.kt
    deleteaccount/DeleteAccountReasonSheet.kt
    deleteaccount/DeleteAccountScreen.kt
    editprofile/EditProfileScreen.kt
  ui/components/
    CustomDatePickerDialog.kt
    StitchPadFab.kt
  ui/theme/DesignTokens.kt                    ← final task: delete 11 @Deprecated aliases
docs/
  rebrand-pr-b-checklist.md                   ← may add audit-resolution notes
```

---

## Migration Mapping Reference

This is the canonical mapping table the subagent applies to every callsite. Don't deviate without explicit reasoning logged in the commit.

| Pattern (deprecated reference + surrounding context)                                                                  | Replace with                                                            |
| --------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `DesignTokens.primary500` as `containerColor`, `tint`, `color = ` on link text, button fill, icon tint, brand emphasis | `MaterialTheme.colorScheme.primary`                                     |
| `DesignTokens.primary600`, `primary700`, `primary800`, `primary900` used as deeper brand emphasis (CTA pressed, dark-mode strong text, hero accent on light) | `MaterialTheme.colorScheme.primary`                                     |
| `DesignTokens.primary400`, `primary300`, `primary200` used as standard brand emphasis                                  | `MaterialTheme.colorScheme.primary`                                     |
| `DesignTokens.primary50`, `primary100` used as subtle tint backgrounds (pill bg, hero card tint, indicator color, chip bg) | `MaterialTheme.colorScheme.primaryContainer`                            |
| `DesignTokens.primary900` used as deep dark "filled-chip / avatar background"                                          | `MaterialTheme.colorScheme.primaryContainer` (see UserAvatar fix in commit `6afb67a` as the reference pattern) |
| `DesignTokens.primaryButtonBorder`                                                                                     | `MaterialTheme.colorScheme.primary`                                     |
| Conditional `if (isDark) DesignTokens.primary400 else DesignTokens.primary600` (or similar mode-aware branches)       | `MaterialTheme.colorScheme.primary` (already mode-aware via ColorScheme) |
| Conditional `if (isDark) DesignTokens.primary900 else DesignTokens.primary50`                                          | `MaterialTheme.colorScheme.primaryContainer`                            |
| `Color(0xFFE8A800)` literal in feature code                                                                            | `LocalStitchPadColors.current.heritageAccent` IF the use is a genuine heritage moment; otherwise `MaterialTheme.colorScheme.primaryContainer` (background tint) or `MaterialTheme.colorScheme.primary` (semantic emphasis) |
| Variable `val saffron = DesignTokens.primary500` (misnamed leftover — `saffron` no longer routes to saffron post-alias) | Inline the reference, or rename to `val brand` + replace value with `MaterialTheme.colorScheme.primary` |

**Why `MaterialTheme.colorScheme.primary` works for every depth (primary400, 500, 700, 900) in light or dark:** Material3's `ColorScheme` already returns the mode-appropriate tonal step. On light mode `primary` resolves to `indigo500` (`#2C3E7C`); on dark mode it resolves to `indigo400` (`#5871B8`) per the bump landed in commit `e874c80`. The depth differentiation that the old saffron ramp encoded (50 → 900) collapses into Material's two-slot model: container = subtle tint, primary = strong emphasis. Stop reading per-depth from `DesignTokens` and start trusting the scheme.

**Composable-context requirement:** `MaterialTheme.colorScheme.*` is a `@Composable`-restricted getter. Some callsites currently live at file-level vals (e.g., default parameter values like `color: Color = DesignTokens.primary500` in `Sparkline.kt:29`). Those must move inside the `@Composable` function body (read the scheme color and assign to a local val) before the deprecated reference can go away. The default-parameter pattern (`Color = DesignTokens.primary500`) becomes the nullable-default pattern (`color: Color? = null` + `val resolvedColor = color ?: MaterialTheme.colorScheme.primary` inside).

---

## Task 0: Branch from updated main

**Files:**
- N/A (git only)

- [ ] **Step 1: Verify clean working tree on main**

Run: `git status && git log --oneline -3`
Expected: working tree clean, HEAD at `569cdfe feat(rebrand): PR-A — Adire Atelier tokens-only foundation (#42)` or later.

- [ ] **Step 2: Create migration branch**

Run: `git checkout -b feature/rebrand-migration`
Expected: `Switched to a new branch 'feature/rebrand-migration'`.

---

## Task A: Map Material titleLarge / titleMedium / titleSmall to Manrope

**Why this runs before the color migration:** A Cursor review of PR-A flagged that `StitchPadTypography()` defines display/headline/body/label but **omits** `titleLarge`, `titleMedium`, and `titleSmall`. Many screens use `MaterialTheme.typography.title*` (dashboard section titles, list-item primary text, settings rows, reports cards, order/customer detail headers), so those slots currently render with Material3's defaults (Roboto on Android, system on iOS) — a large surface that won't pick up Manrope/Fraunces. Mapping these now means the rest of PR-B's color migration also visually lands the typography rebrand simultaneously.

**Decision rationale (Manrope, not Fraunces):** Title slots are Material's "section heading" / "card title" / "list-item title" surface — dense, repeated, smaller than display/headline. Manrope SemiBold reads cleanly at these sizes; Fraunces would feel editorial and too display-y. Fraunces stays on display + headline (the brand-emphasis surfaces) plus the AuthHero wordmark (Task 1).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt`

- [ ] **Step 1: Add the three title styles to `StitchPadTypography()`**

Insert these three `TextStyle` blocks between the `headlineSmall = ...` block (currently lines 87–92) and the `bodyLarge = ...` block (currently line 94+):

```kotlin
        // Title — Manrope. Used by Material3 as section-heading / card-title /
        // list-item primary text. Sized between headlineSmall (18sp) and
        // bodyLarge (16sp) per Material3 type scale convention.
        titleLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingSm,
            lineHeight = DesignTokens.headingSm * 1.33f,
        ),
        titleMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.bodyLg,
            lineHeight = DesignTokens.bodyLg * 1.4f,
        ),
        titleSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelLg,
            lineHeight = DesignTokens.labelLg * 1.43f,
        ),
```

`titleLarge` = 18sp (headingSm), `titleMedium` = 16sp (bodyLg), `titleSmall` = 14sp (labelLg). This sits between headlineSmall (18sp Fraunces) and bodyLarge (16sp Manrope) as Material3 expects.

- [ ] **Step 2: Compile Android**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Detekt**

Run: `./gradlew detekt 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): map title{Large,Medium,Small} to Manrope

Type.kt previously omitted the Material3 title* slots, leaving section
titles, card titles, and list-item primary text rendering in Material's
default (Roboto/system) — a large UI surface bypassed Manrope/Fraunces.
Map to Manrope SemiBold/Medium at headingSm/bodyLg/labelLg sizes.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Capture baseline callsite count**

Run: `grep -rln "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/ | wc -l`
Expected: `34` (files containing primary* references)

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/ | wc -l`
Expected: `103` (individual callsites)

These two numbers are the "must reach zero" verification target before Task 7.

- [ ] **Step 4: No commit yet** — branch setup only.

---

## Task 1: Auth feature migration (6 files, 16 callsites + AuthHero typography fix)

This task does double duty: the color migration of `DesignTokens.primary*` and a separate Cursor-review-flagged fix for `AuthHero.kt`, which currently uses raw `TextStyle(fontSize = 18.sp, ...)` for the brand wordmark + tagline. Raw `TextStyle` with no `fontFamily` falls back to Material3's default (Roboto on Android, system on iOS), so the auth wordmark currently does NOT render in Fraunces despite Type.kt mapping display/headline correctly. Bundling here keeps the auth-area changes coherent.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthHero.kt:70-87` (NEW — typography fix)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthTextField.kt:91,107`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/StitchPadLogo.kt:54`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/forgotpassword/ForgotPasswordScreen.kt:182,202,231,237,276`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/login/LoginScreen.kt:206,224,249`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/signup/SignUpScreen.kt:256,264,278,342,367`

- [ ] **Step 1: AuthHero.kt — replace raw TextStyle with Fraunces wordmark + MaterialTheme labelSmall tagline**

The current implementation at lines 68–87:

```kotlin
            Text(
                text = stringResource(Res.string.brand_name),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.2.sp,
                ),
            )
            if (showTagline) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.workshop_brand_tagline),
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 3.2.sp,
                    ),
                )
            }
```

Replace with:

```kotlin
            Text(
                text = stringResource(Res.string.brand_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FrauncesFamily(),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.2.sp,
                ),
            )
            if (showTagline) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.workshop_brand_tagline),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 3.2.sp,
                    ),
                )
            }
```

The wordmark uses `titleLarge.copy(fontFamily = FrauncesFamily())` — title-slot sizing (18sp from Task A), Fraunces family explicitly (because the brand wordmark deserves the display face), preserving the existing custom letterSpacing/weight/color. The tagline routes through `labelSmall` (Manrope by default) with custom letterSpacing.

Add imports as needed:
- `import androidx.compose.material3.MaterialTheme`
- `import com.danzucker.stitchpad.ui.theme.FrauncesFamily`

Remove the now-unused imports `import androidx.compose.ui.text.TextStyle` and `import androidx.compose.ui.unit.sp` if no other reference remains in the file (grep before removing).

- [ ] **Step 2: AuthTextField.kt — 2 callsites**

Both are inside the composable body. Apply mapping:
- Line 91 `tint = DesignTokens.primary400` → `tint = MaterialTheme.colorScheme.primary`
- Line 107 `cursorBrush = SolidColor(DesignTokens.primary500)` → `cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)`

Add import: `import androidx.compose.material3.MaterialTheme` if not already present.
Remove import: `import com.danzucker.stitchpad.ui.theme.DesignTokens` ONLY if no other `DesignTokens.*` references remain in the file. Verify with grep before removing.

- [ ] **Step 3: AuthTextField.kt — verify**

Run: `grep -n "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthTextField.kt`
Expected: no output (zero matches).

- [ ] **Step 4: StitchPadLogo.kt (auth) — 1 callsite**

Line 54 `color = DesignTokens.primary500` → `color = MaterialTheme.colorScheme.primary`

Add `import androidx.compose.material3.MaterialTheme` if missing.

- [ ] **Step 5: ForgotPasswordScreen.kt — 5 callsites**

Apply mapping table to each:
- Line 182 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`
- Line 202 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`
- Line 231 `.background(DesignTokens.primary500.copy(alpha = 0.18f))` → `.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))`
- Line 237 `tint = DesignTokens.primary500` → `tint = MaterialTheme.colorScheme.primary`
- Line 276 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 6: LoginScreen.kt — 3 callsites**

- Line 206 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`
- Line 224 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`
- Line 249 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`

- [ ] **Step 7: SignUpScreen.kt — 5 callsites**

- Line 256 `val checkboxColor = if (state.acceptedTerms) DesignTokens.primary500 else Color(0xFF3A3731)` → `val checkboxColor = if (state.acceptedTerms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline`
  - **Note:** the `else` branch `Color(0xFF3A3731)` was the saffron-era dark-border hex; route through `outline` for theme correctness in dark mode. If smoke testing later flags this, fall back to `MaterialTheme.colorScheme.outlineVariant`.
- Line 264 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`
- Line 278 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`
- Line 342 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`
- Line 367 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`

- [ ] **Step 8: Verify auth area clean**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/`
Expected: no output.

- [ ] **Step 9: Compile Android**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -30`
Expected: `BUILD SUCCESSFUL`. No `'primary500' is deprecated` warnings from `feature/auth/**`.

- [ ] **Step 10: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -30`
Expected: `BUILD SUCCESSFUL`. (Per `feedback_kmp_jvm_only_apis` memory, always run iOS compile before declaring done.)

- [ ] **Step 11: Detekt**

Run: `./gradlew detekt 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL` with no new findings in modified files.

- [ ] **Step 12: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate auth screens to MaterialTheme + fix AuthHero typography

Replace DesignTokens.primary{400,500} callsites in AuthTextField,
StitchPadLogo, ForgotPasswordScreen, LoginScreen, SignUpScreen with
MaterialTheme.colorScheme.primary. SignUpScreen's checkbox unselected
state now routes through colorScheme.outline (was hardcoded #3A3731).

AuthHero wordmark + tagline previously used raw TextStyle with no
fontFamily — silently rendered Roboto despite the Fraunces/Manrope
rebrand. Wordmark now uses titleLarge.copy(fontFamily = FrauncesFamily())
so it actually picks up Fraunces; tagline uses labelSmall (Manrope).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Onboarding migration (4 files, 8 callsites + 1 saffron literal)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/OnboardingScreen.kt:133`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt:60`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/components/StitchPadLogo.kt:27`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt:200,208,335,343,375,400`

- [ ] **Step 1: OnboardingScreen.kt — 1 callsite**

Line 133 `DesignTokens.primary500` → `MaterialTheme.colorScheme.primary`

- [ ] **Step 2: SplashScreen.kt — 1 callsite**

Line 60 `.background(DesignTokens.primary500)` → `.background(MaterialTheme.colorScheme.primary)`

- [ ] **Step 3: StitchPadLogo.kt (onboarding) — 1 callsite + variable rename**

Read the file first to confirm structure (already known from plan-writing investigation):
```
val saffron = DesignTokens.primary500
```
The variable `saffron` is misleading post-rebrand — it points to indigo via the alias chain. Replace with:
```
val brandColor = MaterialTheme.colorScheme.primary
```
Then update line 45 reference inside the canvas TextStyle from `color = saffron` to `color = brandColor`.

Add `import androidx.compose.material3.MaterialTheme`.

**Note:** this composable already takes a `Color` from `MaterialTheme.colorScheme` correctly at composition time; the `val brandColor = ...` reads at composition before entering the `Canvas { }` block, which is fine.

- [ ] **Step 4: WorkshopSetupScreen.kt — 5 `DesignTokens.primary*` callsites + 1 saffron literal**

- Line 200 `.background(Color(0xFFE8A800).copy(alpha = 0.18f))` → `.background(MaterialTheme.colorScheme.primaryContainer)`
  - **Why not heritage saffron?** The pill in question is the WhatsApp "(Optional)" indicator. Per `project_rebrand_styleols` memory + spec, heritage saffron is reserved for PRO/Verified/achievement moments — an "optional" pill is brand-emphasis, not heritage. The alpha .18 hack also disappears since `primaryContainer` already gives subtle tint.
- Line 208 `color = DesignTokens.primary300` → `color = MaterialTheme.colorScheme.onPrimaryContainer`
  - The pill text reads against the new `primaryContainer` background — use `onPrimaryContainer` for guaranteed contrast in both modes.
- Line 335 `DesignTokens.primary500.copy(alpha = 0.15f)` → `MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)`
- Line 343 `tint = DesignTokens.primary400` → `tint = MaterialTheme.colorScheme.primary`
- Line 375 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`
- Line 400 `color = DesignTokens.primary400` → `color = MaterialTheme.colorScheme.primary`

- [ ] **Step 5: Verify onboarding area clean**

Run: `grep -rn "DesignTokens.primary\|0xFFE8A800" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/`
Expected: no output.

- [ ] **Step 6: Compile Android + iOS + detekt (same as Task 1, steps 8–10)**

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate onboarding screens to MaterialTheme.colorScheme

Replace DesignTokens.primary* callsites in OnboardingScreen, SplashScreen,
StitchPadLogo (canvas), and WorkshopSetupScreen with MaterialTheme.colorScheme.
WorkshopSetupScreen's hardcoded 0xFFE8A800 saffron-tint "Optional" pill now
routes through primaryContainer/onPrimaryContainer for theme correctness.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Dashboard + Customer + Main + Order + Measurement (5 files, 18 callsites)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt:943,944,1009,1010`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/list/CustomerListScreen.kt:332,337`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt:126,127,128`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt:595,600`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt:172,336,341`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormScreen.kt:352,357,381`

- [ ] **Step 1: DashboardScreen.kt — 4 callsites in 2 conditionals**

Lines 943–944:
```kotlin
accent = if (isDark) DesignTokens.primary400 else DesignTokens.primary600,
accentBackground = if (isDark) DesignTokens.primary900 else DesignTokens.primary50,
```
becomes:
```kotlin
accent = MaterialTheme.colorScheme.primary,
accentBackground = MaterialTheme.colorScheme.primaryContainer,
```

Same replacement for lines 1009–1010. The `isDark` parameter into that helper may become unused after migration — leave it; another caller might still need it. (Verify with `grep -n "isDark" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` before removing.)

- [ ] **Step 2: CustomerListScreen.kt — 2 callsites (FilterChip pattern)**

- Line 332 `selectedLabelColor = DesignTokens.primary600` → `selectedLabelColor = MaterialTheme.colorScheme.primary`
- Line 337 `BorderStroke(1.dp, DesignTokens.primary500)` → `BorderStroke(1.dp, MaterialTheme.colorScheme.primary)`

- [ ] **Step 3: MainScreen.kt — 3 callsites (bottom nav bar)**

Per the PR-B checklist verification: bottom nav already works correctly because `NavigationBarItemDefaults` picks up the scheme automatically. But the explicit overrides on lines 126–128 should still migrate:

- Line 126 `selectedIconColor = DesignTokens.primary700` → `selectedIconColor = MaterialTheme.colorScheme.primary`
- Line 127 `selectedTextColor = DesignTokens.primary600` → `selectedTextColor = MaterialTheme.colorScheme.primary`
- Line 128 `indicatorColor = DesignTokens.primary50` → `indicatorColor = MaterialTheme.colorScheme.primaryContainer`

- [ ] **Step 4: OrderFormScreen.kt — 2 callsites**

- Line 595 `selectedLabelColor = DesignTokens.primary600` → `selectedLabelColor = MaterialTheme.colorScheme.primary`
- Line 600 `BorderStroke(1.dp, DesignTokens.primary500)` → `BorderStroke(1.dp, MaterialTheme.colorScheme.primary)`

- [ ] **Step 5: OrderListScreen.kt — 3 callsites**

- Line 172 `spotColor = DesignTokens.primary500` → `spotColor = MaterialTheme.colorScheme.primary`
- Line 336 `selectedLabelColor = DesignTokens.primary600` → `selectedLabelColor = MaterialTheme.colorScheme.primary`
- Line 341 `BorderStroke(1.dp, DesignTokens.primary500)` → `BorderStroke(1.dp, MaterialTheme.colorScheme.primary)`

- [ ] **Step 6: MeasurementFormScreen.kt — 3 callsites**

- Line 352 `selectedLabelColor = DesignTokens.primary600` → `selectedLabelColor = MaterialTheme.colorScheme.primary`
- Line 357 `BorderStroke(1.dp, DesignTokens.primary500)` → `BorderStroke(1.dp, MaterialTheme.colorScheme.primary)`
- Line 381 `section.fields.any { f -> fields[f.key]?.isNotBlank() == true } -> DesignTokens.primary400` → `section.fields.any { f -> fields[f.key]?.isNotBlank() == true } -> MaterialTheme.colorScheme.primary`

- [ ] **Step 7: Verify cleanup**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/`
Expected: no output.

- [ ] **Step 8: Compile Android + iOS + detekt**

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/{dashboard,customer,main,order,measurement}/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate dashboard/customer/main/order/measurement screens

Replace DesignTokens.primary* callsites with MaterialTheme.colorScheme.primary
or primaryContainer. Dashboard accent/accentBackground conditionals collapse
into single scheme references (Material3 already handles mode-awareness).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Reports components migration (9 files, 25 callsites)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/CustomRangePickerDialog.kt:146,153,340,346,359,360,381,382,448`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/KpiGrid.kt:44,45`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/OutstandingBalancesCard.kt:193`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/ReportsEmptyState.kt:42,48`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/ReportsPaywallCard.kt:58,64,88`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/ReportsTabRow.kt:70`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/SelectedRangeChip.kt:45,48,59,66,81,89`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/Sparkline.kt:29`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/TopCustomersCard.kt:168,169,242,247`

- [ ] **Step 1: CustomRangePickerDialog.kt — 9 callsites**

This file has a date-range picker with selected / hover / today / in-range states. All `primary*` references migrate per the mapping table:

- Line 146 `.background(DesignTokens.primary500)` → `.background(MaterialTheme.colorScheme.primary)`
- Line 153 `color = DesignTokens.primary900` → `color = MaterialTheme.colorScheme.onPrimary` (this is text drawn over the line-146 primary background)
- Line 340 `.background(if (state.showLeftBar) DesignTokens.primary100 else Color.Transparent)` → `.background(if (state.showLeftBar) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)`
- Line 346 same pattern → `MaterialTheme.colorScheme.primaryContainer`
- Line 359 `state.isSelected -> base.background(DesignTokens.primary500)` → `state.isSelected -> base.background(MaterialTheme.colorScheme.primary)`
- Line 360 `state.isToday -> base.border(1.5.dp, DesignTokens.primary500, CircleShape)` → `base.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)`
- Line 381 `state.isInRange -> DesignTokens.primary800` → `state.isInRange -> MaterialTheme.colorScheme.onPrimaryContainer` (this is text color over a `primaryContainer` background, so use the matching on-color)
- Line 382 `state.isToday -> DesignTokens.primary600` → `state.isToday -> MaterialTheme.colorScheme.primary`
- Line 448 `val applyBg = if (canConfirm) DesignTokens.primary500 else DesignTokens.primary100` → `val applyBg = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer`

- [ ] **Step 2: KpiGrid.kt — 2 callsites**

- Line 44 `iconTint = DesignTokens.primary500` → `iconTint = MaterialTheme.colorScheme.primary`
- Line 45 `iconBackground = DesignTokens.primary50` → `iconBackground = MaterialTheme.colorScheme.primaryContainer`

- [ ] **Step 3: OutstandingBalancesCard.kt — 1 callsite (semantic urgency fix, not just rename)**

Line 193 `val saffron = DesignTokens.primary500` is NOT a generic misnamed brand local — it's an urgency-tier color in a graduated ramp (red overdue → orange due-today → saffron due-this-week → muted later). Cursor's review flagged that simply mapping to `MaterialTheme.colorScheme.primary` (indigo) would make "due this week" amounts read like brand links rather than soft warnings.

The right semantic fix: route the "this week" tier through **sienna** (`MaterialTheme.colorScheme.tertiary` — verified to resolve to `DesignTokens.sienna500` on light / `sienna300` on dark). Sienna is the rebrand's "workshop bench warmth" accent, sitting tonally between red/orange (alert) and green/muted (calm), which is exactly the soft-warning intent.

Replace line 193 and adjust the local name:

```kotlin
val dueSoon = MaterialTheme.colorScheme.tertiary
val red = DesignTokens.error500
val orange = DesignTokens.warning500
val green = DesignTokens.success500
val muted = MaterialTheme.colorScheme.onSurfaceVariant
```

Then update the usage at the `daysUntil <= DAYS_THIS_WEEK` branch (around line 226):

```kotlin
daysUntil <= DAYS_THIS_WEEK -> UrgencyStyle(
    amountColor = dueSoon,
    // ...rest unchanged
)
```

Run `grep -n "saffron" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/OutstandingBalancesCard.kt` after the edit — expected: no output.

- [ ] **Step 4: ReportsEmptyState.kt — 2 callsites**

- Line 42 `.background(DesignTokens.primary50)` → `.background(MaterialTheme.colorScheme.primaryContainer)`
- Line 48 `tint = DesignTokens.primary700` → `tint = MaterialTheme.colorScheme.primary`

- [ ] **Step 5: ReportsPaywallCard.kt — 3 callsites**

- Line 58 `.background(DesignTokens.primary50)` → `.background(MaterialTheme.colorScheme.primaryContainer)`
- Line 64 `tint = DesignTokens.primary500` → `tint = MaterialTheme.colorScheme.primary`
- Line 88 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 6: ReportsTabRow.kt — 1 callsite**

Line 70 `DesignTokens.primary500` → `MaterialTheme.colorScheme.primary`. This is the selected pill fill color.

- [ ] **Step 7: SelectedRangeChip.kt — 6 callsites**

- Line 45 `.background(DesignTokens.primary50)` → `.background(MaterialTheme.colorScheme.primaryContainer)`
- Line 48 `color = DesignTokens.primary200` → `color = MaterialTheme.colorScheme.primary`
- Line 59 `tint = DesignTokens.primary700` → `tint = MaterialTheme.colorScheme.onPrimaryContainer` (inside the line-45 primaryContainer bg)
- Line 66 `color = DesignTokens.primary800` → `color = MaterialTheme.colorScheme.onPrimaryContainer`
- Line 81 `.background(DesignTokens.primary200)` → `.background(MaterialTheme.colorScheme.primary)` (this is the close-X icon's filled circle backdrop — emphasis, not tint)
- Line 89 `tint = DesignTokens.primary800` → `tint = MaterialTheme.colorScheme.onPrimary` (the X icon over the line-81 primary fill)

- [ ] **Step 8: Sparkline.kt — 1 callsite (default-parameter pattern)**

Line 29 currently: `color: Color = DesignTokens.primary500,` — this is a `@Composable` function with a default parameter that can't read MaterialTheme. Refactor:

```kotlin
@Composable
fun Sparkline(
    // ...other params,
    color: Color? = null,
    // ...
) {
    val resolvedColor = color ?: MaterialTheme.colorScheme.primary
    // ...use resolvedColor inside the canvas/render code
}
```

Update all call sites of `Sparkline(color = ...)` if any pass `null` or rely on the default — but only callers that ALREADY pass an explicit non-default value need no change. Run `grep -rn "Sparkline(" composeApp/src/commonMain/kotlin/` to enumerate callers and verify.

- [ ] **Step 9: TopCustomersCard.kt — 4 callsites**

- Line 168 `fg = DesignTokens.primary700` → `fg = MaterialTheme.colorScheme.primary`
- Line 169 `bg = DesignTokens.primary100` → `bg = MaterialTheme.colorScheme.primaryContainer`
- Line 242 `color = DesignTokens.primary500` → `color = MaterialTheme.colorScheme.primary`
- Line 247 `tint = DesignTokens.primary500` → `tint = MaterialTheme.colorScheme.primary`

- [ ] **Step 10: Verify reports area clean**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/`
Expected: no output.

- [ ] **Step 11: Compile Android + iOS + detekt**

- [ ] **Step 12: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate reports components to MaterialTheme.colorScheme

Replace DesignTokens.primary* across nine reports components. Sparkline's
default-parameter pattern moves to nullable + composable-body resolution.
OutstandingBalancesCard's misleading 'saffron' local renamed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Settings migration (7 files, 22 callsites)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/changeemail/ChangeEmailScreen.kt:106`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/changepassword/ChangePasswordScreen.kt:108`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/PlanCard.kt:124,125,144,171,175,176,287`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/ProfileHeroCard.kt:88,93,103,183,197,204`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/SettingsRow.kt:61,66`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/deleteaccount/DeleteAccountReasonSheet.kt:178,183,203,210,211`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/deleteaccount/DeleteAccountScreen.kt:172,191,220`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/editprofile/EditProfileScreen.kt:130`

- [ ] **Step 1: ChangeEmailScreen.kt — 1 callsite**

Line 106 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 2: ChangePasswordScreen.kt — 1 callsite**

Line 108 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 3: PlanCard.kt — 7 callsites (per `project_freemium_plan_card` memory, dormant file but still compiles)**

- Line 124 `backgroundColor = DesignTokens.primary50` → `backgroundColor = MaterialTheme.colorScheme.primaryContainer`
- Line 125 `contentColor = DesignTokens.primary700` → `contentColor = MaterialTheme.colorScheme.onPrimaryContainer`
- Line 144 `color = DesignTokens.primary700` → `color = MaterialTheme.colorScheme.primary`
- Line 171 `colors = listOf(DesignTokens.primary500.copy(alpha = 0.45f), Color.Transparent)` → `colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), Color.Transparent)`
- Line 175 `pillBorderColor = DesignTokens.primary300.copy(alpha = 0.40f)` → `pillBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)`
- Line 176 `pillContentColor = DesignTokens.primary300` → `pillContentColor = MaterialTheme.colorScheme.primary`
- Line 287 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 4: ProfileHeroCard.kt — 6 callsites**

- Line 88 `DesignTokens.primary50` → `MaterialTheme.colorScheme.primaryContainer`
- Line 93 `DesignTokens.primary900.copy(alpha = 0.45f)` → `MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)`
- Line 103 `DesignTokens.primary100` → `MaterialTheme.colorScheme.primaryContainer`
- Line 183 `border = BorderStroke(1.dp, DesignTokens.primary500)` → `border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)`
- Line 197 `tint = DesignTokens.primary500` → `tint = MaterialTheme.colorScheme.primary`
- Line 204 `color = DesignTokens.primary500` → `color = MaterialTheme.colorScheme.primary`

- [ ] **Step 5: SettingsRow.kt — 2 callsites**

- Line 61 `DesignTokens.primary700` → `MaterialTheme.colorScheme.primary`
- Line 66 `DesignTokens.primary50` → `MaterialTheme.colorScheme.primaryContainer`

- [ ] **Step 6: DeleteAccountReasonSheet.kt — 5 callsites**

- Line 178 `DesignTokens.primary500` → `MaterialTheme.colorScheme.primary`
- Line 183 `DesignTokens.primary50` → `MaterialTheme.colorScheme.primaryContainer`
- Line 203 `color = if (isSelected) DesignTokens.primary900 else MaterialTheme.colorScheme.onSurface` → `color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface`
- Line 210 `val borderColor = if (isSelected) DesignTokens.primary700 else MaterialTheme.colorScheme.outline` → `val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline`
- Line 211 `val fillColor = if (isSelected) DesignTokens.primary500 else Color.Transparent` → `val fillColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent`

- [ ] **Step 7: DeleteAccountScreen.kt — 3 callsites**

- Line 172 `color = DesignTokens.primary500` → `color = MaterialTheme.colorScheme.primary`
- Line 191 `.background(DesignTokens.primary500)` → `.background(MaterialTheme.colorScheme.primary)`
- Line 220 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 8: EditProfileScreen.kt — 1 callsite**

Line 130 `containerColor = DesignTokens.primary500` → `containerColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 9: Verify settings area clean**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/`
Expected: no output.

- [ ] **Step 10: Compile Android + iOS + detekt**

- [ ] **Step 11: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate settings screens to MaterialTheme.colorScheme

Replace DesignTokens.primary* callsites across ChangeEmail, ChangePassword,
PlanCard, ProfileHeroCard, SettingsRow, DeleteAccount{ReasonSheet,Screen},
and EditProfile. Selected-state text colors route through onPrimaryContainer
where they overlay a primaryContainer background.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Shared ui/components migration (2 files, 7 callsites)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/CustomDatePickerDialog.kt:133,140,340,341,366,397`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadFab.kt:35`

- [ ] **Step 1: CustomDatePickerDialog.kt — 6 callsites**

Mirror of CustomRangePickerDialog from Task 4 (sibling component):

- Line 133 `.background(DesignTokens.primary500)` → `.background(MaterialTheme.colorScheme.primary)`
- Line 140 `color = DesignTokens.primary900` → `color = MaterialTheme.colorScheme.onPrimary`
- Line 340 `isSelected -> base.background(DesignTokens.primary500)` → `MaterialTheme.colorScheme.primary`
- Line 341 `isToday -> base.border(1.5.dp, DesignTokens.primary500, CircleShape)` → `MaterialTheme.colorScheme.primary`
- Line 366 `isToday -> DesignTokens.primary600` → `MaterialTheme.colorScheme.primary`
- Line 397 `val applyBg = if (canConfirm) DesignTokens.primary500 else DesignTokens.primary100` → `val applyBg = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer`

- [ ] **Step 2: StitchPadFab.kt — 1 callsite**

Line 35 `spotColor = DesignTokens.primary500` → `spotColor = MaterialTheme.colorScheme.primary`

- [ ] **Step 3: Verify ui/components clean**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/`
Expected: no output.

- [ ] **Step 4: Global verification — feature/** and ui/components/** clean**

Run: `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/`
Expected: no output (zero references anywhere in feature code).

If output is non-zero, the migration missed something — go back to the offending file and apply the mapping table before proceeding.

- [ ] **Step 5: Compile Android + iOS + detekt**

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/
git commit -m "$(cat <<'EOF'
refactor(rebrand): migrate shared ui/components to MaterialTheme.colorScheme

Replace DesignTokens.primary* in CustomDatePickerDialog and StitchPadFab.
Feature/** and ui/components/** now have zero deprecated primary references.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Delete the 11 `@Deprecated` aliases

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt:35-105`

- [ ] **Step 1: Read DesignTokens.kt around lines 35–105**

Run: `sed -n '30,110p' composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt`

Expected: the 11 `@Deprecated` blocks for `primary50`, `primary100`, `primary200`, `primary300`, `primary400`, `primary500`, `primary600`, `primary700`, `primary800`, `primary900`, and `primaryButtonBorder`, plus the explanatory comment block at lines 35–39.

- [ ] **Step 2: Delete lines 35–105**

Replace the block from the comment header through the last `@Deprecated` closing `val primaryButtonBorder = indigo700` with: nothing. The next existing block (`// Neutral / Surface Colors`) at line 107 becomes adjacent to the saffron-line block at line 33.

Be careful with the spacing — leave one blank line between `val inkDark = Color(0xFF14110E)` and `// Neutral / Surface Colors`.

- [ ] **Step 3: Compile Android — this is the verification gate**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -50`
Expected: `BUILD SUCCESSFUL`. If anything references a deleted alias, Kotlin will fail with `Unresolved reference: primary500` (or similar) — go back, find the missed callsite, apply the mapping, and re-run.

- [ ] **Step 4: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -50`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Detekt**

Run: `./gradlew detekt 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. No `Deprecation` findings (the warnings vanish because the deprecated members are gone).

- [ ] **Step 6: Confirm zero deprecation warnings**

Run: `./gradlew :composeApp:assembleDebug 2>&1 | grep -i "deprecat" | head -20`
Expected: no output, or only deprecations unrelated to `primary*` (e.g., Compose Multiplatform API churn).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt
git commit -m "$(cat <<'EOF'
refactor(rebrand): remove 11 @Deprecated DesignTokens.primary* aliases

All 103 callsites migrated to MaterialTheme.colorScheme.{primary,primaryContainer,
on*}. Compile is the verification gate — any remaining reference would fail.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Audit five dark-mode + warm-neutral contrast areas

This task verifies the five items the PR-B checklist flagged for post-migration audit (four dark-mode contrast spots plus the hardcoded warm-neutral text-color sweep). Each is a separate decision; if a fix is needed, apply it in this task with its own commit.

**Files (read first, modify only if a fix is needed):**
- Audit: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/presentation/components/CustomerAvatar.kt` (uses `DesignTokens.avatarColors[0]`)
- Audit: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/` (look for `DashedAvatar`, `AvatarWithDot`, `AvatarBadge` — exact paths discovered during the audit)
- Audit: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/PlanCard.kt` (heritage-saffron rule check)
- Audit: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/reports/presentation/components/ReportsTabRow.kt` (icon contrast inside the active pill)

- [ ] **Step 1: CustomerAvatar — verify avatarColors[0] dark-mode**

Run: `find composeApp/src/commonMain/kotlin -name "CustomerAvatar*"` and read the file.

Find the call to `DesignTokens.avatarColors[0]` (or however the saffron-era 0th pair is selected). The 0th avatar pair in `DesignTokens.kt:170` is:
```kotlin
AvatarColor(Color(0xFFFFF8E7), Color(0xFFE8A800), Color(0xFF4F3800), Color(0xFFF9CC50)), // Saffron
```
The dark-mode background `#4F3800` is a deep saffron-brown that may read as a "hole" on the new warm-ink `#14110E` dark bg.

**Decision rule:**
- If the avatar reads readably in dark mode (`PlaceholderInitialsCircle` paints `#F9CC50` text over `#4F3800` bg with at least 4.5:1 contrast — verify) — leave it. Saffron-toned default avatars are fine as heritage flavor.
- If it reads as a hole / disappears — replace the avatarColors[0] entry's `darkBg` from `Color(0xFF4F3800)` to `Color(0xFF2D3B6B)` (a deep indigo, harmonized with the new palette). Light-mode pair stays untouched for warm cohesion with paper bg.

Run an Android debug build (`./gradlew :composeApp:installDebug`) and switch the device to dark mode to see the result. Document the choice with a short commit message.

- [ ] **Step 2: Order-detail avatar composables — verify**

Run: `grep -rln "DashedAvatar\|AvatarWithDot\|AvatarBadge" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/`

For each match, read the file. Verify that these composables either:
- Use `MaterialTheme.colorScheme.primaryContainer`/`onPrimaryContainer` (clean — no change needed); or
- Still use a hardcoded brown/saffron hex that disappears on warm-ink dark bg (needs fix).

If a fix is needed, apply the same `primaryContainer/onPrimaryContainer` mapping pattern used elsewhere.

- [ ] **Step 3: PlanCard heritage-saffron rule check**

Read `PlanCard.kt` lines 100–290. Per the brand rules (in `docs/superpowers/specs/2026-05-14-rebrand-design.md`), saffron is RARE — reserved for PRO badge, Verified Tailor chip, achievement burst.

Verify:
- The "FREE" plan pill (lines 122–126 in the unmigrated file — now indigo via Task 5 Step 3) reads as indigo, not saffron. ✓ already correct post-migration.
- The "ALMOST FULL" warn-state pill (lines 174–176, also migrated to indigo) — verify intent. If the design intent was a warning amber/saffron pill, this should route through `LocalStitchPadColors.current.heritageAccent` instead of `colorScheme.primary`. **Default judgment:** "almost full" is a state warning, not a heritage moment — keep indigo.
- The "PRO" badge (if it exists in this file) — saffron via `LocalStitchPadColors.current.heritageAccent` is correct here. Verify the badge exists and uses the right slot.

Document the audit conclusion in a comment in the file ONLY if the file needed no change. If a fix was needed, apply it.

- [ ] **Step 4: ReportsTabRow active-pill icon contrast**

Read `ReportsTabRow.kt`. The pill background after Task 4 migration is `MaterialTheme.colorScheme.primary` (the active tab's fill). Any icon or text inside the active pill must be `MaterialTheme.colorScheme.onPrimary` for guaranteed contrast.

If the icon tint inside the pill still reads from `MaterialTheme.colorScheme.onSurface` or a hardcoded hex, change it to `MaterialTheme.colorScheme.onPrimary`.

- [ ] **Step 5: Hardcoded warm-neutral text color sweep**

The PR-A bundled-fix commits handled hardcoded text colors on **primary-fill** containers (where dark text used to read because saffron was light). But the checklist flags that other `DesignTokens.neutral{700,800,900}` text references may still exist outside primary-fill contexts.

Run: `grep -rn "color = DesignTokens.neutral" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/`

For each match, classify:
- **Migrate to scheme:** If the text reads against `MaterialTheme.colorScheme.{background,surface,surfaceVariant}` (the default content area). Replace `DesignTokens.neutral800` (or 700, 900) with `MaterialTheme.colorScheme.onBackground` / `onSurface` / `onSurfaceVariant` as appropriate to the container.
- **Leave hardcoded:** If the text reads against a specific photo/illustration backdrop where the warm tone is intentional (the auth fabric hero is the canonical example — `Color.White` directly, fixed in PR-A commit `0d1cc96`). These intentional overrides stay.

Apply migrations as a single edit pass. If zero matches, this step is a no-op — skip to Step 6.

- [ ] **Step 6: Compile Android + iOS + detekt after any fixes**

- [ ] **Step 7: Commit any audit-driven changes**

Use a single commit for whatever bundle of audit fixes was needed:

```bash
git add <changed audit files>
git commit -m "$(cat <<'EOF'
fix(rebrand): dark-mode contrast audit follow-ups

<describe what was changed: avatar darkBg swap, order-detail avatar fix,
PlanCard heritage check, ReportsTabRow icon tint — whichever applied>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If no audit fixes were needed (clean pass), skip the commit and proceed to Task 9.

---

## Task 9: Manual smoke test + PR

**Files:**
- N/A (testing + PR creation)

- [ ] **Step 1: Android smoke test**

Run: `./gradlew :composeApp:installDebug`
Then on the connected Android emulator (Pixel 8 or similar):

Walk these 10 screens in BOTH light and dark mode:
1. Splash → Auth (login + signup with at-rest checkbox toggle)
2. Forgot Password
3. Workshop setup (verify the "(Optional)" pill on the WhatsApp row reads cleanly in both modes — this was the saffron-literal fix)
4. Dashboard (verify hero card accents)
5. Customer list (verify FilterChip selected state)
6. Customer detail (verify avatar — this was the audit-flagged area)
7. Order list + Order form (verify date picker today/selected states)
8. Measurement form (verify section pills)
9. Reports tab (verify tab pill + KPI tile icons + sparkline color + empty state + outstanding-balances saffron-rename area)
10. Settings home + all subscreens (Edit Profile, Change Email, Change Password, Delete Account flow with reason sheet)

Watch for: any spot still rendering saffron / amber when it should be indigo; any text reading as a hole on a primaryContainer background; the "(Optional)" pill from Task 2.

Document any regressions found — they go in a follow-up commit before the PR opens.

- [ ] **Step 2: iOS smoke test**

Per `reference_test_environment` memory, run on iPhone 17 Pro sim.

Walk the same 10 screens in both modes. Pay special attention to Fraunces serif rendering on the auth wordmark (per `feedback_kotlin_native_epoch_days` and `feedback_kmp_jvm_only_apis` memories — iOS surfaces a different class of bug than Android).

- [ ] **Step 3: Push the branch**

Run: `git push -u origin feature/rebrand-migration`

- [ ] **Step 4: Open the PR**

Use a HEREDOC for the body. Smoke test steps are mandatory per `feedback_qa_smoke_tests` memory:

```bash
gh pr create --title "refactor(rebrand): PR-B — migrate 103 callsites to MaterialTheme.colorScheme + delete aliases" --body "$(cat <<'EOF'
## Summary

Completes the rebrand by migrating the remaining 103 `DesignTokens.primary*`
callsites across 34 feature files to `MaterialTheme.colorScheme.{primary,primaryContainer,on*}`,
removing the lone `Color(0xFFE8A800)` saffron literal in WorkshopSetupScreen,
and deleting all 11 `@Deprecated` aliases from DesignTokens.kt.

PR-A (#42) shipped the indigo tokens + alias chain; this PR finishes the
sweep so the compiler now enforces the new theme contract.

- 34 feature files migrated (auth, onboarding, dashboard, customer, main, order, measurement, reports, settings, ui/components)
- 11 @Deprecated aliases deleted; compile-driven verification catches any miss
- 4 dark-mode contrast audit items resolved (CustomerAvatar, order-detail avatars, PlanCard heritage rule, ReportsTabRow pill icons)

## Test plan

Manual smoke on Android (Pixel 8) and iOS (iPhone 17 Pro sim) — verify in both light and dark mode:

- [ ] Auth flow (login + signup with checkbox toggle) — primary-color CTAs are indigo
- [ ] Forgot Password — code-resend section pill reads cleanly
- [ ] Workshop setup — "(Optional)" pill next to WhatsApp number reads cleanly (was the saffron-literal fix)
- [ ] Dashboard — hero card accents are indigo, not amber
- [ ] Customer list — FilterChip selected state is indigo border + indigo text
- [ ] Customer detail — default avatar reads with adequate contrast (audit item)
- [ ] Order form + Order list — date picker today/selected states are indigo
- [ ] Measurement form — section pills indigo
- [ ] Reports tab — active pill is indigo with white icon (audit item), empty state pill indigo, outstanding balances renders correctly
- [ ] Settings home → all subscreens (Edit Profile, Change Email, Change Password, Delete Account + reason sheet)
- [ ] No deprecation warnings in build output

## CI

- [ ] secrets-scan
- [ ] detekt
- [ ] functions-tests
- [ ] build-android
- [ ] build-ios

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Watch CI**

Run: `gh pr checks --watch` (or `gh pr view --json statusCheckRollup` polling).

If anything red, fix in a follow-up commit on the same branch.

When green, the PR is ready for review and merge.

---

## Out of scope for PR-B (tracked elsewhere)

- **Adire illustration swap (PR-C).** Daniel has placed 12 `_adire`-suffix replacement PNGs at `~/Desktop/stitchpad_images/`: `auth_background_adire.png`, `auth_background_adire_two.png`, plus 10 dashboard heroes/empty states (`dashboard_hero_{welcome,busy,quiet,steady,pickup,first_order,money}_adire.png`, `dashboard_empty_{customers,nba,pipeline}_adire.png`). Integration path is `cwebp -q 80 <src>.png -o composeApp/src/commonMain/composeResources/drawable/<original-name-without-_adire>.webp`. Keep separate from PR-B to limit blast radius and review surface.
- **OrderReceiptSharer saffron headers.** Both `OrderReceiptSharer.android.kt` and `OrderReceiptSharer.ios.kt` still paint receipt headers/totals/borders with `#E8A800`. Cursor flagged for PR-C follow-up; receipts are customer-facing shared images and warrant their own bundled review.
- **Shared StitchPadLogo composable.** Cursor noted two near-duplicate `StitchPadLogo` composables (auth + onboarding). Worth a follow-up refactor but bundling here bloats PR-B; track as a separate small PR after migration lands.
- **Asset weight check.** `auth_background.webp` grew ~55KB → ~151KB and a new `auth_background_alt.webp` (~156KB) shipped in PR-A. Worth an APK-size + cold-start check on a low-end device, but it's a perf concern not a rebrand correctness one.

## Verification expectations (final)

After all 10 tasks complete, these must all hold:

- `grep -rn "DesignTokens.primary" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/` returns no output.
- `grep -rn "@Deprecated" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt` returns no output (or only deprecations unrelated to primary aliases — none currently exist in the file).
- `./gradlew :composeApp:assembleDebug` succeeds with no deprecation warnings.
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64` succeeds.
- `./gradlew detekt` succeeds with no new findings.
- Manual smoke on Android + iOS in both modes shows no regressions vs. PR-A.
- The "(Optional)" pill in WorkshopSetupScreen and any audit-item fixes render correctly in dark mode.

When all of the above pass, PR-B is mergeable.
