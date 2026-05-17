package com.danzucker.stitchpad.feature.customer.data

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.data.mapper.toCustomer
import com.danzucker.stitchpad.core.data.mapper.toCustomerDto
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "CustomerRepo"

/**
 * Counts the number of ACTIVE-slot customers in [dtos].
 * LOCKED customers are excluded — they don't consume active cap.
 * Exposed as `internal` so it can be unit-tested directly without
 * needing to fake a [FirebaseFirestore] instance.
 */
internal fun countActiveCustomers(dtos: List<CustomerDto>): Int =
    dtos.count { it.slotState.lowercase() == CustomerSlotState.ACTIVE.wireValue }

class FirebaseCustomerRepository(
    private val firestore: FirebaseFirestore,
    private val entitlements: EntitlementsProvider,
) : CustomerRepository {

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .snapshots()
            .map { snapshot ->
                val customers = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.data<CustomerDto>().toCustomer(userId) }.getOrNull()
                }
                Result.Success(customers) as Result<List<Customer>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeCustomers failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override fun observeCustomer(
        userId: String,
        customerId: String,
    ): Flow<Result<Customer, DataError.Network>> =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .document(customerId)
            .snapshots
            .map { snapshot ->
                if (!snapshot.exists) {
                    Result.Error(DataError.Network.NOT_FOUND) as Result<Customer, DataError.Network>
                } else {
                    val dto = snapshot.data<CustomerDto>()
                    Result.Success(dto.toCustomer(userId))
                }
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observeCustomer failed customerId=$customerId"
                }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    override suspend fun getCustomer(
        userId: String,
        customerId: String
    ): Result<Customer, DataError.Network> {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("customers")
                .document(customerId)
                .get()
            if (!doc.exists) return Result.Error(DataError.Network.NOT_FOUND)
            val dto = doc.data<CustomerDto>()
            Result.Success(dto.toCustomer(userId))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "getCustomer failed customerId=$customerId"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun createCustomer(
        userId: String,
        customer: Customer
    ): EmptyResult<DataError.Network> {
        // Cap enforcement: count ACTIVE-slot customers; LOCKED ones don't count.
        // Fetch all docs and count client-side via countActiveCustomers() — consistent
        // with how the rest of this repo avoids nullable-field where-clauses (see
        // the observeOrders comment about whereEqualTo(field, null) cross-platform issues).
        val entitlement = entitlements.current()
        val activeCount = try {
            val dtos = firestore.collection("users")
                .document(userId)
                .collection("customers")
                .get()
                .documents
                .mapNotNull { doc -> runCatching { doc.data<CustomerDto>() }.getOrNull() }
            countActiveCustomers(dtos)
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _e: Throwable) {
            // If we can't count, fail soft — server is still source of truth.
            AppLogger.w(tag = TAG) { "Cap count failed; allowing createCustomer to proceed" }
            0
        }
        if (activeCount >= entitlement.customerCap) {
            AppLogger.i(tag = TAG) {
                "createCustomer blocked: activeCount=$activeCount cap=${entitlement.customerCap}"
            }
            return Result.Error(DataError.Network.CAP_REACHED)
        }

        val docRef = if (customer.id.isBlank()) {
            firestore.collection("users").document(userId).collection("customers").document
        } else {
            firestore.collection("users").document(userId).collection("customers")
                .document(customer.id)
        }
        return try {
            val dto = customer.toCustomerDto().copy(id = docRef.id)
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "createCustomer failed customerId=${docRef.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun updateCustomer(
        userId: String,
        customer: Customer
    ): EmptyResult<DataError.Network> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("customers")
                .document(customer.id)
                .set(customer.toCustomerDto())
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "updateCustomer failed customerId=${customer.id}"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }

    override suspend fun deleteCustomer(
        userId: String,
        customerId: String
    ): EmptyResult<DataError.Network> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("customers")
                .document(customerId)
                .delete()
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) {
                "deleteCustomer failed customerId=$customerId"
            }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
