package com.danzucker.stitchpad.feature.auth.presentation.signup

import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SignUpViewModelTest {

    private lateinit var viewModel: SignUpViewModel
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var emailValidator: FakePatternValidator

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = FakeAuthRepository()
        emailValidator = FakePatternValidator(shouldMatch = true)
        viewModel = SignUpViewModel(authRepository, emailValidator)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- State management ---

    @Test
    fun initialStateIsEmpty() {
        val state = viewModel.state.value
        assertEquals("", state.displayName)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isPasswordVisible)
        assertFalse(state.isLoading)
        assertNull(state.displayNameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
    }

    @Test
    fun onDisplayNameChangeUpdatesState() {
        viewModel.onAction(SignUpAction.OnDisplayNameChange("Ade Fashions"))
        assertEquals("Ade Fashions", viewModel.state.value.displayName)
    }

    @Test
    fun onEmailChangeUpdatesState() {
        viewModel.onAction(SignUpAction.OnEmailChange("ade@gmail.com"))
        assertEquals("ade@gmail.com", viewModel.state.value.email)
    }

    @Test
    fun onPasswordChangeUpdatesState() {
        viewModel.onAction(SignUpAction.OnPasswordChange("pass123"))
        assertEquals("pass123", viewModel.state.value.password)
    }

    @Test
    fun onConfirmPasswordChangeUpdatesState() {
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("pass123"))
        assertEquals("pass123", viewModel.state.value.confirmPassword)
    }

    @Test
    fun onTogglePasswordVisibilityTogglesState() {
        assertFalse(viewModel.state.value.isPasswordVisible)
        viewModel.onAction(SignUpAction.OnTogglePasswordVisibility)
        assertTrue(viewModel.state.value.isPasswordVisible)
        viewModel.onAction(SignUpAction.OnTogglePasswordVisibility)
        assertFalse(viewModel.state.value.isPasswordVisible)
    }

    // --- Validation errors ---

    @Test
    fun signUpWithBlankDisplayNameShowsError() {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnDisplayNameChange(""))
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertNotNull(viewModel.state.value.displayNameError)
    }

    @Test
    fun signUpWithInvalidEmailShowsError() {
        emailValidator = FakePatternValidator(shouldMatch = false)
        viewModel = SignUpViewModel(authRepository, emailValidator)

        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertNotNull(viewModel.state.value.emailError)
    }

    @Test
    fun signUpWithShortPasswordShowsError() {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnPasswordChange("12345"))
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("12345"))
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertNotNull(viewModel.state.value.passwordError)
    }

    @Test
    fun signUpWithMismatchedPasswordsShowsError() {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("different"))
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertNotNull(viewModel.state.value.confirmPasswordError)
    }

    @Test
    fun signUpWithMultipleErrorsSetsAllErrors() {
        emailValidator = FakePatternValidator(shouldMatch = false)
        viewModel = SignUpViewModel(authRepository, emailValidator)

        // All fields invalid: blank name, bad email, short password, mismatched confirm
        viewModel.onAction(SignUpAction.OnPasswordChange("123"))
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("456"))
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertNotNull(viewModel.state.value.displayNameError)
        assertNotNull(viewModel.state.value.emailError)
        assertNotNull(viewModel.state.value.passwordError)
        assertNotNull(viewModel.state.value.confirmPasswordError)
    }

    // --- Field change clears error ---

    @Test
    fun displayNameChangeClearsError() {
        viewModel.onAction(SignUpAction.OnSignUpClick)
        assertNotNull(viewModel.state.value.displayNameError)

        viewModel.onAction(SignUpAction.OnDisplayNameChange("Ade"))
        assertNull(viewModel.state.value.displayNameError)
    }

    @Test
    fun emailChangeClearsError() {
        emailValidator = FakePatternValidator(shouldMatch = false)
        viewModel = SignUpViewModel(authRepository, emailValidator)
        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)
        assertNotNull(viewModel.state.value.emailError)

        viewModel.onAction(SignUpAction.OnEmailChange("new@test.com"))
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun passwordChangeClearsError() {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnPasswordChange("123"))
        viewModel.onAction(SignUpAction.OnSignUpClick)
        assertNotNull(viewModel.state.value.passwordError)

        viewModel.onAction(SignUpAction.OnPasswordChange("newpass123"))
        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun confirmPasswordChangeClearsError() {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("wrong"))
        viewModel.onAction(SignUpAction.OnSignUpClick)
        assertNotNull(viewModel.state.value.confirmPasswordError)

        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("pass123"))
        assertNull(viewModel.state.value.confirmPasswordError)
    }

    // --- Async flows ---

    @Test
    fun successfulSignUpEmitsNavigateToHome() = runTest {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)

        val event = viewModel.events.first()
        assertIs<SignUpEvent.NavigateToHome>(event)
    }

    @Test
    fun failedSignUpEmitsShowError() = runTest {
        authRepository.shouldReturnError = AuthError.EMAIL_ALREADY_IN_USE

        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)

        val event = viewModel.events.first()
        assertIs<SignUpEvent.ShowError>(event)
    }

    @Test
    fun loginClickEmitsNavigateToLogin() = runTest {
        viewModel.onAction(SignUpAction.OnLoginClick)

        val event = viewModel.events.first()
        assertIs<SignUpEvent.NavigateToLogin>(event)
    }

    // --- Helper ---

    private fun fillValidForm() {
        viewModel.onAction(SignUpAction.OnDisplayNameChange("Ade Fashions"))
        viewModel.onAction(SignUpAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(SignUpAction.OnPasswordChange("pass123"))
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("pass123"))
    }
}
