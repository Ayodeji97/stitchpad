package com.danzucker.stitchpad.feature.settings.presentation.foundersnote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.founders_note_paragraph_1
import stitchpad.composeapp.generated.resources.founders_note_paragraph_2
import stitchpad.composeapp.generated.resources.founders_note_paragraph_3
import stitchpad.composeapp.generated.resources.founders_note_paragraph_4
import stitchpad.composeapp.generated.resources.founders_note_paragraph_5
import stitchpad.composeapp.generated.resources.founders_note_screen_heading
import stitchpad.composeapp.generated.resources.founders_note_signature
import stitchpad.composeapp.generated.resources.founders_note_title
import stitchpad.composeapp.generated.resources.founders_note_whatsapp_cta

/**
 * Long-form explainer of the V1.0 freemium model in the founder's voice.
 *
 * Reachable from Settings → "About your plan". Static screen — no ViewModel
 * needed, just two callbacks (back, WhatsApp). The Root + nav wiring live
 * upstream in MainScreen.kt.
 *
 * Per V1.0 design spec decision #8 — turn the transition risk into a trust
 * moment. The note humanizes the constraint ("AI calls cost real money")
 * and surfaces the four V1.0 marketing value props inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundersNoteScreen(
    onNavigateBack: () -> Unit,
    onOpenWhatsApp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.founders_note_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Text(
                text = stringResource(Res.string.founders_note_screen_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Each paragraph is its own Text so the line-height and spacing
            // breathe — better than one big multi-line block for readability
            // on small phones.
            ParagraphText(stringResource(Res.string.founders_note_paragraph_1))
            ParagraphText(stringResource(Res.string.founders_note_paragraph_2))
            ParagraphText(stringResource(Res.string.founders_note_paragraph_3))
            ParagraphText(stringResource(Res.string.founders_note_paragraph_4))
            ParagraphText(stringResource(Res.string.founders_note_paragraph_5))

            Spacer(modifier = Modifier.height(DesignTokens.space2))

            Text(
                text = stringResource(Res.string.founders_note_signature),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(DesignTokens.space4))

            OutlinedButton(
                onClick = onOpenWhatsApp,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.height(DesignTokens.space2))
                Text(
                    text = stringResource(Res.string.founders_note_whatsapp_cta),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.space4))
        }
    }
}

@Composable
private fun ParagraphText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FoundersNoteScreenPreview() {
    StitchPadTheme {
        FoundersNoteScreen(onNavigateBack = {}, onOpenWhatsApp = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FoundersNoteScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        FoundersNoteScreen(onNavigateBack = {}, onOpenWhatsApp = {})
    }
}
