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
}
