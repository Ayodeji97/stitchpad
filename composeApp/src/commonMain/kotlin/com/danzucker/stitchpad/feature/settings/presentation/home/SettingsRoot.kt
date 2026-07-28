package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToReferralCode: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToDebugMenu: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToFoundersNote: () -> Unit,
    onNavigateToShareGiftLink: () -> Unit,
    onNavigateToRedeemGift: () -> Unit,
    onNavigateToHelpTutorials: () -> Unit,
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToInviteRewards: () -> Unit = {},
    onNavigateToHelpSupport: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToEditProfile = onNavigateToEditProfile,
        onNavigateToChangeEmail = onNavigateToChangeEmail,
        onNavigateToChangePassword = onNavigateToChangePassword,
        onNavigateToReferralCode = onNavigateToReferralCode,
        onNavigateToDeleteAccount = onNavigateToDeleteAccount,
        onSignedOut = onSignedOut,
        onNavigateToDebugMenu = onNavigateToDebugMenu,
        onNavigateToUpgrade = onNavigateToUpgrade,
        onNavigateToFoundersNote = onNavigateToFoundersNote,
        onNavigateToShareGiftLink = onNavigateToShareGiftLink,
        onNavigateToRedeemGift = onNavigateToRedeemGift,
        onNavigateToHelpTutorials = onNavigateToHelpTutorials,
        onNavigateToAccountSecurity = onNavigateToAccountSecurity,
        onNavigateToInviteRewards = onNavigateToInviteRewards,
        onNavigateToHelpSupport = onNavigateToHelpSupport,
    )

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}
