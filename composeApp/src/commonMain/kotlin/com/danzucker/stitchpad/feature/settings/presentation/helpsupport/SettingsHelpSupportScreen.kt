package com.danzucker.stitchpad.feature.settings.presentation.helpsupport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Groups
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
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_back_cd
import stitchpad.composeapp.generated.resources.settings_help_support_title
import stitchpad.composeapp.generated.resources.settings_row_community
import stitchpad.composeapp.generated.resources.settings_row_community_subtitle
import stitchpad.composeapp.generated.resources.settings_row_contact
import stitchpad.composeapp.generated.resources.settings_row_contact_subtitle
import stitchpad.composeapp.generated.resources.settings_row_tutorials
import stitchpad.composeapp.generated.resources.settings_row_tutorials_subtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpSupportScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_help_support_title),
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
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    label = stringResource(Res.string.settings_row_tutorials),
                    subtitle = stringResource(Res.string.settings_row_tutorials_subtitle),
                    onClick = { onAction(SettingsAction.OnHelpTutorialsClick) },
                    trailing = { SettingsRowChevron() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    label = stringResource(Res.string.settings_row_contact),
                    subtitle = stringResource(Res.string.settings_row_contact_subtitle),
                    onClick = { onAction(SettingsAction.OnContactClick) },
                    trailing = { SettingsRowChevron() },
                )
                if (state.showCommunityRow) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Groups,
                        label = stringResource(Res.string.settings_row_community),
                        subtitle = stringResource(Res.string.settings_row_community_subtitle),
                        onClick = { onAction(SettingsAction.OnCommunityClick) },
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
private fun SettingsHelpSupportScreenPreview() {
    StitchPadTheme {
        SettingsHelpSupportScreen(
            state = SettingsState(
                isLoading = false,
                communityEnabled = true,
                communityUrl = "https://chat.whatsapp.com/preview-invite-code",
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsHelpSupportScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SettingsHelpSupportScreen(
            state = SettingsState(
                isLoading = false,
                communityEnabled = true,
                communityUrl = "https://chat.whatsapp.com/preview-invite-code",
            ),
            onAction = {},
        )
    }
}
