package com.danzucker.stitchpad.ui.components.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.review.presentation.ReviewController
import com.danzucker.stitchpad.feature.review.presentation.ReviewEffect
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.review_sentiment_love
import stitchpad.composeapp.generated.resources.review_sentiment_not_really
import stitchpad.composeapp.generated.resources.review_sentiment_subtitle
import stitchpad.composeapp.generated.resources.review_sentiment_title

/**
 * App-root host: shows the sentiment bottom sheet over whatever screen the user is on
 * when the controller arms it, and runs Compose-bound effects (opening the feedback URL).
 */
@Composable
fun ReviewPromptHost(content: @Composable () -> Unit) {
    val controller = koinInject<ReviewController>()
    val show by controller.current.collectAsState()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        controller.effects.collect { effect ->
            when (effect) {
                is ReviewEffect.OpenFeedback -> runCatching { uriHandler.openUri(effect.url) }
            }
        }
    }
    content()
    if (show) {
        ReviewSentimentSheet(
            onLoveIt = controller::onLoveIt,
            onNotReally = controller::onNotReally,
            onDismiss = controller::onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSentimentSheet(
    onLoveIt: () -> Unit,
    onNotReally: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        ) {
            Text(
                text = stringResource(Res.string.review_sentiment_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.review_sentiment_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space4))
            StitchPadButton(
                text = stringResource(Res.string.review_sentiment_love),
                onClick = onLoveIt,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onNotReally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.review_sentiment_not_really))
            }
            Spacer(Modifier.height(DesignTokens.space2))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReviewSentimentSheetPreviewLight() {
    StitchPadTheme { ReviewSentimentSheet(onLoveIt = {}, onNotReally = {}, onDismiss = {}) }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReviewSentimentSheetPreviewDark() {
    StitchPadTheme(darkTheme = true) { ReviewSentimentSheet(onLoveIt = {}, onNotReally = {}, onDismiss = {}) }
}
