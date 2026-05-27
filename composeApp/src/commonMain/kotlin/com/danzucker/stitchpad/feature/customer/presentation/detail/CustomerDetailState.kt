package com.danzucker.stitchpad.feature.customer.presentation.detail

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.presentation.UiText

data class CustomerDetailState(
    val customer: Customer? = null,
    val measurements: List<Measurement> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val measurementToDelete: Measurement? = null,
    val errorMessage: UiText? = null,
    /**
     * PTSP-12: UUID → label map for the tailor's custom measurement fields,
     * INCLUDING archived ones. Used by the measurement preview row so custom
     * values render with their human label instead of disappearing.
     */
    val customFieldLabels: Map<String, String> = emptyMap(),
) {
    /**
     * True when the loaded customer is in the LOCKED slot state. Drives the read-only
     * banner + hides Edit / FAB / write actions and surfaces an Upgrade CTA at the
     * bottom (per V1.0 design spec decision #2 — locked data stays visible).
     *
     * False during isLoading=true so the loading state doesn't flash the locked
     * banner before the customer data resolves.
     */
    val isLocked: Boolean
        get() = customer?.slotState == CustomerSlotState.LOCKED
}
