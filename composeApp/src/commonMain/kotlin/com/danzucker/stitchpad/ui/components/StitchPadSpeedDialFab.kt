package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val FAB_SHADOW_ELEVATION = 12.dp
private val FAB_CORNER_RADIUS = 16.dp
private val MINI_FAB_SIZE = 48.dp
private const val ROTATION_DURATION_MS = 200
private const val MINI_FAB_STAGGER_MS = 30

/**
 * One action surfaced by [StitchPadSpeedDialFab]. Actions render
 * top-to-bottom in the order given, stacked ABOVE the main FAB —
 * so the LAST action in the list sits closest to the main FAB.
 *
 * For example, passing `[customer, order]` renders:
 *   customer  (top)
 *   order     (closest to FAB)
 *   [main FAB]
 */
data class SpeedDialAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Speed-dial floating action button. The main FAB toggles expansion;
 * each mini-FAB renders with a pill-label to its left.
 *
 * Expansion state is **lifted** so the caller can render a backdrop
 * scrim covering the screen — the Scaffold's `floatingActionButton`
 * slot only covers the FAB's own bounds.
 *
 * Mini-FAB taps invoke their `onClick` and DO NOT auto-collapse —
 * collapse is the caller's responsibility (typically by setting
 * `isExpanded = false` inside the click lambda passed in).
 */
@Composable
fun StitchPadSpeedDialFab(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: List<SpeedDialAction>,
    closeContentDescription: String,
    addContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FAB_CORNER_RADIUS)
    val targetRotation = if (isExpanded) 45f else 0f
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = ROTATION_DURATION_MS),
        label = "speed_dial_rotation",
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier,
    ) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = ROTATION_DURATION_MS,
                        delayMillis = index * MINI_FAB_STAGGER_MS,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = ROTATION_DURATION_MS,
                        delayMillis = index * MINI_FAB_STAGGER_MS,
                    ),
                    initialOffsetY = { it / 2 },
                ),
                // Exit collapses all mini-FABs simultaneously (no stagger) — a faster
                // dismissal felt right; symmetric stagger was not required by spec.
                exit = fadeOut(animationSpec = tween(durationMillis = ROTATION_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = ROTATION_DURATION_MS),
                        targetOffsetY = { it / 2 },
                    ),
            ) {
                MiniFabRow(action = action, shape = shape)
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.shadow(
                elevation = FAB_SHADOW_ELEVATION,
                shape = shape,
                spotColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) closeContentDescription else addContentDescription,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun MiniFabRow(
    action: SpeedDialAction,
    shape: RoundedCornerShape,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier
            .clip(shape)
            .clickable(role = Role.Button, onClick = action.onClick),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(DesignTokens.radiusSm),
            shadowElevation = 2.dp,
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space3,
                    vertical = DesignTokens.space2,
                ),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(MINI_FAB_SIZE)
                .shadow(elevation = 6.dp, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadSpeedDialFabCollapsedPreview() {
    StitchPadTheme {
        StitchPadSpeedDialFab(
            isExpanded = false,
            onToggle = {},
            actions = emptyList(),
            closeContentDescription = "Close",
            addContentDescription = "Add",
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadSpeedDialFabExpandedPreview() {
    StitchPadTheme {
        StitchPadSpeedDialFab(
            isExpanded = true,
            onToggle = {},
            actions = listOf(
                SpeedDialAction(
                    label = "Add customer",
                    icon = Icons.Default.Person,
                    contentDescription = "Add a new customer",
                    onClick = {},
                ),
                SpeedDialAction(
                    label = "Add order",
                    icon = Icons.Default.Add,
                    contentDescription = "Add a new order",
                    onClick = {},
                ),

            ),
            closeContentDescription = "Close",
            addContentDescription = "Add",
        )
    }
}
