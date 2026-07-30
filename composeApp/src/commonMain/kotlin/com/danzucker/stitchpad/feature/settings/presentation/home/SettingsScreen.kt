@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.presentation.account.SignOutConfirmDialog
import com.danzucker.stitchpad.feature.settings.presentation.account.providerSubtitle
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
import stitchpad.composeapp.generated.resources.gift_redeem_settings_subtitle
import stitchpad.composeapp.generated.resources.gift_redeem_title
import stitchpad.composeapp.generated.resources.gift_share_settings_row
import stitchpad.composeapp.generated.resources.gift_share_settings_subtitle
import stitchpad.composeapp.generated.resources.referral_code_settings_row
import stitchpad.composeapp.generated.resources.referral_code_settings_subtitle
import stitchpad.composeapp.generated.resources.settings_back_cd
import stitchpad.composeapp.generated.resources.settings_receipt_image_dark
import stitchpad.composeapp.generated.resources.settings_receipt_image_light
import stitchpad.composeapp.generated.resources.settings_row_account_security
import stitchpad.composeapp.generated.resources.settings_row_account_security_subtitle
import stitchpad.composeapp.generated.resources.settings_row_appearance
import stitchpad.composeapp.generated.resources.settings_row_change_password
import stitchpad.composeapp.generated.resources.settings_row_community
import stitchpad.composeapp.generated.resources.settings_row_community_subtitle
import stitchpad.composeapp.generated.resources.settings_row_contact
import stitchpad.composeapp.generated.resources.settings_row_contact_subtitle
import stitchpad.composeapp.generated.resources.settings_row_daily_digest
import stitchpad.composeapp.generated.resources.settings_row_daily_push
import stitchpad.composeapp.generated.resources.settings_row_debug_menu
import stitchpad.composeapp.generated.resources.settings_row_delete_account
import stitchpad.composeapp.generated.resources.settings_row_email
import stitchpad.composeapp.generated.resources.settings_row_founders_note
import stitchpad.composeapp.generated.resources.settings_row_founders_note_subtitle
import stitchpad.composeapp.generated.resources.settings_row_help_support
import stitchpad.composeapp.generated.resources.settings_row_help_support_subtitle
import stitchpad.composeapp.generated.resources.settings_row_invite
import stitchpad.composeapp.generated.resources.settings_row_invite_rewards
import stitchpad.composeapp.generated.resources.settings_row_invite_rewards_subtitle
import stitchpad.composeapp.generated.resources.settings_row_invite_subtitle
import stitchpad.composeapp.generated.resources.settings_row_legal_about
import stitchpad.composeapp.generated.resources.settings_row_legal_about_subtitle
import stitchpad.composeapp.generated.resources.settings_row_measurement_units
import stitchpad.composeapp.generated.resources.settings_row_measurement_units_centimeters
import stitchpad.composeapp.generated.resources.settings_row_measurement_units_inches
import stitchpad.composeapp.generated.resources.settings_row_privacy
import stitchpad.composeapp.generated.resources.settings_row_receipt_image
import stitchpad.composeapp.generated.resources.settings_row_sign_out
import stitchpad.composeapp.generated.resources.settings_row_signin_method
import stitchpad.composeapp.generated.resources.settings_row_team
import stitchpad.composeapp.generated.resources.settings_row_team_subtitle
import stitchpad.composeapp.generated.resources.settings_row_terms
import stitchpad.composeapp.generated.resources.settings_row_tutorials
import stitchpad.composeapp.generated.resources.settings_row_tutorials_subtitle
import stitchpad.composeapp.generated.resources.settings_section_account
import stitchpad.composeapp.generated.resources.settings_section_business
import stitchpad.composeapp.generated.resources.settings_section_legal
import stitchpad.composeapp.generated.resources.settings_section_manage
import stitchpad.composeapp.generated.resources.settings_section_preferences
import stitchpad.composeapp.generated.resources.settings_section_support
import stitchpad.composeapp.generated.resources.settings_theme_dark
import stitchpad.composeapp.generated.resources.settings_theme_light
import stitchpad.composeapp.generated.resources.settings_theme_system
import stitchpad.composeapp.generated.resources.settings_title

