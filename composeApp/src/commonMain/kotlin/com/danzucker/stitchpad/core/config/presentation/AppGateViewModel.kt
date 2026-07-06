package com.danzucker.stitchpad.core.config.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.config.domain.AppGate
import com.danzucker.stitchpad.core.config.domain.AppGateDecision
import com.danzucker.stitchpad.core.config.domain.repository.AppConfigRepository
import com.danzucker.stitchpad.core.domain.currentAppBuildNumber
import com.danzucker.stitchpad.core.domain.currentPlatformName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppGateState(
    val decision: AppGateDecision = AppGateDecision.Allowed,
)

/**
 * App-level gate: maps the live remote [AppConfigRepository.config] into an
 * [AppGateDecision]. Fail-open by construction — the initial value is
 * [AppGateDecision.Allowed], so the app renders normally until (and unless) the
 * remote config actively says to block.
 *
 * [isIos] and [currentBuild] carry Kotlin defaults sourced from platform globals,
 * so this VM is registered with the lambda Koin form (not `viewModelOf`, which
 * can't skip default params) and can be unit-tested with injected fakes.
 */
class AppGateViewModel(
    appConfigRepository: AppConfigRepository,
    isIos: Boolean = currentPlatformName == "ios",
    currentBuild: Int? = currentAppBuildNumber,
) : ViewModel() {

    val state: StateFlow<AppGateState> = appConfigRepository.config
        .map { config -> AppGateState(AppGate.evaluate(config, isIos, currentBuild)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppGateState(),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
