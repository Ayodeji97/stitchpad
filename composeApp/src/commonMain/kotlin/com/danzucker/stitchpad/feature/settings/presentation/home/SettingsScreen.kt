package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.presentation.components.PlanCard
import com.danzucker.stitchpad.feature.settings.presentation.components.ProfileHeroCard
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRow
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowChevron
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowDivider
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowExternalIcon
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsRowValue
import com.danzucker.stitchpad.feature.settings.presentation.components.SettingsSectionCard
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_hero_plan_pro
import stitchpad.composeapp.generated.resources.settings_row_appearance
import stitchpad.composeapp.generated.resources.settings_row_change_password
import stitchpad.composeapp.generated.resources.settings_row_contact
import stitchpad.composeapp.generated.resources.settings_row_contact_subtitle
import stitchpad.composeapp.generated.resources.settings_row_delete_account
import stitchpad.composeapp.generated.resources.settings_row_email
import stitchpad.composeapp.generated.resources.settings_row_invite
import stitchpad.composeapp.generated.resources.settings_row_invite_subtitle
import stitchpad.composeapp.generated.resources.settings_row_measurement_units
import stitchpad.composeapp.generated.resources.settings_row_measurement_units_centimeters
import stitchpad.composeapp.generated.resources.settings_row_measurement_units_inches
import stitchpad.composeapp.generated.resources.settings_row_privacy
import stitchpad.composeapp.generated.resources.settings_row_sign_out
import stitchpad.composeapp.generated.resources.settings_row_signin_method
import stitchpad.composeapp.generated.resources.settings_row_terms
import stitchpad.composeapp.generated.resources.settings_section_account
import stitchpad.composeapp.generated.resources.settings_section_business
import stitchpad.composeapp.generated.resources.settings_section_legal
import stitchpad.composeapp.generated.resources.settings_section_plan
import stitchpad.composeapp.generated.resources.settings_section_preferences
import stitchpad.composeapp.generated.resources.settings_section_support
import stitchpad.composeapp.generated.resources.settings_theme_dark
import stitchpad.composeapp.generated.resources.settings_theme_light
import stitchpad.composeapp.generated.resources.settings_theme_system
import stitchpad.composeapp.generated.resources.settings_title
import stitchpad.composeapp.generated.resources.sign_out_dialog_body
import stitchpad.composeapp.generated.resources.sign_out_dialog_cancel
import stitchpad.composeapp.generated.resources.sign_out_dialog_confirm
import stitchpad.composeapp.generated.resources.sign_out_dialog_title
import stitchpad.composeapp.generated.resources.signin_provider_apple
import stitchpad.composeapp.generated.resources.signin_provider_email
import stitchpad.composeapp.generated.resources.signin_provider_google
import stitchpad.composeapp.generated.resources.signin_provider_unknown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space3),
        ) {
            Spacer(Modifier.height(DesignTokens.space2))

            ProfileHeroCard(
                businessName = state.businessName.ifBlank { "—" },
                subtitle = state.heroSubtitle.ifBlank { state.email },
                avatarColorIndex = state.avatarColorIndex,
                onClick = { onAction(SettingsAction.OnProfileClick) },
                planBadgeLabel = if (state.isPremium) {
                    stringResource(Res.string.settings_hero_plan_pro)
                } else {
                    null
                },
            )

            if (state.showPlanCard) {
                Spacer(Modifier.height(DesignTokens.space4))
                Text(
                    text = stringResource(Res.string.settings_section_plan).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = DesignTokens.space2,
                        bottom = DesignTokens.space2,
                    ),
                )
                PlanCard(
                    customerCount = state.customerCount,
                    customerLimit = state.customerLimit,
                    onUpgradeClick = { onAction(SettingsAction.OnUpgradeClick) },
                    onComparePlansClick = { onAction(SettingsAction.OnComparePlansClick) },
                )
            }

            SettingsSectionCard(label = stringResource(Res.string.settings_section_business)) {
                SettingsRow(
                    icon = Icons.Outlined.PersonAddAlt,
                    label = stringResource(Res.string.settings_row_invite),
                    subtitle = stringResource(Res.string.settings_row_invite_subtitle),
                    onClick = { onAction(SettingsAction.OnInviteClick) },
                    trailing = { SettingsRowChevron() },
                )
            }

            SettingsSectionCard(label = stringResource(Res.string.settings_section_preferences)) {
                SettingsRow(
                    icon = Icons.Outlined.Straighten,
                    label = stringResource(Res.string.settings_row_measurement_units),
                    onClick = { onAction(SettingsAction.OnMeasurementUnitClick) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsRowValue(
                                text = stringResource(
                                    when (state.measurementUnit) {
                                        MeasurementUnit.INCHES -> Res.string.settings_row_measurement_units_inches
                                        MeasurementUnit.CM -> Res.string.settings_row_measurement_units_centimeters
                                    }
                                ),
                            )
                        }
                    },
                )
                SettingsRowDivider()
                AppearanceSegmentedRow(
                    selected = state.themePreference,
                    onSelect = { onAction(SettingsAction.OnThemeSelect(it)) },
                )
            }

            SettingsSectionCard(label = stringResource(Res.string.settings_section_account)) {
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

            SettingsSectionCard(label = stringResource(Res.string.settings_section_support)) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    label = stringResource(Res.string.settings_row_contact),
                    subtitle = stringResource(Res.string.settings_row_contact_subtitle),
                    onClick = { onAction(SettingsAction.OnContactClick) },
                    trailing = { SettingsRowChevron() },
                )
            }

            SettingsSectionCard(label = stringResource(Res.string.settings_section_legal)) {
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
            }

            // Visual gap so the headerless Delete account card reads as its own
            // standalone section rather than a third row in Legal. Without this
            // the two cards render flush because the section card only pads above
            // when it has a label.
            Spacer(Modifier.height(DesignTokens.space4))

            SettingsSectionCard {
                SettingsRow(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(Res.string.settings_row_delete_account),
                    onClick = { onAction(SettingsAction.OnDeleteAccountClick) },
                    isDanger = true,
                    trailing = { SettingsRowChevron() },
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

/**
 * Appearance picker rendered inline as a segmented control instead of a
 * bottom-sheet pop-up — picking one of three options doesn't deserve a modal.
 * Top half is the standard SettingsRow layout (icon + label); the segmented
 * control sits directly under the label so the choice is visible and one-tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSegmentedRow(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    val options = remember {
        listOf(
            ThemePreference.SYSTEM,
            ThemePreference.LIGHT,
            ThemePreference.DARK,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DesignTokens.space4,
                end = DesignTokens.space4,
                top = DesignTokens.space3,
                bottom = DesignTokens.space3,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusMd))
                    .background(DesignTokens.primary50),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Brightness6,
                    contentDescription = null,
                    tint = DesignTokens.primary500,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(DesignTokens.space3))
            Text(
                text = stringResource(Res.string.settings_row_appearance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(DesignTokens.space3))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, theme ->
                SegmentedButton(
                    selected = selected == theme,
                    onClick = { onSelect(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(
                        text = stringResource(
                            when (theme) {
                                ThemePreference.SYSTEM -> Res.string.settings_theme_system
                                ThemePreference.LIGHT -> Res.string.settings_theme_light
                                ThemePreference.DARK -> Res.string.settings_theme_dark
                            }
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun providerSubtitle(provider: SignInProvider, identifier: String): String {
    val providerLabelRes: StringResource = when (provider) {
        SignInProvider.EMAIL_PASSWORD -> Res.string.signin_provider_email
        SignInProvider.APPLE -> Res.string.signin_provider_apple
        SignInProvider.GOOGLE -> Res.string.signin_provider_google
        SignInProvider.UNKNOWN -> Res.string.signin_provider_unknown
    }
    val label = stringResource(providerLabelRes)
    return if (identifier.isBlank()) label else "$label • $identifier"
}

@Composable
private fun SignOutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.sign_out_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(Res.string.sign_out_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.sign_out_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.sign_out_dialog_cancel))
            }
        },
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsScreenPreview() {
    StitchPadTheme {
        SettingsScreen(
            state = SettingsState(
                isLoading = false,
                businessName = "Folake's Atelier",
                email = "folake@stitchpad.app",
                whatsappNumber = "+234 803 555 0142",
                avatarColorIndex = 0,
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                maskedSignInIdentifier = "folake@stitchpad.app",
                isPremium = false,
                customerCount = 8,
                customerLimit = 15,
                measurementUnit = MeasurementUnit.INCHES,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsScreenWarnPreview() {
    StitchPadTheme {
        SettingsScreen(
            state = SettingsState(
                isLoading = false,
                businessName = "Folake's Atelier",
                email = "folake@stitchpad.app",
                whatsappNumber = "+234 803 555 0142",
                avatarColorIndex = 3,
                signInProvider = SignInProvider.APPLE,
                maskedSignInIdentifier = "folake@privaterelay.appleid.com",
                isPremium = false,
                customerCount = 13,
                customerLimit = 15,
                measurementUnit = MeasurementUnit.CM,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SettingsScreen(
            state = SettingsState(
                isLoading = false,
                businessName = "Folake's Atelier",
                email = "folake@stitchpad.app",
                whatsappNumber = "+234 803 555 0142",
                avatarColorIndex = 4,
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                maskedSignInIdentifier = "folake@stitchpad.app",
                isPremium = false,
                customerCount = 8,
                customerLimit = 15,
            ),
            onAction = {},
        )
    }
}
