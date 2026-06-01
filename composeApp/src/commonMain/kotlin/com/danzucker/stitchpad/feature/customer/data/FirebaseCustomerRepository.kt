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
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "CustomerRepo"

/**
 * Counts the number of ACTIVE-slot customers in [dtos].
 * LOCKED customers are excluded — they don't consume active cap.
 *
 * Uses [CustomerSlotState.fromWire] so the cap count matches the way the
 * rest of the system buckets slot states: the UI mapper, the server's
 * `normalizeSlotState` in reconcileSlots.ts, and Firestore rules all treat
 * anything-not-`"locked"` as `ACTIVE`. An exact-equality count would skip
 * an arbitrary-string `slotState` (e.g. `"fancy"` from a direct Firestore
 * SDK write) here while the UI + reconcile still count it — a brief
 * client-side cap-bypass window before reconcile fires. Cursor's PR #70
 * review caught this as the client-side mirror of the server fix shipped
 * in PR #69.
 *
 * Exposed as `internal` so it can be unit-tested directly without
 * needing to fake a [FirebaseFirestore] instance.
 */
internal fun countActiveCustomers(dtos: List<CustomerDto>): Int =
    dtos.count { CustomerSlotState.fromWire(it.slotState) == CustomerSlotState.ACTIVE }

internal fun shouldBlockActiveCustomerCreate(
    cachedActiveCount: Int?,
    customerCap: Int,
    entitlementsHydrated: Boolean = true,
): Boolean =
    entitlementsHydrated && cachedActiveCount != null && cachedActiveCount >= customerCap

class FirebaseCustomerRepository(
    private val firestore: FirebaseFirestore,
    private val entitlements: EntitlementsProvider,
    private val offlineWrites: OfflineWriteDispatcher,
) : CustomerRepository {

    private val cachedActiveCustomerCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        firestore.collection("users")
            .document(userId)
            .collection("customers")
            .snapshots()
            .map { snapshot ->
                val customerDtos = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.data<CustomerDto>() }.getOrNull()
                }
                cacheActiveCustomerCount(userId, countActiveCustomers(customerDtos))
                val customers = customerDtos.map { it.toCustomer(userId) }
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

    @Suppress("ReturnCount")
    override suspend fun createCustomer(
        userId: String,
        customer: Customer
    ): EmptyResult<DataError.Network> {
        val docRef = if (customer.id.isBlank()) {
            firestore.collection("users").document(userId).collection("customers").document
        } else {
            firestore.collection("users").document(userId).collection("customers")
                .document(customer.id)
        }
        val dto = customer.toCustomerDto().copy(id = docRef.id)
        if (CustomerSlotState.fromWire(dto.slotState) == CustomerSlotState.ACTIVE) {
            val cap = entitlements.current().customerCap
            val activeCount = cachedActiveCustomerCounts.value[userId]
            // Offline create must not block on a remote count read. If the
            // current session has not observed real entitlements/list counts yet,
            // accept locally and let existing server reconciliation restore truth.
            if (shouldBlockActiveCustomerCreate(activeCount, cap, entitlements.hasHydrated())) {
                AppLogger.i(tag = TAG) {
                    "createCustomer blocked by cap: activeCount=$activeCount cap=$cap"
                }
                return Result.Error(DataError.Network.CAP_REACHED)
            }
        }
        val accepted = offlineWrites.enqueue("createCustomer customerId=${docRef.id}") {
            docRef.set(dto)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        if (CustomerSlotState.fromWire(dto.slotState) == CustomerSlotState.ACTIVE) {
            val current = cachedActiveCustomerCounts.value[userId] ?: 0
            cacheActiveCustomerCount(userId, current + 1)
        }
        return Result.Success(Unit)
    }

    private fun cacheActiveCustomerCount(userId: String, count: Int) {
        cachedActiveCustomerCounts.value = cachedActiveCustomerCounts.value + (userId to count)
    }

    private fun invalidateActiveCustomerCount(userId: String) {
        cachedActiveCustomerCounts.value = cachedActiveCustomerCounts.value - userId
    }

    override suspend fun updateCustomer(
        userId: String,
        customer: Customer
    ): EmptyResult<DataError.Network> {
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("customers")
            .document(customer.id)
        val dto = customer.toCustomerDto()
        val editableFields = mapOf(
            "name" to dto.name,
            "phone" to dto.phone,
            "email" to dto.email,
            "address" to dto.address,
            "updatedAt" to dto.updatedAt,
        )
        val accepted = offlineWrites.enqueue("updateCustomer customerId=${customer.id}") {
            // Merge only fields from the edit form. slotState/lockedAt are
            // server-owned and must not be reconstructed from offline UI state.
            docRef.set(editableFields, merge = true)
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteCustomer(
        userId: String,
        customerId: String
    ): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("deleteCustomer customerId=$customerId") {
            firestore.collection("users")
                .document(userId)
                .collection("customers")
                .document(customerId)
                .delete()
        }
        if (!accepted) {
            return Result.Error(DataError.Network.UNKNOWN)
        }
        invalidateActiveCustomerCount(userId)
        return Result.Success(Unit)
    }
}
