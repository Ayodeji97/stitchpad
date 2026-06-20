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
- **`SignUpScreen`** — email → `NewEmail`, password → `NewPassword`

Sign-up is the primary *save* trigger (dedicated new-credential content types);
login is the *fill* trigger. Login's password stays plain `Password` — tagging it
`NewPassword` would wrongly suppress fill suggestions.

## Why this fires the save prompt

Compose commits the autofill context when the autofillable nodes leave composition
— i.e. on successful login/signup navigation. At that point Android/iOS offers to
save the credential. On the next visit the OS offers to fill it.

## Out of scope

- Forgot-password screen and `ReauthBottomSheet` (per agreed Login + Sign-up scope).
- Native Credential Manager / custom credential-picker UI.
- Session persistence (Firebase Auth already persists sessions; not the reported pain).

## Expected iOS behavior to note during testing

Tagging the sign-up password field as `newPassword` makes iOS offer an
auto-generated strong password (tinted QuickType overlay). The user can tap
**"Choose My Own Password"** to type their own — generation is never forced. This
is standard iOS behavior, not a defect.

## Testing / verification

Pure Compose semantics change — no ViewModel logic changes, so existing unit tests
remain valid. Autofill does not surface reliably on simulators, so verification is
a manual smoke test on **real devices**:

**Android device**
1. Sign up with a new email/password → expect "Save password to Google?" prompt.
2. Sign out → return to Login → expect the email/password to be offered as autofill.

**iOS device**
1. Sign up → expect iCloud Keychain "Save Password?" (or use the strong-password
   suggestion) → confirm saved.
2. Sign out → return to Login → expect the QuickType / key-icon autofill suggestion.

## Risks

- Experimental Compose API (`@ExperimentalComposeUiApi`) — surface area is small
  and contained to `AuthTextField`.
- iOS autofill maturity in CMP — verify on a real iPhone before declaring done
  (matches the project's standing rule to run a real iOS device for auth changes).
