package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_empty_pipeline

private val ILLUSTRATION_BACKDROP_WIDTH = 110.dp
private val ILLUSTRATION_INNER_SIZE = 92.dp

/**
 * Hero-style empty state for the work pipeline. Featured saffron border,
 * tinted illustration tile on the left, and a primary CTA below the copy.
 *
 * Used in place of the generic [EmptyIllustrationCard] when the pipeline is
 * empty so the section earns its own visual weight and a clear next step.
 * IntrinsicSize.Min lets the illustration tile stretch to whatever height
 * the right-hand text column lands on — no fixed band of empty padding.
 */
@Composable
fun PipelineEmptyHeroCard(
    sectionLabel: String,
    title: String,
    supporting: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val scheme = MaterialTheme.colorScheme
    val backdropAlpha = if (LocalIsDarkTheme.current) 0.08f else 0.12f
    val backdropColor = scheme.primary.copy(alpha = backdropAlpha)
    val backdropShape = RoundedCornerShape(DesignTokens.radiusMd)

    Surface(
        shape = shape,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            modifier = Modifier
                .padding(DesignTokens.space4)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(
                modifier = Modifier
                    .width(ILLUSTRATION_BACKDROP_WIDTH)
                    .fillMaxHeight()
                    .clip(backdropShape)
                    .background(color = backdropColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.dashboard_empty_pipeline),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(ILLUSTRATION_INNER_SIZE),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionPill(label = sectionLabel)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DesignTokens.space1))
                Button(
                    onClick = onCtaClick,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.iconInline),
                    )
                    Spacer(Modifier.size(DesignTokens.space1))
                    Text(
                        text = ctaLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionPill(label: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusSm))
            .background(color = scheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = DesignTokens.space2, vertical = 3.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineEmptyHeroCardLightPreview() {
    StitchPadTheme {
        PipelineEmptyHeroCard(
            sectionLabel = "Empty pipeline",
            title = "Nothing in progress yet",
            supporting = "Start your first order to unlock cutting, sewing, fitting, and delivery stages.",
            ctaLabel = "Add first order",
            onCtaClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineEmptyHeroCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineEmptyHeroCard(
            sectionLabel = "Empty pipeline",
            title = "Nothing in progress yet",
            supporting = "Start your first order to unlock cutting, sewing, fitting, and delivery stages.",
            ctaLabel = "Add first order",
            onCtaClick = {},
            modifier = Modifier.padding(DesignTokens.space4),
        )
    }
}
