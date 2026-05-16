package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.smartinfra.domain.language.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary

data class DraftMessageState(
    val customer: CustomerSummary? = null,
    val customerOptions: List<CustomerSummary> = emptyList(),
    val orderOptions: List<OrderSummary> = emptyList(),
    val order: OrderSummary? = null,
    val intent: DraftIntent? = null,
    val language: DraftLanguage = DraftLanguage.English,
    val customNotes: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val remainingFreeQuota: Int? = null,
    val isOnline: Boolean = true,
) {
    val canGenerate: Boolean
        get() = customer != null &&
            order != null &&
            intent != null &&
            isOnline &&
            generationState !is GenerationState.Generating

    /**
     * Out of free drafts according to the *process-local* counter. Drives an
     * inline hint near the Generate CTA so the user knows the next tap will
     * hit the Upgrade sheet, but it is NOT a hard gate — the cache doesn't
     * track month rollover, so blocking on it would trap a user whose
     * server-side counter has actually reset into the next month. The server
     * is the source of truth.
     */
    val isOutOfFreeDrafts: Boolean
        get() = remainingFreeQuota == 0
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Generating : GenerationState
    data class Success(val draftText: String) : GenerationState
}

sealed interface DraftMessageAction {
    data object LoadCustomers : DraftMessageAction
    data class SelectCustomer(val customer: CustomerSummary) : DraftMessageAction
    data class SelectOrder(val order: OrderSummary) : DraftMessageAction
    data class SelectIntent(val intent: DraftIntent) : DraftMessageAction
    data class ToggleLanguage(val language: DraftLanguage) : DraftMessageAction
    data class UpdateCustomNotes(val notes: String) : DraftMessageAction
    data object GenerateDraft : DraftMessageAction
    data class EditDraft(val text: String) : DraftMessageAction
    data object SendViaWhatsApp : DraftMessageAction
    data object CopyDraft : DraftMessageAction
}

sealed interface DraftMessageEvent {
    data class ShowSnackbar(val text: UiText) : DraftMessageEvent
    data object ShowUpgradeSheet : DraftMessageEvent
    data class LaunchWhatsApp(val phoneE164: String, val message: String) : DraftMessageEvent
    data class CopyToClipboard(val text: String) : DraftMessageEvent
}
