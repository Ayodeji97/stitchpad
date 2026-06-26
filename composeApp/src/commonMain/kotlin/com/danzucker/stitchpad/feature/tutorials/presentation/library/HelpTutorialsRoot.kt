package com.danzucker.stitchpad.feature.tutorials.presentation.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HelpTutorialsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (tutorialId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HelpTutorialsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HelpTutorialsEvent.NavigateBack -> onNavigateBack()
            is HelpTutorialsEvent.NavigateToPlayer -> onNavigateToPlayer(event.tutorialId)
        }
    }

    HelpTutorialsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
