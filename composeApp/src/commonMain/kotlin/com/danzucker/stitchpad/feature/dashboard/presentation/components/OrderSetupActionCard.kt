package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.formatNaira
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_setup_action_cta
import stitchpad.composeapp.generated.resources.order_setup_action_missing_both
import stitchpad.composeapp.generated.resources.order_setup_action_missing_deposit
import stitchpad.composeapp.generated.resources.order_setup_action_missing_due_date
import stitchpad.composeapp.generated.resources.order_setup_action_section
import stitchpad.composeapp.generated.resources.order_setup_action_subtitle
import stitchpad.composeapp.generated.resources.order_setup_action_title

private val AVATAR_SIZE = 48.dp

/**
 * Replaces the generic NBA carousel during first-order onboarding when the
 * order has been created but is still missing a due date and/or deposit.
 *
 * The whole card (and the primary "Open order" button) routes to the order
 * detail screen for [FirstOrderSetupUi.orderId]; the user fills the missing
 * fields there and the dashboard recomputes setup state on the next emission.
 *
 * Caller is responsible for only rendering this when [FirstOrderSetupUi.hasOrder]
 * is true and at least one of `hasDueDate` / `hasDeposit` is false. When both
 * are set, [FirstOrderSetupUi] flips to null and this card never renders.
 */
@Composable
fun OrderSetupActionCard(
    setup: FirstOrderSetupUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.order_setup_action_section).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
        )
        Surface(
            shape = shape,
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick, role = Role.Button),
        ) {
            Column(
                modifier = Modifier.padding(DesignTokens.space4),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                ) {
                    AvatarBadge(initials = initialsOf(setup.customerName))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.order_setup_action_title,
                                setup.customerName,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SubtitleLine(setup = setup)
                        MissingFieldsLine(setup = setup)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.order_setup_action_cta),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarBadge(initials: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(
                color = scheme.primary.copy(alpha = 0.14f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

@Composable
private fun SubtitleLine(setup: FirstOrderSetupUi) {
    val text = if (setup.garmentLabel.isNotBlank() && setup.totalAmount > 0.0) {
        stringResource(
            Res.string.order_setup_action_subtitle,
            setup.garmentLabel,
            formatNaira(setup.totalAmount),
        )
    } else if (setup.garmentLabel.isNotBlank()) {
        setup.garmentLabel
    } else {
        // Fallback when garment label / total are unavailable — render nothing
        // useful here, so let the missing-fields line do the talking instead.
        ""
    }
    if (text.isNotBlank()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Spacer(Modifier.size(0.dp))
    }
}

@Composable
private fun MissingFieldsLine(setup: FirstOrderSetupUi) {
    val labelRes = when {
        !setup.hasDueDate && !setup.hasDeposit -> Res.string.order_setup_action_missing_both
        !setup.hasDueDate -> Res.string.order_setup_action_missing_due_date
        else -> Res.string.order_setup_action_missing_deposit
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderSetupActionCardBothMissingPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderSetupActionCard(
            setup = FirstOrderSetupUi(
                customerName = "Omobolanle Johnson",
                orderId = "o1",
                hasOrder = true,
                hasDueDate = false,
                hasDeposit = false,
                garmentLabel = "Aso Oke blouse",
                totalAmount = 200_000.0,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderSetupActionCardMissingDepositPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderSetupActionCard(
            setup = FirstOrderSetupUi(
                customerName = "Omobolanle Johnson",
                orderId = "o1",
                hasOrder = true,
                hasDueDate = true,
                hasDeposit = false,
                garmentLabel = "Senator",
                totalAmount = 200_000.0,
            ),
            onClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}
