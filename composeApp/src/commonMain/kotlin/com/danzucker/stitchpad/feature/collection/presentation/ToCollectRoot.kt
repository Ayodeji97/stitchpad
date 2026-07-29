package com.danzucker.stitchpad.feature.collection.presentation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_whatsapp_launch_failed
import stitchpad.composeapp.generated.resources.to_collect_chase_no_contact

@Composable
fun ToCollectRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrderDetail: (String) -> Unit,
    viewModel: ToCollectViewModel = koinViewModel(),
    whatsAppLauncher: WhatsAppLauncher = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ToCollectEvent.NavigateBack -> onNavigateBack()
            is ToCollectEvent.NavigateToOrderDetail -> onNavigateToOrderDetail(event.orderId)
            is ToCollectEvent.LaunchWhatsApp -> scope.launch {
                val message = WhatsAppMessageBuilder.buildForCollection(
                    event.order,
                    event.customer,
                    event.signature,
                )
                val launched = whatsAppLauncher.launch(event.customer.phone, message)
                if (!launched) {
                    snackbarHostState.showSnackbar(getString(Res.string.dashboard_whatsapp_launch_failed))
                }
            }
            ToCollectEvent.ChaseUnavailable -> scope.launch {
                snackbarHostState.showSnackbar(getString(Res.string.to_collect_chase_no_contact))
            }
        }
    }

    ToCollectScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}
