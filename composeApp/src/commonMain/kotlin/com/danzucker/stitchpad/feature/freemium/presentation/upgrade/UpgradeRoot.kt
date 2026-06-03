package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpgradeRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpgradeViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UpgradeEvent.OpenExternalBrowser -> uriHandler.openUri(event.url)
            is UpgradeEvent.ShowSnackbar -> {
                scope.launch {
                    val message = when (val text = event.message) {
                        is UiText.DynamicString -> text.value
                        is UiText.StringResourceText -> getString(text.id)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
            UpgradeEvent.UpgradeDetected -> onBack()
        }
    }

    UpgradeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}
