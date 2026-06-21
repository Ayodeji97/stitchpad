# Scroll-to-dismiss keyboard — design

**Status:** Design approved (2026-06-21)
**Branch:** `feat/scroll-to-dismiss-keyboard`
**Source:** tester pain point — on a multi-field form the soft keyboard only dismisses
by tapping outside; the expected mobile behavior (scroll the form → keyboard closes)
is missing.

## Problem

The app dismisses the soft keyboard only via `clearFocusOnTap()` (tap an empty area).
Users expect the standard behavior where **dragging/scrolling a form dismisses the
keyboard**. Without it, on a form taller than the visible area the user has to hunt
for empty space to tap.

## Approach

Add a reusable `Modifier.dismissKeyboardOnScroll()` next to the existing
`clearFocusOnTap()` in `util/KeyboardDismiss.kt`. It attaches a remembered
`NestedScrollConnection` that, on a **user-drag** scroll, clears focus and dismisses
the keyboard — then consumes nothing so the scroll proceeds normally. Apply it to the
scrollable container of every input-bearing screen.

### Why nestedScroll + UserInput only (not scroll-state observation)

Reacting only to `NestedScrollSource.UserInput` is essential: Compose performs a
**programmatic** auto-scroll to keep a newly-focused field above the keyboard. If we
dismissed on any scroll (e.g. observing `scrollState`), tapping a field would trigger
that auto-scroll and **immediately close the keyboard you just opened**. Filtering to
user-drag avoids this. A single app-level nestedScroll was also rejected — it won't
catch every independently-scrolling container.

## The modifier

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/util/KeyboardDismiss.kt`:

```kotlin
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

- `clearFocus()` hides the IME for Compose `BasicTextField`/`OutlinedTextField`.
- `dismissNativeKeyboard()` (the existing `expect/actual`) resigns the iOS native
  `UITextField`s (auth fields), which are outside Compose's focus system — same
  pairing `clearFocusOnTap()` already uses, so behavior stays consistent. No-op on
  Android.
- `available.y != 0f` ensures it only fires on a real vertical drag.
- `NestedScrollSource.UserInput` — if the installed Compose version still exposes the
  older `Drag` name, use whichever the version provides (verify at implement time).

## Rollout

Apply `.dismissKeyboardOnScroll()` to the **scrollable container** (the `verticalScroll`
Column or the `LazyColumn`/`LazyVerticalGrid`) on every screen that has text input.
Priority set = the screens that already use `clearFocusOnTap()` plus the New-Order form:

- `feature/order/presentation/form/OrderFormScreen.kt`
- `feature/auth/presentation/login/LoginScreen.kt`, `signup/SignUpScreen.kt`,
  `forgotpassword/ForgotPasswordScreen.kt`
- `feature/customer/presentation/...` (add/edit customer form), `customer/presentation/list/CustomerListScreen.kt` (search field)
- `feature/settings/presentation/changepassword/ChangePasswordScreen.kt`,
  `changeemail/ChangeEmailScreen.kt`, `editprofile/EditProfileScreen.kt`,
  `deleteaccount/DeleteAccountReasonSheet.kt`, `components/ReauthBottomSheet.kt`
- `feature/onboarding/presentation/workshop/WorkshopSetupScreen.kt`
- `feature/smart/presentation/draft/DraftMessageScreen.kt`
- `feature/style/presentation/form/StyleFormScreen.kt` (title/description-era fields — verify current inputs)

On pure content lists with no keyboard (dashboard, orders list, reports) the modifier
is a harmless no-op; applying it there is optional — prefer the input-bearing screens
above to keep the diff focused, but it is safe anywhere.

**Placement note:** the modifier goes on the SAME node as `verticalScroll(...)` /
the `LazyColumn` (the node that owns the scroll). For a `verticalScroll` Column, chain
`.dismissKeyboardOnScroll().verticalScroll(state)`. For `LazyColumn`/`LazyVerticalGrid`,
add it to their `modifier`.

## Bottom sheets

Sheets with their own scrolling input (`ReauthBottomSheet`, `DeleteAccountReasonSheet`,
the picker sheets) get the modifier on their internal scroll container too, so dragging
the sheet content dismisses the keyboard like the screens.

## Error handling / edge cases

- Idempotent: `clearFocus()` with nothing focused is a no-op; firing repeatedly during
  a drag is cheap and harmless.
- Does not interfere with the focus auto-scroll (filtered to user-drag).
- Does not consume scroll deltas, so scroll/fling behavior is unchanged.

## Testing

NestedScroll modifiers aren't cleanly unit-testable. Verification is a manual smoke
pass on a device (Daniel as QA):

1. New Order (or any multi-field form): tap a field → keyboard opens → **drag to
   scroll** → keyboard dismisses. Scrolling works normally afterward.
2. Tap a field near the bottom → the keyboard opens and the field auto-scrolls into
   view; confirm the keyboard does **NOT** immediately dismiss (the auto-scroll must
   not trip it).
3. iOS auth (login/signup, native UITextFields): focus a field → drag the form →
   keyboard dismisses (verifies `dismissNativeKeyboard()` fires on scroll).
4. A bottom sheet with input (Reauth): focus → drag the sheet → keyboard dismisses.
