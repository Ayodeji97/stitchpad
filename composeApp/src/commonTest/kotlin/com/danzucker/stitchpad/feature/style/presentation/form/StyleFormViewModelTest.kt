package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Style
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
class StyleFormViewModelTest {

    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var orderRepository: FakeOrderRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        styleRepository = FakeStyleRepository()
        authRepository = FakeAuthRepository()
        orderRepository = FakeOrderRepository()
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
        customerId: String = "customer-1",
        styleId: String? = null,
        linkToOrderId: String? = null,
        folderId: String? = null,
        tier: SubscriptionTier = SubscriptionTier.FREE,
    ): StyleFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (styleId != null) put("styleId", styleId)
            if (linkToOrderId != null) put("linkToOrderId", linkToOrderId)
            if (folderId != null) put("folderId", folderId)
        }
        val vm = StyleFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            styleRepository = styleRepository,
            authRepository = authRepository,
            orderRepository = orderRepository,
            entitlements = FakeEntitlementsProvider(tier),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

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

    // --- Initial state ---

    @Test
    fun missingCustomerId_usesInspirationLocation_withoutNavigatingBack() = runTest {
        val vm = StyleFormViewModel(
            savedStateHandle = SavedStateHandle(),
            styleRepository = styleRepository,
            authRepository = authRepository,
            orderRepository = orderRepository,
            entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }

        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.isEditMode)
    }

    @Test
    fun addMode_initialState_hasEditModeFalse() = runTest {
        val vm = createViewModel()

        assertFalse(vm.state.value.isEditMode)
        assertFalse(vm.state.value.isLoading)
        assertEquals("", vm.state.value.description)
        assertNull(vm.state.value.existingStyle)
    }

    // --- Load existing style (edit mode) ---

    @Test
    fun editMode_loadSuccess_populatesDescriptionAndExistingStyle() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "style-7", description = "Existing style")
        styleRepository.stylesList = listOf(existing)

        val vm = createViewModel(styleId = "style-7")

        assertTrue(vm.state.value.isEditMode)
        assertEquals("Existing style", vm.state.value.description)
        assertEquals(existing, vm.state.value.existingStyle)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun editMode_loadStyleNotFound_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = listOf(fakeStyle(id = "other"))

        val vm = createViewModel(styleId = "missing-id")

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.existingStyle)
    }

    @Test
    fun editMode_observeError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN

        val vm = createViewModel(styleId = "style-7")

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun editMode_noAuthUser_skipsLoad() = runTest {
        // no signUp
        val vm = createViewModel(styleId = "style-7")

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.existingStyle)
    }

    // --- Description change ---

    @Test
    fun onDescriptionChange_updatesDescription() = runTest {
        val vm = createViewModel()

        vm.onAction(StyleFormAction.OnDescriptionChange("Blue kaftan"))

        assertEquals("Blue kaftan", vm.state.value.description)
    }

    // --- Photo picked ---

    @Test
    fun onPhotosPicked_underLimit_setsSelectedPhotos() = runTest {
        val vm = createViewModel()
        val bytes = ByteArray(1024) { it.toByte() }

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(bytes)))

        assertEquals(1, vm.state.value.selectedPhotos.size)
        assertEquals(1024, vm.state.value.selectedPhotos.first().size)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun onPhotosPicked_multiple_setsAllSelectedPhotos() = runTest {
        val vm = createViewModel()

        vm.onAction(
            StyleFormAction.OnPhotosPicked(
                listOf(ByteArray(10), ByteArray(20), ByteArray(30))
            )
        )

        assertEquals(3, vm.state.value.selectedPhotos.size)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun onPhotosPicked_anyOverLimit_setsErrorMessage_andClearsSelection() = runTest {
        val vm = createViewModel()
        val tooLarge = ByteArray(5 * 1024 * 1024 + 1)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10), tooLarge)))

        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.selectedPhotos.isEmpty())
    }

    @Test
    fun addToCloset_allowsMultiPhoto_butEditAndLinkDoNot() = runTest {
        assertTrue(createViewModel().state.value.allowMultiPhoto)
        assertFalse(createViewModel(styleId = "style-7").state.value.allowMultiPhoto)
        assertFalse(createViewModel(linkToOrderId = "order-1").state.value.allowMultiPhoto)
    }

    // --- Save: create flow ---

    @Test
    fun onSaveClick_createMode_blankDescription_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))
        // description is blank

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
    }

    @Test
    fun onSaveClick_createMode_noPhoto_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada"))
        // no photo

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
    }

    @Test
    fun onSaveClick_createMode_success_callsCreate_andEmitsNavigateBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada  "))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10) { it.toByte() })))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertEquals("Red agbada", styleRepository.lastCreatedDescription)
        assertNotNull(styleRepository.lastCreatedPhotoBytes)
        assertIs<StyleFormEvent.NavigateBack>(event)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_createMode_multiplePhotos_callsBatchCreate_andEmitsNavigateBack() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Owambe inspiration"))
        vm.onAction(
            StyleFormAction.OnPhotosPicked(listOf(ByteArray(10), ByteArray(20), ByteArray(30)))
        )

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        // Batch path used, not the single-create path.
        assertEquals(3, styleRepository.lastBatchCreatedCount)
        assertEquals("Owambe inspiration", styleRepository.lastBatchCreatedDescription)
        assertNull(styleRepository.lastCreatedDescription)
        assertIs<StyleFormEvent.NavigateBack>(event)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_createMode_repositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_createMode_noAuthUser_doesNotCallRepository() = runTest {
        // no signUp
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_editMode_existingStyleNull_doesNotFallThroughToCreate() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = listOf(fakeStyle(id = "other"))
        val vm = createViewModel(styleId = "missing-id")
        // style failed to load → existingStyle is null but isEditMode is true
        vm.onAction(StyleFormAction.OnDescriptionChange("New description"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
        assertNull(styleRepository.lastUpdatedStyle)
    }

    // --- Save: cap enforcement ---

    @Test
    fun save_batchOverCap_blocked_noCreate() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer: maxImagesPerFolder = 3. Existing 2 styles + picking 4 = 6 > 3 → blocked.
        styleRepository.stylesList = List(2) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnDescriptionChange("Ankara set"))
        vm.onAction(StyleFormAction.OnPhotosPicked(List(4) { ByteArray(10) }))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertIs<StyleFormEvent.CapReached>(event)
        assertEquals(3, event.cap)
        // No create call recorded.
        assertNull(styleRepository.lastCreatedDescription)
        assertNull(styleRepository.lastBatchCreatedDescription)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun save_batchWithinCap_creates() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer: maxImagesPerFolder = 3. Existing 1 + picking 2 = 3 == cap → allowed.
        styleRepository.stylesList = List(1) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnDescriptionChange("Ankara set"))
        vm.onAction(StyleFormAction.OnPhotosPicked(List(2) { ByteArray(10) }))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertIs<StyleFormEvent.NavigateBack>(event)
        // Batch create was called (2 photos).
        assertEquals(2, styleRepository.lastBatchCreatedCount)
        assertFalse(vm.state.value.isSaving)
    }

    // --- Save: edit flow ---

    @Test
    fun onSaveClick_editMode_withoutNewPhoto_callsUpdateWithNullPhoto() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "style-7", description = "Old")
        styleRepository.stylesList = listOf(existing)
        val vm = createViewModel(styleId = "style-7")
        vm.onAction(StyleFormAction.OnDescriptionChange("New description"))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertEquals("style-7", styleRepository.lastUpdatedStyle?.id)
        assertEquals("New description", styleRepository.lastUpdatedStyle?.description)
        assertNull(styleRepository.lastUpdatedPhotoBytes)
        assertIs<StyleFormEvent.NavigateBack>(event)
    }

    @Test
    fun onSaveClick_editMode_withNewPhoto_callsUpdateWithNewBytes() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "style-7", description = "Old")
        styleRepository.stylesList = listOf(existing)
        val vm = createViewModel(styleId = "style-7")
        val newBytes = ByteArray(20) { (it + 1).toByte() }
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(newBytes)))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNotNull(styleRepository.lastUpdatedPhotoBytes)
        assertEquals(20, styleRepository.lastUpdatedPhotoBytes?.size)
    }

    @Test
    fun onSaveClick_editMode_blankDescription_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "style-7", description = "Old")
        styleRepository.stylesList = listOf(existing)
        val vm = createViewModel(styleId = "style-7")
        vm.onAction(StyleFormAction.OnDescriptionChange("   "))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastUpdatedStyle)
    }

    // --- Navigation & error dismiss ---

    @Test
    fun onNavigateBack_emitsNavigateBack() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnNavigateBack)
        assertIs<StyleFormEvent.NavigateBack>(vm.events.first())
    }

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(5 * 1024 * 1024 + 1))))
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(StyleFormAction.OnErrorDismiss)

        assertNull(vm.state.value.errorMessage)
    }
}
