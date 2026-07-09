package com.danzucker.stitchpad.feature.measurement.presentation.entry

import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementEntryResolverTest {

    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var resolver: MeasurementEntryResolver

    @BeforeTest
    fun setUp() {
        measurementRepository = FakeMeasurementRepository()
        authRepository = FakeAuthRepository()
        authRepository.currentUser = User(
            id = "user-1",
            email = "tailor@example.com",
            displayName = "Test Tailor",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        resolver = MeasurementEntryResolver(measurementRepository, authRepository)
    }

    private fun measurement(id: String) = Measurement(
        id = id, customerId = "customer-1", gender = CustomerGender.FEMALE, name = "M",
        fields = mapOf("waist" to 31.0), unit = MeasurementUnit.INCHES, notes = null,
        dateTaken = 1L, createdAt = 1L,
    )

    @Test
    fun `single measurement resolves to detail`() = runTest {
        measurementRepository.measurementsList = listOf(measurement("meas-1"))
        assertEquals(
            MeasurementEntryDestination.Detail("customer-1", "meas-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `zero measurements resolve to customer detail`() = runTest {
        measurementRepository.measurementsList = emptyList()
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `multiple measurements resolve to customer detail`() = runTest {
        measurementRepository.measurementsList = listOf(measurement("m1"), measurement("m2"))
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `repository error resolves to customer detail`() = runTest {
        measurementRepository.observeError = DataError.Network.UNKNOWN
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }

    @Test
    fun `signed-out user resolves to customer detail`() = runTest {
        authRepository.currentUser = null
        assertEquals(
            MeasurementEntryDestination.CustomerDetail("customer-1"),
            resolver.resolve("customer-1"),
        )
    }
}
