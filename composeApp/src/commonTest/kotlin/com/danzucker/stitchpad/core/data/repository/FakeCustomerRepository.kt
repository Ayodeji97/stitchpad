package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeCustomerRepository : CustomerRepository {
    var shouldReturnError: DataError.Network? = null
    var storedCustomer: Customer? = null
    var lastCreatedCustomer: Customer? = null
    var lastUpdatedCustomer: Customer? = null

    private val customersFlow = MutableStateFlow<List<Customer>>(emptyList())
    var customersList: List<Customer>
        get() = customersFlow.value
        set(value) { customersFlow.value = value }

    /**
     * Per-uid override streams (Slice 8e — kill-switch/session re-subscription
     * tests): a uid opted in via [setCustomersFor] gets its OWN independent list
     * so a test can simulate two different workshop trees live in the same VM.
     * Any uid that never calls [setCustomersFor] keeps sharing [customersFlow]
     * via [customersList] — every existing single-tenant test is unaffected.
     */
    private val perUidCustomersFlow = mutableMapOf<String, MutableStateFlow<List<Customer>>>()

    fun setCustomersFor(userId: String, customers: List<Customer>) {
        perUidCustomersFlow.getOrPut(userId) { MutableStateFlow(emptyList()) }.value = customers
    }

    private fun customersFlowFor(userId: String): StateFlow<List<Customer>> =
        perUidCustomersFlow[userId] ?: customersFlow

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        customersFlowFor(userId).map { list ->
            shouldReturnError?.let { return@map Result.Error(it) }
            Result.Success(list)
        }

    override fun observeCustomer(
        userId: String,
        customerId: String,
    ): Flow<Result<Customer, DataError.Network>> =
        customersFlow.map { list ->
            shouldReturnError?.let { return@map Result.Error(it) }
            list.firstOrNull { it.id == customerId }
                ?.let { Result.Success(it) as Result<Customer, DataError.Network> }
                ?: Result.Error(DataError.Network.NOT_FOUND)
        }

    override suspend fun getCustomer(
        userId: String,
        customerId: String,
    ): Result<Customer, DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        return storedCustomer?.let { Result.Success(it) }
            ?: Result.Error(DataError.Network.NOT_FOUND)
    }

    override suspend fun createCustomer(
        userId: String,
        customer: Customer,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastCreatedCustomer = customer
        customersFlow.value = customersFlow.value + customer
        return Result.Success(Unit)
    }

    override suspend fun updateCustomer(
        userId: String,
        customer: Customer,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedCustomer = customer
        customersFlow.value = customersFlow.value.map {
            if (it.id == customer.id) customer else it
        }
        return Result.Success(Unit)
    }

    override suspend fun deleteCustomer(
        userId: String,
        customerId: String,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        customersFlow.value = customersFlow.value.filterNot { it.id == customerId }
        return Result.Success(Unit)
    }
}
