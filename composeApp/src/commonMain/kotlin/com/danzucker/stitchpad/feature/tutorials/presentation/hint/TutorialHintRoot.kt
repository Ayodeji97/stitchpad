package com.danzucker.stitchpad.feature.tutorials.presentation.hint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Drop-in contextual tutorial nudge for an empty state. Self-contained (owns its
 * [TutorialHintViewModel], keyed by [topic]) so a screen only needs to pass [onNavigateToPlayer].
 * Renders nothing until the catalog resolves or when no clip is published for [topic].
 */
@Composable
fun TutorialHintRoot(
    topic: TutorialTopic,
    onNavigateToPlayer: (tutorialId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // @Preview has no Koin graph — skip the ViewModel so empty-state previews still render.
    if (LocalInspectionMode.current) return

    val viewModel: TutorialHintViewModel = koinViewModel(key = "tutorial-hint-${topic.id}") {
        parametersOf(topic.id)
    }
    val state by viewModel.state.collectAsState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is TutorialHintEvent.NavigateToPlayer -> onNavigateToPlayer(event.tutorialId)
        }
    }

    val tutorial = state.tutorial
    if (state.resolved && tutorial != null) {
        TutorialHintCard(
            tutorial = tutorial,
            expanded = state.expanded,
            onWatch = { viewModel.onAction(TutorialHintAction.OnWatch) },
            onDismiss = { viewModel.onAction(TutorialHintAction.OnDismiss) },
            modifier = modifier,
        )
    }
}
