package com.danzucker.stitchpad.feature.debug.presentation

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
fun DebugMenuRoot(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: DebugMenuViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DebugMenuEvent.NavigateBack -> onNavigateBack()
            DebugMenuEvent.NavigateToLogin -> onNavigateToLogin()
            is DebugMenuEvent.ShowSnackbar -> {
                scope.launch {
                    val msg = when (val t = event.message) {
                        is UiText.DynamicString -> t.value
                        is UiText.StringResourceText -> getString(t.id)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    DebugMenuScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}
