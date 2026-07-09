package com.danzucker.stitchpad.feature.measurement.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.measurement_share_sheet_title
import stitchpad.composeapp.generated.resources.measurement_share_whatsapp_description
import stitchpad.composeapp.generated.resources.measurement_share_whatsapp_title
import stitchpad.composeapp.generated.resources.share_as_image_description
import stitchpad.composeapp.generated.resources.share_as_image_title
import stitchpad.composeapp.generated.resources.share_as_pdf_description
import stitchpad.composeapp.generated.resources.share_as_pdf_title

/**
 * Share-format picker for a measurement: image, PDF, or a plain-text WhatsApp
 * message. Mirrors the receipt share sheet's option-card pattern, simplified
 * to three fixed options (no doc-type toggle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMeasurementSheet(
    measurementName: String,
    customerName: String,
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ShareMeasurementSheetContent(
            measurementName = measurementName,
            customerName = customerName,
            onShareAsImage = onShareAsImage,
            onShareAsPdf = onShareAsPdf,
            onShareWhatsApp = onShareWhatsApp,
        )
    }
}

@Composable
private fun ShareMeasurementSheetContent(
    measurementName: String,
    customerName: String,
    onShareAsImage: () -> Unit,
    onShareAsPdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space6),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.measurement_share_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$measurementName — $customerName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        ShareOptionRow(
            icon = Icons.Default.Image,
            title = stringResource(Res.string.share_as_image_title),
            description = stringResource(Res.string.share_as_image_description),
            onClick = onShareAsImage,
        )
        ShareOptionRow(
            icon = Icons.Default.PictureAsPdf,
            title = stringResource(Res.string.share_as_pdf_title),
            description = stringResource(Res.string.share_as_pdf_description),
            onClick = onShareAsPdf,
        )
        ShareOptionRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = stringResource(Res.string.measurement_share_whatsapp_title),
            description = stringResource(Res.string.measurement_share_whatsapp_description),
            onClick = onShareWhatsApp,
        )
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            modifier = Modifier.padding(DesignTokens.space3),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// region — Previews (content only; ModalBottomSheet needs an Activity)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ShareMeasurementSheetLightPreview() {
    StitchPadTheme {
        Surface {
            ShareMeasurementSheetContent(
                measurementName = "Wedding guest gown",
                customerName = "Amaka Obi",
                onShareAsImage = {},
                onShareAsPdf = {},
                onShareWhatsApp = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ShareMeasurementSheetDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface {
            ShareMeasurementSheetContent(
                measurementName = "Kaftan",
                customerName = "Chidi Eze",
                onShareAsImage = {},
                onShareAsPdf = {},
                onShareWhatsApp = {},
            )
        }
    }
}

// endregion
