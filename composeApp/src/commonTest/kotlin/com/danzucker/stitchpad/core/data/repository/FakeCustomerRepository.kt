package com.danzucker.stitchpad.core.data.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeCustomerRepository : CustomerRepository {
    var shouldReturnError: DataError.Network? = null
    var storedCustomer: Customer? = null
    var lastCreatedCustomer: Customer? = null
    var lastUpdatedCustomer: Customer? = null

    override fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>> =
        emptyFlow()

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
        return Result.Success(Unit)
    }

    override suspend fun updateCustomer(
        userId: String,
        customer: Customer,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        lastUpdatedCustomer = customer
        return Result.Success(Unit)
    }

    override suspend fun deleteCustomer(
        userId: String,
        customerId: String,
    ): EmptyResult<DataError.Network> {
        shouldReturnError?.let { return Result.Error(it) }
        return Result.Success(Unit)
    }
}
