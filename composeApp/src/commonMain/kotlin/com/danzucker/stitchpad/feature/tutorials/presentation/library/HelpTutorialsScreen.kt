package com.danzucker.stitchpad.feature.tutorials.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import com.danzucker.stitchpad.feature.tutorials.presentation.components.TutorialThumbnail
import com.danzucker.stitchpad.feature.tutorials.presentation.tutorialDescription
import com.danzucker.stitchpad.feature.tutorials.presentation.tutorialTitle
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorials_empty
import stitchpad.composeapp.generated.resources.tutorials_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTutorialsScreen(
    state: HelpTutorialsState,
    onAction: (HelpTutorialsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.tutorials_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(HelpTutorialsAction.OnBack) }) {
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
    ) { padding: PaddingValues ->
        when {
            state.isLoading && state.tutorials.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    LoadingDots()
                }
            }

            state.tutorials.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(Res.string.tutorials_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = DesignTokens.space4,
                        end = DesignTokens.space4,
                        top = padding.calculateTopPadding() + DesignTokens.space3,
                        bottom = padding.calculateBottomPadding() + DesignTokens.space6,
                    ),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                ) {
                    items(state.tutorials, key = { it.id }) { tutorial ->
                        TutorialListCard(
                            tutorial = tutorial,
                            onClick = { onAction(HelpTutorialsAction.OnTutorialClick(tutorial.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialListCard(
    tutorial: Tutorial,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(DesignTokens.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TutorialThumbnail(
                durationSec = tutorial.durationSec,
                modifier = Modifier.size(width = 96.dp, height = 64.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
                Text(
                    text = tutorialTitle(tutorial),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tutorialDescription(tutorial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun HelpTutorialsScreenPreview() {
    StitchPadTheme {
        HelpTutorialsScreen(
            state = HelpTutorialsState(
                isLoading = false,
                tutorials = listOf(
                    Tutorial(TutorialTopic.AddCustomer.id, TutorialTopic.AddCustomer.id, "", "", "x", null, 40, 0),
                    Tutorial(TutorialTopic.CreateOrder.id, TutorialTopic.CreateOrder.id, "", "", "x", null, 55, 1),
                ),
            ),
            onAction = {},
        )
    }
}
