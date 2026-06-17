package com.danzucker.stitchpad.feature.gift.presentation.redeem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.gift.domain.GiftRepository
import com.danzucker.stitchpad.feature.gift.presentation.toUiText
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_redeem_success

class RedeemGiftViewModel(
    private val giftRepository: GiftRepository,
    private val authRepository: AuthRepository,
    pendingDeepLink: PendingDeepLinkHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(RedeemGiftState())
    val state: StateFlow<RedeemGiftState> = _state.asStateFlow()

    private val _events = Channel<RedeemGiftEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // A claim deep link (https link.getstitchpad.com/claim?code= or
        // stitchpad://claim?code=) pre-fills the code and jumps straight to the
        // Accept sheet — one tap to claim. Consumed once so it doesn't re-trigger.
        val deepLinkCode = pendingDeepLink.consumeClaimGiftCode()
        if (deepLinkCode != null) {
            _state.update { it.copy(code = deepLinkCode, showAcceptSheet = true) }
        }
        // The Accept sheet confirms which account the gift lands on.
        viewModelScope.launch {
            val email = authRepository.getCurrentUser()?.email
            _state.update { it.copy(accountEmail = email) }
        }
    }

    fun onAction(action: RedeemGiftAction) {
        when (action) {
            is RedeemGiftAction.OnCodeChange ->
                if (!_state.value.isRedeeming) _state.update { it.copy(code = action.code) }
            RedeemGiftAction.OnRedeemClick ->
                if (_state.value.canRedeem) _state.update { it.copy(showAcceptSheet = true) }
            RedeemGiftAction.OnConfirmAccept -> redeem()
            RedeemGiftAction.OnDismissSheet ->
                if (!_state.value.isRedeeming) _state.update { it.copy(showAcceptSheet = false) }
            RedeemGiftAction.OnBack -> viewModelScope.launch { _events.send(RedeemGiftEvent.NavigateBack) }
        }
    }

    private fun redeem() {
        val code = normalize(_state.value.code)
        if (code.isBlank() || _state.value.isRedeeming) return
        _state.update { it.copy(isRedeeming = true) }
        viewModelScope.launch {
            when (val result = giftRepository.redeemGift(code)) {
                is Result.Success -> {
                    _state.update { it.copy(isRedeeming = false, showAcceptSheet = false) }
                    _events.send(
                        RedeemGiftEvent.Redeemed(UiText.StringResourceText(Res.string.gift_redeem_success)),
                    )
                }
                is Result.Error -> {
                    _state.update { it.copy(isRedeeming = false, showAcceptSheet = false) }
                    _events.send(RedeemGiftEvent.ShowSnackbar(result.error.toUiText()))
                }
            }
        }
    }

    private companion object {
        /** Gift codes are uppercase base32; normalize typed input (case + stray spaces). */
        fun normalize(raw: String): String = raw.uppercase().filter { !it.isWhitespace() }
    }
}
