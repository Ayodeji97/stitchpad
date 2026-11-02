package com.danzucker.stitchpad.ui.components

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonePrefixTransformationTest {
    @Test
    fun internationalPrefixIsShownOnceAndCaretCanReachBothEndsOfNationalNumber() {
        val raw = "+233244123456"
        val display = PhonePrefixTransformation("233").filter(AnnotatedString(raw))
        assertEquals("244123456", display.text.text)
        assertEquals(4, display.offsetMapping.transformedToOriginal(0))
        assertEquals(raw.length, display.offsetMapping.transformedToOriginal(display.text.length))
        assertEquals(0, display.offsetMapping.originalToTransformed(0))
        assertEquals(display.text.length, display.offsetMapping.originalToTransformed(raw.length))
    }

    @Test
    fun foreignExplicitNumberIsNeverHiddenUnderAnotherCountryPrefix() {
        val raw = "+254712345678"
        val display = PhonePrefixTransformation("233").filter(AnnotatedString(raw))
        assertEquals(raw, display.text.text)
        assertEquals(0, display.offsetMapping.transformedToOriginal(0))
    }
}
