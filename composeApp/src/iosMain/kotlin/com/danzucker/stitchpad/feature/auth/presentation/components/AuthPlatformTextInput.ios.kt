package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIFont
import platform.UIKit.UIKeyboardTypeDefault
import platform.UIKit.UIKeyboardTypeEmailAddress
import platform.UIKit.UIKeyboardTypeNumberPad
import platform.UIKit.UIKeyboardTypePhonePad
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextBorderStyle
import platform.UIKit.UITextContentTypeNewPassword
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
internal actual fun AuthPlatformTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    autofill: AuthAutofill,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean,
    isPasswordVisible: Boolean,
    onFocusChange: (Boolean) -> Unit,
) {
    val coordinator = remember { AuthTextFieldCoordinator() }
    coordinator.onValueChange = onValueChange
    coordinator.onFocusChange = onFocusChange
    coordinator.imeAction = imeAction

    UIKitView(
        factory = {
            UITextField(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
                borderStyle = UITextBorderStyle.UITextBorderStyleNone
                // Match the AuthTextField card (#2A2825). A clear background here
                // composites as an opaque BLACK box through the UIKitView interop
                // layer, so paint the card colour to blend in instead.
                setOpaque(false)
                backgroundColor = fieldBackgroundColor
                textColor = fieldTextColor
                tintColor = brandAccentColor
                font = UIFont.systemFontOfSize(15.0)
                autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
                autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
                delegate = coordinator
                addTarget(
                    target = coordinator,
                    action = NSSelectorFromString("editingChanged:"),
                    forControlEvents = UIControlEventEditingChanged,
                )
            }
        },
        modifier = modifier.height(24.dp),
        update = { field ->
            coordinator.field = field
            field.textContentType = autofill.toUITextContentType()
            field.keyboardType = keyboardType.toUIKeyboardType(isPassword)
            field.returnKeyType = imeAction.toUIReturnKeyType()
            field.secureTextEntry = isPassword && !isPasswordVisible
            field.placeholder = placeholder
            if (field.text.orEmpty() != value) {
                field.text = value
            }
        },
    )
}

private class AuthTextFieldCoordinator : NSObject(), UITextFieldDelegateProtocol {
    var onValueChange: (String) -> Unit = {}
    var onFocusChange: (Boolean) -> Unit = {}
    var imeAction: ImeAction = ImeAction.Default
    var field: UITextField? = null

    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun editingChanged(sender: UITextField) {
        onValueChange(sender.text.orEmpty())
    }

    override fun textFieldDidBeginEditing(textField: UITextField) {
        onFocusChange(true)
    }

    override fun textFieldDidEndEditing(textField: UITextField) {
        onFocusChange(false)
    }

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        textField.resignFirstResponder()
        onFocusChange(false)
        return true
    }
}

private fun AuthAutofill.toUITextContentType() = when (this) {
    AuthAutofill.LoginEmail, AuthAutofill.NewEmail -> UITextContentTypeUsername
    AuthAutofill.LoginPassword -> UITextContentTypePassword
    AuthAutofill.NewPassword -> UITextContentTypeNewPassword
    AuthAutofill.None -> null
}

private fun KeyboardType.toUIKeyboardType(isPassword: Boolean) = when {
    isPassword -> UIKeyboardTypeDefault
    this == KeyboardType.Email -> UIKeyboardTypeEmailAddress
    this == KeyboardType.Number -> UIKeyboardTypeNumberPad
    this == KeyboardType.Phone -> UIKeyboardTypePhonePad
    else -> UIKeyboardTypeDefault
}

private fun ImeAction.toUIReturnKeyType() = when (this) {
    ImeAction.Next -> UIReturnKeyType.UIReturnKeyNext
    else -> UIReturnKeyType.UIReturnKeyDone
}

// Matches the AuthTextField card background Color(0xFF2A2825).
private val fieldBackgroundColor = UIColor(
    red = 0x2A / 255.0,
    green = 0x28 / 255.0,
    blue = 0x25 / 255.0,
    alpha = 1.0,
)

private val fieldTextColor = UIColor(
    red = 0xF5 / 255.0,
    green = 0xF2 / 255.0,
    blue = 0xED / 255.0,
    alpha = 1.0,
)

// Cursor/tint = brandAccent on dark surfaces (DesignTokens.indigo200 #9CB0DD).
// Auth fields always render on a dark card. NOT saffron — saffron is a rare
// heritage accent only, never a primary UI element like a caret.
private val brandAccentColor = UIColor(
    red = 0x9C / 255.0,
    green = 0xB0 / 255.0,
    blue = 0xDD / 255.0,
    alpha = 1.0,
)
