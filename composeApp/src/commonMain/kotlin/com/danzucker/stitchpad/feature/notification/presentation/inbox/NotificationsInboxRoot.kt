package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun NotificationsInboxRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    viewModel: NotificationsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Captured once on entry — the relative times/grouping freeze for the screen's
    // lifetime, which is fine for an inbox the user opens, reads, and leaves.
    val now = remember { Clock.System.now().toEpochMilliseconds() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationsInboxEvent.NavigateBack -> onNavigateBack()
            is NotificationsInboxEvent.NavigateToOrderDetail -> onNavigateToOrder(event.orderId)
        }
    }

    NotificationsInboxScreen(
        state = state,
        now = now,
        onAction = viewModel::onAction,
    )
}
