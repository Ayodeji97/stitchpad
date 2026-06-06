package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationsInboxRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    viewModel: NotificationsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationsInboxEvent.NavigateBack -> onNavigateBack()
            is NotificationsInboxEvent.NavigateToOrderDetail -> onNavigateToOrder(event.orderId)
        }
    }

    NotificationsInboxScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}
