package com.danzucker.stitchpad.feature.measurement.data

import android.content.Context
import androidx.core.content.edit
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.MeasurementPreferencesStore

actual class MeasurementPreferences(context: Context) : MeasurementPreferencesStore {
    private val prefs = context.getSharedPreferences("measurement_prefs", Context.MODE_PRIVATE)

    override suspend fun getUnit(): MeasurementUnit =
        runCatching { MeasurementUnit.valueOf(prefs.getString(KEY_UNIT, "INCHES") ?: "INCHES") }
            .getOrDefault(MeasurementUnit.INCHES)

    override suspend fun setUnit(unit: MeasurementUnit) {
        prefs.edit { putString(KEY_UNIT, unit.name) }
    }

    companion object {
        private const val KEY_UNIT = "measurement_unit"
    }
}
