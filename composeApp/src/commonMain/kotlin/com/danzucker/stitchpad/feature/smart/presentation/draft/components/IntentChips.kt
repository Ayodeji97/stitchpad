package com.danzucker.stitchpad.feature.smart.presentation.draft.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_intent_balance
import stitchpad.composeapp.generated.resources.draft_message_intent_custom
import stitchpad.composeapp.generated.resources.draft_message_intent_followup
import stitchpad.composeapp.generated.resources.draft_message_intent_pickup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntentChips(
    selectedIntent: DraftIntent?,
    onIntentChange: (DraftIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
    ) {
        DraftIntent.entries.forEach { intent ->
            FilterChip(
                selected = selectedIntent == intent,
                onClick = { onIntentChange(intent) },
                label = { Text(intentLabel(intent)) },
                modifier = Modifier.padding(end = DesignTokens.space2),
            )
        }
    }
}

@Composable
private fun intentLabel(intent: DraftIntent): String = when (intent) {
    DraftIntent.BalanceReminder -> stringResource(Res.string.draft_message_intent_balance)
    DraftIntent.PickupReady -> stringResource(Res.string.draft_message_intent_pickup)
    DraftIntent.FollowUp -> stringResource(Res.string.draft_message_intent_followup)
    DraftIntent.CustomNote -> stringResource(Res.string.draft_message_intent_custom)
}
