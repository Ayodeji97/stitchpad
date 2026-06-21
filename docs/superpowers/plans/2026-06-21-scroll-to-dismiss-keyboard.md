# Scroll-to-dismiss Keyboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Dragging/scrolling any input-bearing screen dismisses the soft keyboard (standard mobile behavior), via a reusable `Modifier.dismissKeyboardOnScroll()`.

**Architecture:** A remembered `NestedScrollConnection` that, on a USER-DRAG scroll only, clears Compose focus + resigns the iOS native first responder, then consumes nothing. Lives next to `clearFocusOnTap()` in `util/KeyboardDismiss.kt`. Rolled out onto each screen's scroll container. UI behavior — manual smoke tested (nestedScroll isn't cleanly unit-testable).

**Tech Stack:** KMP, Compose Multiplatform, the existing `dismissNativeKeyboard()` expect/actual.

**Spec:** `docs/superpowers/specs/2026-06-21-scroll-to-dismiss-keyboard-design.md`.

---

## Task 1: The `dismissKeyboardOnScroll()` modifier

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/util/KeyboardDismiss.kt`

- [ ] **Step 1: Add the modifier**

Append to `KeyboardDismiss.kt` (mirrors `clearFocusOnTap`'s dismissal pairing — `clearFocus()` for Compose fields + `dismissNativeKeyboard()` for the iOS native UITextFields):
```kotlin
/**
 * Scroll-container modifier that dismisses the soft keyboard when the user DRAGS
 * to scroll. Reacts only to user-input scrolls (not the programmatic auto-scroll
 * Compose does to keep a newly-focused field above the keyboard — otherwise tapping
 * a field would instantly close the keyboard). Consumes nothing, so scroll/fling
 * behave normally.
 */
@Composable
fun Modifier.dismissKeyboardOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    focusManager.clearFocus()
                    dismissNativeKeyboard()
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
```
Add imports: `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.input.nestedscroll.NestedScrollConnection`, `androidx.compose.ui.input.nestedscroll.NestedScrollSource`, `androidx.compose.ui.input.nestedscroll.nestedScroll`.

- [ ] **Step 2: Resolve the `NestedScrollSource` enum name for this Compose version**

`NestedScrollSource.UserInput` is the current name (formerly `Drag`). If Step 3's compile fails with "unresolved reference: UserInput", use `NestedScrollSource.Drag` instead (whichever the installed CMP exposes). Do not guess — let the compiler decide.

- [ ] **Step 3: Compile both platforms + iOS test compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Expected: clean (no callers yet — Task 2 applies it). If the enum name failed, fix per Step 2 and re-run.

- [ ] **Step 4: Commit**
```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/util/KeyboardDismiss.kt
git commit -m "feat(ux): add dismissKeyboardOnScroll modifier"
```

---

## Task 2: Roll out across the input-bearing scrollable screens

**Files (resolve the exact set by grep):**
- `feature/order/presentation/form/OrderFormScreen.kt`
- `feature/style/presentation/form/StyleFormScreen.kt`
- Every screen in the `clearFocusOnTap` set:
  `feature/auth/presentation/login/LoginScreen.kt`, `signup/SignUpScreen.kt`,
  `forgotpassword/ForgotPasswordScreen.kt`;
  `feature/settings/presentation/changepassword/ChangePasswordScreen.kt`,
  `changeemail/ChangeEmailScreen.kt`, `editprofile/EditProfileScreen.kt`,
  `deleteaccount/DeleteAccountReasonSheet.kt`, `components/ReauthBottomSheet.kt`;
  `feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt`;
  `feature/smart/presentation/draft/DraftMessageScreen.kt`;
  `feature/customer/presentation/list/CustomerListScreen.kt` + the customer add/edit form screen (grep `CustomerForm`/`customer*form` for the file).

- [ ] **Step 1: Enumerate the targets**

Run `grep -rln 'clearFocusOnTap' composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature` and add `OrderFormScreen.kt` + `StyleFormScreen.kt` + the customer add/edit form. For each file, locate its PRIMARY scrollable container that holds the text inputs: a `Column(... .verticalScroll(state))` or a `LazyColumn`/`LazyVerticalGrid`.

- [ ] **Step 2: Apply the modifier to each scroll container**

Add `import com.danzucker.stitchpad.util.dismissKeyboardOnScroll` to each file, then add `.dismissKeyboardOnScroll()` to the scroll container's `Modifier`:
- For a verticalScroll Column, chain it adjacent to the scroll: `Modifier.fillMaxSize().dismissKeyboardOnScroll().verticalScroll(scrollState)` (order relative to `verticalScroll` doesn't matter for nestedScroll, but keep it on the same node).
- For `LazyColumn`/`LazyVerticalGrid`, add `.dismissKeyboardOnScroll()` to its `modifier`.
- For the bottom sheets (`ReauthBottomSheet`, `DeleteAccountReasonSheet`, and any picker sheet with scrolling input), apply it to the sheet's internal scroll container.
Apply to ONE input-bearing scroll container per screen (the one that owns the form fields). Skip nested non-form scrollables.

- [ ] **Step 3: Compile both platforms + iOS test compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q`
Then: `./gradlew :composeApp:testDebugUnitTest --tests '*Order*' --tests '*Style*' --tests '*auth*' -q`
Expected: all green (no logic changed; existing tests unaffected).

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "feat(ux): dismiss keyboard on scroll across input screens"
```

---

## Manual smoke test (device — Daniel is QA)
1. New Order: tap a field → keyboard opens → **drag to scroll** → keyboard dismisses; scrolling still works.
2. Tap a field near the bottom → keyboard opens, field auto-scrolls into view → confirm the keyboard does **NOT** auto-dismiss (the focus auto-scroll must not trip it).
3. iOS Login/Signup (native UITextFields): focus a field → drag the form → keyboard dismisses.
4. Reauth bottom sheet: focus → drag the sheet content → keyboard dismisses.

## Self-review notes
- Modifier (Task 1) + rollout (Task 2). ✓
- User-drag-only filtering prevents the focus-auto-scroll dismiss footgun. ✓
- iOS native fields handled via `dismissNativeKeyboard()`. ✓
- iOS **test** compile in every gate. ✓
