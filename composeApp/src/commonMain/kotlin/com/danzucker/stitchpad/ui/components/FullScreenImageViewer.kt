package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_close_image_viewer

@Composable
fun FullScreenImageViewer(
    images: List<Any>,
    startIndex: Int = 0,
    contentDescription: String? = null,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val closeCd = stringResource(Res.string.cd_close_image_viewer)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
                .semantics {
                    role = Role.Button
                    this.contentDescription = closeCd
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            ) { page ->
                SubcomposeAsyncImage(
                    model = images[page],
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) { LoadingDots() }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Counter pill (top-left, doesn't overlap with close button)
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // Dots (bottom-center)
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(images.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(
                                    width = if (active) 22.dp else 7.dp,
                                    height = 7.dp,
                                )
                                .background(
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeCd,
                    tint = Color.White,
                )
            }
        }
    }
}
