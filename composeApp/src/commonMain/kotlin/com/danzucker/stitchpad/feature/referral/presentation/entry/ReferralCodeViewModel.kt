package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.referral.domain.ReferralPreferencesStore
import com.danzucker.stitchpad.feature.referral.domain.ReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.ReferralSource
import com.danzucker.stitchpad.feature.referral.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_applied

class ReferralCodeViewModel(
    private val referralRepository: ReferralRepository,
    private val preferences: ReferralPreferencesStore,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferralCodeState())
    val state = _state.asStateFlow()

    private val _events = Channel<ReferralCodeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: ReferralCodeAction) {
        when (action) {
            is ReferralCodeAction.OnCodeChange -> _state.update { it.copy(codeInput = action.value) }
            ReferralCodeAction.OnApplyClick -> apply()
            ReferralCodeAction.OnBackClick -> emit(ReferralCodeEvent.NavigateBack)
        }
    }

    private fun apply() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val deviceHash = preferences.getOrCreateDeviceId()
            val result = referralRepository.recordAttribution(
                code = current.code,
                deviceHash = deviceHash,
                source = ReferralSource.MANUAL,
            )
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Result.Success -> {
                    // Keep local capture state consistent so the auto-capture
                    // coordinator doesn't re-attempt on next launch.
                    preferences.setAttributed()
                    if (!result.data.alreadyAttributed) {
                        analytics.logEvent(
                            AnalyticsEvent.ReferralCodeApplied(
                                source = ReferralSource.MANUAL.wire,
                                surface = "settings",
                            ),
                        )
                    }
                    emit(ReferralCodeEvent.ApplySucceeded(UiText.StringResourceText(Res.string.referral_code_applied)))
                }
                is Result.Error -> emit(ReferralCodeEvent.ShowMessage(result.error.toUiText()))
            }
        }
    }

    private fun emit(event: ReferralCodeEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
