package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.onboarding_step_customer_desc
import stitchpad.composeapp.generated.resources.onboarding_step_customer_label
import stitchpad.composeapp.generated.resources.onboarding_step_measurement_desc
import stitchpad.composeapp.generated.resources.onboarding_step_measurement_label
import stitchpad.composeapp.generated.resources.onboarding_step_order_desc
import stitchpad.composeapp.generated.resources.onboarding_step_order_label

private val ICON_BADGE_SIZE = 40.dp
private val ICON_SIZE = 22.dp
private val TILE_MIN_HEIGHT = 132.dp

/**
 * BrandNew-state onboarding tile grid: three equal-weight cards with icon,
 * bold label, and 2-line description. Tapping a tile dispatches its action.
 *
 * Rendered only on the first-launch / empty state, beneath the
 * [IllustratedFocusCard], to give a brand-new user a clear three-step path:
 * add customer → save measurements → create order.
 */
@Composable
fun OnboardingStepsCard(
    onAddCustomerClick: () -> Unit,
    onSaveMeasurementsClick: () -> Unit,
    onCreateOrderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        OnboardingStepTile(
            icon = Icons.Default.PersonAdd,
            label = stringResource(Res.string.onboarding_step_customer_label),
            description = stringResource(Res.string.onboarding_step_customer_desc),
            onClick = onAddCustomerClick,
            modifier = Modifier.weight(1f),
        )
        OnboardingStepTile(
            icon = Icons.Default.Straighten,
            label = stringResource(Res.string.onboarding_step_measurement_label),
            description = stringResource(Res.string.onboarding_step_measurement_desc),
            onClick = onSaveMeasurementsClick,
            modifier = Modifier.weight(1f),
        )
        OnboardingStepTile(
            icon = Icons.AutoMirrored.Filled.NoteAdd,
            label = stringResource(Res.string.onboarding_step_order_label),
            description = stringResource(Res.string.onboarding_step_order_desc),
            onClick = onCreateOrderClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OnboardingStepTile(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .defaultMinSize(minHeight = TILE_MIN_HEIGHT)
            .clip(shape)
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BADGE_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    color = scheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(ICON_BADGE_SIZE),
                ) {}
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun OnboardingStepsCardPreview() {
    StitchPadTheme {
        OnboardingStepsCard(
            onAddCustomerClick = {},
            onSaveMeasurementsClick = {},
            onCreateOrderClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun OnboardingStepsCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OnboardingStepsCard(
            onAddCustomerClick = {},
            onSaveMeasurementsClick = {},
            onCreateOrderClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}
