package com.danzucker.stitchpad.feature.settings.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRow
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowChevron
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowDivider
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_account_title
import stitchpad.composeapp.generated.resources.settings_back_cd
import stitchpad.composeapp.generated.resources.settings_row_change_password
import stitchpad.composeapp.generated.resources.settings_row_email
import stitchpad.composeapp.generated.resources.settings_row_sign_out
import stitchpad.composeapp.generated.resources.settings_row_signin_method

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.settings_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space3),
        ) {
            Spacer(Modifier.height(DesignTokens.space2))
            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Outlined.AccountCircle,
                    label = stringResource(Res.string.settings_row_signin_method),
                    onClick = null,
                    subtitle = providerSubtitle(state.signInProvider, state.maskedSignInIdentifier),
                )
                if (state.showChangeEmailRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Email,
                        label = stringResource(Res.string.settings_row_email),
                        subtitle = state.email,
                        onClick = { onAction(SettingsAction.OnEmailRowClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
                if (state.showChangePasswordRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        label = stringResource(Res.string.settings_row_change_password),
                        onClick = { onAction(SettingsAction.OnChangePasswordClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Logout,
                    label = stringResource(Res.string.settings_row_sign_out),
                    onClick = { onAction(SettingsAction.OnSignOutRowClick) },
                )
            }
            Spacer(Modifier.height(DesignTokens.space5))
        }
        if (state.showSignOutDialog) {
            SignOutConfirmDialog(
                onConfirm = { onAction(SettingsAction.OnSignOutConfirm) },
                onDismiss = { onAction(SettingsAction.OnSignOutDismiss) },
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsAccountScreenPreview() {
    StitchPadTheme {
        SettingsAccountScreen(
            state = SettingsState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                maskedSignInIdentifier = "folake@stitchpad.app",
                subscriptionTier = SubscriptionTier.FREE,
                measurementUnit = MeasurementUnit.INCHES,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsAccountScreenSsoPreview() {
    StitchPadTheme {
        SettingsAccountScreen(
            state = SettingsState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.APPLE,
                maskedSignInIdentifier = "folake@privaterelay.appleid.com",
                subscriptionTier = SubscriptionTier.PRO,
                measurementUnit = MeasurementUnit.CM,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsAccountScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SettingsAccountScreen(
            state = SettingsState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                maskedSignInIdentifier = "folake@stitchpad.app",
                subscriptionTier = SubscriptionTier.FREE,
                measurementUnit = MeasurementUnit.INCHES,
            ),
            onAction = {},
        )
    }
}
