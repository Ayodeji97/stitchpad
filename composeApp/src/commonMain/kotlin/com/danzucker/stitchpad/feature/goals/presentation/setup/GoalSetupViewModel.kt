package com.danzucker.stitchpad.feature.goals.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.goals.domain.model.WeeklyGoal
import com.danzucker.stitchpad.feature.goals.domain.repository.WeeklyGoalRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.goals_setup_error_save
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class GoalSetupViewModel(
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val authRepository: AuthRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private var hasLoadedInitialData = false
    private val _state = MutableStateFlow(GoalSetupState())

    private val _events = Channel<GoalSetupEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                loadGoal()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = GoalSetupState()
        )

    fun onAction(action: GoalSetupAction) {
        when (action) {
            is GoalSetupAction.OnTargetAmountChange ->
                _state.update { it.copy(targetAmountInput = action.value) }
            is GoalSetupAction.OnQuickPickClick ->
                _state.update { it.copy(targetAmountInput = action.amount.toString()) }
            GoalSetupAction.OnSaveClick -> save()
            GoalSetupAction.OnBackClick -> emit(GoalSetupEvent.NavigateBack)
            GoalSetupAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadGoal() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            weeklyGoalRepository.observeWeeklyGoal(user.id).collect { result ->
                val current = (result as? Result.Success)?.data
                _state.update {
                    it.copy(
                        isLoading = false,
                        targetAmountInput = current?.targetAmount?.roundToLong()?.toString()
                            ?: it.targetAmountInput
                    )
                }
            }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        val target = current.targetAmountInput.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            _state.update { it.copy(isSaving = true) }
            val result = weeklyGoalRepository.setWeeklyGoal(
                userId = user.id,
                goal = WeeklyGoal(targetAmount = target, updatedAt = nowMillis())
            )
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is Result.Success -> {
                    emit(GoalSetupEvent.GoalSaved)
                    emit(GoalSetupEvent.NavigateBack)
                }
                is Result.Error -> _state.update {
                    it.copy(
                        errorMessage = UiText.StringResourceText(Res.string.goals_setup_error_save)
                    )
                }
            }
        }
    }

    private fun emit(event: GoalSetupEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
