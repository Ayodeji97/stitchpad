package com.danzucker.stitchpad.feature.goals.presentation.setup

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.goals.data.FakeWeeklyGoalRepository
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
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
class GoalSetupViewModelTest {

    private lateinit var goalRepository: FakeWeeklyGoalRepository
    private lateinit var authRepository: FakeAuthRepository

    private val fixedNow = 1_700_000_000_000L

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        goalRepository = FakeWeeklyGoalRepository()
        authRepository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun signIn() {
        authRepository.signUpWithEmail("t@t.com", "pass", "Ade Bello")
    }

    private fun TestScope.createViewModel(): GoalSetupViewModel {
        val vm = GoalSetupViewModel(
            weeklyGoalRepository = goalRepository,
            authRepository = authRepository,
            nowMillis = { fixedNow }
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    // --- Prefill ---

    @Test
    fun prefill_populatesInput_fromExistingGoal() = runTest {
        signIn()
        goalRepository.storedGoal = WeeklyGoal(targetAmount = 250_000.0, updatedAt = 0L)

        val vm = createViewModel()

        assertEquals("250000", vm.state.value.targetAmountInput)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun prefill_leavesInputBlank_whenNoGoalSaved() = runTest {
        signIn()

        val vm = createViewModel()

        assertEquals("", vm.state.value.targetAmountInput)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun prefill_clearsLoading_evenWhenRepositoryErrors() = runTest {
        signIn()
        goalRepository.shouldReturnError = DataError.Network.UNKNOWN

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        // Repository error during prefill is silent — the input stays blank and the screen
        // is responsive. Save flow surfaces its own errors.
        assertEquals("", vm.state.value.targetAmountInput)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun prefill_doesNotOverwriteUserEdits_onSubsequentEmissions() = runTest {
        // Regression for the loadGoal-clobber fix: a Firestore re-emission after the
        // initial prefill must not stomp on what the user has typed.
        signIn()
        goalRepository.storedGoal = WeeklyGoal(targetAmount = 100_000.0, updatedAt = 0L)
        val vm = createViewModel()
        assertEquals("100000", vm.state.value.targetAmountInput)

        vm.onAction(GoalSetupAction.OnTargetAmountChange("9"))
        // Simulate Firestore re-emitting (e.g. background sync) with a different value.
        goalRepository.storedGoal = WeeklyGoal(targetAmount = 500_000.0, updatedAt = 0L)

        assertEquals("9", vm.state.value.targetAmountInput)
    }

    // --- Input handling + canSave ---

    @Test
    fun onTargetAmountChange_updatesInput() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(GoalSetupAction.OnTargetAmountChange("12345"))

        assertEquals("12345", vm.state.value.targetAmountInput)
    }

    @Test
    fun onQuickPickClick_setsInputToAmount() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(GoalSetupAction.OnQuickPickClick(300_000L))

        assertEquals("300000", vm.state.value.targetAmountInput)
    }

    // --- Save ---

    @Test
    fun save_success_emitsNavigateBack_andPersistsGoal() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(GoalSetupAction.OnTargetAmountChange("400000"))

        vm.onAction(GoalSetupAction.OnSaveClick)

        val event = vm.events.first()
        assertEquals(GoalSetupEvent.NavigateBack, event)
        val saved = goalRepository.lastSavedGoal
        assertNotNull(saved)
        assertEquals(400_000.0, saved.targetAmount)
        assertEquals(fixedNow, saved.updatedAt)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun save_error_setsErrorMessage_andClearsSaving() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(GoalSetupAction.OnTargetAmountChange("400000"))
        goalRepository.shouldReturnError = DataError.Network.NO_INTERNET

        vm.onAction(GoalSetupAction.OnSaveClick)

        assertNotNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun save_doesNothing_whenInputIsBlank() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(GoalSetupAction.OnSaveClick)

        assertNull(goalRepository.lastSavedGoal)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun save_doesNothing_whenInputIsNotNumeric() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(GoalSetupAction.OnTargetAmountChange("abc"))

        vm.onAction(GoalSetupAction.OnSaveClick)

        assertNull(goalRepository.lastSavedGoal)
    }

    // --- Errors / dismissal ---

    @Test
    fun onErrorDismiss_clearsErrorMessage() = runTest {
        signIn()
        val vm = createViewModel()
        vm.onAction(GoalSetupAction.OnTargetAmountChange("400000"))
        goalRepository.shouldReturnError = DataError.Network.NO_INTERNET
        vm.onAction(GoalSetupAction.OnSaveClick)
        assertNotNull(vm.state.value.errorMessage)

        vm.onAction(GoalSetupAction.OnErrorDismiss)

        assertNull(vm.state.value.errorMessage)
    }

    // --- Navigation ---

    @Test
    fun onBackClick_emitsNavigateBack() = runTest {
        signIn()
        val vm = createViewModel()

        vm.onAction(GoalSetupAction.OnBackClick)

        assertIs<GoalSetupEvent.NavigateBack>(vm.events.first())
    }

    @Test
    fun noAuthUser_clearsLoading_andDoesNotPrefill() = runTest {
        // No signIn() — getCurrentUser returns null.
        goalRepository.storedGoal = WeeklyGoal(targetAmount = 250_000.0, updatedAt = 0L)

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals("", vm.state.value.targetAmountInput)
    }

    @Test
    fun save_doesNothing_whenNoAuthUser() = runTest {
        // No signIn().
        val vm = createViewModel()
        vm.onAction(GoalSetupAction.OnTargetAmountChange("400000"))

        vm.onAction(GoalSetupAction.OnSaveClick)

        assertNull(goalRepository.lastSavedGoal)
        assertTrue(vm.state.value.errorMessage == null)
    }
}
