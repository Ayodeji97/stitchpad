package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.community_banner_body
import stitchpad.composeapp.generated.resources.community_banner_cta
import stitchpad.composeapp.generated.resources.community_banner_dismiss_cd
import stitchpad.composeapp.generated.resources.community_banner_icon_cd
import stitchpad.composeapp.generated.resources.community_banner_title

@Composable
fun CommunityBanner(
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
    ) {
        Box {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.community_banner_dismiss_cd),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(DesignTokens.space4)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(DesignTokens.radiusSm))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = rememberVectorPainter(Icons.Outlined.Groups),
                        contentDescription = stringResource(Res.string.community_banner_icon_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(DesignTokens.space3))
                Text(
                    text = stringResource(Res.string.community_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DesignTokens.space1))
                Text(
                    text = stringResource(Res.string.community_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DesignTokens.space3))
                Button(onClick = onJoin) {
                    Text(stringResource(Res.string.community_banner_cta))
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityBannerPreviewLight() {
    StitchPadTheme(darkTheme = false) {
        CommunityBanner(onJoin = {}, onDismiss = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CommunityBannerPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        CommunityBanner(onJoin = {}, onDismiss = {})
    }
}
