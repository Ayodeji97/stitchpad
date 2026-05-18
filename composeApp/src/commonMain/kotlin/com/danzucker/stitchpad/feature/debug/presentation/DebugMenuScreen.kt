package com.danzucker.stitchpad.feature.debug.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRow
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowDivider
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    state: DebugMenuState,
    snackbarHostState: SnackbarHostState,
    onAction: (DebugMenuAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Debug menu") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space3),
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SettingsSectionCard(label = "Seed data") {
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    label = "Brand-new tailor",
                    onClick = { onAction(DebugMenuAction.OnSeedBrandNewClick) },
                    trailing = if (state.activeScenario == DebugScenario.BrandNew) {
                        { ActiveScenarioPill() }
                    } else {
                        null
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    label = "Active workshop",
                    onClick = { onAction(DebugMenuAction.OnSeedActiveWorkshopClick) },
                    trailing = if (state.activeScenario == DebugScenario.ActiveWorkshop) {
                        { ActiveScenarioPill() }
                    } else {
                        null
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    label = "All-reconnect",
                    onClick = { onAction(DebugMenuAction.OnSeedAllReconnectClick) },
                    trailing = if (state.activeScenario == DebugScenario.AllReconnect) {
                        { ActiveScenarioPill() }
                    } else {
                        null
                    },
                )
                if (state.activeScenario != null) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Refresh,
                        label = "Clear active state",
                        onClick = { onAction(DebugMenuAction.OnClearActiveScenarioClick) },
                    )
                }
            }

            SettingsSectionCard(label = "Session") {
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    label = "Reset onboarding flags",
                    onClick = { onAction(DebugMenuAction.OnResetOnboardingClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    label = "Sign out",
                    onClick = { onAction(DebugMenuAction.OnSignOutClick) },
                )
            }

            SettingsSectionCard(label = "Switch account") {
                SettingsRow(
                    icon = Icons.Outlined.SwapHoriz,
                    label = if (state.testAccountsConfigured) {
                        "Switch to Fola"
                    } else {
                        "Switch to Fola (not configured)"
                    },
                    onClick = { onAction(DebugMenuAction.OnSwitchToFolaClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.SwapHoriz,
                    label = if (state.testAccountsConfigured) {
                        "Switch to Gabby"
                    } else {
                        "Switch to Gabby (not configured)"
                    },
                    onClick = { onAction(DebugMenuAction.OnSwitchToGabbyClick) },
                )
            }

            SettingsSectionCard(label = "Freemium · tier") {
                SettingsRow(
                    icon = Icons.Outlined.Star,
                    label = "Set tier: Free",
                    onClick = { onAction(DebugMenuAction.OnSetTierFreeClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Star,
                    label = "Set tier: Pro",
                    onClick = { onAction(DebugMenuAction.OnSetTierProClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Star,
                    label = "Set tier: Atelier",
                    onClick = { onAction(DebugMenuAction.OnSetTierAtelierClick) },
                )
            }

            SettingsSectionCard(label = "Freemium · welcome window") {
                SettingsRow(
                    icon = Icons.Outlined.HourglassEmpty,
                    label = "Expire welcome window",
                    onClick = { onAction(DebugMenuAction.OnExpireWelcomeWindowClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    label = "Reset welcome window (now)",
                    onClick = { onAction(DebugMenuAction.OnResetWelcomeWindowClick) },
                )
            }

            SettingsSectionCard(label = "Freemium · Smart coins") {
                SettingsRow(
                    icon = Icons.Outlined.MonetizationOn,
                    label = "Drain bonus coins (→ 0)",
                    onClick = { onAction(DebugMenuAction.OnDrainBonusCoinsClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.MonetizationOn,
                    label = "Refill bonus coins (→ 30)",
                    onClick = { onAction(DebugMenuAction.OnRefillBonusCoinsClick) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Refresh,
                    label = "Reset Smart usage doc",
                    onClick = { onAction(DebugMenuAction.OnResetSmartUsageClick) },
                )
            }

            SettingsSectionCard(label = "Freemium · slots") {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    label = "Reconcile customer slots",
                    onClick = { onAction(DebugMenuAction.OnReconcileSlotsClick) },
                )
            }

            SettingsSectionCard(label = "Danger zone") {
                SettingsRow(
                    icon = Icons.Outlined.Delete,
                    label = "Wipe my data",
                    onClick = { onAction(DebugMenuAction.OnWipeDataClick) },
                    isDanger = true,
                )
            }
        }
    }
}

@Composable
private fun ActiveScenarioPill() {
    Text(
        text = "Active",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun DebugMenuScreenPreview() {
    StitchPadTheme {
        DebugMenuScreen(
            state = DebugMenuState(testAccountsConfigured = true),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun DebugMenuScreenNotConfiguredPreview() {
    StitchPadTheme {
        DebugMenuScreen(
            state = DebugMenuState(testAccountsConfigured = false),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun DebugMenuScreenActiveScenarioPreview() {
    StitchPadTheme {
        DebugMenuScreen(
            state = DebugMenuState(
                testAccountsConfigured = true,
                activeScenario = DebugScenario.ActiveWorkshop,
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}
