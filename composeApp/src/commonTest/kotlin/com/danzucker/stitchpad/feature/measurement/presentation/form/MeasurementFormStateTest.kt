package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementFormStateTest {

    @Test
    fun canSave_isFalse_whenGenderIsNull() {
        val state = MeasurementFormState(
            gender = null,
            fields = mapOf("chest" to "38"),
        )
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_isFalse_whenGenderSet_butAllFieldsBlank() {
        // PTSP-6: this is the regression we're fixing. Today Save is enabled
        // with no figures entered; after this change it must be disabled.
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "", "waist" to "", "hip" to ""),
        )
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_isTrue_whenGenderSet_andAtLeastOneFieldHasParsableNumber() {
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "38", "waist" to "", "hip" to ""),
        )
        assertTrue(state.canSave)
    }

    @Test
    fun canSave_isFalse_whenOnlyFieldIsLoneDot() {
        // The input filter at MeasurementFormScreen allows `.` (one decimal point
        // is legal during typing), but `save()` parses it via toDoubleOrNull() ?: 0.0
        // and drops the field as 0.0 — which would silently persist an empty
        // measurement. Gate must use parsable-number, not isNotBlank().
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to ".", "waist" to "", "hip" to ""),
        )
        assertFalse(state.canSave)
    }

    @Test
    fun canSave_isTrue_whenFieldIsZero() {
        // Per spec §8 deferred items, strict `>0` validation is out of scope.
        // "0" parses to 0.0 (non-null) so it currently counts as a figure.
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "0"),
        )
        assertTrue(state.canSave)
    }

    @Test
    fun canSave_isFalse_whenLoading_evenIfFieldsValid() {
        val state = MeasurementFormState(
            gender = CustomerGender.FEMALE,
            fields = mapOf("chest" to "38"),
            isLoading = true,
        )
        assertFalse(state.canSave)
    }
}
