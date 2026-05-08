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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.action_call
import stitchpad.composeapp.generated.resources.action_whatsapp
import stitchpad.composeapp.generated.resources.order_detail_add_phone
import stitchpad.composeapp.generated.resources.order_detail_customer_section
import stitchpad.composeapp.generated.resources.order_detail_customer_since

private val WHATSAPP_GREEN = Color(0xFF25D366)
private val LEFT_INDENT = 28.dp + DesignTokens.space2

@Composable
fun OrderCustomerCard(
    customerName: String,
    phone: String?,
    customerCreatedAt: Long?,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onAddPhoneClick: () -> Unit,
    onCustomerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCustomerClick, role = Role.Button),
    ) {
        Column(
            modifier = Modifier.padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space4,
                bottom = DesignTokens.space3,
            ),
        ) {
            CardHeader()

            Spacer(Modifier.height(DesignTokens.space2))

            Text(
                text = customerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = LEFT_INDENT),
            )

            val sinceCaption = customerCreatedAt
                ?.takeIf { it > 0L }
                ?.let { formatCustomerSince(it) }
            if (sinceCaption != null) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = LEFT_INDENT),
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(
                            Res.string.order_detail_customer_since,
                            sinceCaption,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(DesignTokens.space2))

            if (phone.isNullOrBlank()) {
                AddPhoneCta(onClick = onAddPhoneClick)
            } else {
                ReachOutChips(
                    onWhatsAppClick = onWhatsAppClick,
                    onCallClick = onCallClick,
                )
            }
        }
    }
}

@Composable
private fun CardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(DesignTokens.iconInline),
        )
    }
}

@Composable
private fun ReachOutChips(
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.padding(start = LEFT_INDENT),
    ) {
        AssistChip(
            onClick = onWhatsAppClick,
            label = {
                Text(
                    text = stringResource(Res.string.action_whatsapp),
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
            ),
        )
        AssistChip(
            onClick = onCallClick,
            label = {
                Text(
                    text = stringResource(Res.string.action_call),
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
    }
}

@Composable
private fun AddPhoneCta(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = LEFT_INDENT - DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.order_detail_add_phone),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
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

private fun formatCustomerSince(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "$month ${date.year}"
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
            customerCreatedAt = 1_746_316_800_000L,
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
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
            customerCreatedAt = 1_746_316_800_000L,
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
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
            customerCreatedAt = 1_743_638_400_000L,
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
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
            customerCreatedAt = 1_743_638_400_000L,
            onWhatsAppClick = {},
            onCallClick = {},
            onAddPhoneClick = {},
            onCustomerClick = {},
        )
    }
}

// endregion
