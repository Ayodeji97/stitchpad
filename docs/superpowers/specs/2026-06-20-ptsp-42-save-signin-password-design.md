# PTSP-42 — Save & autofill sign-in credentials

**Status:** Design locked
**Date:** 2026-06-20
**Branch:** `fix/ptsp-42-save-signin-password`
**Platforms:** Android + iOS (single common-code change)

## Problem

Testers must re-type their email and password on every login. The OS never offers
to save the credential, so Google Password Manager (Android) and iCloud Keychain
(iOS) have nothing to autofill on return visits.

**Root cause:** `AuthTextField`
(`feature/auth/presentation/components/AuthTextField.kt`) wraps a `BasicTextField`
with no autofill semantics. Without a content-type hint, neither platform's
credential manager recognizes the email/password fields, so it never offers to
save or fill them.

## Approach

Use Compose Multiplatform's built-in autofill `ContentType` semantics. The project
is on CMP 1.11.0, which maps these semantics to native autofill on **both** Android
(→ Google Password Manager) and iOS (→ iCloud Keychain) from one common-code path.
No expect/actual, no native Credential Manager wiring.

Rejected alternative: native Android Credential Manager API + iOS AutoFill via
expect/actual. Gives finer control (custom credential picker UI) but is far more
code and redundant now that CMP exposes autofill natively. Not warranted by this
ticket.

## Changes

### 1. New enum — `AuthAutofill`
Small file in `feature/auth/presentation/components/`. Keeps the experimental
Compose autofill API contained behind an app-level type so call sites stay clean.

```kotlin
enum class AuthAutofill { LoginEmail, LoginPassword, NewEmail, NewPassword, None }
```

### 2. `AuthTextField`
Add one parameter: `autofill: AuthAutofill = AuthAutofill.None`.

Inside the composable, map it to a Compose `ContentType` and apply
`Modifier.semantics { contentType = … }` on the `BasicTextField`. All
experimental autofill imports and the `@OptIn` annotation stay contained in this
one file.

Mapping:

| `AuthAutofill` | Compose `ContentType`        |
|----------------|------------------------------|
| `LoginEmail`   | `Username + EmailAddress`    |
| `LoginPassword`| `Password`                   |
| `NewEmail`     | `NewUsername + EmailAddress` |
| `NewPassword`  | `NewPassword`                |
| `None`         | (no semantics added)         |

