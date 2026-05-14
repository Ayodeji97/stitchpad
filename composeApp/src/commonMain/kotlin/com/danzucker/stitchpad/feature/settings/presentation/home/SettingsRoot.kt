package com.danzucker.stitchpad.feature.settings.presentation.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChangeEmail: () -> Unit,
    onNavigateToChangePassword: () -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.NavigateToEditProfile -> onNavigateToEditProfile()
            SettingsEvent.NavigateToChangeEmail -> onNavigateToChangeEmail()
            SettingsEvent.NavigateToChangePassword -> onNavigateToChangePassword()
            SettingsEvent.NavigateToDeleteAccount -> onNavigateToDeleteAccount()
            SettingsEvent.NavigateToLoginAfterSignOut -> onSignedOut()
            is SettingsEvent.OpenUrl -> uriHandler.openUri(event.url)
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

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}
