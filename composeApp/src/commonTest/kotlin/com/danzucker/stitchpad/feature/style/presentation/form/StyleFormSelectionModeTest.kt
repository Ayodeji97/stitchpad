package com.danzucker.stitchpad.feature.style.presentation.form

import com.preat.peekaboo.image.picker.SelectionMode
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleFormSelectionModeTest {

    @Test
    fun single_whenMultiPhotoDisabled() {
        assertEquals(
            SelectionMode.Single,
            styleFormSelectionMode(allowMultiPhoto = false, maxPhotoSelection = 10),
        )
    }

    @Test
    fun single_whenMultiPhotoAllowedButOnlyOneSlotRemains() {
        // Regression guard: SelectionMode.Multiple(maxSelection = 1) crashes Android's
        // PickMultipleVisualMedia with "Max items must be higher than 1".
        assertEquals(
            SelectionMode.Single,
            styleFormSelectionMode(allowMultiPhoto = true, maxPhotoSelection = 1),
        )
    }

    @Test
    fun multiple_whenMultiPhotoAllowedAndSeveralSlotsRemain() {
        assertEquals(
            SelectionMode.Multiple(maxSelection = 3),
            styleFormSelectionMode(allowMultiPhoto = true, maxPhotoSelection = 3),
        )
    }
}
