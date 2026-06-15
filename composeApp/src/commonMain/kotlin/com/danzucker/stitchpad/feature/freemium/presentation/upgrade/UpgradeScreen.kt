@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.upgrade_atelier_annual
import stitchpad.composeapp.generated.resources.upgrade_atelier_name
import stitchpad.composeapp.generated.resources.upgrade_atelier_price
import stitchpad.composeapp.generated.resources.upgrade_back_content_description
import stitchpad.composeapp.generated.resources.upgrade_cadence_annual
import stitchpad.composeapp.generated.resources.upgrade_cadence_monthly
import stitchpad.composeapp.generated.resources.upgrade_confirm_body
import stitchpad.composeapp.generated.resources.upgrade_confirm_cancel
import stitchpad.composeapp.generated.resources.upgrade_confirm_continue
import stitchpad.composeapp.generated.resources.upgrade_confirm_eyebrow
import stitchpad.composeapp.generated.resources.upgrade_confirm_plan_summary
import stitchpad.composeapp.generated.resources.upgrade_confirm_title
import stitchpad.composeapp.generated.resources.upgrade_pay_with_paystack
import stitchpad.composeapp.generated.resources.upgrade_pro_annual
import stitchpad.composeapp.generated.resources.upgrade_pro_name
import stitchpad.composeapp.generated.resources.upgrade_pro_price
import stitchpad.composeapp.generated.resources.upgrade_screen_title
import stitchpad.composeapp.generated.resources.upgrade_starting_checkout
import stitchpad.composeapp.generated.resources.upgrade_terms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    state: UpgradeState,
    snackbarHostState: SnackbarHostState,
    onAction: (UpgradeAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.upgrade_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.upgrade_back_content_description),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space4)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(DesignTokens.space4))

            // Tier picker
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                TierCard(
                    name = stringResource(Res.string.upgrade_pro_name),
                    monthlyPrice = stringResource(Res.string.upgrade_pro_price),
                    annualHint = stringResource(Res.string.upgrade_pro_annual),
                    isSelected = state.selectedTier == SubscriptionTier.PRO,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.PRO)) },
                )
                TierCard(
                    name = stringResource(Res.string.upgrade_atelier_name),
                    monthlyPrice = stringResource(Res.string.upgrade_atelier_price),
                    annualHint = stringResource(Res.string.upgrade_atelier_annual),
                    isSelected = state.selectedTier == SubscriptionTier.ATELIER,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER)) },
                )
            }

            Spacer(Modifier.height(DesignTokens.space6))

            // Cadence toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.billingCadence == BillingCadence.MONTHLY,
                    onClick = { onAction(UpgradeAction.SelectCadence(BillingCadence.MONTHLY)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(Res.string.upgrade_cadence_monthly)) },
                )
                SegmentedButton(
                    selected = state.billingCadence == BillingCadence.ANNUAL,
                    onClick = { onAction(UpgradeAction.SelectCadence(BillingCadence.ANNUAL)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(Res.string.upgrade_cadence_annual)) },
                )
            }

            Spacer(Modifier.height(DesignTokens.space8))

            // Pay CTA
            Button(
                onClick = { onAction(UpgradeAction.PayWithPaystack) },
                enabled = !state.isStartingCheckout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state.isStartingCheckout) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = DesignTokens.space2)
                            .size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.isStartingCheckout) {
                            Res.string.upgrade_starting_checkout
                        } else {
                            Res.string.upgrade_pay_with_paystack
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = DesignTokens.space2),
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            Text(
                text = stringResource(Res.string.upgrade_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(DesignTokens.space5))
        }
    }

    if (state.showCheckoutConfirmSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val tierName = when (state.selectedTier) {
            SubscriptionTier.PRO -> stringResource(Res.string.upgrade_pro_name)
            SubscriptionTier.ATELIER -> stringResource(Res.string.upgrade_atelier_name)
            else -> stringResource(Res.string.upgrade_pro_name)
        }
        val tierPrice = when (state.selectedTier) {
            SubscriptionTier.PRO -> if (state.billingCadence == BillingCadence.ANNUAL) {
                stringResource(Res.string.upgrade_pro_annual)
            } else {
                stringResource(Res.string.upgrade_pro_price)
            }
            SubscriptionTier.ATELIER -> if (state.billingCadence == BillingCadence.ANNUAL) {
                stringResource(Res.string.upgrade_atelier_annual)
            } else {
                stringResource(Res.string.upgrade_atelier_price)
            }
            else -> stringResource(Res.string.upgrade_pro_price)
        }
        val cadenceLabel = when (state.billingCadence) {
            BillingCadence.MONTHLY -> stringResource(Res.string.upgrade_cadence_monthly)
            BillingCadence.ANNUAL -> stringResource(Res.string.upgrade_cadence_annual)
        }
        ModalBottomSheet(
            onDismissRequest = { onAction(UpgradeAction.DismissCheckoutSheet) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CheckoutConfirmSheetContent(
                tierName = tierName,
                tierPrice = tierPrice,
                cadenceLabel = cadenceLabel,
                isStartingCheckout = state.isStartingCheckout,
                onConfirm = { onAction(UpgradeAction.ConfirmCheckout) },
                onDismiss = { onAction(UpgradeAction.DismissCheckoutSheet) },
            )
        }
    }
}

