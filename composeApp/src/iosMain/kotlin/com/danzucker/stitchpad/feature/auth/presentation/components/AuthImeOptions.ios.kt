package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername

/**
 * Enables CMP's native iOS text-input mode and tags the field with a
 * `UITextContentType` so iCloud Keychain offers to fill/save.
 *
 * Email/username fields use `.username` (the identifier iCloud pairs with the
 * password). Sign-up password uses `.newPassword` (offers a strong password and
 * the save prompt); login password uses `.password` (offers to fill a saved one).
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun authImeOptions(autofill: AuthAutofill): PlatformImeOptions? {
    val contentType = when (autofill) {
        AuthAutofill.LoginEmail, AuthAutofill.NewEmail -> UITextContentTypeUsername
        AuthAutofill.LoginPassword -> UITextContentTypePassword
        AuthAutofill.NewPassword -> UITextContentTypeNewPassword
        AuthAutofill.None -> return null
    }
    return PlatformImeOptions {
        usingNativeTextInput(true)
        textContentType(contentType)
    }
}
