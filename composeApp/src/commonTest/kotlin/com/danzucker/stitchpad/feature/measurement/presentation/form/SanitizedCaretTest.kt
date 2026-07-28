package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.feature.measurement.presentation.form.components.sanitizedCaret
import com.danzucker.stitchpad.feature.measurement.presentation.sanitizeMeasurementInput
import kotlin.test.Test
import kotlin.test.assertEquals

class SanitizedCaretTest {

    @Test
    fun `caret keeps position when typing a comma mid string`() {
        // "40 45" with caret after "40" (index 2), user types a comma -> "40, 45"?
        // Here the raw text already contains the inserted comma; caret is right after it.
        val raw = "40,45"
        val caret = sanitizedCaret(rawText = raw, selectionEnd = 3, sanitize = ::sanitizeMeasurementInput)
        assertEquals(3, caret)
    }

    @Test
    fun `caret does not advance past a rejected letter`() {
        // User typed "a" after "30" -> raw "30a", caret at 3. Sanitize strips the
        // letter, so the caret must land at 2 (after "30"), not 3.
        val caret = sanitizedCaret(rawText = "30a", selectionEnd = 3, sanitize = ::sanitizeMeasurementInput)
        assertEquals(2, caret)
    }

    @Test
    fun `caret clamps when selection end exceeds text length`() {
        val caret = sanitizedCaret(rawText = "16.5", selectionEnd = 99, sanitize = ::sanitizeMeasurementInput)
        assertEquals(4, caret)
    }

    @Test
    fun `identity sanitize preserves caret exactly`() {
        val caret = sanitizedCaret(rawText = "Owambe", selectionEnd = 3, sanitize = { it })
        assertEquals(3, caret)
    }
}
