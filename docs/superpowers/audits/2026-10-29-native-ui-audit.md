# Native Android/iOS look & feel: audit (2026-10-29)

Daniel asked for the app to feel native on both Android (Material 3) and iOS (Human Interface Guidelines), while keeping the Adire Atelier brand.

This is a read-only audit and the input to a separate design/plan after Africa expansion PR 1. **Today, all shared UI is Material 3, with no per-platform adaptation** and no adaptive library (no Calf, no compose-cupertino).

## Key findings

### Real bug: swipe-back on iOS
`iosMain/.../util/BackHandler.ios.kt` is a no-op, so an iOS swipe-back ignores every in-screen back intercept:
- the multi-step form step-back in `OrderFormScreen.kt:264`
- the "processing" guard in `DeleteAccountScreen.kt:66,71`
- `CelebrationOverlay.kt:162`
- the FAB collapse in `DashboardScreen.kt:636`

CMP 1.11 has a common back handler, so the old comment saying it doesn't is out of date.

### Android
NavHost gets predictive back by default (targetSdk 36). No `PredictiveBackHandler` is used.

### Top bars and tab bar
- **Top bars:** 36 `TopAppBar` calls and 0 centred-title bars. 67 back-arrow icons and no iOS chevrons.
- **Tab bar:** `MainScreen.kt:162-200` uses the M3 `NavigationBar` with a pill indicator, which isn't an iOS look.

### Sheets, alerts and menus
- **Sheets:** 39 `ModalBottomSheet` calls. Only 2 skip the half-expanded state.
- **Alerts:** 25 `AlertDialog` calls in 14 files, all M3 style.
- **Overflow menus:** 9 `DropdownMenu` calls plus 1 `ExposedDropdownMenu`.
- `PhotoSourceSheet` and `CustomerActionsSheet` should be iOS action sheets.

### Pickers and controls
- **Date pickers:** `CustomDatePickerDialog` and `CustomRangePickerDialog` are Material-style. iOS should use `UIDatePicker`.
- **Controls:**
  - Switches: 8 calls in 3 files, all M3 style.
  - Segmented controls: 3 files, in M3 `SegmentedButton` style rather than UISegmentedControl.
  - Spinners: 36 `CircularProgressIndicator` calls.
  - Press feedback: iOS gets Android ripples everywhere, because there is no `LocalIndication` override in `Theme.kt`.

### Platform behaviours
- **Keyboard:** 21 numeric or phone keyboards have no iOS "Done" bar, and `imePadding` is used in only 4 files.
- **Haptics:** a single call in the whole app (`CelebrationOverlay.kt:156`).
- **Already native:** the share sheet (`UIActivityViewController`), the auth text fields (`UITextField`), safe areas and Dynamic Type.

### Typography
- Check on iOS that Fraunces' variable font weight is actually applied; if not, all weights render the same.
- `LineHeightStyle` is not set, so text in buttons and chips may sit slightly off-centre on iOS.

### Existing pieces to build on
- The expect/actual pattern: `util/Platform.kt` (`isIos`), `BackHandler`, `KeyboardDismiss`, `ReduceMotion`, `PlatformTextStyleCompat`.
- `AuthPlatformTextInput`, a working `UIKitView` example.

## Prioritised changes

### Must feel native
1. **Fix the iOS BackHandler** using CMP's common back handler. S, 2-3 files.
2. **`StitchPadAlert`:** `UIAlertController` on iOS, M3 `AlertDialog` on Android, with destructive and cancel roles. Migrate the 25 calls. M, about 16 files.
3. **`StitchPadActionSheet`:** a UIAlertController action sheet on iOS, a ModalBottomSheet on Android. Use it for photo source, customer actions and the 9 overflow menus. M, about 10 files.
4. **Native `UIDatePicker` on iOS** behind the date and range pickers; Android stays custom. M, 4-5 files.
5. **`StitchPadSwitch`:** iOS-style switch plus a selection haptic. S, 4 files.
6. **iOS number-pad "Done" bar,** plus `imePadding` on forms. M, about 12 files.
7. **Haptics helper** (`rememberHaptics()`), wired to switches, segments, swipe-delete and confirms. S-M, about 10 files.

### Brand stays custom (platform-tuned chrome)
8. **`StitchPadTopBar`:** centred title and chevron on iOS; leading title and arrow on Android. M, 36 files (mechanical).
9. **iOS tab bar** with no pill, plus `rememberStitchPadSheetState()` that skips the half-expanded state on iOS. S-M, about 34 files (mostly one line each).
10. **iOS press feedback, segments and spinner:**
    - Press feedback dims (opacity) instead of a ripple, set in `Theme.kt`.
    - An iOS-style segmented control and spinner.
    - Fix the Fraunces weights and line heights.
    - M, about 6 files.

## What stays the same on both platforms
Adire Atelier colours, radii, Fraunces/Manrope fonts, the OutlinedTextField styling and the StitchPadButton press scale.

## Already applied in Africa expansion PR 1
Task 6's `CountryPickerSheet` is built platform-adaptive: a Material sheet on Android, and an iOS-styled sheet on iOS with a grabber, search, a grouped list and a checkmark.
