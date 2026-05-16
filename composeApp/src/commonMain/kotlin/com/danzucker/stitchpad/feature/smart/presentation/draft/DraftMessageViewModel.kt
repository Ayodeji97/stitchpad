package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_no_whatsapp_helper
import stitchpad.composeapp.generated.resources.smart_error_invalid_input
import stitchpad.composeapp.generated.resources.smart_error_network
import stitchpad.composeapp.generated.resources.smart_error_service_unavailable
import stitchpad.composeapp.generated.resources.smart_error_unknown

@Suppress("TooManyFunctions")
class DraftMessageViewModel(
    private val repository: SmartRepository,
    private val orderProvider: OpenOrdersProvider,
    private val customerProvider: CustomerSearchProvider,
    private val connectivity: StateFlow<Boolean>,
    private val usageStore: SmartUsageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DraftMessageState(
            isOnline = connectivity.value,
            // Seed from the cross-feature cache so the chip is filled
            // immediately if the user has already drafted this session.
            remainingFreeQuota = usageStore.remainingFreeQuota.value,
        )
    )
    val state: StateFlow<DraftMessageState> = _state.asStateFlow()

    private val _events = Channel<DraftMessageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.collectLatest { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }
    }

    fun onAction(action: DraftMessageAction) {
        when (action) {
            DraftMessageAction.LoadCustomers -> loadCustomers()
            is DraftMessageAction.SelectCustomer -> selectCustomer(action.customer)
            is DraftMessageAction.SelectOrder -> _state.update {
                it.copy(order = action.order).clearStaleDraft()
            }
            is DraftMessageAction.SelectIntent -> _state.update {
                it.copy(intent = action.intent).clearStaleDraft()
            }
            is DraftMessageAction.ToggleLanguage -> _state.update {
                it.copy(language = action.language).clearStaleDraft()
            }
            is DraftMessageAction.UpdateCustomNotes -> _state.update {
                it.copy(customNotes = action.notes).clearStaleDraft()
            }
            DraftMessageAction.GenerateDraft -> generate()
            is DraftMessageAction.EditDraft -> _state.update {
                if (it.generationState is GenerationState.Success) {
                    it.copy(generationState = GenerationState.Success(action.text))
                } else {
                    it
                }
            }
            DraftMessageAction.SendViaWhatsApp -> sendViaWhatsApp()
            DraftMessageAction.CopyDraft -> copyDraft()
        }
    }

    /**
     * After a draft succeeds, any change to the request inputs (customer,
     * order, intent, language, notes) makes the previewed text stale —
     * keeping `Success` around would let the user send a previous
     * customer's draft to the newly selected customer's WhatsApp number.
     */
    private fun DraftMessageState.clearStaleDraft(): DraftMessageState =
        if (generationState is GenerationState.Success) {
            copy(generationState = GenerationState.Idle)
        } else {
            this
        }

    private fun loadCustomers() {
        viewModelScope.launch {
            val customers = customerProvider.search("")
            _state.update { it.copy(customerOptions = customers) }
        }
    }

    private fun selectCustomer(customer: CustomerSummary) {
        _state.update {
            it.copy(customer = customer, order = null, orderOptions = emptyList()).clearStaleDraft()
        }
        viewModelScope.launch {
            val orders = orderProvider.openOrdersFor(customer.id)
            _state.update {
                it.copy(
                    orderOptions = orders,
                    // Auto-select when the customer has exactly one open order
                    // (common case — saves a tap into the picker sheet).
                    order = orders.singleOrNull(),
                )
            }
        }
    }

    @Suppress("ReturnCount")
    private fun generate() {
        val s = _state.value
        val customer = s.customer ?: return
        val order = s.order ?: return
        val intent = s.intent ?: return
        if (!s.isOnline) return

        _state.update { it.copy(generationState = GenerationState.Generating) }
        viewModelScope.launch {
            val req = DraftMessageRequest(
                customerId = customer.id,
                orderId = order.id,
                intent = intent,
                language = s.language,
                customNotes = s.customNotes.takeIf { it.isNotBlank() },
            )
            when (val result = repository.draftMessage(req)) {
                is Result.Success -> {
                    _state.update {
                        it.copy(
                            generationState = GenerationState.Success(result.data.draftText),
                            remainingFreeQuota = result.data.remainingFreeQuota,
                        )
                    }
                    // Publish to the cross-feature cache so the dashboard
                    // chip stays in sync without an extra server call.
                    usageStore.update(result.data.remainingFreeQuota)
                }
                is Result.Error -> handleError(result.error)
            }
        }
    }

    private suspend fun handleError(error: SmartError) {
        _state.update { it.copy(generationState = GenerationState.Idle) }
        when (error) {
            SmartError.FreeTierExhausted -> _events.send(DraftMessageEvent.ShowUpgradeSheet)
            SmartError.InvalidInput -> {
                _state.update { it.copy(order = null) }
                _events.send(
                    DraftMessageEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.smart_error_invalid_input)
                    )
                )
            }
            SmartError.ServiceUnavailable -> _events.send(
                DraftMessageEvent.ShowSnackbar(
                    UiText.StringResourceText(Res.string.smart_error_service_unavailable)
                )
            )
            SmartError.Network -> _events.send(
                DraftMessageEvent.ShowSnackbar(UiText.StringResourceText(Res.string.smart_error_network))
            )
            SmartError.Unknown -> _events.send(
                DraftMessageEvent.ShowSnackbar(UiText.StringResourceText(Res.string.smart_error_unknown))
            )
        }
    }

    private fun sendViaWhatsApp() {
        val s = _state.value
        val draft = (s.generationState as? GenerationState.Success)?.draftText ?: return
        val phone = s.customer?.whatsappNumber
        if (phone == null) {
            viewModelScope.launch {
                _events.send(
                    DraftMessageEvent.ShowSnackbar(
                        UiText.StringResourceText(Res.string.draft_message_no_whatsapp_helper)
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _events.send(DraftMessageEvent.LaunchWhatsApp(phoneE164 = phone, message = draft))
        }
    }

    private fun copyDraft() {
        val draft = (_state.value.generationState as? GenerationState.Success)?.draftText ?: return
        viewModelScope.launch {
            _events.send(DraftMessageEvent.CopyToClipboard(draft))
        }
    }
}
