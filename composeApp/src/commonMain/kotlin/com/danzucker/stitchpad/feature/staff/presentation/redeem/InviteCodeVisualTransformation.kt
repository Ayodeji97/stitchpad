package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Characters per group; a hyphen is rendered after the first group. */
private const val GROUP_SIZE = 4

/**
 * Renders a normalised invite code as "K7QP-3RM9" while the field's value stays
 * the raw "K7QP3RM9".
 *
 * The hyphen MUST be a visual transformation rather than a change to the value.
 * `BasicTextField`'s String overload carries the previous selection across
 * recomposition and only clamps it to the new length, so re-formatting the value
 * shifts the text under a caret that stays put: typing the 5th character pushed
 * the caret in front of it, and the next keystroke landed one position too early
 * (typing "3" then "R" produced "K7QPR3").
 */
internal object InviteCodeVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= GROUP_SIZE) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val grouped = "${raw.take(GROUP_SIZE)}-${raw.drop(GROUP_SIZE)}"
        return TransformedText(AnnotatedString(grouped), HyphenAfterFirstGroup)
    }

    /**
     * One character is inserted at [GROUP_SIZE], so every caret position past the
     * boundary shifts right by one. Transformed offsets on either side of the
     * hyphen collapse back to the boundary — neither is a real position in the
     * stored code.
     */
    private object HyphenAfterFirstGroup : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            if (offset <= GROUP_SIZE) offset else offset + 1

        override fun transformedToOriginal(offset: Int): Int =
            if (offset <= GROUP_SIZE) offset else offset - 1
    }
}
