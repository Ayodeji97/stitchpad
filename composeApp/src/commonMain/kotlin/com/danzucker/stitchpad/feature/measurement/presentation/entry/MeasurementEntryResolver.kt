package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first

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
        val userId = authRepository.getCurrentUser()?.id
            ?: return MeasurementEntryDestination.CustomerDetail(customerId)
        val measurements = when (val result = measurementRepository.observeMeasurements(userId, customerId).first()) {
            is Result.Success -> result.data
            is Result.Error -> emptyList()
        }
        return when (measurements.size) {
            1 -> MeasurementEntryDestination.Detail(customerId, measurements.single().id)
            else -> MeasurementEntryDestination.CustomerDetail(customerId)
        }
    }
}
