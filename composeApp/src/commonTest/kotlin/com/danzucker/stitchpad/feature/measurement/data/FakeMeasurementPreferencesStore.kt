package com.danzucker.stitchpad.feature.measurement.data

import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore

class FakeMeasurementPreferencesStore : MeasurementPreferencesStore {
    var unit: MeasurementUnit = MeasurementUnit.INCHES

    override suspend fun getUnit(): MeasurementUnit = unit

    override suspend fun setUnit(unit: MeasurementUnit) {
        this.unit = unit
    }
}
