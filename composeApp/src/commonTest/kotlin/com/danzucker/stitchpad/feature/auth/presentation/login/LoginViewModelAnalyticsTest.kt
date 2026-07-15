package com.danzucker.stitchpad.feature.auth.presentation.login

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.data.FakePatternValidator
import com.danzucker.stitchpad.feature.auth.domain.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelAnalyticsTest {

    private lateinit var analytics: FakeAnalytics
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        analytics = FakeAnalytics()
        authRepository = FakeAuthRepository()
        viewModel = LoginViewModel(authRepository, FakePatternValidator(shouldMatch = true), analytics)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emailLoginSuccessLogsLoginWithEmailMethod() = runTest {
        authRepository.signUpWithEmail("ade@gmail.com", "pass123", "Ade Fashions")
        fillCredentials()
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "email")))
    }

    @Test
    fun emailLoginFailureLogsNothing() = runTest {
        authRepository.shouldReturnError = AuthError.INVALID_CREDENTIALS
        fillCredentials()
        viewModel.onAction(LoginAction.OnLoginClick)

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun googleSignInReturningUserLogsLoginWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.SignUp })
    }

    @Test
    fun googleSignInNewUserLogsSignUpWithGoogleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "google")))
        assertFalse(analytics.events.any { it is AnalyticsEvent.Login })
    }

    @Test
    fun appleSignInReturningUserLogsLoginWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = false
        viewModel.onAction(LoginAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.Login(method = "apple")))
    }

    @Test
    fun appleSignInNewUserLogsSignUpWithAppleMethod() = runTest {
        authRepository.ssoIsNewUser = true
        viewModel.onAction(LoginAction.OnAppleSignInClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp(method = "apple")))
    }

    @Test
    fun ssoFailureLogsNothing() = runTest {
        authRepository.shouldReturnError = AuthError.UNKNOWN
        viewModel.onAction(LoginAction.OnGoogleSignInClick)

        assertTrue(analytics.events.isEmpty())
    }

    // --- Helper ---

    private fun fillCredentials() {
        viewModel.onAction(LoginAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("pass123"))
    }
}
