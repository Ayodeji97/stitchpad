package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.community_banner_dismiss_cd
import stitchpad.composeapp.generated.resources.community_banner_icon_cd
import stitchpad.composeapp.generated.resources.community_banner_title
import stitchpad.composeapp.generated.resources.community_strip_join
import stitchpad.composeapp.generated.resources.community_strip_subtitle
import stitchpad.composeapp.generated.resources.ic_whatsapp_glyph

private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Slim, low-key dashboard invite to the WhatsApp community. Outline (no filled
 * card), placed below the hero focus card so it never competes with the
 * tailor's work. The whole row is the join target; the trailing ✕ dismisses.
 */
@Composable
fun CommunityStrip(
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onJoin)
                .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(DesignTokens.radiusSm))
                    .background(WhatsAppGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_whatsapp_glyph),
                    contentDescription = stringResource(Res.string.community_banner_icon_cd),
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(DesignTokens.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.community_banner_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.community_strip_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(DesignTokens.space2))
            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.community_banner_dismiss_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.size(DesignTokens.space1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.community_strip_join),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityStripPreviewLight() {
    StitchPadTheme(darkTheme = false) {
        CommunityStrip(onJoin = {}, onDismiss = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityStripPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        CommunityStrip(onJoin = {}, onDismiss = {})
    }
}
