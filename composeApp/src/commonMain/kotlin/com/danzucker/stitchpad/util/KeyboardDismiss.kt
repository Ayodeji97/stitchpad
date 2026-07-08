package com.danzucker.stitchpad.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs

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
 * to scroll. Detects a genuine finger drag (past touch slop) and clears focus, but
 * NEVER fires on the programmatic auto-scroll Compose runs to keep a newly-focused
 * field above the keyboard — that scroll produces no pointer events, so this
 * observer can't see it. Consumes nothing, so the underlying scroll/fling behaves
 * exactly as before.
 *
 * Why pointer observation instead of a NestedScrollConnection: the original
 * implementation filtered `onPreScroll` to `NestedScrollSource.UserInput`, assuming
 * the focus auto-scroll would arrive as `SideEffect`. On Compose Multiplatform iOS
 * that auto-scroll is reported as `UserInput`, so tapping a field low on a long form
 * (e.g. Edit Profile) tripped the filter and instantly closed the keyboard the tap
 * had just opened. A finger drag always emits pointer events; a programmatic scroll
 * never does — so keying off real pointer motion removes the platform ambiguity.
 */
@Composable
fun Modifier.dismissKeyboardOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    return this.pointerInput(focusManager, touchSlop) {
        awaitEachGesture {
            // Observe on the Initial pass and never consume, so the child scrollable
            // still drags/flings normally. requireUnconsumed = false so we still see
            // the gesture even when a child (text field / button) consumes the press.
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var totalDragY = 0f
            var dismissed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!dismissed) {
                    totalDragY += event.changes.fold(0f) { acc, change ->
                        acc + change.positionChangeIgnoreConsumed().y
                    }
                    if (abs(totalDragY) > touchSlop) {
                        dismissed = true
                        focusManager.clearFocus()
                        dismissNativeKeyboard()
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * Resigns the platform's current native text first responder (dismissing its
 * keyboard). No-op on Android, where Compose focus already handles dismissal.
 */
expect fun dismissNativeKeyboard()
