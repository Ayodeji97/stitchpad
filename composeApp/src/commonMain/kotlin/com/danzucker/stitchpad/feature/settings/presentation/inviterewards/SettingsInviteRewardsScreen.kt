package com.danzucker.stitchpad.feature.settings.presentation.inviterewards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Redeem
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
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRow
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowChevron
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowDivider
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.feature.settings.presentation.home.GIFTING_ENABLED
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_redeem_settings_subtitle
import stitchpad.composeapp.generated.resources.gift_redeem_title
import stitchpad.composeapp.generated.resources.gift_share_settings_row
import stitchpad.composeapp.generated.resources.gift_share_settings_subtitle
import stitchpad.composeapp.generated.resources.referral_code_settings_row
import stitchpad.composeapp.generated.resources.referral_code_settings_subtitle
import stitchpad.composeapp.generated.resources.settings_back_cd
import stitchpad.composeapp.generated.resources.settings_invite_rewards_title
import stitchpad.composeapp.generated.resources.settings_row_invite
import stitchpad.composeapp.generated.resources.settings_row_invite_subtitle

@Suppress("UnusedParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInviteRewardsScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_invite_rewards_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
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
                    icon = Icons.Outlined.PersonAddAlt,
                    label = stringResource(Res.string.settings_row_invite),
                    subtitle = stringResource(Res.string.settings_row_invite_subtitle),
                    onClick = { onAction(SettingsAction.OnInviteClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Redeem,
                    label = stringResource(Res.string.referral_code_settings_row),
                    subtitle = stringResource(Res.string.referral_code_settings_subtitle),
                    onClick = { onAction(SettingsAction.OnReferralCodeClick) },
                    trailing = { SettingsRowChevron() },
                )
                if (GIFTING_ENABLED) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.CardGiftcard,
                        label = stringResource(Res.string.gift_share_settings_row),
                        subtitle = stringResource(Res.string.gift_share_settings_subtitle),
                        onClick = { onAction(SettingsAction.OnGetGiftedClick) },
                        trailing = { SettingsRowChevron() },
                    )
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Redeem,
                        label = stringResource(Res.string.gift_redeem_title),
                        subtitle = stringResource(Res.string.gift_redeem_settings_subtitle),
                        onClick = { onAction(SettingsAction.OnRedeemGiftClick) },
                        trailing = { SettingsRowChevron() },
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.space5))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsInviteRewardsScreenPreview() {
    StitchPadTheme {
        SettingsInviteRewardsScreen(
            state = SettingsState(isLoading = false),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsInviteRewardsScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SettingsInviteRewardsScreen(
            state = SettingsState(isLoading = false),
            onAction = {},
        )
    }
}
