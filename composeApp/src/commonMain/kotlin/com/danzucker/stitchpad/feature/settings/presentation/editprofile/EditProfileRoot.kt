package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
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
                    snackbarHostState.showSnackbar(resolve(event.message))
                }
            }
            is EditProfileEvent.SaveSucceeded -> {
                scope.launch {
                    // Suspend until the snackbar dismisses so the user actually
                    // sees the confirmation; only then tear down the scaffold.
                    snackbarHostState.showSnackbar(resolve(event.message))
                    onNavigateBack()
                }
            }
            is EditProfileEvent.LaunchWhatsAppConfirm -> Unit // TODO Task 10
        }
    }

    val logoPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        scope = scope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let {
                viewModel.onAction(EditProfileAction.OnLogoPicked(it))
            }
        },
    )

    EditProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onLaunchLogoPicker = { logoPicker.launch() },
        onAction = viewModel::onAction,
    )
}

@Suppress("SpreadOperator")
private suspend fun resolve(text: UiText): String = when (text) {
    is UiText.DynamicString -> text.value
    is UiText.StringResourceText -> getString(text.id, *text.args)
}
