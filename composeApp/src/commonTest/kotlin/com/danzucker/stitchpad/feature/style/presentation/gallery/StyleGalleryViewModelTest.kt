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
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapKind
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
        createdAt: Long = 0L,
    ) = Style(
        id = id,
        customerId = customerId,
        description = description,
        photoUrl = "https://example.com/p.jpg",
        photoStoragePath = "users/u/customers/$customerId/styles/$id.jpg",
        createdAt = createdAt,
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
    fun observeStyles_error_paidTier_setsErrorMessage_andClearsLoading() = runTest {
        // On paid tiers the VM uses observePerFolder which propagates errors directly.
        // On FREE it uses observeFlattened with keep-last resilience (silently degrades).
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN

        val vm = createViewModel(tier = SubscriptionTier.PRO)

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    // --- Task 3: tier-aware locking ---

    @Test
    fun freeTier_closet_flattensAllFolders_locksOldestOverFlatCap() = runTest {
        // FREE forCustomer: flatCap = 5, foldersEnabled = false.
        // 4 root styles (createdAt 700..400, newest) + 3 named-folder styles (createdAt 300..100, oldest)
        // = 7 total. Newest 5 by createdAt: root700, root600, root500, root400, folder300 → active.
        // Locked = folder200 + folder100 (the 2 oldest).
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        val rootStyles = listOf(
            fakeStyle(id = "root-1", createdAt = 700L),
            fakeStyle(id = "root-2", createdAt = 600L),
            fakeStyle(id = "root-3", createdAt = 500L),
            fakeStyle(id = "root-4", createdAt = 400L),
        )
        val folderStyles = listOf(
            fakeStyle(id = "folder-1", createdAt = 300L),
            fakeStyle(id = "folder-2", createdAt = 200L),
            fakeStyle(id = "folder-3", createdAt = 100L),
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1")] = rootStyles
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset("customer-1")] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1", "f1")] = folderStyles

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.FREE)

        // All 7 styles flattened — nothing hidden
        assertEquals(7, vm.state.value.styles.size)
        // Newest first
        assertEquals("root-1", vm.state.value.styles.first().id)
        // flatCap=5 → oldest 2 are locked: folder-2 (200) and folder-3 (100)
        assertEquals(setOf("folder-2", "folder-3"), vm.state.value.lockedStyleIds)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun proTier_closet_perFolder_locksOldestOverFolderCap() = runTest {
        // PRO forCustomer: maxImagesPerFolder = 3, foldersEnabled = true.
        // Seed 5 styles in the root folder → newest 3 active, oldest 2 locked.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        val rootStyles = listOf(
            fakeStyle(id = "s1", createdAt = 500L),
            fakeStyle(id = "s2", createdAt = 400L),
            fakeStyle(id = "s3", createdAt = 300L),
            fakeStyle(id = "s4", createdAt = 200L),
            fakeStyle(id = "s5", createdAt = 100L),
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1")] = rootStyles

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)

        assertEquals(5, vm.state.value.styles.size)
        // maxImagesPerFolder=3 → oldest 2 (s4, s5) are locked
        assertEquals(setOf("s4", "s5"), vm.state.value.lockedStyleIds)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun proTier_closet_perFolder_scrambledDates_sortsNewestFirst_andLocksOldest() = runTest {
        // PRO forCustomer: maxImagesPerFolder = 3, foldersEnabled = true.
        // Seed 5 styles in NON-descending createdAt order (scrambled).
        // VM must sort newest-first in state AND compute locked IDs on the sorted list.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        val scrambledStyles = listOf(
            fakeStyle(id = "s2", createdAt = 400L),
            fakeStyle(id = "s5", createdAt = 100L),
            fakeStyle(id = "s1", createdAt = 500L),
            fakeStyle(id = "s3", createdAt = 300L),
            fakeStyle(id = "s4", createdAt = 200L),
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-1")] = scrambledStyles

        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)

        // All 5 styles present
        assertEquals(5, vm.state.value.styles.size)
        // Styles in state must be newest-first (descending by createdAt)
        val stateIds = vm.state.value.styles.map { it.id }
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), stateIds)
        // maxImagesPerFolder=3 → newest 3 active (s1, s2, s3), oldest 2 locked (s4, s5)
        assertEquals(setOf("s4", "s5"), vm.state.value.lockedStyleIds)
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
    fun onAddClick_folderAtCap_setsCapSheet_stylesFree() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // Free inspiration: flatCap = 10. Seed 10 styles (already full).
        styleRepository.stylesList = List(10) { fakeStyle(id = "s$it", customerId = "") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)

        vm.onAction(StyleGalleryAction.OnAddClick)

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertEquals(SubscriptionTier.FREE, capSheet.currentTier)
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
    fun onTargetCustomerSelected_destinationFolderFull_setsCapSheet_noCopy() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        // FREE customer flat cap is 5 — fill the destination to the cap.
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2")] =
            List(5) { fakeStyle(id = "dest-$it") }
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertNull(styleRepository.lastCopied)
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

    // --- Destination-folder picker (paid path) ---

    @Test
    fun transferToPaidTarget_showsDestinationFolders() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        // Seed one named folder in the destination customer's closet.
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset("customer-2")] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        // Seed 1 style in the default location and 0 in the named folder.
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2")] =
            listOf(fakeStyle(id = "dest-default"))
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2", "f1")] = emptyList()

        // PRO tier — forCustomer(PRO): foldersEnabled=true, maxImagesPerFolder=3
        val vm = createViewModel(tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        val transfer = vm.state.value.transfer
        assertNotNull(transfer)
        val folders = transfer.destinationFolders
        assertNotNull(folders)
        assertEquals(2, folders.size)
        // First option = default (folderId null)
        assertNull(folders[0].folderId)
        assertEquals(1, folders[0].count)
        assertEquals(3, folders[0].cap)
        // Second option = named "Wedding"
        assertEquals("f1", folders[1].folderId)
        assertEquals("Wedding", folders[1].name)
        assertEquals(0, folders[1].count)
        // Transfer is NOT performed yet — no copy happened.
        assertNull(styleRepository.lastCopied)
    }

    @Test
    fun onDestinationFolderSelected_named_copiesToThatFolder() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset("customer-2")] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2")] = emptyList()
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2", "f1")] = emptyList()

        val vm = createViewModel(tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        vm.onAction(StyleGalleryAction.OnDestinationFolderSelected("f1"))

        assertEquals(
            Triple(StyleLocation.CustomerCloset("customer-1"), "s1", StyleLocation.CustomerCloset("customer-2", "f1")),
            styleRepository.lastCopied,
        )
        val event = vm.events.first()
        assertIs<StyleGalleryEvent.StyleTransferred>(event)
        assertEquals("f1", event.destinationFolderId)
        assertNull(vm.state.value.transfer)
    }

    @Test
    fun onDestinationFolderSelected_fullFolder_setsCapSheet_noCopy() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        styleRepository.foldersByLocation[StyleLocation.CustomerCloset("customer-2")] = listOf(
            StyleFolder(id = "f1", name = "Wedding", createdAt = 0L, updatedAt = 0L)
        )
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2")] = emptyList()
        // PRO forCustomer: maxImagesPerFolder = 3 — fill the named folder to cap.
        styleRepository.stylesByLocation[StyleLocation.CustomerCloset("customer-2", "f1")] =
            List(3) { fakeStyle(id = "dst-$it") }

        val vm = createViewModel(tier = SubscriptionTier.PRO)
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        vm.onAction(StyleGalleryAction.OnDestinationFolderSelected("f1"))

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertNull(styleRepository.lastCopied)
    }

    // --- Cap sheet: dismiss + upgrade actions ---

    @Test
    fun onDismissCapSheet_clearsCapSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = List(10) { fakeStyle(id = "s$it", customerId = "") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)
        vm.onAction(StyleGalleryAction.OnAddClick)
        assertNotNull(vm.state.value.capSheet)

        vm.onAction(StyleGalleryAction.OnDismissCapSheet)

        assertNull(vm.state.value.capSheet)
    }

    @Test
    fun onUpgradeFromCap_clearsCapSheet_andEmitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = List(10) { fakeStyle(id = "s$it", customerId = "") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)
        vm.onAction(StyleGalleryAction.OnAddClick)
        assertNotNull(vm.state.value.capSheet)

        vm.onAction(StyleGalleryAction.OnUpgradeFromCap)

        assertNull(vm.state.value.capSheet)
        assertIs<StyleGalleryEvent.NavigateToUpgrade>(vm.events.first())
    }

    // --- Error dismiss ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        // Use PRO tier so the per-folder path propagates errors (FREE silently degrades).
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel(tier = SubscriptionTier.PRO)
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(StyleGalleryAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }

    // --- FIX 5 + FIX 7(gallery): performTransfer live count re-read ---

    @Test
    fun transfer_whenDestinationCountReadErrors_noCopy_errorSurfaced() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        customerRepository.customersList = listOf(
            fakeCustomer(id = "customer-1"),
            fakeCustomer(id = "customer-2", name = "Bisi"),
        )
        // observeError makes the live re-read in performTransfer fail.
        styleRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleLongPress(fakeStyle(id = "s1")))
        vm.onAction(StyleGalleryAction.OnCopyClick)

        // observeError also blocks the folder load in onTargetSelected (paid path skipped;
        // for FREE tier the destCount read also errors → verify fail-safe).
        vm.onAction(StyleGalleryAction.OnTargetCustomerSelected("customer-2"))

        assertNull(styleRepository.lastCopied)
        assertNotNull(vm.state.value.errorMessage)
    }
}
