package com.danzucker.stitchpad.feature.auth.presentation.signup

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
class SignUpViewModelAnalyticsTest {

    private lateinit var analytics: FakeAnalytics
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var emailValidator: FakePatternValidator
    private lateinit var viewModel: SignUpViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        analytics = FakeAnalytics()
        authRepository = FakeAuthRepository()
        emailValidator = FakePatternValidator(shouldMatch = true)
        viewModel = SignUpViewModel(authRepository, emailValidator, analytics, FakeReferralAttribution())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emailSignUpSuccessLogsSignUpEvent() = runTest {
        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertTrue(analytics.events.contains(AnalyticsEvent.SignUp))
    }

    @Test
    fun emailSignUpFailureDoesNotLogSignUpEvent() = runTest {
        authRepository.shouldReturnError = AuthError.EMAIL_ALREADY_IN_USE
        fillValidForm()
        viewModel.onAction(SignUpAction.OnSignUpClick)

        assertFalse(analytics.events.contains(AnalyticsEvent.SignUp))
    }

    @Test
    fun googleSignInSuccessDoesNotLogSignUpEvent() = runTest {
        viewModel.onAction(SignUpAction.OnGoogleSignInClick)

        assertFalse(analytics.events.contains(AnalyticsEvent.SignUp))
    }

    @Test
    fun appleSignInSuccessDoesNotLogSignUpEvent() = runTest {
        viewModel.onAction(SignUpAction.OnAppleSignInClick)

        assertFalse(analytics.events.contains(AnalyticsEvent.SignUp))
    }

    // --- Helper ---

    private fun fillValidForm() {
        viewModel.onAction(SignUpAction.OnDisplayNameChange("Ade Fashions"))
        viewModel.onAction(SignUpAction.OnEmailChange("ade@gmail.com"))
        viewModel.onAction(SignUpAction.OnPasswordChange("pass123"))
        viewModel.onAction(SignUpAction.OnConfirmPasswordChange("pass123"))
        viewModel.onAction(SignUpAction.OnTermsToggle)
    }
}
