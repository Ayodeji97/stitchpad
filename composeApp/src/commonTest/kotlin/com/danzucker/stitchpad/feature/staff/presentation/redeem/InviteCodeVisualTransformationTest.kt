package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class InviteCodeVisualTransformationTest {

    private fun transform(raw: String) = InviteCodeVisualTransformation.filter(AnnotatedString(raw))

    @Test
    fun underFourCharactersRendersUnchanged() {
        assertEquals("SNN", transform("SNN").text.text)
    }

    @Test
    fun exactlyFourCharactersRendersTrailingHyphen() {
        assertEquals("SNNY-", transform("SNNY").text.text)
    }

    @Test
    fun fullCodeRendersGrouped() {
        assertEquals("SNNY-1234", transform("SNNY1234").text.text)
    }

    @Test
    fun caretAtBoundarySitsAfterTheHyphen() {
        // Raw caret 4 (end of "SNNY") must display after the hyphen (transformed 5)
        assertEquals(5, transform("SNNY").offsetMapping.originalToTransformed(4))
        assertEquals(3, transform("SNNY").offsetMapping.originalToTransformed(3))
    }

    @Test
    fun transformedOffsetsOnEitherSideOfHyphenCollapseToBoundary() {
        val mapping = transform("SNNY1234").offsetMapping
        assertEquals(4, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(8, mapping.transformedToOriginal(9))
    }

    // --- Restored from PR #349 (git history 8d3052d0) ---

    @Test
    fun caretBeforeTheHyphenIsUnshifted() {
        val mapping = transform("K7QP3RM9").offsetMapping
        (0..3).forEach { offset ->
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
}
