package com.danzucker.stitchpad.feature.gift.presentation.sharelink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.gift.domain.GiftRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShareGiftLinkViewModel(
    private val giftRepository: GiftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareGiftLinkState())
    val state: StateFlow<ShareGiftLinkState> = _state.asStateFlow()

    private val _events = Channel<ShareGiftLinkEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun onAction(action: ShareGiftLinkAction) {
        when (action) {
            ShareGiftLinkAction.OnCopyClick -> {
                val url = _state.value.link?.url ?: return
                emit(ShareGiftLinkEvent.CopyToClipboard(url))
            }
            ShareGiftLinkAction.OnShareClick -> {
                val url = _state.value.link?.url ?: return
                emit(ShareGiftLinkEvent.ShareViaWhatsApp(url))
            }
            ShareGiftLinkAction.OnRetry -> load()
            ShareGiftLinkAction.OnBack -> emit(ShareGiftLinkEvent.NavigateBack)
        }
    }

    private fun emit(event: ShareGiftLinkEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            when (val result = giftRepository.getOrCreateGiftLink()) {
                is Result.Success ->
                    _state.update { it.copy(isLoading = false, link = result.data, hasError = false) }
                is Result.Error ->
                    _state.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }
}
