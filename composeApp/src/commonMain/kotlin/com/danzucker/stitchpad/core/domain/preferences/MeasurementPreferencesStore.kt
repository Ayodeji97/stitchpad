package com.danzucker.stitchpad.core.domain.preferences

import com.danzucker.stitchpad.core.domain.model.MeasurementUnit

interface MeasurementPreferencesStore {
    suspend fun getUnit(): MeasurementUnit
    suspend fun setUnit(unit: MeasurementUnit)
}
