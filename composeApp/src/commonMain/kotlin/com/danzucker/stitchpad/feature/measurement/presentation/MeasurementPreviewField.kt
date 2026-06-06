package com.danzucker.stitchpad.feature.measurement.presentation

/** A filled measurement field resolved to its display label and value. */
data class MeasurementPreviewField(
    val label: String,
    val value: Double,
)
