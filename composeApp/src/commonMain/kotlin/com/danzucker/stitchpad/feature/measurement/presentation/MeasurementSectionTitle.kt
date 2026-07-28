package com.danzucker.stitchpad.feature.measurement.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_section_title
import stitchpad.composeapp.generated.resources.section_arms
import stitchpad.composeapp.generated.resources.section_body_lengths
import stitchpad.composeapp.generated.resources.section_bust
import stitchpad.composeapp.generated.resources.section_neck_shoulders
import stitchpad.composeapp.generated.resources.section_trouser
import stitchpad.composeapp.generated.resources.section_upper_body
import stitchpad.composeapp.generated.resources.section_waist_hip

/**
 * Resolves a [MeasurementSection.titleKey] to a localized title. Shared by the
 * measurement form (page heading) and the read-only detail screen. Unknown future
 * keys degrade to the raw key rather than crash; a null key is the custom section.
 */
@Composable
fun measurementSectionTitle(titleKey: String?): String = when (titleKey) {
    "section_upper_body" -> stringResource(Res.string.section_upper_body)
    "section_body_lengths" -> stringResource(Res.string.section_body_lengths)
    "section_trouser" -> stringResource(Res.string.section_trouser)
    "section_neck_shoulders" -> stringResource(Res.string.section_neck_shoulders)
    "section_bust" -> stringResource(Res.string.section_bust)
    "section_waist_hip" -> stringResource(Res.string.section_waist_hip)
    "section_arms" -> stringResource(Res.string.section_arms)
    null -> stringResource(Res.string.custom_field_section_title)
    else -> titleKey
}
