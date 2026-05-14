package com.danzucker.stitchpad.feature.debug.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.debug.DebugSeeder
import com.danzucker.stitchpad.core.debug.DebugSessionActions
import com.danzucker.stitchpad.core.debug.DebugTestAccounts
import com.danzucker.stitchpad.core.debug.SeedResult
import com.danzucker.stitchpad.core.debug.SessionActionResult
import com.danzucker.stitchpad.core.presentation.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DebugMenuViewModel(
    private val seeder: DebugSeeder,
    private val sessionActions: DebugSessionActions,
    private val testAccountsConfigured: Boolean = DebugTestAccounts.isConfigured,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DebugMenuState(testAccountsConfigured = testAccountsConfigured)
    )
    val state = _state.asStateFlow()

    private val _events = Channel<DebugMenuEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: DebugMenuAction) {
        when (action) {
            DebugMenuAction.OnBackClick -> emit(DebugMenuEvent.NavigateBack)
            DebugMenuAction.OnSeedBrandNewClick -> runSeed(DebugScenario.BrandNew) { seeder.seedBrandNew() }
            DebugMenuAction.OnSeedActiveWorkshopClick -> runSeed(
                DebugScenario.ActiveWorkshop
            ) { seeder.seedActiveWorkshop() }
            DebugMenuAction.OnSeedAllReconnectClick -> runSeed(DebugScenario.AllReconnect) { seeder.seedAllReconnect() }
            DebugMenuAction.OnClearActiveScenarioClick -> {
                _state.update { it.copy(activeScenario = null) }
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Active state cleared")))
            }
            DebugMenuAction.OnResetOnboardingClick -> runJob {
                sessionActions.resetOnboardingFlags()
                emit(DebugMenuEvent.NavigateToSplash)
            }
            DebugMenuAction.OnSignOutClick -> runJob {
                val r = sessionActions.signOut()
                if (r is SessionActionResult.Success) {
                    emit(DebugMenuEvent.NavigateToLogin)
                } else {
                    emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Sign-out failed")))
                }
            }
            DebugMenuAction.OnSwitchToFolaClick -> runJob {
                handleSwitch(
                    sessionActions.switchAccount(
                        DebugTestAccounts.FOLA_EMAIL,
                        DebugTestAccounts.FOLA_PASSWORD,
                    )
                )
            }
            DebugMenuAction.OnSwitchToGabbyClick -> runJob {
                handleSwitch(
                    sessionActions.switchAccount(
                        DebugTestAccounts.GABBY_EMAIL,
                        DebugTestAccounts.GABBY_PASSWORD,
                    )
                )
            }
            DebugMenuAction.OnWipeDataClick -> runJob {
                val r = seeder.wipeAllData()
                when (r) {
                    SeedResult.Success -> {
                        _state.update { it.copy(activeScenario = null) }
                        emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Data wiped")))
                    }
                    is SeedResult.Failure ->
                        emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Wipe failed: ${r.reason}")))
                }
            }
        }
    }

    private fun runSeed(scenario: DebugScenario, block: suspend () -> SeedResult) = runJob {
        val r = block()
        val message = when (r) {
            SeedResult.Success -> {
                _state.update { it.copy(activeScenario = scenario) }
                UiText.DynamicString("Seed complete")
            }
            is SeedResult.Failure -> UiText.DynamicString("Seed failed: ${r.reason}")
        }
        emit(DebugMenuEvent.ShowSnackbar(message))
    }

    private fun runJob(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(isWorking = false) }
            }
        }
    }

    private fun handleSwitch(r: SessionActionResult) {
        when (r) {
            SessionActionResult.Success -> emit(DebugMenuEvent.NavigateToSplash)
            SessionActionResult.ConfigurationMissing ->
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Test creds not configured")))
            is SessionActionResult.Failure ->
                emit(DebugMenuEvent.ShowSnackbar(UiText.DynamicString("Switch failed: ${r.reason}")))
        }
    }

    private fun emit(event: DebugMenuEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
