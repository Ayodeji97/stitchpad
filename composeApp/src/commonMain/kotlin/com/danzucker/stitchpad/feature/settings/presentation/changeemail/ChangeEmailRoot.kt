package com.danzucker.stitchpad.feature.settings.presentation.changeemail

import androidx.compose.material3.SnackbarDuration
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
fun ChangeEmailRoot(
    onNavigateBack: () -> Unit,
    viewModel: ChangeEmailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ChangeEmailEvent.NavigateBack -> onNavigateBack()
            is ChangeEmailEvent.ShowSnackbar -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = resolve(event.message),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
            is ChangeEmailEvent.SaveSucceeded -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = resolve(event.message),
                        duration = SnackbarDuration.Long,
                    )
                    onNavigateBack()
                }
            }
        }
    }

    ChangeEmailScreen(
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
