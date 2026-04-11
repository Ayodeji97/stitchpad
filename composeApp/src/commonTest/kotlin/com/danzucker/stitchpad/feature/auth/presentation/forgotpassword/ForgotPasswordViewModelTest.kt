package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private lateinit var authRepository: FakeAuthRepository

    private fun buildViewModel(emailValid: Boolean = true): ForgotPasswordViewModel {
        return ForgotPasswordViewModel(authRepository, FakePatternValidator(emailValid))
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = buildViewModel().state.value
        assertEquals("", state.email)
        assertNull(state.emailError)
        assertTrue(!state.isLoading)
        assertTrue(!state.isSuccess)
    }

    @Test
    fun onEmailChangeUpdatesState() {
        val viewModel = buildViewModel()
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("test@test.com"))
        assertEquals("test@test.com", viewModel.state.value.email)
    }

    @Test
    fun sendWithInvalidEmailShowsError() {
        val viewModel = buildViewModel(emailValid = false)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("not-an-email"))
        viewModel.onAction(ForgotPasswordAction.OnSendClick)

        assertNull(authRepository.resetEmailSentTo)
        assertTrue(viewModel.state.value.emailError != null)
    }

    @Test
    fun sendWithValidEmailSetsSuccessState() = runTest {
        val viewModel = buildViewModel(emailValid = true)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("tailor@gmail.com"))
        viewModel.onAction(ForgotPasswordAction.OnSendClick)

        assertTrue(viewModel.state.value.isSuccess)
        assertEquals("tailor@gmail.com", authRepository.resetEmailSentTo)
    }

    @Test
    fun sendWithUserNotFoundStillShowsSuccess() = runTest {
        authRepository.shouldReturnError = AuthError.USER_NOT_FOUND
        val viewModel = buildViewModel(emailValid = true)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("unknown@gmail.com"))
        viewModel.onAction(ForgotPasswordAction.OnSendClick)

        assertTrue(viewModel.state.value.isSuccess)
    }

    @Test
    fun sendWithNetworkErrorEmitsShowError() = runTest {
        authRepository.shouldReturnError = AuthError.NETWORK_ERROR
        val viewModel = buildViewModel(emailValid = true)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("tailor@gmail.com"))
        viewModel.onAction(ForgotPasswordAction.OnSendClick)

        val event = viewModel.events.first()
        assertIs<ForgotPasswordEvent.ShowError>(event)
    }

    @Test
    fun backToLoginEmitsNavigateToLogin() = runTest {
        val viewModel = buildViewModel()
        viewModel.onAction(ForgotPasswordAction.OnBackToLoginClick)

        val event = viewModel.events.first()
        assertIs<ForgotPasswordEvent.NavigateToLogin>(event)
    }

    @Test
    fun emailChangeAfterErrorClearsError() {
        val viewModel = buildViewModel(emailValid = false)
        viewModel.onAction(ForgotPasswordAction.OnEmailChange("bad"))
        viewModel.onAction(ForgotPasswordAction.OnSendClick)
        assertTrue(viewModel.state.value.emailError != null)

        viewModel.onAction(ForgotPasswordAction.OnEmailChange("tailor@gmail.com"))
        assertNull(viewModel.state.value.emailError)
    }
}