### 3. Call sites
- **`LoginScreen`** — email → `LoginEmail`, password → `LoginPassword`
- **`SignUpScreen`** — email → `NewEmail`, password → `NewPassword`,
  confirm-password → `NewPassword` (the standard confirm pattern). This is
  **required**, not cosmetic: `BasicTextField` auto-derives `ContentType.Password`
  from the password keyboard (see note below), so an untagged confirm field would
  become a stray *existing-password fill* target. Tagging it `NewPassword` folds it
  into the new credential. (iOS autofill is deferred, so the iOS "empty confirm after
  generated strong password" concern doesn't apply now; revisit when iOS is enabled.)

Sign-up is the primary *save* trigger (dedicated new-credential content types);
login is the *fill* trigger. Login's password stays plain `Password` — tagging it
`NewPassword` would wrongly suppress fill suggestions.

### Framework auto-derivation (important)

In CMP 1.11, `BasicTextField` already assigns a content type from the keyboard type
(`CoreTextFieldSemanticsModifier`): `Email → EmailAddress`, `Password → Password`,
`Phone → PhoneNumber`. Our explicit `Modifier.semantics { contentType = … }`
*overrides* that default (per Android's autofill docs). Consequence: a field cannot
be fully opted *out* of autofill just by omitting an app-level role — a password
field is always at least `Password`. To change its role you must set an explicit one.

## Triggering the save prompt (Android) — `commit()` is required

> **Revised 2026-06-20 after device testing showed nothing on either platform.**

Setting `contentType` alone was **not enough**. Per Google's Compose autofill docs,
the save dialog is raised when the autofill session is *committed*. Auto-commit on
"navigate away" is timing-dependent and did not fire for our submit-then-navigate
flow. The deterministic, documented fix is to call
`LocalAutofillManager.current?.commit()` on success:

- `LoginRoot` — on `LoginEvent.NavigateToHome` (login succeeded), before navigating.
- `SignUpRoot` — on `SignUpEvent.NavigateToEmailVerification` (email/password sign-up
  succeeded), before navigating. SSO paths have no password to save, so they're skipped.

`LocalAutofillManager` is `androidx.compose.ui.platform.LocalAutofillManager`; it
returns a non-null `AutofillManager` on Android and **`null` on iOS** (the iOS owner
hardcodes `autofillManager = null`), so the call is a safe no-op on iOS.

**Android also requires, on the device:** a credential provider enabled in system
settings (Settings → Passwords / Autofill service → Google). With no provider
selected, autofill never appears regardless of code — this is the most common cause
of "nothing happened." Fill additionally needs a previously-saved credential to offer.

## iOS — native UITextField bridge (PR #201, pending device verification)

Two earlier Compose-level approaches both failed:
1. `contentType` semantics — ignored on iOS (`AutofillManager` is `null` there).
2. CMP native text-input mode (`PlatformImeOptions { usingNativeTextInput(true) }`)
   — **juddered** the auth screens on keyboard dismiss and still surfaced no
   autofill on device. Reverted.

**Current approach:** the editable core of `AuthTextField` is now an
`expect/actual` composable, `AuthPlatformTextInput`:
- **Android** (`.android.kt`) — the original Compose `BasicTextField`, unchanged
  (Android autofill keeps working as in #200).
- **iOS** (`.ios.kt`) — a real **UIKit `UITextField` via `UIKitView`**, with
  `textContentType` set to `.username` / `.password` / `.newPassword`, a
  `UITextFieldDelegate` coordinator bridging value/focus changes back to Compose.
  The surrounding Compose chrome (icon, dark card, eye toggle, error/helper) is
  unchanged.

**Required infra — Associated Domains `webcredentials`:** iOS only binds iCloud
Keychain credentials to the app if the live
`link.getstitchpad.com/.well-known/apple-app-site-association` includes a
`webcredentials` section. As of this writing it has only `applinks`. Add:
```json
"webcredentials": { "apps": ["7DUJFVWF7W.com.danzucker.stitchpad"] }
```
The app entitlement `webcredentials:link.getstitchpad.com` is already added.
Until the AASA is deployed, iOS autofill will NOT work.

**Verify on device:** autofill fill/save; and that the bridge didn't regress
typing, caret, password masking + show/hide toggle, field-to-field navigation, and
scrolling (UIKitView in a scroll container).

**Keyboard "Next" key — fixed.** An `AuthFocusController` (common) keeps an ordered
registry of the on-screen fields; the iOS bridge registers each `UITextField` in
composition order (and unregisters on `onRelease`). `textFieldShouldReturn` advances
first responder to the next registered field for `ImeAction.Next`, and dismisses on
`Done`/the last field. Android is unchanged (Compose `KeyboardActions` already does this).

**Remaining follow-ups (non-blocking):** the native iOS field uses the system font
(not Manrope) and the default placeholder color (not `#7D7970`).

## Out of scope

- Forgot-password screen and `ReauthBottomSheet` (per agreed Login + Sign-up scope).
- Native Credential Manager / custom credential-picker UI.
- Native UIKit `UITextField` bridge for iOS (considered; too costly vs. value now).
- Session persistence (Firebase Auth already persists sessions; not the reported pain).

## Testing / verification

Autofill does not surface on simulators/emulators reliably, so verify on a **real
Android device** (iOS is deferred — expect nothing there yet).

**Android device — prerequisite (do this first):**
Settings → search "Autofill service" (or Passwords & accounts) → set the service to
**Google**. Without this, nothing below will appear.

**Android device — smoke test**
1. Sign up with a brand-new email/password → on success expect "Save password to
   Google?" (the `commit()` on sign-up success raises this).
2. Sign out → return to Login → focus the email field → expect a fill chip above the
   keyboard offering the saved credential.
3. (Optional) Log in with a new credential typed manually → on success expect a
   save/update prompt.

**iOS device** — deferred; no autofill expected yet (see "iOS — not supported" above).

## Risks

- `contentType` semantics use `@ExperimentalComposeUiApi` — contained to `AuthTextField`.
- `LocalAutofillManager` / `commit()` are stable (non-experimental) common APIs.
- Android save dialog ultimately depends on the device's autofill service and the
  provider's heuristics — code can request the save, but the OS decides whether to show it.
