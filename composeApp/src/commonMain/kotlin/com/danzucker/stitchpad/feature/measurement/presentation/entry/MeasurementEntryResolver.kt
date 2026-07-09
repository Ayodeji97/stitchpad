package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Where a "view measurements" entry point should land for a customer. */
sealed interface MeasurementEntryDestination {
    data class Detail(val customerId: String, val measurementId: String) : MeasurementEntryDestination
    data class CustomerDetail(val customerId: String) : MeasurementEntryDestination
}

/**
 * The shared routing rule for measurement entry points (spec): exactly one
 * measurement opens it directly; zero or several land on customer detail
 * (whose measurements section handles both). Errors and signed-out fall back
 * to customer detail — never a dead end.
 */
class MeasurementEntryResolver(
    private val measurementRepository: MeasurementRepository,
    private val authRepository: AuthRepository,
) {
    suspend fun resolve(customerId: String): MeasurementEntryDestination {
        val measurements = firstSnapshotOrNull(customerId)
        return if (measurements?.size == 1) {
            MeasurementEntryDestination.Detail(customerId, measurements.single().id)
        } else {
            // 0, several, error, timeout, or signed-out — customer detail handles all.
            MeasurementEntryDestination.CustomerDetail(customerId)
        }
    }

    /**
     * First measurement snapshot, or null when it can't be had: signed-out, or the
     * snapshot never arrives (cold cache with no network can leave the Firestore
     * flow pending indefinitely — the wait is bounded so the tap never silently
     * dies; the sheet that triggered it is already dismissed).
     */
    private suspend fun firstSnapshotOrNull(customerId: String): List<Measurement>? {
        val userId = authRepository.getCurrentUser()?.id ?: return null
        val result = withTimeoutOrNull(FIRST_SNAPSHOT_TIMEOUT_MS) {
            measurementRepository.observeMeasurements(userId, customerId).first()
        }
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> emptyList()
            null -> null
        }
    }

    private companion object {
        const val FIRST_SNAPSHOT_TIMEOUT_MS = 3_000L
    }
}
