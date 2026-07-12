package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Where a "view measurements" entry point should land for a customer. */
sealed interface MeasurementEntryDestination {
    /** null [measurementId] = the detail screen's empty mode (customer confirmed to have zero measurements). */
    data class Detail(val customerId: String, val measurementId: String?) : MeasurementEntryDestination
    data class CustomerDetail(val customerId: String) : MeasurementEntryDestination
}

/**
 * Fetches the measurement snapshot for the actions-sheet entry point and applies
 * the shared [destinationFor] routing rule. Callers that already know the count
 * (the dashboard picker) call [destinationFor] directly instead.
 */
class MeasurementEntryResolver(
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun resolve(customerId: String): MeasurementEntryDestination {
        val measurements = firstSnapshotOrNull(customerId)
        return destinationFor(
            customerId = customerId,
            measurementCount = measurements?.size,
            singleMeasurementId = measurements?.singleOrNull()?.id,
        )
    }

    /**
     * First measurement snapshot, or null when it can't be had: signed-out, a repo
     * error, or the snapshot never arrives (cold cache with no network can leave the
     * Firestore flow pending indefinitely — the wait is bounded so the tap never
     * silently dies; the sheet that triggered it is already dismissed). Null means
     * UNKNOWN — it must never read as "confirmed zero", which would show the empty
     * state for a customer who may well have measurements.
     */
    private suspend fun firstSnapshotOrNull(customerId: String): List<Measurement>? {
        val userId = authRepository.getCurrentUser()?.id ?: return null
        val result = withTimeoutOrNull(FIRST_SNAPSHOT_TIMEOUT_MS) {
            measurementRepository.observeMeasurements(userId, customerId).first()
        }
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> null
            null -> null
        }
    }

    companion object {
        /**
         * The shared routing rule for measurement entry points (spec): exactly one
         * measurement opens its detail; a confirmed zero opens the detail screen's
         * empty state; several land on customer detail (whose measurements section
         * is the list). Unknown count (error, timeout, signed-out) falls back to
         * customer detail — never a dead end, never a false empty state. Inconsistent
         * inputs (e.g. `measurementCount == 1` with a null `singleMeasurementId`) also
         * fall through to the customer-detail fallback.
         */
        fun destinationFor(
            customerId: String,
            measurementCount: Int?,
            singleMeasurementId: String?,
        ): MeasurementEntryDestination = when {
            singleMeasurementId != null ->
                MeasurementEntryDestination.Detail(customerId, singleMeasurementId)
            measurementCount == 0 ->
                MeasurementEntryDestination.Detail(customerId, measurementId = null)
            else -> MeasurementEntryDestination.CustomerDetail(customerId)
        }

        private const val FIRST_SNAPSHOT_TIMEOUT_MS = 3_000L
    }
}
