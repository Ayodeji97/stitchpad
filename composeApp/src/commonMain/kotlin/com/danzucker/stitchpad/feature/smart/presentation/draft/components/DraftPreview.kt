package com.danzucker.stitchpad.feature.smart.presentation.draft.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_copy_text
import stitchpad.composeapp.generated.resources.draft_message_send_whatsapp

@Composable
fun DraftPreview(
    draftText: String,
    hasWhatsappNumber: Boolean,
    onTextChange: (String) -> Unit,
    onSendWhatsApp: () -> Unit,
    onCopyText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = draftText,
            onValueChange = onTextChange,
            minLines = 3,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onCopyText) {
                Text(stringResource(Res.string.draft_message_copy_text))
            }
            Spacer(Modifier.width(DesignTokens.space2))
            Button(
                onClick = onSendWhatsApp,
                enabled = hasWhatsappNumber,
            ) {
                Text(stringResource(Res.string.draft_message_send_whatsapp))
            }
        }
    }
}
