package com.danzucker.stitchpad.feature.style.presentation.folders

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
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
class StyleFoldersViewModelTest {

    private lateinit var styleRepository: FakeStyleRepository
    private lateinit var authRepository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        styleRepository = FakeStyleRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fakeFolder(id: String) = StyleFolder(
        id = id,
        name = "f",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun fakeStyle(
        id: String = "style-1",
        photoUrl: String = "",
        createdAt: Long = 0L,
    ) = Style(
        id = id,
        customerId = "",
        description = "desc",
        photoUrl = photoUrl,
        photoStoragePath = "",
        createdAt = createdAt,
        updatedAt = 0L,
    )

    /**
     * Minimal [EntitlementsProvider] stub. Pass [tier] to simulate a specific subscription.
     * [awaitHydrated] returns the same snapshot as [current] — no race.
     */
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
        customerId: String? = null,
        tier: SubscriptionTier = SubscriptionTier.PRO,
    ): StyleFoldersViewModel {
        val args = if (customerId != null) mapOf("customerId" to customerId) else emptyMap()
        val vm = StyleFoldersViewModel(
            savedStateHandle = SavedStateHandle(args),
            styleRepository = styleRepository,
            authRepository = authRepository,
            entitlements = FakeEntitlementsProvider(tier),
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    // ---------------------------------------------------------------------------
    // Free-user bypass: foldersEnabled=false → immediately RedirectToFlatGallery
    // ---------------------------------------------------------------------------

    @Test
    fun freeUser_inspiration_immediatelyRedirectsToFlatGallery() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.FREE)

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.RedirectToFlatGallery>(event)
        assertNull(event.customerId)
    }

    // ---------------------------------------------------------------------------
    // Observe folders → cards (P2 live derivation)
    // ---------------------------------------------------------------------------

    @Test
    fun observeFolders_success_populatesCards_andClearsLoading() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.folders = listOf(fakeFolder("a"), fakeFolder("b"))

        val vm = createViewModel()

        // 1 default + 2 named = 3 cards
        assertEquals(3, vm.state.value.cards.size)
        assertEquals(2, vm.state.value.namedFolderCount)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun observeFolders_noAuthUser_clearsLoading() = runTest {
        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.cards.isEmpty())
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun cards_includeDefaultFirst_withLiveCounts() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        // 2 styles in the default inspiration location (folderId = null)
        val defaultLocation = StyleLocation.Inspiration(folderId = null)
        styleRepository.stylesByLocation[defaultLocation] = listOf(
            fakeStyle(id = "s1", photoUrl = "https://example.com/s1.jpg", createdAt = 1L),
            fakeStyle(id = "s2", photoUrl = "", createdAt = 0L),
        )