// Gifting (buy-a-gift + redeem-a-code) unlocks a paid plan outside the stores'
// billing, so it stays hidden app-wide while we're not running any payment flow.
// It was first hidden on iOS only (App Store Guideline 3.1.1 / 3.1.2 — App Review
// rejected 1.0 for the redeem-code path); now it's off on Android and web too.
// Flip this back on when payments resume — iOS gifting must then route through
// Apple Offer Codes, not this flow. The /claim + /redeem deep links stay intact.
internal const val GIFTING_ENABLED = false

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
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back_cd),
                        )
                    }
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
            if (state.settingsHubEnabled) {
                SettingsLandingHub(state = state, onAction = onAction)
            } else {
                SettingsLandingLegacy(state = state, onAction = onAction)
            }
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
 * The flag-off (legacy) Settings landing: hero + plan + the six flat sections
 * (Business, Preferences, Account, Support, Legal) + pinned Delete-account and
 * debug cards. Kept byte-for-byte identical to the pre-hub layout.
 */
@Composable
private fun SettingsLandingLegacy(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(DesignTokens.space2))

    ProfileHeroCard(
        businessName = state.businessName.ifBlank { "—" },
        logoUrl = state.businessLogoUrl,
        subtitle = state.heroSubtitle.ifBlank { state.email },
        avatarColorIndex = state.avatarColorIndex,
        onClick = { onAction(SettingsAction.OnProfileClick) },
        planBadgeLabel = state.proBadgeLabel?.let { stringResource(it) },
    )

    Spacer(Modifier.height(DesignTokens.space3))

    PlanCard(
        tier = state.subscriptionTier,
        customerCount = state.customerCount,
        customerLimit = state.customerLimit,
        aiDraftsUsed = state.aiDraftsUsed,
        aiDraftLimit = state.aiDraftLimit,
        isFirstMonth = state.isFirstMonth,
        welcomeDaysLeft = state.welcomeDaysLeft,
        onUpgradeClick = { onAction(SettingsAction.OnUpgradeClick) },
        modifier = Modifier,
        subscriptionStatus = state.subscriptionStatus,
    )

    SettingsSectionCard(label = stringResource(Res.string.settings_section_business)) {
        // Slice 7: owner-only Team management (invite staff, approve/revoke access).
        if (state.isOwner) {
            SettingsRow(
                icon = Icons.Outlined.Groups,
                label = stringResource(Res.string.settings_row_team),
                subtitle = stringResource(Res.string.settings_row_team_subtitle),
                onClick = { onAction(SettingsAction.OnTeamClick) },
                trailing = { SettingsRowChevron() },
            )
        }
        SettingsRow(
            icon = Icons.Outlined.PersonAddAlt,
            label = stringResource(Res.string.settings_row_invite),
            subtitle = stringResource(Res.string.settings_row_invite_subtitle),
            onClick = { onAction(SettingsAction.OnInviteClick) },
            trailing = { SettingsRowChevron() },
        )
        SettingsRow(
            icon = Icons.Outlined.Redeem,
            label = stringResource(Res.string.referral_code_settings_row),
            subtitle = stringResource(Res.string.referral_code_settings_subtitle),
            onClick = { onAction(SettingsAction.OnReferralCodeClick) },
            trailing = { SettingsRowChevron() },
        )
        // Gifting entry points are hidden while payments are paused — see
        // GIFTING_ENABLED at the top of this file.
        if (GIFTING_ENABLED) {
            SettingsRow(
                icon = Icons.Outlined.CardGiftcard,
                label = stringResource(Res.string.gift_share_settings_row),
                subtitle = stringResource(Res.string.gift_share_settings_subtitle),
                onClick = { onAction(SettingsAction.OnGetGiftedClick) },
                trailing = { SettingsRowChevron() },
            )
            SettingsRow(
                icon = Icons.Outlined.Redeem,
                label = stringResource(Res.string.gift_redeem_title),
                subtitle = stringResource(Res.string.gift_redeem_settings_subtitle),
                onClick = { onAction(SettingsAction.OnRedeemGiftClick) },
                trailing = { SettingsRowChevron() },
            )
        }
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
        SettingsRow(
            icon = Icons.Outlined.Brightness6,
            label = stringResource(Res.string.settings_row_appearance),
            onClick = { onAction(SettingsAction.OnAppearanceClick) },
            trailing = {
                SettingsRowValue(
                    text = stringResource(
                        when (state.themePreference) {
                            ThemePreference.SYSTEM -> Res.string.settings_theme_system
                            ThemePreference.LIGHT -> Res.string.settings_theme_light
                            ThemePreference.DARK -> Res.string.settings_theme_dark
                        }
                    ),
                )
            },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.Image,
            label = stringResource(Res.string.settings_row_receipt_image),
            onClick = { onAction(SettingsAction.OnReceiptImageStyleClick) },
            trailing = {
                SettingsRowValue(text = receiptImageStyleLabel(state.receiptImageStyle))
            },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.Notifications,
            label = stringResource(Res.string.settings_row_daily_digest),
            onClick = { onAction(SettingsAction.OnDailyDigestToggle(!state.dailyDigestEmailEnabled)) },
            trailing = {
                Switch(
                    checked = state.dailyDigestEmailEnabled,
                    onCheckedChange = { onAction(SettingsAction.OnDailyDigestToggle(it)) },
                )
            },
        )
        if (state.pushReminderSupported) {
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                label = stringResource(Res.string.settings_row_daily_push),
                onClick = { onAction(SettingsAction.OnDailyPushToggle(!state.dailyPushEnabled)) },
                trailing = {
                    Switch(
                        checked = state.dailyPushEnabled,
                        onCheckedChange = { onAction(SettingsAction.OnDailyPushToggle(it)) },
                    )
                },
            )
        }
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
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.Info,
            label = stringResource(Res.string.settings_row_founders_note),
            subtitle = stringResource(Res.string.settings_row_founders_note_subtitle),
            onClick = { onAction(SettingsAction.OnFoundersNoteClick) },
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

    if (isDebugBuild) {
        Spacer(Modifier.height(DesignTokens.space4))
        SettingsSectionCard {
            SettingsRow(
                icon = Icons.Outlined.BugReport,
                label = stringResource(Res.string.settings_row_debug_menu),
                onClick = { onAction(SettingsAction.OnDebugMenuClick) },
                trailing = { SettingsRowChevron() },
            )
        }
    }

    Spacer(Modifier.height(DesignTokens.space5))
}

