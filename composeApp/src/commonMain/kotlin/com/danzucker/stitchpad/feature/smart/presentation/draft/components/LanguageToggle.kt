package com.danzucker.stitchpad.feature.smart.presentation.draft.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_language_english
import stitchpad.composeapp.generated.resources.draft_message_language_pidgin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageToggle(
    selectedLanguage: DraftLanguage,
    onLanguageChange: (DraftLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        DraftLanguage.entries.forEachIndexed { index, language ->
            SegmentedButton(
                selected = selectedLanguage == language,
                onClick = { onLanguageChange(language) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DraftLanguage.entries.size,
                ),
                label = { Text(languageLabel(language)) },
            )
        }
    }
}

@Composable
private fun languageLabel(language: DraftLanguage): String = when (language) {
    DraftLanguage.English -> stringResource(Res.string.draft_message_language_english)
    DraftLanguage.Pidgin -> stringResource(Res.string.draft_message_language_pidgin)
}
