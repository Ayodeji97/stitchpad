package com.danzucker.stitchpad.core.domain.model

/**
 * A body-profile template field. [key] is the persisted Firestore map key and
 * never changes. [label] is the English fallback only — the UI resolves the
 * localized name from [labelKey] (see measurementFieldLabel in the measurement
 * presentation package). [labelKey] defaults to [key]; it differs only where two
 * genders share a key but show different names (male "Neck" vs female
 * "Neck circumference").
 */
data class MeasurementField(
    val key: String,
    val label: String,
    val isEssential: Boolean,
    val labelKey: String = key,
)
