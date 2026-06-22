package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.MeasurementDto
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementMapperTest {

    @Test
    fun name_roundTrips_throughDto() {
        val m = Measurement(
            id = "m1",
            customerId = "c1",
            gender = CustomerGender.FEMALE,
            name = "Wedding Agbada",
            fields = mapOf("bust" to 36.0),
            unit = MeasurementUnit.INCHES,
            notes = null,
            dateTaken = 1L,
            createdAt = 1L,
        )
        assertEquals("Wedding Agbada", m.toMeasurementDto().toMeasurement("c1").name)
    }

    @Test
    fun legacyDto_withoutName_mapsToEmptyName() {
        val dto = MeasurementDto(id = "m1", gender = "FEMALE", fields = mapOf("bust" to 36.0))
        assertEquals("", dto.toMeasurement("c1").name)
    }
}
