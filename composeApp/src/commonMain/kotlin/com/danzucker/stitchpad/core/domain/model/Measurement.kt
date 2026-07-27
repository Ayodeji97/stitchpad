package com.danzucker.stitchpad.core.domain.model

data class Measurement(
    val id: String,
    val customerId: String,
    val gender: CustomerGender,
    val name: String = "",
    // Free-text per-field values (e.g. "36", "16.5", "40, 45, 56"). Tailors record
    // gown lengths in segments ("shoulder-to-knee, -midi, -floor") and half sizes,
    // so a value is a String, not a Double — see PTSP measurement-punctuation fix.
    val fields: Map<String, String>,
    val unit: MeasurementUnit,
    val notes: String?,
    val dateTaken: Long,
    val createdAt: Long
)
