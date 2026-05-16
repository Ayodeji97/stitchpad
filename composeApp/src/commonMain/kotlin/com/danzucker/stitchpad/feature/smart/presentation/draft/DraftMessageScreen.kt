package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.CustomerPickerSheet
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.DraftPreview
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.IntentChips
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.LanguageToggle
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.OrderPickerSheet
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_generate_cta
import stitchpad.composeapp.generated.resources.draft_message_generating
import stitchpad.composeapp.generated.resources.draft_message_intent_section_label
import stitchpad.composeapp.generated.resources.draft_message_no_open_orders
import stitchpad.composeapp.generated.resources.draft_message_notes_label
import stitchpad.composeapp.generated.resources.draft_message_notes_placeholder
import stitchpad.composeapp.generated.resources.draft_message_offline_helper
import stitchpad.composeapp.generated.resources.draft_message_pick_customer
import stitchpad.composeapp.generated.resources.draft_message_pick_order

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftMessageScreen(
    state: DraftMessageState,
    onAction: (DraftMessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomerSheet by remember { mutableStateOf(false) }
    var showOrderSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.space4),
    ) {
        // Customer picker trigger
        OutlinedButton(
            onClick = { showCustomerSheet = true },
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.customer?.firstName
                    ?: stringResource(Res.string.draft_message_pick_customer),
            )
        }

        Spacer(Modifier.height(DesignTokens.space3))

        // Order picker trigger (disabled until customer selected, and
        // disabled with explanatory helper when the selected customer has
        // no open orders so the user isn't stranded at a dead-end button).
        val customerHasNoOpenOrders =
            state.customer != null && state.orderOptions.isEmpty()
        OutlinedButton(
            onClick = { showOrderSheet = true },
            enabled = state.customer != null && state.orderOptions.isNotEmpty(),
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.order?.garmentLabel
                    ?: stringResource(Res.string.draft_message_pick_order),
            )
        }
        if (customerHasNoOpenOrders) {
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = stringResource(Res.string.draft_message_no_open_orders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = DesignTokens.space2),
            )
        }

        Spacer(Modifier.height(DesignTokens.space4))

        // Intent chips
        Text(
            text = stringResource(Res.string.draft_message_intent_section_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        IntentChips(
            selectedIntent = state.intent,
            onIntentChange = { onAction(DraftMessageAction.SelectIntent(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space4))

        // Language toggle
        LanguageToggle(
            selectedLanguage = state.language,
            onLanguageChange = { onAction(DraftMessageAction.ToggleLanguage(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space4))

        // Notes field
        OutlinedTextField(
            value = state.customNotes,
            onValueChange = { onAction(DraftMessageAction.UpdateCustomNotes(it)) },
            label = { Text(stringResource(Res.string.draft_message_notes_label)) },
            placeholder = { Text(stringResource(Res.string.draft_message_notes_placeholder)) },
            minLines = 3,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space4))

        // Offline helper text
        if (!state.isOnline) {
            Text(
                text = stringResource(Res.string.draft_message_offline_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(DesignTokens.space2))
        }

        // Generate CTA
        Button(
            onClick = { onAction(DraftMessageAction.GenerateDraft) },
            enabled = state.canGenerate,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.generationState is GenerationState.Generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = if (state.generationState is GenerationState.Generating) {
                        stringResource(Res.string.draft_message_generating)
                    } else {
                        stringResource(Res.string.draft_message_generate_cta)
                    },
                )
            }
        }

        // Draft preview (shown only after success)
        if (state.generationState is GenerationState.Success) {
            Spacer(Modifier.height(DesignTokens.space4))
            DraftPreview(
                draftText = state.generationState.draftText,
                hasWhatsappNumber = state.customer?.whatsappNumber != null,
                onTextChange = { onAction(DraftMessageAction.EditDraft(it)) },
                onSendWhatsApp = { onAction(DraftMessageAction.SendViaWhatsApp) },
                onCopyText = { onAction(DraftMessageAction.CopyDraft) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(DesignTokens.space6))
    }

    if (showCustomerSheet) {
        CustomerPickerSheet(
            customers = state.customerOptions,
            onSelect = { onAction(DraftMessageAction.SelectCustomer(it)) },
            onDismissRequest = { showCustomerSheet = false },
        )
    }

    if (showOrderSheet) {
        OrderPickerSheet(
            orders = state.orderOptions,
            onSelect = { onAction(DraftMessageAction.SelectOrder(it)) },
            onDismissRequest = { showOrderSheet = false },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DraftMessageScreenIdlePreview() {
    StitchPadTheme {
        DraftMessageScreen(state = DraftMessageState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun DraftMessageScreenSuccessPreview() {
    StitchPadTheme {
        DraftMessageScreen(
            state = DraftMessageState(
                customer = CustomerSummary(
                    id = "c",
                    firstName = "Folake",
                    whatsappNumber = "+2348012345678",
                ),
                order = OrderSummary(
                    id = "o",
                    customerId = "c",
                    garmentLabel = "Adire boubou",
                    balanceFormatted = "₦7,500",
                    deadlineFormatted = "Fri",
                ),
                intent = DraftIntent.BalanceReminder,
                generationState = GenerationState.Success(
                    "Hi Folake, just a friendly note about your balance.",
                ),
                remainingFreeQuota = 4,
            ),
            onAction = {},
        )
    }
}
