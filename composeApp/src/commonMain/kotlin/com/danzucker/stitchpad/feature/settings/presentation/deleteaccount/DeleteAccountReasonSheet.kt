package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.feature.settings.domain.DeletionReason
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.delete_account_reason_cancel
import stitchpad.composeapp.generated.resources.delete_account_reason_complex
import stitchpad.composeapp.generated.resources.delete_account_reason_continue
import stitchpad.composeapp.generated.resources.delete_account_reason_duplicate
import stitchpad.composeapp.generated.resources.delete_account_reason_just_trying
import stitchpad.composeapp.generated.resources.delete_account_reason_missing_features
import stitchpad.composeapp.generated.resources.delete_account_reason_notes_placeholder
import stitchpad.composeapp.generated.resources.delete_account_reason_other
import stitchpad.composeapp.generated.resources.delete_account_reason_privacy
import stitchpad.composeapp.generated.resources.delete_account_reason_subtitle
import stitchpad.composeapp.generated.resources.delete_account_reason_switching
import stitchpad.composeapp.generated.resources.delete_account_reason_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountReasonSheet(
    selectedReason: DeletionReason?,
    additionalNotes: String,
    canContinue: Boolean,
    onSelectReason: (DeletionReason) -> Unit,
    onNotesChange: (String) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DesignTokens.space4,
                    end = DesignTokens.space4,
                    bottom = DesignTokens.space5,
                ),
        ) {
            Text(
                text = stringResource(Res.string.delete_account_reason_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.delete_account_reason_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space4))

            ReasonChips.forEach { (reason, labelRes) ->
                ReasonRow(
                    label = stringResource(labelRes),
                    isSelected = selectedReason == reason,
                    onClick = { onSelectReason(reason) },
                )
                Spacer(Modifier.height(DesignTokens.space2))
            }

            Spacer(Modifier.height(DesignTokens.space2))
            OutlinedTextField(
                value = additionalNotes,
                onValueChange = onNotesChange,
                placeholder = {
                    Text(stringResource(Res.string.delete_account_reason_notes_placeholder))
                },
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(DesignTokens.space4))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.delete_account_reason_cancel))
                }
                Button(
                    onClick = onContinue,
                    enabled = canContinue,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(Res.string.delete_account_reason_continue),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        DesignTokens.primary500
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = if (isSelected) {
        DesignTokens.primary50
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space3, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReasonRadio(isSelected = isSelected)
        Spacer(Modifier.width(DesignTokens.space3))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) DesignTokens.primary900 else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReasonRadio(isSelected: Boolean) {
    val borderColor = if (isSelected) DesignTokens.primary700 else MaterialTheme.colorScheme.outline
    val fillColor = if (isSelected) DesignTokens.primary500 else Color.Transparent
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(fillColor)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val ReasonChips: List<Pair<DeletionReason, StringResource>> = listOf(
    DeletionReason.DUPLICATE_OR_WRONG_ACCOUNT to Res.string.delete_account_reason_duplicate,
    DeletionReason.TOO_COMPLEX to Res.string.delete_account_reason_complex,
    DeletionReason.MISSING_FEATURES to Res.string.delete_account_reason_missing_features,
    DeletionReason.SWITCHING_APP to Res.string.delete_account_reason_switching,
    DeletionReason.PRIVACY_CONCERNS to Res.string.delete_account_reason_privacy,
    DeletionReason.JUST_TRYING to Res.string.delete_account_reason_just_trying,
    DeletionReason.OTHER to Res.string.delete_account_reason_other,
)
