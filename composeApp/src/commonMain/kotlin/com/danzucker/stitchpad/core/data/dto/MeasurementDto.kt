package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementDto(
    val id: String = "",
    val gender: String = "FEMALE",
    val name: String = "",
    val bodyShape: String? = null,
    // Legacy numeric values — records written before the punctuation fix stored
    // each field as a Double. Kept so old documents still deserialize; new writes
    // leave this empty and use [fieldValues]. The mapper prefers fieldValues and
    // falls back to formatting these legacy numbers to strings.
    val fields: Map<String, Double> = emptyMap(),
    // Free-text field values ("36", "16.5", "40, 45, 56"). The current storage
    // for measurement values; see MeasurementMapper for the read/write reconcile.
    val fieldValues: Map<String, String> = emptyMap(),
    val unit: String = "INCHES",
    val notes: String? = null,
    val dateTaken: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    // Legacy field — kept for backwards compatibility with Sprint 2 records
    val garmentType: String? = null
)
