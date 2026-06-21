package com.danzucker.stitchpad.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication

/**
 * Sends `resignFirstResponder` to whatever native view currently holds it, which
 * dismisses the keyboard. Used because the auth fields are native UITextFields
 * that Compose's focus manager cannot resign.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual fun dismissNativeKeyboard() {
    UIApplication.sharedApplication.sendAction(
        NSSelectorFromString("resignFirstResponder"),
        to = null,
        from = null,
        forEvent = null,
    )
}
