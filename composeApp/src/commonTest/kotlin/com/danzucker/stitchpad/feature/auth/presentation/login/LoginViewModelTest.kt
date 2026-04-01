package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var viewModel: LoginViewModel
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var emailValidator: FakePatternValidator

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = FakeAuthRepository()
        emailValidator = FakePatternValidator(shouldMatch = true)
        viewModel = LoginViewModel(authRepository, emailValidator)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = viewModel.state.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertFalse(state.isPasswordVisible)
    }

    @Test
    fun onEmailChangeUpdatesState() {
        viewModel.onAction(LoginAction.OnEmailChange("test@test.com"))
        assertEquals("test@test.com", viewModel.state.value.email)
    }

    @Test
    fun onPasswordChangeUpdatesState() {
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
        assertEquals("pass123", viewModel.state.value.password)
    }

    @Test
    fun onTogglePasswordVisibilityTogglesState() {
        assertFalse(viewModel.state.value.isPasswordVisible)
        viewModel.onAction(LoginAction.OnTogglePasswordVisibility)
        assertTrue(viewModel.state.value.isPasswordVisible)
    }

    @Test
    fun loginWithInvalidEmailShowsError() {
        emailValidator = FakePatternValidator(shouldMatch = false)
        viewModel = LoginViewModel(authRepository, emailValidator)

        viewModel.onAction(LoginAction.OnEmailChange("bad"))
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(viewModel.state.value.emailError != null)
    }

    @Test
    fun loginWithShortPasswordShowsError() {
        viewModel.onAction(LoginAction.OnEmailChange("test@test.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("12345"))
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(viewModel.state.value.passwordError != null)
    }

    @Test
    fun successfulLoginEmitsNavigateToHome() = runTest {
        // First sign up so there's a user
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        viewModel.onAction(LoginAction.OnEmailChange("test@test.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
        viewModel.onAction(LoginAction.OnLoginClick)

        val event = viewModel.events.first()
        assertIs<LoginEvent.NavigateToHome>(event)
    }

    @Test
    fun failedLoginEmitsShowError() = runTest {
        authRepository.shouldReturnError = AuthError.INVALID_CREDENTIALS

        viewModel.onAction(LoginAction.OnEmailChange("test@test.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
        viewModel.onAction(LoginAction.OnLoginClick)

        val event = viewModel.events.first()
        assertIs<LoginEvent.ShowError>(event)
    }

    @Test
    fun signUpClickEmitsNavigateToSignUp() = runTest {
        viewModel.onAction(LoginAction.OnSignUpClick)

        val event = viewModel.events.first()
        assertIs<LoginEvent.NavigateToSignUp>(event)
    }
}
