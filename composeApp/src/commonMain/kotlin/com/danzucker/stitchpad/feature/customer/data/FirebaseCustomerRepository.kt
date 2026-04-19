package com.danzucker.stitchpad.feature.customer.data

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.data.mapper.toCustomer
import com.danzucker.stitchpad.core.data.mapper.toCustomerDto
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "CustomerRepo"

class FirebaseCustomerRepository(
    private val firestore: FirebaseFirestore
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
