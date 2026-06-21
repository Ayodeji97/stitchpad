package com.danzucker.stitchpad.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Root-container modifier that dismisses any open soft-keyboard when the user
 * taps anywhere not handled by a child composable. Required because iOS
 * numeric keypads (KeyboardType.Phone / Number) never show a Done/Return key
 * — without an explicit dismissal affordance the user gets stuck behind the
 * keyboard with no way to submit the form.
 *
 * Implementation notes:
 *  - `indication = null` so the tap has no ripple (it would look wrong on a
 *    full-screen background).
 *  - Child composables that handle their own clicks (buttons, list rows, the
 *    text fields themselves) intercept the tap before it reaches this
 *    modifier, so this is safe to apply to any root container.
 *  - Paired with KeyboardActions.onDone = { focusManager.clearFocus() } in
 *    AuthTextField for keyboards that DO show a Done key (Text, Email, etc).
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            focusManager.clearFocus()
            // iOS auth fields are native UITextFields outside Compose's focus
            // system, so clearFocus() can't dismiss their keyboard — resign the
            // native first responder too. No-op on Android.
            dismissNativeKeyboard()
        },
    )
}

/**
 * Scroll-container modifier that dismisses the soft keyboard when the user DRAGS
 * to scroll. Reacts only to user-input scrolls (not the programmatic auto-scroll
 * Compose does to keep a newly-focused field above the keyboard — otherwise tapping
 * a field would instantly close the keyboard). Consumes nothing, so scroll/fling
 * behave normally.
 */
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

/**
 * Resigns the platform's current native text first responder (dismissing its
 * keyboard). No-op on Android, where Compose focus already handles dismissal.
 */
expect fun dismissNativeKeyboard()
