package com.danzucker.stitchpad.feature.settings.presentation.foundersnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import com.danzucker.stitchpad.core.sharing.buildWhatsAppUrl
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_support_intro_message

private const val SUPPORT_WHATSAPP_NUMBER = "+2348064816696"

/**
 * Nav-host wrapper for [FoundersNoteScreen]. No ViewModel needed — the screen is
 * pure content; the only side effect is the WhatsApp deeplink which is handled
 * here via [LocalUriHandler] to match the existing pattern from [SettingsRoot].
 *
 * Support number kept in sync with `SUPPORT_WHATSAPP_NUMBER` in SettingsViewModel.
 * If we add more entry points to support, lift this constant into a shared
 * `core/support/SupportEndpoints.kt` — not worth the new file for two callers.
 */
@Composable
fun FoundersNoteRoot(
    onNavigateBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    FoundersNoteScreen(
        onNavigateBack = onNavigateBack,
        onOpenWhatsApp = {
            scope.launch {
                val message = getString(Res.string.settings_support_intro_message)
                uriHandler.openUri(buildWhatsAppUrl(SUPPORT_WHATSAPP_NUMBER, message))
            }
        },
    )
}
