package com.danzucker.stitchpad.feature.settings.presentation.inviterewards

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsEventEffect
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsInviteRewardsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToReferralCode: () -> Unit,
    onNavigateToShareGiftLink: () -> Unit,
    onNavigateToRedeemGift: () -> Unit,
    onNavigateToFoundingTailors: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsEventEffect(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToReferralCode = onNavigateToReferralCode,
        onNavigateToShareGiftLink = onNavigateToShareGiftLink,
        onNavigateToRedeemGift = onNavigateToRedeemGift,
        onNavigateToFoundingTailors = onNavigateToFoundingTailors,
    )
    SettingsInviteRewardsScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}
