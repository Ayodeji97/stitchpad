package com.danzucker.stitchpad.feature.measurement.presentation.form.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Caret offset after [sanitize] runs on user input. Because [sanitize] only ever
 * REMOVES characters, the new caret is the length of the sanitized prefix up to the
 * old caret — so inserting a comma/decimal mid-string keeps the caret in place and a
 * rejected character does not nudge it forward. Pure + unit-testable.
 */
fun sanitizedCaret(
    rawText: String,
    selectionEnd: Int,
    sanitize: (String) -> String,
): Int = sanitize(rawText.take(selectionEnd.coerceIn(0, rawText.length))).length

/**
 * Binds a hoisted [value] String to a local [TextFieldValue] so the caret survives
 * the ViewModel round-trip. A String-bound field loses its caret to the end when the
 * StateFlow pushes the value back (see the "Compose TextFieldValue cursor" note).
 * Optionally [sanitize]s input (default identity) and enforces [maxLength] by
 * rejecting the keystroke.
 *
 * @return the current [TextFieldValue] to display and the change handler for the field.
 */
@Composable
fun rememberSanitizedTextFieldValue(
    value: String,
    sanitize: (String) -> String = { it },
    maxLength: Int? = null,
    onValueChange: (String) -> Unit,
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    // Reconcile external (programmatic) changes: value seeding, gender switch, load.
    // Caret-to-end is correct here — the user is not mid-edit. Gated in a
    // LaunchedEffect (not a body check) so an unrelated recomposition while a
    // keystroke round-trip is still in flight (local tfv ahead of the
    // not-yet-echoed value) doesn't snap the field back to the stale value and
    // push the caret to the end — see the project's TextFieldValue cursor note.
    LaunchedEffect(value) {
        if (tfv.text != value) {
            tfv = TextFieldValue(value, TextRange(value.length))
        }
    }

    val onChange: (TextFieldValue) -> Unit = onChange@{ raw ->
        val sanitized = sanitize(raw.text)
        if (maxLength != null && sanitized.length > maxLength) return@onChange
        val caret = sanitizedCaret(raw.text, raw.selection.end, sanitize)
        tfv = TextFieldValue(sanitized, TextRange(caret.coerceIn(0, sanitized.length)))
        if (sanitized != value) onValueChange(sanitized)
    }
    return tfv to onChange
}
