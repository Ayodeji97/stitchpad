package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun observeCustomers(userId: String): Flow<Result<List<Customer>, DataError.Network>>
    suspend fun getCustomer(userId: String, customerId: String): Result<Customer, DataError.Network>
    suspend fun createCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network>
    suspend fun updateCustomer(userId: String, customer: Customer): EmptyResult<DataError.Network>
    suspend fun deleteCustomer(userId: String, customerId: String): EmptyResult<DataError.Network>
}
