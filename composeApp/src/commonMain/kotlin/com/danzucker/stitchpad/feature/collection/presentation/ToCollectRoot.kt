package com.danzucker.stitchpad.feature.collection.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ToCollectRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrderDetail: (String) -> Unit,
    viewModel: ToCollectViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ToCollectEvent.NavigateBack -> onNavigateBack()
            is ToCollectEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
            is ToCollectEvent.LaunchWhatsApp -> scope.launch {
                val message = WhatsAppMessageBuilder.buildForOrder(event.order, event.customer)
                whatsAppLauncher.launch(event.customer.phone, message)
            }
        }
    }

    ToCollectScreen(state = state, onAction = viewModel::onAction)
}
