package com.danzucker.stitchpad.feature.debug.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

            SettingsSectionCard(label = "Bulk seed") {
                SettingsRow(
                    icon = Icons.Outlined.Group,
                    label = "Seed N demo customers…",
                    onClick = { onAction(DebugMenuAction.OnBulkSeedClick) },
                )
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
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.HourglassEmpty,
                    label = "Set welcome days left…",
                    onClick = { onAction(DebugMenuAction.OnSetWelcomeDaysLeftClick) },
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
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.MonetizationOn,
                    label = "Set Smart usage…",
                    onClick = { onAction(DebugMenuAction.OnSetSmartUsageClick) },
                )
            }

            SettingsSectionCard(label = "Freemium · slots") {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    label = "Reconcile customer slots",
                    onClick = { onAction(DebugMenuAction.OnReconcileSlotsClick) },
                )
            }

            SettingsSectionCard(label = "Notifications") {
                val onSendDigestClick: (() -> Unit)? = if (state.isWorking) {
                    null
                } else {
                    { onAction(DebugMenuAction.OnSendDailyDigestClick) }
                }
                val onSendTestPushClick: (() -> Unit)? = if (state.isWorking) {
                    null
                } else {
                    { onAction(DebugMenuAction.OnSendTestPushClick) }
                }
                SettingsRow(
                    icon = Icons.Outlined.Mail,
                    label = "Send daily digest now",
                    onClick = onSendDigestClick,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.NotificationsNone,
                    label = "Send digest + push (test)",
                    onClick = onSendTestPushClick,
                )
                SettingsRowDivider()
                val onSendReminderClick: (() -> Unit)? = if (state.isWorking) {
                    null
                } else {
                    { onAction(DebugMenuAction.OnSendRenewalReminderClick) }
                }
                SettingsRow(
                    icon = Icons.Outlined.Mail,
                    label = "Send renewal reminder now",
                    onClick = onSendReminderClick,
                )
            }

            SettingsSectionCard(label = "Analytics") {
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    label = "Analytics collection",
                    subtitle = "Session-scoped display — SDK persists the real value across launches",
                    onClick = {
                        onAction(
                            DebugMenuAction.ToggleAnalyticsCollection(!state.analyticsCollectionEnabled)
                        )
                    },
                    trailing = {
                        Switch(
                            checked = state.analyticsCollectionEnabled,
                            onCheckedChange = { enabled ->
                                onAction(DebugMenuAction.ToggleAnalyticsCollection(enabled))
                            },
                        )
                    },
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

        state.bulkSeed?.let { dialog ->
            BulkSeedDialog(
                state = dialog,
                onAction = onAction,
            )
        }
        state.smartUsage?.let { dialog ->
            SmartUsageDialog(
                state = dialog,
                onAction = onAction,
            )
        }
        state.welcomeDaysLeft?.let { dialog ->
            WelcomeDaysLeftDialog(
                state = dialog,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun WelcomeDaysLeftDialog(
    state: WelcomeDaysLeftDialogState,
    onAction: (DebugMenuAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(DebugMenuAction.OnSetWelcomeDaysLeftDismiss) },
        title = {
            Text(text = "Set welcome days left", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Backdates welcomeBonusAppliedAt so the rolling 30-day " +
                        "First Month window ends in N days. Range: 0–30.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.daysInput,
                    onValueChange = { onAction(DebugMenuAction.OnSetWelcomeDaysLeftChange(it)) },
                    label = { Text("Days left (0–30)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(DebugMenuAction.OnSetWelcomeDaysLeftConfirm) },
                enabled = state.isValid,
            ) {
                Text(text = "Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DebugMenuAction.OnSetWelcomeDaysLeftDismiss) }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SmartUsageDialog(
    state: SmartUsageDialogState,
    onAction: (DebugMenuAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(DebugMenuAction.OnSetSmartUsageDismiss) },
        title = {
            Text(
                text = "Set Smart usage",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Writes users/{uid}/usage/smart_drafts directly. " +
                        "Set count=5 to land at \"next call → upgrade sheet\". " +
                        "Both fields use \"drafts used\" semantic (e.g. bonus=6 → \"6 of 30 used\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.countInput,
                    onValueChange = { onAction(DebugMenuAction.OnSetSmartUsageCountChange(it)) },
                    label = { Text("Free drafts used this month") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.bonusUsedInput,
                    onValueChange = { onAction(DebugMenuAction.OnSetSmartUsageBonusUsedChange(it)) },
                    label = { Text("Bonus drafts used (0–30)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(DebugMenuAction.OnSetSmartUsageConfirm) },
                enabled = state.isValid,
            ) {
                Text(text = "Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DebugMenuAction.OnSetSmartUsageDismiss) }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun BulkSeedDialog(
    state: BulkSeedDialogState,
    onAction: (DebugMenuAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(DebugMenuAction.OnBulkSeedDismiss) },
        title = {
            Text(
                text = "Seed demo customers",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Additive — does not wipe existing data. " +
                        "Subject to your current tier's customer cap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.totalInput,
                    onValueChange = { onAction(DebugMenuAction.OnBulkSeedTotalChange(it)) },
                    label = { Text("Total customers") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.measurementsInput,
                    onValueChange = { onAction(DebugMenuAction.OnBulkSeedMeasurementsChange(it)) },
                    label = { Text("# with measurements") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(DesignTokens.space2))
                OutlinedTextField(
                    value = state.ordersInput,
                    onValueChange = { onAction(DebugMenuAction.OnBulkSeedOrdersChange(it)) },
                    label = { Text("# with orders") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(DebugMenuAction.OnBulkSeedConfirm) },
                enabled = state.isValid,
            ) {
                Text(text = "Seed", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DebugMenuAction.OnBulkSeedDismiss) }) {
                Text("Cancel")
            }
        },
    )
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
private fun DebugMenuScreenBulkSeedDialogPreview() {
    StitchPadTheme {
        DebugMenuScreen(
            state = DebugMenuState(
                testAccountsConfigured = true,
                bulkSeed = BulkSeedDialogState(),
            ),
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
