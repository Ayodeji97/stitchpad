package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.sharing.WhatsAppLauncher
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.UpgradeBottomSheet
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_copy_confirmed
import stitchpad.composeapp.generated.resources.draft_message_free_tier_chip_compact
import stitchpad.composeapp.generated.resources.draft_message_screen_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftMessageRoot(
    onUpgradeRequested: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DraftMessageViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val whatsAppLauncher: WhatsAppLauncher = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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
            is DraftMessageEvent.CopyToClipboard -> {
                clipboard.setText(AnnotatedString(event.text))
                scope.launch {
                    snackbarHostState.showSnackbar(
                        getString(Res.string.draft_message_copy_confirmed)
                    )
                }
            }
            DraftMessageEvent.NavigateBack -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.draft_message_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    val remaining = state.remainingFreeQuota
                    if (remaining != null) {
                        Text(
                            text = stringResource(
                                Res.string.draft_message_free_tier_chip_compact,
                                remaining,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        DraftMessageScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = Modifier.padding(innerPadding),
        )
    }

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
