package com.danzucker.stitchpad.feature.style.presentation.form

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
class StyleFormViewModelTest {

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
        styleId: String? = null,
    ): StyleFormViewModel {
        val args = buildMap {
            put("customerId", customerId)
            if (styleId != null) put("styleId", styleId)
        }
        val vm = StyleFormViewModel(
            savedStateHandle = SavedStateHandle(args),
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

    // --- Initial state ---

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
    fun onPhotoPicked_underLimit_setsSelectedPhotoBytes() = runTest {
        val vm = createViewModel()
        val bytes = ByteArray(1024) { it.toByte() }

        vm.onAction(StyleFormAction.OnPhotoPicked(bytes))

        assertNotNull(vm.state.value.selectedPhotoBytes)
        assertEquals(1024, vm.state.value.selectedPhotoBytes?.size)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun onPhotoPicked_overLimit_setsErrorMessage_andDoesNotUpdatePhoto() = runTest {
        val vm = createViewModel()
        val tooLarge = ByteArray(5 * 1024 * 1024 + 1)

        vm.onAction(StyleFormAction.OnPhotoPicked(tooLarge))

        assertNotNull(vm.state.value.errorMessage)
        assertNull(vm.state.value.selectedPhotoBytes)
    }

    // --- Save: create flow ---

    @Test
    fun onSaveClick_createMode_blankDescription_doesNotCallRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(10)))
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
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(10) { it.toByte() }))

        vm.onAction(StyleFormAction.OnSaveClick)
        val event = vm.events.first()

        assertEquals("Red agbada", styleRepository.lastCreatedDescription)
        assertNotNull(styleRepository.lastCreatedPhotoBytes)
        assertIs<StyleFormEvent.NavigateBack>(event)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_createMode_repositoryError_setsErrorMessage() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        styleRepository.operationError = DataError.Network.UNKNOWN
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada"))
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(10)))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun onSaveClick_createMode_noAuthUser_doesNotCallRepository() = runTest {
        // no signUp
        val vm = createViewModel()
        vm.onAction(StyleFormAction.OnDescriptionChange("Red agbada"))
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(10)))

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
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(10)))

        vm.onAction(StyleFormAction.OnSaveClick)

        assertNull(styleRepository.lastCreatedDescription)
        assertNull(styleRepository.lastUpdatedStyle)
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
        vm.onAction(StyleFormAction.OnPhotoPicked(newBytes))

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
        vm.onAction(StyleFormAction.OnPhotoPicked(ByteArray(5 * 1024 * 1024 + 1)))
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(StyleFormAction.OnErrorDismiss)

        assertNull(vm.state.value.errorMessage)
    }
}
