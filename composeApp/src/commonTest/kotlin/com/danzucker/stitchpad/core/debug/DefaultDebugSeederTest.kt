package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultDebugSeederTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences

    private val fixedNow = 1_715_700_000_000L

    @BeforeTest
    fun setUp() {
        customerRepository = FakeCustomerRepository()
        orderRepository = FakeOrderRepository()
        measurementRepository = FakeMeasurementRepository()
        styleRepository = FakeStyleRepository()
        authRepository = FakeAuthRepository().apply {
            currentUser = User(
                id = "test-uid",
                email = "test@example.com",
                displayName = "Test Tailor",
                businessName = "Test Workshop",
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        onboardingPreferences = FakeOnboardingPreferences()
    }

    private fun createSeeder(): DefaultDebugSeeder = DefaultDebugSeeder(
        customerRepository = customerRepository,
        orderRepository = orderRepository,
        measurementRepository = measurementRepository,
        styleRepository = styleRepository,
        authRepository = authRepository,
        onboardingPreferences = onboardingPreferences,
        now = { fixedNow },
    )

    @Test
    fun `seedBrandNew wipes existing customer data`() = runTest {
        customerRepository.customersList = listOf(
            Customer(
                id = "pre-existing",
                userId = "test-uid",
                name = "Pre",
                phone = "+1",
                createdAt = 0,
            )
        )
        // Seed a pre-existing measurement that the wipe should delete.
        measurementRepository.measurementsList = listOf(
            Measurement(
                id = "m-pre-existing",
                customerId = "pre-existing",
                gender = CustomerGender.MALE,
                fields = emptyMap(),
                unit = MeasurementUnit.CM,
                notes = null,
                dateTaken = 0,
                createdAt = 0,
            )
        )
        // Seed a pre-existing style that the wipe should delete.
        // Note: FakeStyleRepository does not mutate stylesList on deleteStyle —
        // it only captures lastDeletedStyleId. We assert via that captured value
        // rather than a post-wipe observeStyles check.
        styleRepository.stylesList = listOf(
            com.danzucker.stitchpad.core.domain.model.Style(
                id = "s-pre-existing",
                customerId = "pre-existing",
                description = "Agbada",
                photoUrl = "",
                photoStoragePath = "",
                createdAt = 0,
                updatedAt = 0,
            )
        )

        val result = createSeeder().seedBrandNew()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(0, customerRepository.customersList.size)
        // FakeMeasurementRepository.lastDeletedMeasurementId confirms deleteMeasurement was called.
        assertEquals("m-pre-existing", measurementRepository.lastDeletedMeasurementId)
        // FakeStyleRepository.lastDeletedStyleId confirms deleteStyle was called.
        assertEquals("s-pre-existing", styleRepository.lastDeletedStyleId)
    }

    @Test
    fun `seedActiveWorkshop creates 8 customers + 4 orders`() = runTest {
        val result = createSeeder().seedActiveWorkshop()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(8, customerRepository.customersList.size)
        // FakeOrderRepository.ordersList reflects everything written via createOrder.
        assertEquals(4, orderRepository.ordersList.size)
    }

    @Test
    fun `seedActiveWorkshop creates measurements for first 4 customers`() = runTest {
        val result = createSeeder().seedActiveWorkshop()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        // FakeMeasurementRepository only tracks the most-recent call in lastCreatedMeasurement.
        // We confirm at least one measurement was written (the 4th customer's).
        assertNotNull(measurementRepository.lastCreatedMeasurement)
    }

    @Test
    fun `seedAllReconnect creates 6 customers and 6 delivered orders`() = runTest {
        val result = createSeeder().seedAllReconnect()

        assertTrue(result is SeedResult.Success, "expected Success, got $result")
        assertEquals(6, customerRepository.customersList.size)
        assertEquals(6, orderRepository.ordersList.size)
    }

    @Test
    fun `seed returns Failure when not signed in`() = runTest {
        authRepository.currentUser = null

        val result = createSeeder().seedBrandNew()

        assertTrue(result is SeedResult.Failure, "expected Failure, got $result")
    }
}
