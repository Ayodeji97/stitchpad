package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeCustomMeasurementFieldRepository
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.sharing.FakeMeasurementSharer
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareFormatter
import com.danzucker.stitchpad.feature.measurement.presentation.share.MeasurementShareLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementDetailViewModelTest {

    private lateinit var measurementRepository: FakeMeasurementRepository
    private lateinit var customFieldRepository: FakeCustomMeasurementFieldRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var analytics: FakeAnalytics
    private lateinit var measurementSharer: FakeMeasurementSharer

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        measurementRepository = FakeMeasurementRepository()
        customFieldRepository = FakeCustomMeasurementFieldRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
        // Fakes ignore the userId value itself (they key off customerId/measurementId),
        // but the ViewModel bails out of every observe*() early when getCurrentUser()
        // is null — so a signed-in user must be present for these tests to reach them.
        authRepository.currentUser = User(
            id = "user-1",
            email = "tailor@example.com",
            displayName = "Test Tailor",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
        analytics = FakeAnalytics()
        measurementSharer = FakeMeasurementSharer()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeMeasurement(id: String = "meas-1", name: String = "Wedding gown") = Measurement(
        id = id,
        customerId = "customer-1",
        gender = CustomerGender.FEMALE,
        name = name,
        fields = mapOf("waist" to 31.0),
        unit = MeasurementUnit.INCHES,
        notes = null,
        dateTaken = 1L,
        createdAt = 1L,
    )

    private fun fakeCustomer(slotState: CustomerSlotState = CustomerSlotState.ACTIVE) = Customer(
        id = "customer-1",
        userId = "user-1",
        name = "Chidinma Eze",
        phone = "0705 991 2340",
        slotState = slotState,
    )

    // Stand-in for the production resolver (MeasurementDetailViewModel.kt's
    // defaultShareLabelsResolver), which calls getString() — that throws
    // "Method getSystem in android.content.res.Resources not mocked" under plain-JVM
    // ViewModel unit tests (no Robolectric here). This fake returns fixed English
    // labels instead so the share pipeline is testable without resource loading;
    // MeasurementShareFormatter.format falls back to the raw section key when a
    // title is missing from the map, so the empty sectionTitles below is fine —
    // no test asserts on section titles.
    private fun fakeShareLabels(measurement: Measurement): MeasurementShareLabels = MeasurementShareLabels(
        measurementName = measurement.name.ifBlank { "Customer measurement" },
        genderLabel = if (measurement.gender == CustomerGender.FEMALE) "Women's" else "Men's",
        unitLabel = if (measurement.unit == MeasurementUnit.INCHES) "Inches" else "Centimetres",
        unitSuffix = if (measurement.unit == MeasurementUnit.INCHES) "″" else "cm",
        dateFormatted = MeasurementShareFormatter.formatShareDate(measurement.dateTaken),
        businessName = null,
        sectionTitles = emptyMap(),
        customSectionTitle = "Custom",
    )

    private fun TestScope.createViewModel(
        measurementId: String = "meas-1",
        source: String = MeasurementDetailSource.CUSTOMER_DETAIL,
        fromSave: Boolean = false,
    ): MeasurementDetailViewModel {
        val vm = MeasurementDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "customerId" to "customer-1",
                    "measurementId" to measurementId,
                    "source" to source,
                    "fromSave" to fromSave,
                ),
            ),
            measurementRepository = measurementRepository,
            customFieldRepository = customFieldRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            analytics = analytics,
            measurementSharer = measurementSharer,
            shareLabelsResolver = ::fakeShareLabels,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `loads measurement and lock state on start`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals("Wedding gown", state.measurement?.name)
        assertEquals(false, state.isLocked)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `logs viewed analytics with source once`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        createViewModel(source = MeasurementDetailSource.POST_SAVE)

        val event = analytics.events.filterIsInstance<AnalyticsEvent.MeasurementDetailViewed>().single()
        assertEquals("post_save", event.source)
    }

    @Test
    fun `missing measurement navigates back`() = runTest {
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.events.test {
            assertIs<MeasurementDetailEvent.NavigateBack>(awaitItem())
        }
    }

    @Test
    fun `edit on unlocked customer navigates to edit form`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnEditClick)
            val event = assertIs<MeasurementDetailEvent.NavigateToEdit>(awaitItem())
            assertEquals("customer-1", event.customerId)
            assertEquals("meas-1", event.measurementId)
        }
    }

    @Test
    fun `edit rename and delete on locked customer route to upgrade`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnEditClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
            vm.onAction(MeasurementDetailAction.OnRenameClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
            vm.onAction(MeasurementDetailAction.OnDeleteClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
        }
        assertNull(vm.state.value.renameDraft)
        assertEquals(false, vm.state.value.showDeleteDialog)
    }

    @Test
    fun `confirm delete fires repository delete and navigates back`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnDeleteClick)
        assertTrue(vm.state.value.showDeleteDialog)
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnConfirmDelete)
            assertIs<MeasurementDetailEvent.NavigateBack>(awaitItem())
        }
        assertEquals("meas-1", measurementRepository.lastDeletedMeasurementId)
    }

    @Test
    fun `confirm rename updates repository with trimmed name`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnRenameClick)
        assertEquals("Wedding gown", vm.state.value.renameDraft)
        vm.onAction(MeasurementDetailAction.OnRenameDraftChange("  Aso-ebi  "))
        vm.onAction(MeasurementDetailAction.OnConfirmRename)
        assertEquals("Aso-ebi", measurementRepository.lastUpdatedMeasurement?.name)
        assertNull(vm.state.value.renameDraft)
    }

    @Test
    fun `fromSave arg arms the saved snackbar once`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(fromSave = true)
        assertTrue(vm.state.value.showSavedMessage)
        vm.onAction(MeasurementDetailAction.OnSavedMessageShown)
        assertEquals(false, vm.state.value.showSavedMessage)
    }

    @Test
    fun `fromSave initial miss waits for the enqueued write instead of navigating back`() = runTest {
        // The create is applied to the local cache asynchronously (OfflineWriteDispatcher),
        // so the detail screen's first snapshot can predate the new document.
        measurementRepository.measurementsList = emptyList()
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel(fromSave = true, source = MeasurementDetailSource.POST_SAVE)
        vm.events.test {
            expectNoEvents()
            measurementRepository.measurementsList = listOf(fakeMeasurement())
            expectNoEvents()
        }
        assertEquals("Wedding gown", vm.state.value.measurement?.name)
    }

    @Test
    fun `measurement deleted elsewhere after loading navigates back`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        assertEquals("Wedding gown", vm.state.value.measurement?.name)
        vm.events.test {
            measurementRepository.measurementsList = emptyList()
            assertIs<MeasurementDetailEvent.NavigateBack>(awaitItem())
        }
    }

    @Test
    fun `gated actions no-op while lock state is unknown`() = runTest {
        // No customer doc emitted (observeCustomer returns NOT_FOUND) — isLocked stays null.
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = emptyList()
        val vm = createViewModel()
        assertNull(vm.state.value.isLocked)
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnEditClick)
            vm.onAction(MeasurementDetailAction.OnRenameClick)
            vm.onAction(MeasurementDetailAction.OnDeleteClick)
            expectNoEvents()
        }
        assertNull(vm.state.value.renameDraft)
        assertEquals(false, vm.state.value.showDeleteDialog)
    }

    @Test
    fun `share click opens sheet and image share builds data and logs analytics`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnShareClick)
        assertTrue(vm.state.value.showShareSheet)
        vm.onAction(MeasurementDetailAction.OnShareAsImageClick)
        assertEquals(false, vm.state.value.showShareSheet)
        val data = measurementSharer.lastImageData
        assertEquals("Chidinma Eze", data?.customerName)
        assertEquals("Wedding gown", data?.measurementName)
        val event = analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().single()
        assertEquals("image", event.format)
    }

    @Test
    fun `whatsapp share emits LaunchWhatsApp with customer phone and bold text`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnShareWhatsAppClick)
            val event = assertIs<MeasurementDetailEvent.LaunchWhatsApp>(awaitItem())
            assertEquals("0705 991 2340", event.phone)
            assertTrue(event.message.contains("*Chidinma Eze — Wedding gown*"))
        }
        assertEquals(
            "whatsapp_text",
            analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().single().format,
        )
    }

    @Test
    fun `whatsapp share with blank phone falls back to text share`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer().copy(phone = ""))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnShareWhatsAppClick)
            expectNoEvents()
        }
        assertTrue(measurementSharer.lastSharedText.orEmpty().contains("*Chidinma Eze — Wedding gown*"))
        val event = analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().single()
        assertEquals("whatsapp_text", event.format)
    }

    @Test
    fun `share failure surfaces error message`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        measurementSharer.throwOnShare = true
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnShareAsPdfClick)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun `share on locked customer routes to upgrade and unknown lock ignores tap`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnShareClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
        }
        assertEquals(false, vm.state.value.showShareSheet)
    }

    @Test
    fun `lock flipping while share sheet is open re-gates the share action`() = runTest {
        measurementRepository.measurementsList = listOf(fakeMeasurement())
        customerRepository.customersList = listOf(fakeCustomer())
        val vm = createViewModel()
        vm.onAction(MeasurementDetailAction.OnShareClick)
        assertTrue(vm.state.value.showShareSheet)
        // Live customer listener flips the slot to LOCKED while the sheet sits open.
        customerRepository.customersList = listOf(fakeCustomer(slotState = CustomerSlotState.LOCKED))
        assertEquals(true, vm.state.value.isLocked)
        vm.events.test {
            vm.onAction(MeasurementDetailAction.OnShareAsImageClick)
            assertIs<MeasurementDetailEvent.NavigateToUpgrade>(awaitItem())
        }
        assertNull(measurementSharer.lastImageData)
        assertEquals(false, vm.state.value.showShareSheet)
        assertTrue(analytics.events.filterIsInstance<AnalyticsEvent.MeasurementShared>().isEmpty())
    }
}