        // 1 named folder "f1" with 3 styles
        val folder = StyleFolder(id = "f1", name = "Corset", createdAt = 0L, updatedAt = 0L)
        styleRepository.folders = listOf(folder)
        val namedLocation = StyleLocation.Inspiration(folderId = "f1")
        styleRepository.stylesByLocation[namedLocation] = listOf(
            fakeStyle(id = "n1", photoUrl = "https://example.com/n1.jpg", createdAt = 2L),
            fakeStyle(id = "n2", photoUrl = "https://example.com/n2.jpg", createdAt = 1L),
            fakeStyle(id = "n3", photoUrl = "", createdAt = 0L),
        )

        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)

        val cards = vm.state.value.cards
        assertEquals(2, cards.size)

        // Default card must be first
        val defaultCard = cards[0]
        assertNull(defaultCard.folderId)
        assertEquals(2, defaultCard.count)
        // Cover = most-recent style with non-blank photoUrl (s1 has createdAt=1 and has photoUrl)
        assertEquals("https://example.com/s1.jpg", defaultCard.coverUrl)
        assertNull(defaultCard.source)

        // Named card
        val namedCard = cards[1]
        assertEquals("f1", namedCard.folderId)
        assertEquals(3, namedCard.count)
        // Cover = most-recent style with non-blank photoUrl (n1, createdAt=2)
        assertEquals("https://example.com/n1.jpg", namedCard.coverUrl)
        assertEquals(folder, namedCard.source)

        assertEquals(1, vm.state.value.namedFolderCount)
        assertFalse(vm.state.value.isLoading)
    }

    // ---------------------------------------------------------------------------
    // Create: cap enforcement
    // ---------------------------------------------------------------------------

    @Test
    fun createBlockedAtCap_emitsUpgrade() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO inspiration: maxFolders = 10. Pre-load 9 named folders.
        // namedFolderCount (9) + 1 (default) = 10 >= maxFolders (10) → blocked.
        styleRepository.folders = List(9) { fakeFolder("f$it") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)

        vm.onAction(StyleFoldersAction.OnCreateClick)

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.NavigateToUpgrade>(event)
    }

    @Test
    fun createUnderCap_opensSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        // PRO inspiration: maxFolders = 10. 3 named folders → total = 4, well under cap.
        styleRepository.folders = List(3) { fakeFolder("f$it") }
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)

        vm.onAction(StyleFoldersAction.OnCreateClick)

        assertTrue(vm.state.value.showCreateSheet)
    }

    @Test
    fun onConfirmCreate_callsRepo() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnCreateClick)
        vm.onAction(StyleFoldersAction.OnConfirmCreate("Corset"))

        assertEquals("Corset", styleRepository.lastCreatedFolderName)
        assertFalse(vm.state.value.showCreateSheet)
    }

    @Test
    fun onConfirmCreate_atCap_blocksCreate_emitsUpgrade() = runTest {
        // Guards the loading-race: the sheet can open before the live count lands,
        // so confirm must re-check the cap before calling createFolder.
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.folders = List(9) { fakeFolder("f$it") } // PRO: 9 + default = 10 = cap
        val vm = createViewModel(customerId = null, tier = SubscriptionTier.PRO)

        vm.onAction(StyleFoldersAction.OnConfirmCreate("Corset"))

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.NavigateToUpgrade>(event)
        assertNull(styleRepository.lastCreatedFolderName)
    }

    @Test
    fun onConfirmCreate_blankName_doesNotCallRepo() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnCreateClick)
        vm.onAction(StyleFoldersAction.OnConfirmCreate("   "))

        assertNull(styleRepository.lastCreatedFolderName)
    }

    // ---------------------------------------------------------------------------
    // Rename
    // ---------------------------------------------------------------------------

    @Test
    fun onRenameClick_setsRenameTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnRenameClick(folder))

        assertEquals(folder, vm.state.value.renameTarget)
    }

    @Test
    fun onConfirmRename_callsRepo_andClearsTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()
        vm.onAction(StyleFoldersAction.OnRenameClick(folder))

        vm.onAction(StyleFoldersAction.OnConfirmRename("Wedding looks"))

        assertEquals("f1" to "Wedding looks", styleRepository.lastRenamedFolder)
        assertNull(vm.state.value.renameTarget)
    }

    // ---------------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------------

    @Test
    fun onDeleteClick_setsDeleteTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnDeleteClick(folder))

        assertEquals(folder, vm.state.value.deleteTarget)
    }

    @Test
    fun onConfirmDelete_callsRepo_andClearsTarget() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()
        vm.onAction(StyleFoldersAction.OnDeleteClick(folder))

        vm.onAction(StyleFoldersAction.OnConfirmDelete)

        assertEquals("f1", styleRepository.lastDeletedFolderId)
        assertNull(vm.state.value.deleteTarget)
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    @Test
    fun onFolderClick_null_emitsNavigateToFolderWithNullFolderId() = runTest {
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnFolderClick(null))

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.NavigateToFolder>(event)
        assertNull(event.folderId)
    }

    @Test
    fun onFolderClick_withId_emitsNavigateToFolderWithId() = runTest {
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnFolderClick("folder-42"))

        val event = vm.events.first()
        assertIs<StyleFoldersEvent.NavigateToFolder>(event)
        assertEquals("folder-42", event.folderId)
    }

    @Test
    fun onNavigateBack_emitsNavigateBack() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleFoldersAction.OnNavigateBack)
        assertIs<StyleFoldersEvent.NavigateBack>(vm.events.first())
    }

    @Test
    fun onUpgradeClick_emitsNavigateToUpgrade() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleFoldersAction.OnUpgradeClick)
        assertIs<StyleFoldersEvent.NavigateToUpgrade>(vm.events.first())
    }

    // ---------------------------------------------------------------------------
    // Error dismiss
    // ---------------------------------------------------------------------------

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.observeError = com.danzucker.stitchpad.core.domain.error.DataError.Network.UNKNOWN
        val vm = createViewModel()
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(StyleFoldersAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }

    // ---------------------------------------------------------------------------
    // Long-press action sheet
    // ---------------------------------------------------------------------------

    @Test
    fun onFolderLongPress_thenRename_setsRenameTarget_clearsActionSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()

        // Long-press opens the action sheet for the folder
        vm.onAction(StyleFoldersAction.OnFolderLongPress(folder))
        assertEquals("f1", vm.state.value.actionSheetFolder?.id)

        // Tapping Rename clears the action sheet and sets renameTarget
        vm.onAction(StyleFoldersAction.OnRenameClick(folder))
        assertEquals("f1", vm.state.value.renameTarget?.id)
        assertNull(vm.state.value.actionSheetFolder)
    }

    @Test
    fun onFolderLongPress_thenDelete_setsDeleteTarget_clearsActionSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnFolderLongPress(folder))
        assertEquals("f1", vm.state.value.actionSheetFolder?.id)

        vm.onAction(StyleFoldersAction.OnDeleteClick(folder))
        assertEquals("f1", vm.state.value.deleteTarget?.id)
        assertNull(vm.state.value.actionSheetFolder)
    }

    @Test
    fun onDismissFolderActionSheet_clearsActionSheet() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val folder = fakeFolder("f1")
        val vm = createViewModel()

        vm.onAction(StyleFoldersAction.OnFolderLongPress(folder))
        assertNotNull(vm.state.value.actionSheetFolder)

        vm.onAction(StyleFoldersAction.OnDismissFolderActionSheet)
        assertNull(vm.state.value.actionSheetFolder)
    }
}
