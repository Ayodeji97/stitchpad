package com.danzucker.stitchpad.feature.tutorials.presentation.hint

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.components.TutorialThumbnail
import com.danzucker.stitchpad.feature.tutorials.presentation.tutorialTitle
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorials_hint_dismiss
import stitchpad.composeapp.generated.resources.tutorials_hint_watch

/**
 * Contextual empty-state nudge. [expanded] shows a prominent card (thumbnail + title + watch +
 * dismiss) on first visit; once seen it collapses to a quiet inline link. Colors come from the
 * theme, so both light and dark are covered. Stateless — [TutorialHintRoot] owns the state.
 */
@Composable
fun TutorialHintCard(
    tutorial: Tutorial,
    expanded: Boolean,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Row(
                modifier = Modifier.padding(DesignTokens.space3),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TutorialThumbnail(
                    durationSec = tutorial.durationSec,
                    modifier = Modifier.size(width = 84.dp, height = 56.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tutorialTitle(tutorial),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = onWatch,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(Res.string.tutorials_hint_watch),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = DesignTokens.space1),
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.tutorials_hint_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .clickable(onClick = onWatch)
                .padding(vertical = DesignTokens.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.tutorials_hint_watch),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = DesignTokens.space1),
            )
        }
    }
}

private val sampleTutorial = Tutorial(
    id = TutorialTopic.AddCustomer.id,
    topicId = TutorialTopic.AddCustomer.id,
    title = "",
    description = "",
    storagePath = "tutorials/add_customer.mp4",
    thumbnailPath = null,
    durationSec = 40,
    sortOrder = 0,
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialHintCardExpandedPreview() {
    StitchPadTheme {
        TutorialHintCard(tutorial = sampleTutorial, expanded = true, onWatch = {}, onDismiss = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialHintCardCollapsedPreview() {
    StitchPadTheme {
        TutorialHintCard(tutorial = sampleTutorial, expanded = false, onWatch = {}, onDismiss = {})
    }
}
