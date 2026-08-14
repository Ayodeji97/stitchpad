package com.danzucker.stitchpad.feature.foundingtailors.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.openUriSafely
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoundingTailorsRoot(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoundingTailorsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    // Mint (or read) the tailor's referral link once on first composition.
    LaunchedEffect(Unit) {
        viewModel.onAction(FoundingTailorsAction.LoadLink)
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // Plain browser handoff to the public leaderboard — the established
            // external-link pattern (no Custom Tabs), same as SettingsEventEffect.
            is FoundingTailorsEvent.OpenUrl -> uriHandler.openUriSafely(event.url, tag = "FoundingTailorsRoot")
            // The share text is already resolved by the ViewModel. Empty phone
            // opens WhatsApp's share picker — same path as the gift/team share.
            is FoundingTailorsEvent.ShareText ->
                uriHandler.openUriSafely(buildWhatsAppUrl("", event.text), tag = "FoundingTailorsRoot")
        }
    }

    FoundingTailorsScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
