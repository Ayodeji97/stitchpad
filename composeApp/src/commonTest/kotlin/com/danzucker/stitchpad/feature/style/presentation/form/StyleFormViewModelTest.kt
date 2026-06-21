package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeOrderRepository
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.media.FakeImageCompressor
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapKind
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.test.runCurrent
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
        initialTier: SubscriptionTier = SubscriptionTier.FREE,
    ) : EntitlementsProvider {
        private val _flow = MutableStateFlow(entitlementsFor(initialTier))
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value

        fun emitTier(tier: SubscriptionTier) {
            _flow.value = entitlementsFor(tier)
        }

        companion object {
            fun entitlementsFor(tier: SubscriptionTier) = UserEntitlements(
                tier = tier,
                customerCap = if (tier == SubscriptionTier.FREE) 15 else Int.MAX_VALUE,
                smartCoinAllowance = if (tier == SubscriptionTier.FREE) 5 else 50,
                isInWelcomeWindow = false,
                welcomeEndsAt = null,
                isWithinWelcomeEndingWarning = false,
                welcomeDaysLeft = null,
                canUseCustomMeasurements = tier != SubscriptionTier.FREE,
            )
        }
    }

    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
        styleId: String? = null,
        linkToOrderId: String? = null,
        folderId: String? = null,
        tier: SubscriptionTier = SubscriptionTier.FREE,
        readOnly: Boolean = false,
        fakeEntitlements: FakeEntitlementsProvider = FakeEntitlementsProvider(tier),
        imageCompressor: ImageCompressor = FakeImageCompressor(),
    ): StyleFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (styleId != null) put("styleId", styleId)
            if (linkToOrderId != null) put("linkToOrderId", linkToOrderId)
            if (folderId != null) put("folderId", folderId)
            if (readOnly) put("readOnly", readOnly)
        }
        val vm = StyleFormViewModel(
            savedStateHandle = SavedStateHandle(args),
            styleRepository = styleRepository,
            authRepository = authRepository,
            orderRepository = orderRepository,
            entitlements = fakeEntitlements,
            imageCompressor = imageCompressor,
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

    // --- Photo compression ---

    @Test
    fun onPhotosPicked_oversizedGalleryPhoto_isCompressedAndAccepted() = runTest {
        // A 6 MB gallery pick is over the 5 MB cap raw; the compressor shrinks it so
        // it is accepted rather than rejected.
        val vm = createViewModel(imageCompressor = FakeImageCompressor(outputSize = 1024))
        val oversized = ByteArray(6 * 1024 * 1024)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(oversized)))

        assertNull(vm.state.value.errorMessage)
        assertEquals(1, vm.state.value.selectedPhotos.size)
        assertEquals(1024, vm.state.value.selectedPhotos.first().size)
    }

    @Test
    fun onPhotosPicked_compressorFails_fallsBackToOriginal_andOversizedRejected() = runTest {
        // Decode failure (null) must fall back to the original bytes; an oversized
        // original then still trips the size guard rather than being silently dropped.
        val vm = createViewModel(imageCompressor = FakeImageCompressor(returnNull = true))
        val oversized = ByteArray(6 * 1024 * 1024)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(oversized)))

        assertNotNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.selectedPhotos.isEmpty())
    }

    @Test
    fun editMode_saveAwaitsInFlightCompression_usesNewlyPickedPhoto() = runTest {
        // Race guard: tapping Save while a just-picked replacement photo is still
        // compressing must not persist the old style without the new photo.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = listOf(fakeStyle(id = "style-7", description = "Old"))
        val gate = CompletableDeferred<Unit>()
        val vm = createViewModel(
            styleId = "style-7",
            imageCompressor = FakeImageCompressor(outputSize = 2048, gate = gate),
        )

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(100))))
        vm.onAction(StyleFormAction.OnSaveClick)
        // Compression is still gated, so the save must not have run yet.
        assertNull(styleRepository.lastUpdatedPhotoBytes)

        gate.complete(Unit)
        runCurrent()

        // Save proceeded only after compression finished, with the new photo.
        assertEquals(2048, styleRepository.lastUpdatedPhotoBytes?.size)
    }

    @Test
    fun onPhotosPicked_normalPhoto_compressedBytesStored() = runTest {
        // Even an already-small pick is normalized through the compressor.
        val vm = createViewModel(imageCompressor = FakeImageCompressor(outputSize = 512))
        val small = ByteArray(100 * 1024)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(small)))

        assertEquals(1, vm.state.value.selectedPhotos.size)
        assertEquals(512, vm.state.value.selectedPhotos.first().size)
    }

    // --- Initial state ---

    @Test
    fun missingCustomerId_usesInspirationLocation_withoutNavigatingBack() = runTest {
        val vm = StyleFormViewModel(
            savedStateHandle = SavedStateHandle(),
            styleRepository = styleRepository,
            authRepository = authRepository,
            orderRepository = orderRepository,
            entitlements = FakeEntitlementsProvider(SubscriptionTier.FREE),
            imageCompressor = FakeImageCompressor(),
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
    fun onSaveClick_createMode_blankDescription_withPhoto_createsWithEmptyDescription() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))
        // description is blank — now optional, so a photo alone is enough to save

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertEquals("", styleRepository.lastCreatedDescription)
        assertNotNull(styleRepository.lastCreatedPhotoBytes)
        assertIs<StyleFormEvent.NavigateBack>(event)
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
    fun save_batchOverCap_setsCapSheet_stylesPro() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer: maxImagesPerFolder = 3. Existing 3 styles (at cap) + picking 1 = 4 > 3 → blocked.
        styleRepository.stylesList = List(3) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnDescriptionChange("Ankara set"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet)
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertEquals(SubscriptionTier.PRO, capSheet.currentTier)
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

    // --- Cap sheet: dismiss + upgrade ---

    @Test
    fun onDismissCapSheet_clearsCapSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = List(3) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnDescriptionChange("Ankara set"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))
        vm.onAction(StyleFormAction.OnSaveClick)
        assertNotNull(vm.state.value.capSheet)

        vm.onAction(StyleFormAction.OnDismissCapSheet)

        assertNull(vm.state.value.capSheet)
    }

    @Test
    fun onUpgradeFromCap_clearsCapSheet_andEmitsNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = List(3) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        vm.onAction(StyleFormAction.OnDescriptionChange("Ankara set"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))
        vm.onAction(StyleFormAction.OnSaveClick)
        assertNotNull(vm.state.value.capSheet)

        vm.onAction(StyleFormAction.OnUpgradeFromCap)

        assertNull(vm.state.value.capSheet)
        assertIs<StyleFormEvent.NavigateToUpgrade>(vm.events.first())
    }

    // --- Multi-pick limit = folder's remaining capacity ---

    @Test
    fun maxPhotoSelection_proFolderWithExisting_clampsToRemaining() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO customer: maxImagesPerFolder = 3. 2 already in the folder → can pick 1 more.
        styleRepository.stylesList = List(2) { fakeStyle(id = "existing-$it") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )

        assertEquals(1, vm.state.value.maxPhotoSelection)
    }

    @Test
    fun maxPhotoSelection_emptyFreeCustomer_isFlatCap() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // FREE customer: flatCap = 5, empty → can pick all 5.
        styleRepository.stylesList = emptyList()
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.FREE)

        assertEquals(5, vm.state.value.maxPhotoSelection)
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
    fun onSaveClick_editMode_blankDescription_updatesWithEmptyDescription() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "style-7", description = "Old")
        styleRepository.stylesList = listOf(existing)
        val vm = createViewModel(styleId = "style-7")
        // Clearing the description is now allowed — it is optional.
        vm.onAction(StyleFormAction.OnDescriptionChange("   "))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertEquals("", styleRepository.lastUpdatedStyle?.description)
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

    // --- FIX 7(form): cap check on observe error → blocked, not treated as 0 ---

    @Test
    fun save_whenStyleCountReadErrors_blocked_noCreate_isSavingFalse_errorSet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // observeError will make the count read fail.
        styleRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.PRO)
        vm.onAction(StyleFormAction.OnDescriptionChange("Blue kaftan"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
        assertNull(styleRepository.lastBatchCreatedDescription)
        assertFalse(vm.state.value.isSaving)
        assertNotNull(vm.state.value.errorMessage)
    }

    @Test
    fun free_save_flattenedCountReadError_failsClosed_noCreate_errorSet() = runTest {
        // FREE tier: countStylesAcrossFolders returns null on sub-read failure.
        // The save guard must abort and surface an error rather than treating null as 0.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = DataError.Network.UNKNOWN
        val vm = createViewModel(customerId = "customer-1", tier = SubscriptionTier.FREE)
        vm.onAction(StyleFormAction.OnDescriptionChange("Indigo dress"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription, "No style must be created when count read fails")
        assertNull(styleRepository.lastBatchCreatedDescription)
        assertFalse(vm.state.value.isSaving)
        assertNotNull(vm.state.value.errorMessage, "Error must be surfaced when flattened count can't be read")
    }

    // --- Read-only mode ---

    @Test
    fun readOnly_stateExposesReadOnly() = runTest {
        val vm = createViewModel(readOnly = true)

        assertTrue(vm.state.value.readOnly)
    }

    @Test
    fun readOnly_save_emitsUpgrade_doesNotPersist() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "s1", customerId = "c1")
        styleRepository.stylesList = listOf(existing)
        val vm = createViewModel(customerId = "c1", styleId = "s1", readOnly = true)
        // populate description so the normal validation guard doesn't short-circuit
        vm.onAction(StyleFormAction.OnDescriptionChange("Read-only attempt"))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertIs<StyleFormEvent.NavigateToUpgrade>(event)
        assertNull(styleRepository.lastUpdatedStyle, "readOnly: no write should happen")
        assertNull(styleRepository.lastCreatedDescription, "readOnly: no create should happen")
    }

    // --- Free-tier flattened cap: create path blocked when flat total >= flatCap ---

    @Test
    fun free_save_flattenedTotalAtCap_setsCapSheet_doesNotCreate() = runTest {
        // FREE forCustomer: flatCap = 5.
        // 3 styles in root + 2 styles in a named folder = 5 total (at cap).
        // Root alone = 3 — old single-location check would ALLOW the create. New
        // flattened check must BLOCK it.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val rootLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = null,
        )
        val namedLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = "folder-x",
        )
        styleRepository.stylesByLocation[rootLocation] =
            List(3) { fakeStyle(id = "root-$it", customerId = "customer-flat") }
        styleRepository.stylesByLocation[namedLocation] =
            List(2) { fakeStyle(id = "named-$it", customerId = "customer-flat") }
        styleRepository.foldersByLocation[
            com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset("customer-flat")
        ] = listOf(
            com.danzucker.stitchpad.core.domain.model.StyleFolder(
                id = "folder-x",
                name = "Old Folder",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

        val vm = createViewModel(customerId = "customer-flat", tier = SubscriptionTier.FREE)
        vm.onAction(StyleFormAction.OnDescriptionChange("New style"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)

        val capSheet = vm.state.value.capSheet
        assertNotNull(capSheet, "Cap sheet must be shown when flattened total is at cap")
        assertEquals(StyleCapKind.STYLES, capSheet.kind)
        assertNull(styleRepository.lastCreatedDescription, "No style must be created when at flat cap")
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun free_save_flattenedTotalUnderCap_creates() = runTest {
        // FREE forCustomer: flatCap = 5.
        // 2 styles in root + 2 styles in a named folder = 4 total (under cap) → create allowed.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val rootLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = null,
        )
        val namedLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = "folder-x",
        )
        styleRepository.stylesByLocation[rootLocation] =
            List(2) { fakeStyle(id = "root-$it", customerId = "customer-flat") }
        styleRepository.stylesByLocation[namedLocation] =
            List(2) { fakeStyle(id = "named-$it", customerId = "customer-flat") }
        styleRepository.foldersByLocation[
            com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset("customer-flat")
        ] = listOf(
            com.danzucker.stitchpad.core.domain.model.StyleFolder(
                id = "folder-x",
                name = "Old Folder",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

        val vm = createViewModel(customerId = "customer-flat", tier = SubscriptionTier.FREE)
        vm.onAction(StyleFormAction.OnDescriptionChange("New style"))
        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(ByteArray(10))))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertIs<StyleFormEvent.NavigateBack>(event)
        assertNotNull(styleRepository.lastCreatedDescription, "Style must be created when under flat cap")
        assertNull(vm.state.value.capSheet)
    }

    // --- Additive photo picking + per-photo removal ---

    @Test
    fun picking_photos_twice_appends_instead_of_replacing() = runTest {
        val vm = createViewModel()

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))
        assertEquals(2, vm.state.value.selectedPhotos.size)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(3))))
        assertEquals(3, vm.state.value.selectedPhotos.size)
    }

    @Test
    fun picking_in_single_mode_replaces_instead_of_appending() = runTest {
        // Edit mode (styleId set) → allowMultiPhoto = false. A second pick must REPLACE,
        // not append. save() uses selectedPhotos.firstOrNull(), so stale bytes must not
        // survive a re-pick.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = listOf(fakeStyle(id = "style-7"))
        val vm = createViewModel(styleId = "style-7")

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(1, vm.state.value.selectedPhotos.size)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(2))))

        assertEquals(1, vm.state.value.selectedPhotos.size, "re-pick must replace, not append")
        assertEquals(2, vm.state.value.selectedPhotos.first()[0], "latest picked byte must be stored")
    }

    @Test
    fun appending_is_capped_at_maxPhotoSelection() = runTest {
        // PRO + folderId + 1 existing style → maxPhotoSelection = 3 - 1 = 2.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.stylesList = List(1) { fakeStyle(id = "existing-0") }
        val vm = createViewModel(
            customerId = "customer-1",
            folderId = "f1",
            tier = SubscriptionTier.PRO,
        )
        assertEquals(2, vm.state.value.maxPhotoSelection)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))
        assertEquals(2, vm.state.value.selectedPhotos.size)

        vm.onAction(StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(3))))
        // cap is 2 → merged list (2+1=3) is trimmed to 2
        assertEquals(2, vm.state.value.selectedPhotos.size)
    }

    @Test
    fun removing_a_photo_drops_only_that_index() = runTest {
        val vm = createViewModel()

        vm.onAction(
            StyleFormAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))
        )
        assertEquals(3, vm.state.value.selectedPhotos.size)

        vm.onAction(StyleFormAction.OnRemovePhoto(1))

        assertEquals(2, vm.state.value.selectedPhotos.size)
        // index 0 → byte 1, index 1 → byte 3 (middle removed)
        assertEquals(1, vm.state.value.selectedPhotos[0][0])
        assertEquals(3, vm.state.value.selectedPhotos[1][0])
    }

    // --- Free-tier flattened cap count ---

    // --- Reactive unlock: read-only clears on tier upgrade ---

    @Test
    fun readOnly_clearsWhenTierUpgradesToPaid() = runTest {
        // Open with route readOnly=true (FREE tier, customer closet, no specific styleId
        // — simulates the "add" button being locked at the flat cap).
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val fake = FakeEntitlementsProvider(SubscriptionTier.FREE)
        val vm = createViewModel(
            customerId = "customer-1",
            readOnly = true,
            fakeEntitlements = fake,
        )
        assertTrue(vm.state.value.readOnly)

        // Emit PRO → foldersEnabled=true, styleId=null → not locked → readOnly clears.
        fake.emitTier(SubscriptionTier.PRO)

        assertFalse(vm.state.value.readOnly)
    }

    @Test
    fun readOnly_afterUpgrade_saveDoesNotEmitNavigateToUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val existing = fakeStyle(id = "s1", customerId = "customer-1")
        styleRepository.stylesList = listOf(existing)
        val fake = FakeEntitlementsProvider(SubscriptionTier.FREE)
        val vm = createViewModel(
            customerId = "customer-1",
            styleId = "s1",
            readOnly = true,
            fakeEntitlements = fake,
        )
        assertTrue(vm.state.value.readOnly)

        // Upgrade — readOnly should clear.
        fake.emitTier(SubscriptionTier.PRO)
        assertFalse(vm.state.value.readOnly)

        // Now save — should update (edit mode) rather than redirect to upgrade.
        vm.onAction(StyleFormAction.OnDescriptionChange("Updated description"))
        vm.onAction(StyleFormAction.OnSaveClick)

        val event = vm.events.first()
        assertIs<StyleFormEvent.NavigateBack>(event)
        assertNotNull(styleRepository.lastUpdatedStyle, "update must be called after unlock")
        assertEquals("s1", styleRepository.lastUpdatedStyle?.id)
    }

    // --- Per-folder cap check on tier upgrade ---

    @Test
    fun readOnly_freeToPro_styleWithinFolderCap_unlocks() = runTest {
        // Style "style-within" lives in "folder-a" alongside 1 other style.
        // PRO customer: maxImagesPerFolder = 3. 2 styles ≤ 3 → not over cap → unlocks.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folderLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-1",
            folderId = "folder-a",
        )
        styleRepository.stylesByLocation[folderLocation] = listOf(
            fakeStyle(id = "style-other", customerId = "customer-1").copy(createdAt = 2000L),
            fakeStyle(id = "style-within", customerId = "customer-1").copy(createdAt = 1000L),
        )

        val fake = FakeEntitlementsProvider(SubscriptionTier.FREE)
        val vm = createViewModel(
            customerId = "customer-1",
            styleId = "style-within",
            folderId = "folder-a",
            readOnly = true,
            fakeEntitlements = fake,
        )
        assertTrue(vm.state.value.readOnly)

        // Upgrade to PRO: 2 styles in folder ≤ 3 cap → style-within not in locked set → unlocks.
        fake.emitTier(SubscriptionTier.PRO)

        assertFalse(vm.state.value.readOnly)
    }

    @Test
    fun readOnly_freeToPro_styleStillOverFolderCap_staysLocked() = runTest {
        // Style "style-old" lives in "folder-b" alongside 3 newer styles.
        // PRO customer: maxImagesPerFolder = 3. 4 styles > 3 → oldest is locked → stays locked.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folderLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-1",
            folderId = "folder-b",
        )
        // "style-old" has the smallest createdAt → it lands in the over-cap tail after
        // sortedByDescending(createdAt).drop(3) when there are 4 styles total.
        styleRepository.stylesByLocation[folderLocation] = listOf(
            fakeStyle(id = "style-new1", customerId = "customer-1").copy(createdAt = 4000L),
            fakeStyle(id = "style-new2", customerId = "customer-1").copy(createdAt = 3000L),
            fakeStyle(id = "style-new3", customerId = "customer-1").copy(createdAt = 2000L),
            fakeStyle(id = "style-old",  customerId = "customer-1").copy(createdAt = 1000L),
        )

        val fake = FakeEntitlementsProvider(SubscriptionTier.FREE)
        val vm = createViewModel(
            customerId = "customer-1",
            styleId = "style-old",
            folderId = "folder-b",
            readOnly = true,
            fakeEntitlements = fake,
        )
        assertTrue(vm.state.value.readOnly)

        // Upgrade to PRO: 4 styles in folder > 3 cap → style-old IS in locked set → stays locked.
        fake.emitTier(SubscriptionTier.PRO)

        assertTrue(vm.state.value.readOnly, "style-old must remain read-only: still over per-folder cap after PRO upgrade")
    }

    @Test
    fun free_computeMaxPhotoSelection_countsFlattenedCloset() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // FREE customer flatCap = 5.
        // Seed: 2 styles in the root folder (folderId=null) and 2 in a named folder.
        // Flattened total = 4 → remaining = 5 - 4 = 1 (coerced in [1, ceiling]).
        val rootLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = null,
        )
        val namedLocation = com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset(
            customerId = "customer-flat",
            folderId = "folder-a",
        )
        styleRepository.stylesByLocation[rootLocation] = List(2) { fakeStyle(id = "root-$it", customerId = "customer-flat") }
        styleRepository.stylesByLocation[namedLocation] = List(2) { fakeStyle(id = "named-$it", customerId = "customer-flat") }
        // Expose "folder-a" as an existing folder so observeAllCustomerStyles picks it up.
        styleRepository.foldersByLocation[
            com.danzucker.stitchpad.core.domain.model.StyleLocation.CustomerCloset("customer-flat")
        ] = listOf(
            com.danzucker.stitchpad.core.domain.model.StyleFolder(
                id = "folder-a",
                name = "Folder A",
                createdAt = 0L,
                updatedAt = 0L,
            )
        )

        // No folderId in the args → Free path (no folderId param = root add mode)
        val vm = createViewModel(customerId = "customer-flat", tier = SubscriptionTier.FREE)

        // remaining = flatCap(5) - total(4) = 1
        assertEquals(1, vm.state.value.maxPhotoSelection)
    }
}