@Composable
private fun TierCard(
    name: String,
    monthlyPrice: String,
    annualHint: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = monthlyPrice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = annualHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Inner content of the checkout confirm sheet, extracted so @Preview can render
 * it directly — ModalBottomSheet doesn't lay out in preview mode.
 */
@Composable
private fun CheckoutConfirmSheetContent(
    tierName: String,
    tierPrice: String,
    cadenceLabel: String,
    isStartingCheckout: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space5),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        // Eyebrow — brand accent, all-caps, wide letter spacing.
        Text(
            text = stringResource(Res.string.upgrade_confirm_eyebrow),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = LocalStitchPadColors.current.brandAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Serif headline.
        Text(
            text = stringResource(Res.string.upgrade_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Plan summary — tier name + price + cadence.
        Text(
            text = stringResource(Res.string.upgrade_confirm_plan_summary, tierName, tierPrice, cadenceLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space2))

        // Explanation body.
        Text(
            text = stringResource(Res.string.upgrade_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(DesignTokens.space3))

        // Primary CTA — mirrors the Pay button's spinner/disabled treatment.
        Button(
            onClick = onConfirm,
            enabled = !isStartingCheckout,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (isStartingCheckout) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = DesignTokens.space2)
                        .size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(Res.string.upgrade_starting_checkout),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(DesignTokens.space2))
                Text(
                    text = stringResource(Res.string.upgrade_confirm_continue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space1))

        // Secondary dismiss link.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(DesignTokens.space2),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.upgrade_confirm_cancel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CheckoutConfirmSheetContentPreview() {
    StitchPadTheme {
        CheckoutConfirmSheetContent(
            tierName = "Tailor Pro",
            tierPrice = "₦2,000 / month",
            cadenceLabel = "Monthly",
            isStartingCheckout = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CheckoutConfirmSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        CheckoutConfirmSheetContent(
            tierName = "Tailor Atelier",
            tierPrice = "₦40,000 / year (save ₦8,000)",
            cadenceLabel = "Annual",
            isStartingCheckout = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UpgradeScreenProSelectedPreview() {
    StitchPadTheme {
        UpgradeScreen(
            state = UpgradeState(
                currentTier = SubscriptionTier.FREE,
                selectedTier = SubscriptionTier.PRO,
                billingCadence = BillingCadence.MONTHLY,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
            onBack = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UpgradeScreenAtelierAnnualPreview() {
    StitchPadTheme {
        UpgradeScreen(
            state = UpgradeState(
                currentTier = SubscriptionTier.PRO,
                selectedTier = SubscriptionTier.ATELIER,
                billingCadence = BillingCadence.ANNUAL,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
            onBack = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun UpgradeScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        UpgradeScreen(
            state = UpgradeState(
                currentTier = SubscriptionTier.FREE,
                selectedTier = SubscriptionTier.PRO,
                billingCadence = BillingCadence.ANNUAL,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
            onBack = {},
        )
    }
}
