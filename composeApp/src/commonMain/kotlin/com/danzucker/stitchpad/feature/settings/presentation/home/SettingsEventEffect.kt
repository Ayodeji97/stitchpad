package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Suppress("CyclomaticComplexMethod")
@Composable
fun SettingsEventEffect(
    events: Flow<SettingsEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangeEmail: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToReferralCode: () -> Unit = {},
    onNavigateToTeam: () -> Unit = {},
    onNavigateToDeleteAccount: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onNavigateToDebugMenu: () -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {},
    onNavigateToFoundersNote: () -> Unit = {},
    onNavigateToShareGiftLink: () -> Unit = {},
    onNavigateToRedeemGift: () -> Unit = {},
    onNavigateToHelpTutorials: () -> Unit = {},
    onNavigateToAccountSecurity: () -> Unit = {},
    onNavigateToInviteRewards: () -> Unit = {},
    onNavigateToHelpSupport: () -> Unit = {},
    onNavigateToLegalAbout: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(events) { event ->
        when (event) {
            SettingsEvent.NavigateBack -> onNavigateBack()
            SettingsEvent.NavigateToEditProfile -> onNavigateToEditProfile()
            SettingsEvent.NavigateToChangeEmail -> onNavigateToChangeEmail()
            SettingsEvent.NavigateToChangePassword -> onNavigateToChangePassword()
            SettingsEvent.NavigateToReferralCode -> onNavigateToReferralCode()
            SettingsEvent.NavigateToTeam -> onNavigateToTeam()
            SettingsEvent.NavigateToDeleteAccount -> onNavigateToDeleteAccount()
            SettingsEvent.NavigateToLoginAfterSignOut -> onSignedOut()
            SettingsEvent.NavigateToDebugMenu -> onNavigateToDebugMenu()
            SettingsEvent.NavigateToUpgrade -> onNavigateToUpgrade()
            SettingsEvent.NavigateToFoundersNote -> onNavigateToFoundersNote()
            SettingsEvent.NavigateToShareGiftLink -> onNavigateToShareGiftLink()
            SettingsEvent.NavigateToRedeemGift -> onNavigateToRedeemGift()
            SettingsEvent.NavigateToHelpTutorials -> onNavigateToHelpTutorials()
            SettingsEvent.NavigateToAccountSecurity -> onNavigateToAccountSecurity()
            SettingsEvent.NavigateToInviteRewards -> onNavigateToInviteRewards()
            SettingsEvent.NavigateToHelpSupport -> onNavigateToHelpSupport()
            SettingsEvent.NavigateToLegalAbout -> onNavigateToLegalAbout()
            is SettingsEvent.OpenUrl -> uriHandler.openUri(event.url)
            is SettingsEvent.OpenCommunityLink ->
                runCatching { uriHandler.openUri(event.url) }
                    .onFailure {
                        // Never log the URL — the invite token grants community access.
                        AppLogger.e(tag = "SettingsEventEffect", throwable = it) {
                            "No handler to open community invite"
                        }
                    }
            is SettingsEvent.OpenWhatsApp -> {
                scope.launch {
                    val message = getString(event.messageRes)
                    uriHandler.openUri(buildWhatsAppUrl(event.phoneNumber, message))
                }
            }
            is SettingsEvent.ShowSnackbar -> {
                scope.launch {
                    val message = when (val text = event.message) {
                        is UiText.DynamicString -> text.value
                        is UiText.StringResourceText -> getString(text.id)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
}
