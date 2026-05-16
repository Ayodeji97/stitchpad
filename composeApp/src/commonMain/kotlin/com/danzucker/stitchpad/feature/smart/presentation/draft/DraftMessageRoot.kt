package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.UpgradeBottomSheet
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DraftMessageRoot(
    onUpgradeRequested: () -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val viewModel: DraftMessageViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val scope = rememberCoroutineScope()
    var showUpgradeSheet by remember { mutableStateOf(false) }

    // Load the customer list on first composition.
    LaunchedEffect(Unit) {
        viewModel.onAction(DraftMessageAction.LoadCustomers)
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DraftMessageEvent.ShowSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(resolve(event.text))
            }
            DraftMessageEvent.ShowUpgradeSheet -> showUpgradeSheet = true
            is DraftMessageEvent.LaunchWhatsApp -> scope.launch {
                whatsAppLauncher.launch(event.phoneE164, event.message)
            }
            is DraftMessageEvent.CopyToClipboard -> clipboard.setText(AnnotatedString(event.text))
            DraftMessageEvent.NavigateBack -> onNavigateBack()
        }
    }

    DraftMessageScreen(state = state, onAction = viewModel::onAction, modifier = modifier)

    if (showUpgradeSheet) {
        UpgradeBottomSheet(
            onUpgrade = {
                showUpgradeSheet = false
                onUpgradeRequested()
            },
            onDismiss = { showUpgradeSheet = false },
        )
    }
}

@Suppress("SpreadOperator")
private suspend fun resolve(text: UiText): String = when (text) {
    is UiText.DynamicString -> text.value
    is UiText.StringResourceText -> getString(text.id, *text.args)
}
