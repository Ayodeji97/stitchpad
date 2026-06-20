package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Platform IME options that enable OS autofill for an auth field.
 *
 * Android returns null — autofill there works through the Compose `contentType`
 * semantics already set in [AuthTextField], plus `LocalAutofillManager.commit()`.
 *
 * iOS returns options that switch the field to CMP's native text-input mode and
 * map the role to a `UITextContentType`, which is the only path that surfaces
 * iCloud Keychain autofill on iOS in CMP 1.11.
 */
expect fun authImeOptions(autofill: AuthAutofill): PlatformImeOptions?
