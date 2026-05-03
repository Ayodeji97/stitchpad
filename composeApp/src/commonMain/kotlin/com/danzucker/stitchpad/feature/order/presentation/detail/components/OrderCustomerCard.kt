package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_customer_section

private val WHATSAPP_GREEN = Color(0xFF25D366)

@Composable
fun OrderCustomerCard(
    customerName: String,
    phone: String?,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onMeasurementsClick: () -> Unit,
    onCustomerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCustomerClick, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                SectionIconTile(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.order_detail_customer_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            // Customer name — indented to align with section label (28dp icon + 8dp gap)
            Text(
                text = customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 28.dp + DesignTokens.space2),
            )

            Spacer(Modifier.height(DesignTokens.space3))

            // Action chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier.padding(start = 28.dp + DesignTokens.space2),
            ) {
                AssistChip(
                    onClick = onWhatsAppClick,
                    enabled = phone != null,
                    label = {
                        Text(
                            text = "WhatsApp",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.iconInline),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = WHATSAPP_GREEN,
                        leadingIconContentColor = WHATSAPP_GREEN,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
                )
                AssistChip(
                    onClick = onCallClick,
                    enabled = phone != null,
                    label = {
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.iconInline),
                        )
                    },
                )
                AssistChip(
                    onClick = onMeasurementsClick,
                    label = {
                        Text(
                            text = "Sizes",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            modifier = Modifier.size(DesignTokens.iconInline),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCustomerCardLightWithPhonePreview() {
    StitchPadTheme {
        OrderCustomerCard(
            customerName = "Adaeze Okoro",
            phone = "+2348012345678",
            onWhatsAppClick = {},
            onCallClick = {},
            onMeasurementsClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCustomerCardLightNoPhonePreview() {
    StitchPadTheme {
        OrderCustomerCard(
            customerName = "Adaeze Okoro",
            phone = null,
            onWhatsAppClick = {},
            onCallClick = {},
            onMeasurementsClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCustomerCardDarkWithPhonePreview() {
    StitchPadTheme(darkTheme = true) {
        OrderCustomerCard(
            customerName = "Kunle Adeyemi",
            phone = "+2348087654321",
            onWhatsAppClick = {},
            onCallClick = {},
            onMeasurementsClick = {},
            onCustomerClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderCustomerCardDarkNoPhonePreview() {
    StitchPadTheme(darkTheme = true) {
        OrderCustomerCard(
            customerName = "Kunle Adeyemi",
            phone = null,
            onWhatsAppClick = {},
            onCallClick = {},
            onMeasurementsClick = {},
            onCustomerClick = {},
        )
    }
}

// endregion
