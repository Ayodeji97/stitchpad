package com.danzucker.stitchpad.feature.tutorials.presentation.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TutorialPlayerRoot(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TutorialPlayerViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TutorialPlayerEvent.NavigateBack -> onClose()
        }
    }

    TutorialPlayerScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
