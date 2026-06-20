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
  confirm-password → `None` (left untagged: tagging confirm `NewPassword` makes
  iOS leave it empty after a generated strong password, and the credential saves
  from the primary field regardless — so the tag adds risk, no benefit)

Sign-up is the primary *save* trigger (dedicated new-credential content types);
login is the *fill* trigger. Login's password stays plain `Password` — tagging it
`NewPassword` would wrongly suppress fill suggestions.

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

## iOS — not supported in CMP 1.11 (deferred)

iOS autofill is **deferred**, not delivered. Evidence:
- `AutofillManager` is `null` on iOS, so the `commit()`/save path does not exist there.
- iOS autofill only exists through an opt-in **native text input** mode whose toggle
  (`UIKitNativeTextInputContext.usingNativeTextInput` / `LocalNativeTextInputContext`)
  is `@InternalComposeUiApi` — not a production-safe public API in 1.11.
- JetBrains' 1.11 notes only promise *fill* ("filling from saved passwords one field
  at a time"); the save prompt is unconfirmed, and community reports call it flaky
  (e.g. email/username content type ignored).

Revisit when CMP stabilizes native text input (target 1.12+). The `contentType`
hints are already in place, so iOS should light up with minimal change once the
platform supports it.

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
