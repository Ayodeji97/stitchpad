package com.danzucker.stitchpad.feature.measurement.data

import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore
import platform.Foundation.NSUserDefaults

actual class MeasurementPreferences : MeasurementPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getUnit(): MeasurementUnit =
        runCatching { MeasurementUnit.valueOf(defaults.stringForKey(KEY_UNIT) ?: "INCHES") }
            .getOrDefault(MeasurementUnit.INCHES)

    override suspend fun setUnit(unit: MeasurementUnit) {
        defaults.setObject(unit.name, forKey = KEY_UNIT)
    }

    companion object {
        private const val KEY_UNIT = "measurement_unit"
    }
}
