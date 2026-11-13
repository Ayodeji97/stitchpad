package com.danzucker.stitchpad.feature.measurement.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_field_arm_length
import stitchpad.composeapp.generated.resources.measurement_field_bust_circumference
import stitchpad.composeapp.generated.resources.measurement_field_bust_point
import stitchpad.composeapp.generated.resources.measurement_field_bust_span
import stitchpad.composeapp.generated.resources.measurement_field_chest
import stitchpad.composeapp.generated.resources.measurement_field_elbow_circumference
import stitchpad.composeapp.generated.resources.measurement_field_full_front_length
import stitchpad.composeapp.generated.resources.measurement_field_full_length_gown
import stitchpad.composeapp.generated.resources.measurement_field_hip_circumference
import stitchpad.composeapp.generated.resources.measurement_field_inseam
import stitchpad.composeapp.generated.resources.measurement_field_knee_circumference
import stitchpad.composeapp.generated.resources.measurement_field_long_sleeve_length
import stitchpad.composeapp.generated.resources.measurement_field_nape_to_waist
import stitchpad.composeapp.generated.resources.measurement_field_neck
import stitchpad.composeapp.generated.resources.measurement_field_neck_circumference
import stitchpad.composeapp.generated.resources.measurement_field_shirt_length
import stitchpad.composeapp.generated.resources.measurement_field_shoulder_back
import stitchpad.composeapp.generated.resources.measurement_field_shoulder_to_underbust
import stitchpad.composeapp.generated.resources.measurement_field_shoulder_to_waist
import stitchpad.composeapp.generated.resources.measurement_field_shoulder_width
import stitchpad.composeapp.generated.resources.measurement_field_sleeve_length
import stitchpad.composeapp.generated.resources.measurement_field_thigh_circumference
import stitchpad.composeapp.generated.resources.measurement_field_trouser_hip
import stitchpad.composeapp.generated.resources.measurement_field_trouser_length
import stitchpad.composeapp.generated.resources.measurement_field_trouser_waist
import stitchpad.composeapp.generated.resources.measurement_field_tummy
import stitchpad.composeapp.generated.resources.measurement_field_underbust_circumference
import stitchpad.composeapp.generated.resources.measurement_field_waist
import stitchpad.composeapp.generated.resources.measurement_field_wrist_circumference

/**
 * Localized display names for BodyProfileTemplate fields, keyed by
 * [MeasurementField.labelKey]. The domain template keeps only the stable key plus
 * an English fallback label; everything a user reads goes through here.
 *
 * Custom fields are NOT in this map — they carry the tailor's own label, which is
 * shown unchanged.
 */
internal val measurementFieldLabelResources: Map<String, StringResource> = mapOf(
    "shoulder_width" to Res.string.measurement_field_shoulder_width,
    "bust_circumference" to Res.string.measurement_field_bust_circumference,
    "bust_point" to Res.string.measurement_field_bust_point,
    "shoulder_to_underbust" to Res.string.measurement_field_shoulder_to_underbust,
    "bust_span" to Res.string.measurement_field_bust_span,
    "waist" to Res.string.measurement_field_waist,
    "neck_circumference" to Res.string.measurement_field_neck_circumference,
    "neck" to Res.string.measurement_field_neck,
    "underbust_circumference" to Res.string.measurement_field_underbust_circumference,
    "shoulder_to_waist" to Res.string.measurement_field_shoulder_to_waist,
    "hip_circumference" to Res.string.measurement_field_hip_circumference,
    "full_length_gown" to Res.string.measurement_field_full_length_gown,
    "sleeve_length" to Res.string.measurement_field_sleeve_length,
    "wrist_circumference" to Res.string.measurement_field_wrist_circumference,
    "nape_to_waist" to Res.string.measurement_field_nape_to_waist,
    "full_front_length" to Res.string.measurement_field_full_front_length,
    "arm_length" to Res.string.measurement_field_arm_length,
    "trouser_waist" to Res.string.measurement_field_trouser_waist,
    "trouser_length" to Res.string.measurement_field_trouser_length,
    "trouser_hip" to Res.string.measurement_field_trouser_hip,
    "thigh_circumference" to Res.string.measurement_field_thigh_circumference,
    "inseam" to Res.string.measurement_field_inseam,
    "shoulder_back" to Res.string.measurement_field_shoulder_back,
    "chest" to Res.string.measurement_field_chest,
    "shirt_length" to Res.string.measurement_field_shirt_length,
    "long_sleeve_length" to Res.string.measurement_field_long_sleeve_length,
    "elbow_circumference" to Res.string.measurement_field_elbow_circumference,
    "tummy" to Res.string.measurement_field_tummy,
    "knee_circumference" to Res.string.measurement_field_knee_circumference,
)

/** The string resource for a template label key, or null for unknown / custom keys. */
fun measurementFieldLabelResource(labelKey: String): StringResource? = measurementFieldLabelResources[labelKey]

/**
 * Pure label pick: the pre-resolved localized name for [field] from [localizedLabels]
 * (keyed by [MeasurementField.labelKey]), else the template's English fallback.
 */
fun MeasurementField.displayLabel(localizedLabels: Map<String, String>): String =
    localizedLabels[labelKey] ?: label

/** Localized name for a template label key; [fallback] (stored/custom label) for unknown keys. */
@Composable
fun measurementFieldLabel(labelKey: String, fallback: String): String =
    measurementFieldLabelResource(labelKey)?.let { stringResource(it) } ?: fallback

/** Localized name for a template [field]; falls back to its English label. */
@Composable
fun measurementFieldLabel(field: MeasurementField): String = measurementFieldLabel(field.labelKey, field.label)

/** Suspend twin of [measurementFieldLabel] for ViewModels / share builders. */
suspend fun measurementFieldLabelAsync(labelKey: String, fallback: String): String =
    measurementFieldLabelResource(labelKey)?.let { getString(it) } ?: fallback

/**
 * Every template label resolved in the current locale, keyed by labelKey — the
 * map the pure preview/detail/share builders take. Iterates a fixed map, so the
 * composable call order is stable across recompositions.
 */
@Composable
fun measurementFieldLabels(): Map<String, String> =
    measurementFieldLabelResources.mapValues { (_, res) -> stringResource(res) }

/** Suspend twin of [measurementFieldLabels], for the share text / card. */
suspend fun measurementFieldLabelsAsync(): Map<String, String> =
    measurementFieldLabelResources.mapValues { (_, res) -> getString(res) }
