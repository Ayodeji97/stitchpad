package com.danzucker.stitchpad.feature.settings.presentation.legalabout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
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
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowExternalIcon
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsAction
import com.danzucker.stitchpad.feature.settings.presentation.home.SettingsState
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_back_cd
import stitchpad.composeapp.generated.resources.settings_legal_about_title
import stitchpad.composeapp.generated.resources.settings_row_founders_note
import stitchpad.composeapp.generated.resources.settings_row_founders_note_subtitle
import stitchpad.composeapp.generated.resources.settings_row_privacy
import stitchpad.composeapp.generated.resources.settings_row_terms

@Suppress("UnusedParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLegalAboutScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_legal_about_title),
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
                    icon = Icons.Outlined.PrivacyTip,
                    label = stringResource(Res.string.settings_row_privacy),
                    onClick = { onAction(SettingsAction.OnPrivacyClick) },
                    trailing = { SettingsRowExternalIcon() },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Description,
                    label = stringResource(Res.string.settings_row_terms),
                    onClick = { onAction(SettingsAction.OnTermsClick) },
                    trailing = { SettingsRowExternalIcon() },
                )
                // Slice 6d: "About your plan / founder note" is owner plan+billing
                // content — hidden for active staff (they have no plan).
                if (!state.isActiveStaff) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        label = stringResource(Res.string.settings_row_founders_note),
                        subtitle = stringResource(Res.string.settings_row_founders_note_subtitle),
                        onClick = { onAction(SettingsAction.OnFoundersNoteClick) },
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
private fun SettingsLegalAboutScreenPreview() {
    StitchPadTheme {
        SettingsLegalAboutScreen(
            state = SettingsState(isLoading = false),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsLegalAboutScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SettingsLegalAboutScreen(
            state = SettingsState(isLoading = false),
            onAction = {},
        )
    }
}