/**
 * The flag-on (hub) Settings landing: hero + plan + Preferences (unchanged rows)
 * + a single Manage section that routes into the four category sub-screens
 * (Account & security, Invite & rewards, Help & support, Legal & about) + the
 * pinned Delete-account and debug cards. Sign-out lives in the Account &
 * security sub-screen, not here, so this layout never dispatches
 * [SettingsAction.OnSignOutRowClick].
 */
@Composable
private fun SettingsLandingHub(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    Spacer(Modifier.height(DesignTokens.space2))

    ProfileHeroCard(
        businessName = state.businessName.ifBlank { "—" },
        logoUrl = state.businessLogoUrl,
        subtitle = state.heroSubtitle.ifBlank { state.email },
        avatarColorIndex = state.avatarColorIndex,
        onClick = { onAction(SettingsAction.OnProfileClick) },
        planBadgeLabel = state.proBadgeLabel?.let { stringResource(it) },
    )

    Spacer(Modifier.height(DesignTokens.space3))

    PlanCard(
        tier = state.subscriptionTier,
        customerCount = state.customerCount,
        customerLimit = state.customerLimit,
        aiDraftsUsed = state.aiDraftsUsed,
        aiDraftLimit = state.aiDraftLimit,
        isFirstMonth = state.isFirstMonth,
        welcomeDaysLeft = state.welcomeDaysLeft,
        onUpgradeClick = { onAction(SettingsAction.OnUpgradeClick) },
        modifier = Modifier,
        subscriptionStatus = state.subscriptionStatus,
    )

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
        SettingsRow(
            icon = Icons.Outlined.Brightness6,
            label = stringResource(Res.string.settings_row_appearance),
            onClick = { onAction(SettingsAction.OnAppearanceClick) },
            trailing = {
                SettingsRowValue(
                    text = stringResource(
                        when (state.themePreference) {
                            ThemePreference.SYSTEM -> Res.string.settings_theme_system
                            ThemePreference.LIGHT -> Res.string.settings_theme_light
                            ThemePreference.DARK -> Res.string.settings_theme_dark
                        }
                    ),
                )
            },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.Image,
            label = stringResource(Res.string.settings_row_receipt_image),
            onClick = { onAction(SettingsAction.OnReceiptImageStyleClick) },
            trailing = {
                SettingsRowValue(text = receiptImageStyleLabel(state.receiptImageStyle))
            },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.Notifications,
            label = stringResource(Res.string.settings_row_daily_digest),
            onClick = { onAction(SettingsAction.OnDailyDigestToggle(!state.dailyDigestEmailEnabled)) },
            trailing = {
                Switch(
                    checked = state.dailyDigestEmailEnabled,
                    onCheckedChange = { onAction(SettingsAction.OnDailyDigestToggle(it)) },
                )
            },
        )
        if (state.pushReminderSupported) {
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                label = stringResource(Res.string.settings_row_daily_push),
                onClick = { onAction(SettingsAction.OnDailyPushToggle(!state.dailyPushEnabled)) },
                trailing = {
                    Switch(
                        checked = state.dailyPushEnabled,
                        onCheckedChange = { onAction(SettingsAction.OnDailyPushToggle(it)) },
                    )
                },
            )
        }
    }

    SettingsSectionCard(label = stringResource(Res.string.settings_section_manage)) {
        SettingsRow(
            icon = Icons.Outlined.AccountCircle,
            label = stringResource(Res.string.settings_row_account_security),
            subtitle = stringResource(Res.string.settings_row_account_security_subtitle),
            onClick = { onAction(SettingsAction.OnAccountSecurityClick) },
            trailing = { SettingsRowChevron() },
        )
        // Slice 7: owner-only Team management.
        if (state.isOwner) {
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Outlined.Groups,
                label = stringResource(Res.string.settings_row_team),
                subtitle = stringResource(Res.string.settings_row_team_subtitle),
                onClick = { onAction(SettingsAction.OnTeamClick) },
                trailing = { SettingsRowChevron() },
            )
        }
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.PersonAddAlt,
            label = stringResource(Res.string.settings_row_invite_rewards),
            subtitle = stringResource(Res.string.settings_row_invite_rewards_subtitle),
            onClick = { onAction(SettingsAction.OnInviteRewardsClick) },
            trailing = { SettingsRowChevron() },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            label = stringResource(Res.string.settings_row_help_support),
            subtitle = stringResource(Res.string.settings_row_help_support_subtitle),
            onClick = { onAction(SettingsAction.OnHelpSupportClick) },
            trailing = { SettingsRowChevron() },
        )
        SettingsRowDivider()
        SettingsRow(
            icon = Icons.Outlined.PrivacyTip,
            label = stringResource(Res.string.settings_row_legal_about),
            subtitle = stringResource(Res.string.settings_row_legal_about_subtitle),
            onClick = { onAction(SettingsAction.OnLegalAboutClick) },
            trailing = { SettingsRowChevron() },
        )
    }

    // Visual gap so the headerless Delete account card reads as its own
    // standalone section rather than a fifth row in Manage. Without this
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

    if (isDebugBuild) {
        Spacer(Modifier.height(DesignTokens.space4))
        SettingsSectionCard {
            SettingsRow(
                icon = Icons.Outlined.BugReport,
                label = stringResource(Res.string.settings_row_debug_menu),
                onClick = { onAction(SettingsAction.OnDebugMenuClick) },
                trailing = { SettingsRowChevron() },
            )
        }
    }

    Spacer(Modifier.height(DesignTokens.space5))
}

@Composable
private fun receiptImageStyleLabel(style: ReceiptImageStyle): String {
    val labelRes: StringResource = when (style) {
        ReceiptImageStyle.LIGHT -> Res.string.settings_receipt_image_light
        ReceiptImageStyle.DARK -> Res.string.settings_receipt_image_dark
    }
    return stringResource(labelRes)
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
                subscriptionTier = SubscriptionTier.FREE,
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
private fun SettingsScreenAppleProviderPreview() {
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
                subscriptionTier = SubscriptionTier.PRO,
                customerCount = 42,
                customerLimit = null,
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
                subscriptionTier = SubscriptionTier.FREE,
                customerCount = 13,
                customerLimit = 15,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsScreenHubPreview() {
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
                subscriptionTier = SubscriptionTier.FREE,
                customerCount = 8,
                customerLimit = 15,
                measurementUnit = MeasurementUnit.INCHES,
                settingsHubEnabled = true,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SettingsScreenHubDarkPreview() {
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
                subscriptionTier = SubscriptionTier.PRO,
                customerCount = 42,
                customerLimit = null,
                measurementUnit = MeasurementUnit.CM,
                settingsHubEnabled = true,
            ),
            onAction = {},
        )
    }
}
