package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.presentation.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_added
import stitchpad.composeapp.generated.resources.custom_field_added_female
import stitchpad.composeapp.generated.resources.custom_field_added_male

class CustomFieldAddedMessageTest {

    @Test
    fun `field on current gender uses the plain added message`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.FEMALE,
            genders = setOf(CustomerGender.FEMALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added, text.id)
        assertEquals("Sleeve", text.args.single())
    }

    @Test
    fun `both-gender field uses the plain added message`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.MALE,
            genders = setOf(CustomerGender.FEMALE, CustomerGender.MALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added, text.id)
    }

    @Test
    fun `male-only field added while on female tells the tailor it is on male`() {
        val msg = customFieldAddedMessage(
            label = "Sleeve",
            currentGender = CustomerGender.FEMALE,
            genders = setOf(CustomerGender.MALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added_male, text.id)
    }

    @Test
    fun `female-only field added while on male tells the tailor it is on female`() {
        val msg = customFieldAddedMessage(
            label = "Bust",
            currentGender = CustomerGender.MALE,
            genders = setOf(CustomerGender.FEMALE),
        )
        val text = assertIs<UiText.StringResourceText>(msg)
        assertEquals(Res.string.custom_field_added_female, text.id)
    }
}
