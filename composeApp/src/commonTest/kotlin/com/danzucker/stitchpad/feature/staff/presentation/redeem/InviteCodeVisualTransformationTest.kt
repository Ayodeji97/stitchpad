package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The invite code is stored normalised ("K7QP3RM9") but rendered grouped
 * ("K7QP-3RM9"). Formatting the *value* handed to the text field leaves the
 * caret at a stale index, so typed characters land in the wrong position —
 * these tests pin the caret mapping that keeps the rendered hyphen invisible
 * to the caret.
 */
class InviteCodeVisualTransformationTest {

    private fun transform(raw: String) = InviteCodeVisualTransformation.filter(AnnotatedString(raw))

    @Test
    fun insertsHyphenAfterTheFourthCharacter() {
        assertEquals("K7QP-3RM9", transform("K7QP3RM9").text.text)
    }

    @Test
    fun leavesShortCodesUngrouped() {
        assertEquals("K7QP", transform("K7QP").text.text)
        assertEquals("", transform("").text.text)
    }

    @Test
    fun caretBeforeTheHyphenIsUnshifted() {
        val mapping = transform("K7QP3RM9").offsetMapping
        (0..GROUP_SIZE).forEach { offset ->
            assertEquals(offset, mapping.originalToTransformed(offset), "original->transformed $offset")
            assertEquals(offset, mapping.transformedToOriginal(offset), "transformed->original $offset")
        }
    }

    @Test
    fun caretAfterTheHyphenShiftsByOne() {
        val mapping = transform("K7QP3RM9").offsetMapping
        // Typing the 5th character puts the caret at original 5 — it must render
        // AFTER the hyphen (transformed 6), not before it.
        assertEquals(6, mapping.originalToTransformed(5))
        assertEquals(9, mapping.originalToTransformed(8))
        assertEquals(5, mapping.transformedToOriginal(6))
        assertEquals(8, mapping.transformedToOriginal(9))
    }

    @Test
    fun bothSidesOfTheHyphenCollapseToTheGroupBoundary() {
        val mapping = transform("K7QP3RM9").offsetMapping
        // Transformed 4 sits before the hyphen and 5 sits after it; neither is a
        // real position in the stored code, so both resolve to original 4.
        assertEquals(GROUP_SIZE, mapping.transformedToOriginal(GROUP_SIZE))
        assertEquals(GROUP_SIZE, mapping.transformedToOriginal(GROUP_SIZE + 1))
    }

    @Test
    fun shortCodeMappingIsIdentity() {
        val mapping = transform("K7Q").offsetMapping
        (0..3).forEach { offset ->
            assertEquals(offset, mapping.originalToTransformed(offset))
            assertEquals(offset, mapping.transformedToOriginal(offset))
        }
    }

    @Test
    fun caretAtTheEndOfAFullCodeStaysInsideTheRenderedText() {
        val transformed = transform("K7QP3RM9")
        val end = transformed.offsetMapping.originalToTransformed(INVITE_CODE_LENGTH)
        assertEquals(transformed.text.text.length, end)
    }

    private companion object {
        const val GROUP_SIZE = 4
    }
}
