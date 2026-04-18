package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeStyleRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun TestScope.createViewModel(
        customerId: String = "customer-1",
    ): StyleGalleryViewModel {
        val vm = StyleGalleryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("customerId" to customerId)),
            styleRepository = styleRepository,
            authRepository = authRepository,
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

    // --- Observe styles ---

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
    fun onStyleClick_emitsNavigateToEditStyle_withBothIds() = runTest {
        val vm = createViewModel()
        vm.onAction(StyleGalleryAction.OnStyleClick(fakeStyle(id = "style-42")))

        val event = vm.events.first()
        assertIs<StyleGalleryEvent.NavigateToEditStyle>(event)
        assertEquals("customer-1", event.customerId)
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
        assertFalse(vm.state.value.showDeleteDialog)
        assertNull(vm.state.value.styleToDelete)
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
