package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Where the caret belongs once a sanitizer has rewritten what the user typed.
 *
 * [sanitized] is aligned against [proposed] as a subsequence: every character
 * that survived before [caret] advances the new position, every dropped one does
 * not. That places the caret correctly whether the sanitizer rejected a character
 * mid-string, stripped a leading zero, or trimmed the tail — without this
 * function needing to know which.
 *
 * If [sanitized] is not a subsequence of [proposed] the text was rewritten
 * wholesale (a value cap swapping in a different number, say). Nothing there
 * corresponds to a typed position, so the caret goes to the end.
 */
internal fun caretAfterSanitize(proposed: String, caret: Int, sanitized: String): Int {
    var matched = 0
    var caretAt = -1
    for (index in proposed.indices) {
        if (index == caret) caretAt = matched
        if (matched < sanitized.length && proposed[index] == sanitized[matched]) matched++
    }
    // Not a subsequence — the sanitizer replaced the text rather than filtering it.
    if (matched < sanitized.length) return sanitized.length
    return if (caretAt == -1) sanitized.length else caretAt
}

/**
 * Couples a `TextFieldValue`-based field to plain `String` state that a sanitizer
 * may rewrite on every keystroke.
 *
 * The `String` overload of `TextField` keeps whatever selection the IME reported
 * and only clamps it to the new length. When the sanitizer drops the character
 * just typed, the text shrinks but the caret does not move back — so it drifts
 * one position forward per rejected keystroke, and subsequent typing lands in the
 * wrong place. This holds the [TextFieldValue] itself and re-derives the caret
 * from what actually survived.
 *
 * The sanitizer stays wherever it already lives — a ViewModel action or the
 * caller's `onValueChange` — because the caret is inferred from the result rather
 * than recomputed from the rule. Nothing has to be duplicated into the screen.
 *
 * Usage:
 * ```
 * val amount = rememberSanitizedTextFieldValue(state.price) { onAction(OnPriceChange(it)) }
 * OutlinedTextField(value = amount.value, onValueChange = amount.onValueChange, ...)
 * ```
 */
@Composable
fun rememberSanitizedTextFieldValue(
    value: String,
    onValueChange: (String) -> Unit,
): SanitizedTextFieldValue {
    var fieldState by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // What the IME last proposed, kept so the sanitized value coming back can be
    // aligned against it. Null once reconciled, or when the change came from
    // elsewhere entirely.
    var proposed by remember { mutableStateOf<TextFieldValue?>(null) }

    val field = if (fieldState.text == value) {
        fieldState
    } else {
        val typed = proposed
        if (typed != null) {
            TextFieldValue(value, TextRange(caretAfterSanitize(typed.text, typed.selection.end, value)))
        } else {
            // An external write — a prefill, a reset, a value loaded from storage.
            // There is no typing to align against, so the caret goes to the end.
            TextFieldValue(value, TextRange(value.length))
        }
    }
    // Settle the reconciliation after composition rather than during it, so the
    // derived value above is never read back mid-pass.
    SideEffect {
        if (field != fieldState) fieldState = field
        // Drop the proposal once the field and the hoisted value agree — it has
        // either been consumed or was never pending. Keeping it would let a later
        // external write align against text the user typed long ago.
        if (field.text == value) proposed = null
    }

    return SanitizedTextFieldValue(field) { next ->
        proposed = next
        fieldState = next
        if (next.text != value) onValueChange(next.text)
    }
}

/** The field value and change handler produced by [rememberSanitizedTextFieldValue]. */
@Stable
class SanitizedTextFieldValue internal constructor(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit,
)
