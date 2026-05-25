package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult

interface FreemiumRepository {
    /** Idempotent. Calls the server reconcileCustomerSlots function. */
    suspend fun reconcileSlots(): EmptyResult<DataError.Network>

    /** Swap a locked customer back into the active 15, demoting another. */
    suspend fun swapCustomerSlot(
        promote: String,
        demote: String,
    ): EmptyResult<DataError.Network>
}
