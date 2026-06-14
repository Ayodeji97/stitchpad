package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeCustomerRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StyleGalleryViewModelTest {

    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        styleRepository = FakeStyleRepository()
        customerRepository = FakeCustomerRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeEntitlementsProvider(
        private val tier: SubscriptionTier = SubscriptionTier.FREE,
    ) : EntitlementsProvider {
        private val entitlements = UserEntitlements(
            tier = tier,
            customerCap = if (tier == SubscriptionTier.FREE) 15 else Int.MAX_VALUE,
            smartCoinAllowance = if (tier == SubscriptionTier.FREE) 5 else 50,
            isInWelcomeWindow = false,
            welcomeEndsAt = null,
            isWithinWelcomeEndingWarning = false,
            welcomeDaysLeft = null,
            canUseCustomMeasurements = tier != SubscriptionTier.FREE,
        )
        private val _flow = MutableStateFlow(entitlements)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = entitlements
        override suspend fun awaitHydrated(): UserEntitlements = entitlements
    }

    private fun TestScope.createViewModel(
        customerId: String? = "customer-1",
        folderId: String? = null,
        tier: SubscriptionTier = SubscriptionTier.FREE,
    ): StyleGalleryViewModel {
        val args = buildMap {
            if (customerId != null) put("customerId", customerId)
            if (folderId != null) put("folderId", folderId)
        }
        val vm = StyleGalleryViewModel(
            savedStateHandle = SavedStateHandle(args),
            styleRepository = styleRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            entitlements = FakeEntitlementsProvider(tier),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun fakeCustomer(
        id: String,
        name: String = "Customer $id",
        slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    ) = Customer(
        id = id,
        userId = "test-uid",
        name = name,
        phone = "+2348012345678",
        slotState = slotState,
    )

    private fun fakeStyle(
        id: String = "style-1",
        customerId: String = "customer-1",
        description: String = "Red agbada",
    ) = Style(
        id = id,
        customerId = customerId,
        description = description,
        photoUrl = "https://example.com/p.jpg",
        photoStoragePath = "users/u/customers/$customerId/styles/$id.jpg",
        createdAt = 0L,
        updatedAt = 0L,
    )

    // --- Observe styles ---

    @Test
    fun missingCustomerId_usesInspirationLocation_withoutNavigatingBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = StyleGalleryViewModel(
            savedStateHandle = SavedStateHandle(),
            styleRepository = styleRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.isInspirationGallery)
        assertTrue(vm.state.value.styles.isEmpty())
    }

    @Test
    fun observeStyles_success_populatesStyles_andClearsLoading() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = listOf(fakeStyle(id = "a"), fakeStyle(id = "b"))

        val vm = createViewModel()

        assertEquals(2, vm.state.value.styles.size)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun observeStyles_noAuthUser_clearsLoading_withoutFetching() = runTest {
        // no signUp
        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.styles.isEmpty())
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun observeStyles_error_setsErrorMessage_andClearsLoading() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN

        val vm = createViewModel()

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun observeStyles_emptyList_showsEmpty_andClearsLoading() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = emptyList()

        val vm = createViewModel()

        assertTrue(vm.state.value.styles.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    // --- Cap enforcement ---

    @Test
    fun onAddClick_folderAtCap_emitsCapReached_noNavigate() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // Free inspiration: flatCap = 10. Seed 10 styles (already full).
        styleRepository.stylesList = List(10) { fakeStyle(id = "s$it", customerId = "") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)

        vm.onAction(StyleGalleryAction.OnAddClick)

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.CapReached>(event)
        assertEquals(10, event.cap)
    }

    @Test
    fun onAddClick_underCap_navigatesToAdd() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // Free inspiration: flatCap = 10. Seed 3 styles (under cap).
        styleRepository.stylesList = List(3) { fakeStyle(id = "s$it", customerId = "") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)

        vm.onAction(StyleGalleryAction.OnAddClick)

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToAddStyle>(event)
        assertNull(event.customerId)
        assertNull(event.folderId)
    }

    // --- Navigation events ---

    @Test
    fun onAddClick_emitsNavigateToAddStyle_withCustomerId() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnAddClick)

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToAddStyle>(event)
        assertEquals("customer-1", event.customerId)
    }

    @Test
    fun onAddClick_fromInspiration_emitsNavigateToAddStyle_withNullCustomerId() = runTest {
        val vm = createViewModel(customerId = null)
        vm.onAction(StyleGalleryAction.OnAddClick)

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToAddStyle>(event)
        assertNull(event.customerId)
    }

    @Test
    fun onStyleClick_emitsNavigateToEditStyle_withBothIds() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleClick(fakeStyle(id = "style-42")))

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToEditStyle>(event)
        assertEquals("customer-1", event.customerId)
        assertEquals("style-42", event.styleId)
    }

    @Test
    fun onStyleClick_fromInspiration_emitsNavigateToEditStyle_withNullCustomerId() = runTest {
        val vm = createViewModel(customerId = null)
        vm.onAction(StyleGalleryAction.OnStyleClick(fakeStyle(id = "style-42", customerId = "")))

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToEditStyle>(event)
        assertNull(event.customerId)
        assertEquals("style-42", event.styleId)
    }

    @Test
    fun onNavigateBack_emitsNavigateBack() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnNavigateBack)
        assertIs<StyleGalleryEvent.NavigateBack>(vm.events.first())
    }

    // --- Delete flow ---

    @Test
    fun onDeleteClick_showsDeleteDialog_withStyle() = runTest {
        val vm = createViewModel()
        val style = fakeStyle()

        vm.onAction(StyleGalleryAction.OnDeleteClick(style))

        assertTrue(vm.state.value.showDeleteDialog)
        assertEquals(style, vm.state.value.styleToDelete)
    }

    @Test
    fun onDismissDeleteDialog_hidesDialog_andClearsStyleToDelete() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnDeleteClick(fakeStyle()))
        assertTrue(vm.state.value.showDeleteDialog)

        vm.onAction(StyleGalleryAction.OnDismissDeleteDialog)

        assertFalse(vm.state.value.showDeleteDialog)
        assertNull(vm.state.value.styleToDelete)
    }

    @Test
    fun onConfirmDelete_callsDeleteStyle_andHidesDialog() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        val style = fakeStyle(id = "style-del")
        vm.onAction(StyleGalleryAction.OnDeleteClick(style))
        vm.onAction(StyleGalleryAction.OnConfirmDelete)

        assertEquals("style-del", styleRepository.lastDeletedStyleId)
        assertEquals(StyleLocation.CustomerCloset("customer-1"), styleRepository.lastDeletedLocation)
        assertFalse(vm.state.value.showDeleteDialog)
        assertNull(vm.state.value.styleToDelete)
    }

    @Test
    fun onConfirmDelete_fromInspiration_deletesFromInspirationLocation() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(customerId = null)
        val style = fakeStyle(id = "style-del", customerId = "")
        vm.onAction(StyleGalleryAction.OnDeleteClick(style))
        vm.onAction(StyleGalleryAction.OnConfirmDelete)

        assertEquals("style-del", styleRepository.lastDeletedStyleId)
        assertEquals(StyleLocation.Inspiration(), styleRepository.lastDeletedLocation)
    }

    @Test
    fun onConfirmDelete_withNoStyleToDelete_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        // no OnDeleteClick — styleToDelete is null
        vm.onAction(StyleGalleryAction.OnConfirmDelete)

        assertNull(styleRepository.lastDeletedStyleId)
    }

    @Test
    fun onConfirmDelete_withRepositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnDeleteClick(fakeStyle()))
        vm.onAction(StyleGalleryAction.OnConfirmDelete)

        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun onConfirmDelete_withNoAuthUser_doesNotCallRepository() = runTest {
        // no signUp
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnDeleteClick(fakeStyle()))
        vm.onAction(StyleGalleryAction.OnConfirmDelete)

        assertNull(styleRepository.lastDeletedStyleId)
    }

    // --- Copy / move transfer (PTSP-38) ---

    @Test
    fun onStyleLongPress_opensActionSheet() = runTest {
        val vm = createViewModel()
        val style = fakeStyle(id = "s1")

        vm.onAction(StyleGalleryAction.OnStyleLongPress(style))

        assertEquals(style, vm.state.value.actionSheetStyle)
    }

    @Test
    fun onCopyClick_opensTransfer_withOtherActiveCustomersOnly() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"), // source — excluded
            fakeCustomer(id = "customer-2", name = "Bisi"),
            fakeCustomer(id = "locked", slotState = CustomerSlotState.LOCKED), // excluded
        )
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))

        vm.onAction(StyleGalleryAction.OnCopyClick)

        val transfer = vm.state.value.transfer
        assertNotNull(transfer)
        assertEquals(StyleTransferMode.COPY, transfer.mode)
        assertEquals(listOf("inspiration", "customer-2"), transfer.targets.map { it.id })
        assertNull(vm.state.value.actionSheetStyle)
    }

    @Test
    fun onCopyClick_fromClosetWithCustomerFetchError_keepsInspirationTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.shouldReturnError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))

        vm.onAction(StyleGalleryAction.OnCopyClick)

        val transfer = vm.state.value.transfer
        assertNotNull(transfer)
        assertEquals(listOf(TransferTarget.Inspiration), transfer.targets)
    }

    @Test
    fun onTargetCustomerSelected_copy_callsCopyStyle_andEmitsTransferred() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        assertEquals(
            Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.CustomerCloset("customer-2")),
            styleRepository.lastCopied,
        )
        val event = vm.events.first()
        assertIs<StyleGalleryEvent.StyleTransferred>(event)
        assertEquals(StyleTransferMode.COPY, event.mode)
        assertEquals(TransferTarget.Customer(customerId = "customer-2", name = "Bisi"), event.target)
        assertNull(vm.state.value.transfer)
    }

    @Test
    fun onTargetCustomerSelected_copyToInspiration_callsCopyStyleWithInspirationTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("inspiration"))

        assertEquals(
            Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.Inspiration()),
            styleRepository.lastCopied,
        )
        val event = vm.events.first()
        assertIs<StyleGalleryEvent.StyleTransferred>(event)
        assertEquals(TransferTarget.Inspiration, event.target)
    }

    @Test
    fun onTargetCustomerSelected_move_callsMoveStyle() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnMoveClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        assertEquals(
            Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.CustomerCloset("customer-2")),
            styleRepository.lastMoved,
        )
        assertIs<StyleGalleryEvent.StyleTransferred>(vm.events.first())
    }

    @Test
    fun onDeleteClick_fromActionSheet_clearsSheet_andShowsDeleteDialog() = runTest {
        val vm = createViewModel()
        val style = fakeStyle(id = "s1")
        vm.onAction(StyleGalleryAction.OnStyleLongPress(style))

        vm.onAction(StyleGalleryAction.OnDeleteClick(style))

        assertNull(vm.state.value.actionSheetStyle)
        assertTrue(vm.state.value.showDeleteDialog)
        assertEquals(style, vm.state.value.styleToDelete)
    }

    // --- isInspirationGallery flag ---

    @Test
    fun closetGallery_isInspirationGalleryFalse() = runTest {
        authRepository.signUpWithEmail("t@t.com", "p", "T")
        val vm = createViewModel(customerId = "customer-1")
        assertFalse(vm.state.value.isInspirationGallery)
    }

    @Test
    fun inspirationGallery_isInspirationGalleryTrue() = runTest {
        authRepository.signUpWithEmail("t@t.com", "p", "T")
        val vm = StyleGalleryViewModel(
            savedStateHandle = SavedStateHandle(),
            styleRepository = styleRepository,
            customerRepository = customerRepository,
            authRepository = authRepository,
            entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        assertTrue(vm.state.value.isInspirationGallery)
    }

    // --- Error dismiss ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(StyleGalleryAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }
}
