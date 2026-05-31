package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.WhatsAppConfirmUiState
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.whatsapp_confirm_cta
import stitchpad.composeapp.generated.resources.whatsapp_confirm_input_label
import stitchpad.composeapp.generated.resources.whatsapp_confirm_instructions
import stitchpad.composeapp.generated.resources.whatsapp_confirmed_badge

/**
 * Stateless "Confirm on WhatsApp" affordance rendered under the WhatsApp field
 * on both Workshop Setup and Edit Profile. Renders when [numberValid] is true, or
 * when a prior confirmation badge is retained ([WhatsAppConfirmUiState.confirmed]);
 * otherwise it draws nothing.
 * Proves WhatsApp reachability, not ownership — copy says "WhatsApp confirmed".
 */
@Composable
fun WhatsAppConfirmRow(
    state: WhatsAppConfirmUiState,
    numberValid: Boolean,
    onConfirmClick: () -> Unit,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!numberValid && !state.confirmed) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.confirmed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.whatsapp_confirmed_badge),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            state.promptVisible -> {
                Text(
                    text = stringResource(Res.string.whatsapp_confirm_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(Res.string.whatsapp_confirm_input_label)) },
                    isError = state.error != null,
                    supportingText = state.error?.let { err -> { Text(stringResource(err)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> TextButton(onClick = onConfirmClick) {
                Text(stringResource(Res.string.whatsapp_confirm_cta))
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WhatsAppConfirmRowIdlePreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            WhatsAppConfirmRow(
                state = WhatsAppConfirmUiState(),
                numberValid = true,
                onConfirmClick = {},
                onCodeChange = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WhatsAppConfirmRowPromptingPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            WhatsAppConfirmRow(
                state = WhatsAppConfirmUiState(promptVisible = true, input = "12"),
                numberValid = true,
                onConfirmClick = {},
                onCodeChange = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun WhatsAppConfirmRowConfirmedPreview() {
    StitchPadTheme {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            WhatsAppConfirmRow(
                state = WhatsAppConfirmUiState(confirmed = true),
                numberValid = true,
                onConfirmClick = {},
                onCodeChange = {},
            )
        }
    }
}
