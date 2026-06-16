package com.danzucker.stitchpad.feature.gift.presentation.sharelink

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_share_copied
import stitchpad.composeapp.generated.resources.gift_share_whatsapp_message

@Composable
fun ShareGiftLinkRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ShareGiftLinkViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ShareGiftLinkEvent.CopyToClipboard -> {
                clipboard.setText(AnnotatedString(event.text))
                scope.launch { snackbarHostState.showSnackbar(getString(Res.string.gift_share_copied)) }
            }
            is ShareGiftLinkEvent.ShareViaWhatsApp -> scope.launch {
                val message = getString(Res.string.gift_share_whatsapp_message, event.url)
                // Empty phone opens WhatsApp's share picker (same path as the invite row).
                uriHandler.openUri(buildWhatsAppUrl("", message))
            }
            is ShareGiftLinkEvent.ShowSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
            ShareGiftLinkEvent.NavigateBack -> onBack()
        }
    }

    ShareGiftLinkScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

private suspend fun UiText.resolve(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResourceText -> getString(id)
}
