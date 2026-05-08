package com.danzucker.stitchpad.feature.settings.presentation.editprofile

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
fun EditProfileRoot(
    onNavigateBack: () -> Unit,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            EditProfileEvent.NavigateBack -> onNavigateBack()
            is EditProfileEvent.ShowSnackbar -> {
                scope.launch {
                    val message = when (val text = event.message) {
                        is UiText.DynamicString -> text.value
                        is UiText.StringResourceText -> getString(text.id)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    EditProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}
