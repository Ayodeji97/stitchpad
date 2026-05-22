package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.first

sealed interface SeedResult {
    data object Success : SeedResult
    data class Failure(val reason: String) : SeedResult
}

interface DebugSeeder {
    suspend fun seedBrandNew(): SeedResult
    suspend fun seedActiveWorkshop(): SeedResult
    suspend fun seedAllReconnect(): SeedResult
    suspend fun seedBulkCustomers(
        count: Int,
        withMeasurementsCount: Int,
        withOrdersCount: Int,
    ): SeedResult
    suspend fun wipeAllData(): SeedResult
}

class DefaultDebugSeeder(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val measurementRepository: MeasurementRepository,
    private val styleRepository: StyleRepository,
    private val authRepository: AuthRepository,
    private val now: () -> Long,
) : DebugSeeder {

    override suspend fun seedBrandNew(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        return wipeForUser(userId)
    }

    @Suppress("ReturnCount")
    override suspend fun seedActiveWorkshop(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        val wipeResult = wipeForUser(userId)
        if (wipeResult is SeedResult.Failure) return wipeResult
        val t = now()
        val customers = SeedFixtures.customers(userId, t)

        val customersResult = customers.createEachOrFail("createCustomer") {
            customerRepository.createCustomer(userId, it)
        }
        if (customersResult is SeedResult.Failure) return customersResult

        val measurementsResult = customers.take(4).createEachOrFail("createMeasurement") {
            measurementRepository.createMeasurement(userId, it.id, SeedFixtures.measurementsFor(it, t))
        }
        if (measurementsResult is SeedResult.Failure) return measurementsResult

        return SeedFixtures.activeOrders(customers, t).createEachOrFail("createOrder") {
            orderRepository.createOrder(userId, it)
        }
    }

    @Suppress("ReturnCount")
    override suspend fun seedAllReconnect(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        val wipeResult = wipeForUser(userId)
        if (wipeResult is SeedResult.Failure) return wipeResult
        val t = now()
        val customers = SeedFixtures.allReconnectCustomers(userId, t)

        val customersResult = customers.createEachOrFail("createCustomer") {
            customerRepository.createCustomer(userId, it)
        }
        if (customersResult is SeedResult.Failure) return customersResult

        return SeedFixtures.allReconnectOrders(customers, t).createEachOrFail("createOrder") {
            orderRepository.createOrder(userId, it)
        }
    }

    @Suppress("ReturnCount")
    override suspend fun seedBulkCustomers(
        count: Int,
        withMeasurementsCount: Int,
        withOrdersCount: Int,
    ): SeedResult {
        if (count <= 0) return SeedResult.Failure("Count must be > 0")
        if (withMeasurementsCount < 0 || withOrdersCount < 0) {
            return SeedResult.Failure("Counts cannot be negative")
        }
        if (withMeasurementsCount > count || withOrdersCount > count) {
            return SeedResult.Failure("Measurement/order count cannot exceed total")
        }
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")

        val t = now()
        val customers = SeedFixtures.bulkCustomers(userId, t, count)

        val customersResult = customers.createEachOrFail("createCustomer") {
            customerRepository.createCustomer(userId, it)
        }
        if (customersResult is SeedResult.Failure) return customersResult

        val measurementsResult = customers.take(withMeasurementsCount).createEachOrFail("createMeasurement") {
            measurementRepository.createMeasurement(userId, it.id, SeedFixtures.bulkMeasurementFor(it, t))
        }
        if (measurementsResult is SeedResult.Failure) return measurementsResult

        return customers.take(withOrdersCount).mapIndexed { i, c -> i to c }
            .createEachOrFail("createOrder") { (i, c) ->
                orderRepository.createOrder(userId, SeedFixtures.bulkOrderFor(c, i, t))
            }
    }

    override suspend fun wipeAllData(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        return wipeForUser(userId)
    }

    @Suppress("ReturnCount")
    private suspend fun wipeForUser(userId: String): SeedResult {
        val orders = when (val r = orderRepository.observeOrders(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read orders: ${r.error}")
        }
        val customers = when (val r = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read customers: ${r.error}")
        }
        val orderResult = orders.createEachOrFail("deleteOrder") {
            orderRepository.deleteOrder(userId, it.id)
        }
        if (orderResult is SeedResult.Failure) return orderResult

        for (c in customers) {
            val customerResult = wipeCustomerData(userId, c)
            if (customerResult is SeedResult.Failure) return customerResult
        }
        return SeedResult.Success
    }

    @Suppress("ReturnCount")
    private suspend fun wipeCustomerData(userId: String, customer: Customer): SeedResult {
        val measurements = when (val r = measurementRepository.observeMeasurements(userId, customer.id).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read measurements: ${r.error}")
        }
        val measurementsResult = measurements.createEachOrFail("deleteMeasurement") {
            measurementRepository.deleteMeasurement(userId, customer.id, it.id)
        }
        if (measurementsResult is SeedResult.Failure) return measurementsResult

        val styles = when (val r = styleRepository.observeStyles(userId, customer.id).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read styles: ${r.error}")
        }
        val stylesResult = styles.createEachOrFail("deleteStyle") {
            styleRepository.deleteStyle(userId, customer.id, it)
        }
        if (stylesResult is SeedResult.Failure) return stylesResult

        val deleteResult = customerRepository.deleteCustomer(userId, customer.id)
        return if (deleteResult is Result.Error) {
            SeedResult.Failure("deleteCustomer failed: ${deleteResult.error}")
        } else {
            SeedResult.Success
        }
    }

    private suspend inline fun <T> Iterable<T>.createEachOrFail(
        label: String,
        crossinline create: suspend (T) -> EmptyResult<DataError.Network>,
    ): SeedResult {
        for (item in this) {
            val r = create(item)
            if (r is Result.Error) return SeedResult.Failure("$label failed: ${r.error}")
        }
        return SeedResult.Success
    }
}
