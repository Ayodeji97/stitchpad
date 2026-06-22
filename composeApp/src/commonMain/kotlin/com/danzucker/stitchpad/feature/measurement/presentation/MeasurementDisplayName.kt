package com.danzucker.stitchpad.feature.measurement.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_name_default_female
import stitchpad.composeapp.generated.resources.measurement_name_default_male

/** Display label for a measurement: its name, or a distinct numbered gender default for legacy un-named records. */
@Composable
fun measurementDisplayName(measurement: Measurement, position: Int): String =
    measurement.name.ifBlank {
        stringResource(
            if (measurement.gender == CustomerGender.FEMALE) {
                Res.string.measurement_name_default_female
            } else {
                Res.string.measurement_name_default_male
            },
            position,
        )
    }
