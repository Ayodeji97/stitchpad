package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReferralCodeRoot(
    onNavigateBack: () -> Unit,
    viewModel: ReferralCodeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ReferralCodeEvent.NavigateBack -> onNavigateBack()
            is ReferralCodeEvent.ShowMessage -> {
                scope.launch { snackbarHostState.showSnackbar(resolve(event.message)) }
            }
        }
    }

    ReferralCodeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@Suppress("SpreadOperator")
private suspend fun resolve(text: UiText): String = when (text) {
    is UiText.DynamicString -> text.value
    is UiText.StringResourceText -> getString(text.id, *text.args)
}
