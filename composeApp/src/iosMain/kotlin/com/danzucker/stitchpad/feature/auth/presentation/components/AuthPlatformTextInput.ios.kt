package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSAttributedString
import platform.Foundation.NSSelectorFromString
import platform.Foundation.create
import platform.UIKit.NSForegroundColorAttributeName
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
    accessibilityLabel: String,
    autofill: AuthAutofill,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean,
    isPasswordVisible: Boolean,
    onFocusChange: (Boolean) -> Unit,
) {
    val focusController = LocalAuthFocusController.current
    val coordinator = remember { AuthTextFieldCoordinator() }
    coordinator.onValueChange = onValueChange
    coordinator.onFocusChange = onFocusChange
    coordinator.imeAction = imeAction
    coordinator.focusController = focusController

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
                font = fieldFont
                autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
                autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
                delegate = coordinator
                addTarget(
                    target = coordinator,
                    action = NSSelectorFromString("editingChanged:"),
                    forControlEvents = UIControlEventEditingChanged,
                )
                // Register in composition (visual) order so "Next" can advance.
                focusController.register(this)
            }
        },
        // Native a11y stays off (with multiple UIKitViews only the first would be
        // reachable); describe the field to VoiceOver through Compose semantics instead.
        modifier = modifier
            .height(24.dp)
            .semantics { contentDescription = accessibilityLabel },
        update = { field ->
            coordinator.field = field
            field.textContentType = autofill.toUITextContentType()
            field.keyboardType = keyboardType.toUIKeyboardType(isPassword)
            field.returnKeyType = imeAction.toUIReturnKeyType()
            field.secureTextEntry = isPassword && !isPasswordVisible
            field.attributedPlaceholder = NSAttributedString.create(
                string = placeholder,
                attributes = mapOf<Any?, Any>(NSForegroundColorAttributeName to placeholderColor),
            )
            syncText(field, value)
        },
        onRelease = { field ->
            focusController.unregister(field)
        },
    )
}

/**
 * Pushes the hoisted [value] into the native field without sending the caret to
 * the end.
 *
 * Assigning `UITextField.text` discards `selectedTextRange`, so UIKit re-anchors
 * the caret after the last character. Typing faster than the state round-trips
 * makes that fire constantly: a recomposition carrying the previous keystroke's
 * value arrives while the field already holds the newer text, the assignment
 * runs, and the caret visibly snaps to the back. Restoring the offset afterwards
 * keeps it where the user left it.
 *
 * The caret is restored by absolute offset rather than by the length delta,
 * because the values reaching this field are stored verbatim — a shorter [value]
 * means a stale echo (older text), not a rejected character. A caller that
 * filters characters mid-string would want the delta instead.
 */
@OptIn(ExperimentalForeignApi::class)
private fun syncText(field: UITextField, value: String) {
    val current = field.text.orEmpty()
    if (current == value) return

    // Offset from the START of the text, so edits after the caret don't move it.
    val caret = field.selectedTextRange?.let { range ->
        field.offsetFromPosition(field.beginningOfDocument, range.end)
    }
    field.text = value

    // Null while the field isn't first responder — nothing to restore.
    if (caret == null) return
    // Clamp so a shorter value never asks UIKit for a position past the end.
    val target = caret.coerceIn(0L, value.length.toLong())
    field.positionFromPosition(field.beginningOfDocument, offset = target)?.let { position ->
        field.selectedTextRange = field.textRangeFromPosition(position, toPosition = position)
    }
}

private class AuthTextFieldCoordinator : NSObject(), UITextFieldDelegateProtocol {
    var onValueChange: (String) -> Unit = {}
    var onFocusChange: (Boolean) -> Unit = {}
    var imeAction: ImeAction = ImeAction.Default
    var field: UITextField? = null
    var focusController: AuthFocusController? = null

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
        // On "Next", advance first responder to the next registered field; on
        // "Done" (or the last field) dismiss the keyboard. Focus callbacks are
        // driven by textFieldDidBegin/EndEditing, so don't fire them here.
        val nextField = if (imeAction == ImeAction.Next) {
            focusController?.nextAfter(textField) as? UITextField
        } else {
            null
        }
        if (nextField != null) {
            nextField.becomeFirstResponder()
        } else {
            textField.resignFirstResponder()
        }
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

// Manrope (the app body font) registered via Info.plist UIAppFonts. The bundled
// manrope_regular.ttf reports PostScript name "Manrope-ExtraLight"; using the same
// file Compose renders keeps the native field consistent with the rest of the UI.
// Falls back to the system font if registration ever fails — never a regression.
private val fieldFont: UIFont =
    UIFont.fontWithName("Manrope-ExtraLight", 15.0) ?: UIFont.systemFontOfSize(15.0)

// Placeholder colour matching the Compose field (#7D7970).
private val placeholderColor = UIColor(
    red = 0x7D / 255.0,
    green = 0x79 / 255.0,
    blue = 0x70 / 255.0,
    alpha = 1.0,
)

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
