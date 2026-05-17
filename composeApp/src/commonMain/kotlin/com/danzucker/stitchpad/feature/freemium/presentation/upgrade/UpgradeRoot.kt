package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UpgradeRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: UpgradeViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UpgradeEvent.OpenExternalBrowser -> uriHandler.openUri(event.url)
        }
    }

    UpgradeScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}
