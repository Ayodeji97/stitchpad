package com.danzucker.stitchpad.feature.measurement.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.presentation.UiText

data class MeasurementDetailState(
    val measurement: Measurement? = null,
    /** id → label for ALL custom-field definitions (archived included, so recorded values keep rendering). */
    val customFieldLabels: Map<String, String> = emptyMap(),
    /** Full customer doc — needed for share (name, phone), not just the lock state derived from it. */
    val customer: Customer? = null,
    /** Customer slot lock state; null until the customer doc emits — gated actions fail closed while unknown. */
    val isLocked: Boolean? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    /** Non-null = rename dialog open with this draft text. */
    val renameDraft: String? = null,
    /** One-shot "Measurement saved" snackbar when arriving from a save. */
    val showSavedMessage: Boolean = false,
    val showShareSheet: Boolean = false,
    val errorMessage: UiText? = null,
)
