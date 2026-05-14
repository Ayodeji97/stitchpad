package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.MeasurementRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.OnboardingPreferencesStore
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
    private val authRepository: AuthRepository,
    @Suppress("unused") private val onboardingPreferences: OnboardingPreferencesStore,
    private val now: () -> Long,
) : DebugSeeder {

    override suspend fun seedBrandNew(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        return SeedResult.Success
    }

    override suspend fun seedActiveWorkshop(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        val t = now()
        val customers = SeedFixtures.customers(userId, t)
        customers.forEach { customerRepository.createCustomer(userId, it) }
        customers.take(4).forEach { c ->
            measurementRepository.createMeasurement(userId, c.id, SeedFixtures.measurementsFor(c, t))
        }
        SeedFixtures.activeOrders(customers, t).forEach {
            orderRepository.createOrder(userId, it)
        }
        return SeedResult.Success
    }

    override suspend fun seedAllReconnect(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        val t = now()
        val customers = SeedFixtures.allReconnectCustomers(userId, t)
        customers.forEach { customerRepository.createCustomer(userId, it) }
        SeedFixtures.allReconnectOrders(customers, t).forEach {
            orderRepository.createOrder(userId, it)
        }
        return SeedResult.Success
    }

    override suspend fun wipeAllData(): SeedResult {
        val userId = authRepository.getCurrentUser()?.id
            ?: return SeedResult.Failure("Not signed in")
        wipeForUser(userId)
        return SeedResult.Success
    }

    private suspend fun wipeForUser(userId: String) {
        val orders = currentOrders(userId)
        val customers = currentCustomers(userId)
        orders.forEach { orderRepository.deleteOrder(userId, it.id) }
        customers.forEach { customerRepository.deleteCustomer(userId, it.id) }
    }

    private suspend fun currentCustomers(userId: String): List<Customer> {
        return when (val r = customerRepository.observeCustomers(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> emptyList()
        }
    }

    private suspend fun currentOrders(userId: String): List<Order> {
        return when (val r = orderRepository.observeOrders(userId).first()) {
            is Result.Success -> r.data
            is Result.Error -> emptyList()
        }
    }
}
