package com.danzucker.stitchpad.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
        onClick = { focusManager.clearFocus() },
    )
}
