package com.danzucker.stitchpad.feature.smart.presentation.draft.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_upgrade_sheet_dismiss
import stitchpad.composeapp.generated.resources.smart_upgrade_sheet_message
import stitchpad.composeapp.generated.resources.smart_upgrade_sheet_title
import stitchpad.composeapp.generated.resources.smart_upgrade_sheet_upgrade_cta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeBottomSheet(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
) {
    val heritageAccent = LocalStitchPadColors.current.heritageAccent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DesignTokens.space4,
                    end = DesignTokens.space4,
                    bottom = DesignTokens.space8,
                ),
        ) {
            Text(
                text = stringResource(Res.string.smart_upgrade_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.smart_upgrade_sheet_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space6))
            Button(
                onClick = onUpgrade,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = heritageAccent,
                    // White on saffron #E8A800 only hits ~2:1 contrast (fails
                    // WCAG AA). Pair the heritage accent with inkDark for
                    // ~10:1 — matches the saffron-on-paper treatment used
                    // elsewhere in the rebrand.
                    contentColor = DesignTokens.inkDark,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.smart_upgrade_sheet_upgrade_cta),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(DesignTokens.space2))
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.smart_upgrade_sheet_dismiss))
            }
        }
    }
}
