package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementDetailState(
    val measurement: Measurement? = null,
    /** id → label for ALL custom-field definitions (archived included, so recorded values keep rendering). */
    val customFieldLabels: Map<String, String> = emptyMap(),
    val isLocked: Boolean = false,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    /** Non-null = rename dialog open with this draft text. */
    val renameDraft: String? = null,
    /** One-shot "Measurement saved" snackbar when arriving from a save. */
    val showSavedMessage: Boolean = false,
    val errorMessage: UiText? = null,
)
