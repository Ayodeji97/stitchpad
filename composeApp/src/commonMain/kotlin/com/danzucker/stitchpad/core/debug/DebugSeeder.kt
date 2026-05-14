package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
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

    override suspend fun seedActiveWorkshop(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        val wipeResult = wipeForUser(userId)
        if (wipeResult is SeedResult.Failure) return wipeResult
        val t = now()
        val customers = SeedFixtures.customers(userId, t)
        for (c in customers) {
            val r = customerRepository.createCustomer(userId, c)
            if (r is Result.Error) return SeedResult.Failure("createCustomer failed: ${r.error}")
        }
        for (c in customers.take(4)) {
            val r = measurementRepository.createMeasurement(userId, c.id, SeedFixtures.measurementsFor(c, t))
            if (r is Result.Error) return SeedResult.Failure("createMeasurement failed: ${r.error}")
        }
        for (o in SeedFixtures.activeOrders(customers, t)) {
            val r = orderRepository.createOrder(userId, o)
            if (r is Result.Error) return SeedResult.Failure("createOrder failed: ${r.error}")
        }
        return SeedResult.Success
    }

    override suspend fun seedAllReconnect(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        val wipeResult = wipeForUser(userId)
        if (wipeResult is SeedResult.Failure) return wipeResult
        val t = now()
        val customers = SeedFixtures.allReconnectCustomers(userId, t)
        for (c in customers) {
            val r = customerRepository.createCustomer(userId, c)
            if (r is Result.Error) return SeedResult.Failure("createCustomer failed: ${r.error}")
        }
        for (o in SeedFixtures.allReconnectOrders(customers, t)) {
            val r = orderRepository.createOrder(userId, o)
            if (r is Result.Error) return SeedResult.Failure("createOrder failed: ${r.error}")
        }
        return SeedResult.Success
    }

    override suspend fun wipeAllData(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        return wipeForUser(userId)
    }

    private suspend fun wipeForUser(userId: String): SeedResult {
        val orders = when (val r = orderRepository.observeOrders(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read orders: ${r.error}")
        }
        val customers = when (val r = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> return SeedResult.Failure("Failed to read customers: ${r.error}")
        }
        for (o in orders) {
            val r = orderRepository.deleteOrder(userId, o.id)
            if (r is Result.Error) return SeedResult.Failure("deleteOrder failed: ${r.error}")
        }
        for (c in customers) {
            val measurements = when (val mr = measurementRepository.observeMeasurements(userId, c.id).first()) {
                is Result.Success -> mr.data
                is Result.Error -> return SeedResult.Failure("Failed to read measurements: ${mr.error}")
            }
            for (m in measurements) {
                val r = measurementRepository.deleteMeasurement(userId, c.id, m.id)
                if (r is Result.Error) return SeedResult.Failure("deleteMeasurement failed: ${r.error}")
            }
            val styles = when (val sr = styleRepository.observeStyles(userId, c.id).first()) {
                is Result.Success -> sr.data
                is Result.Error -> return SeedResult.Failure("Failed to read styles: ${sr.error}")
            }
            for (s in styles) {
                val r = styleRepository.deleteStyle(userId, c.id, s)
                if (r is Result.Error) return SeedResult.Failure("deleteStyle failed: ${r.error}")
            }
            val cr = customerRepository.deleteCustomer(userId, c.id)
            if (cr is Result.Error) return SeedResult.Failure("deleteCustomer failed: ${cr.error}")
        }
        return SeedResult.Success
    }
}
