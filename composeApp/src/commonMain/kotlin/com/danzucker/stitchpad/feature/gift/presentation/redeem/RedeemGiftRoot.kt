package com.danzucker.stitchpad.feature.gift.presentation.redeem

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RedeemGiftRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RedeemGiftViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RedeemGiftEvent.ShowSnackbar -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
            is RedeemGiftEvent.Redeemed -> scope.launch {
                // Show the celebratory confirmation, THEN pop — the new tier flows in
                // via EntitlementsProvider, so wherever the user lands reflects it.
                snackbarHostState.showSnackbar(event.message.resolve())
                onBack()
            }
            RedeemGiftEvent.NavigateBack -> onBack()
        }
    }

    RedeemGiftScreen(
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
