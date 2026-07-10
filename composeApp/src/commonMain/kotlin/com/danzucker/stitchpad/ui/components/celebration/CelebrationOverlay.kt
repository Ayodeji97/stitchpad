package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadMark
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.celebration_continue
import stitchpad.composeapp.generated.resources.celebration_first_customer_body
import stitchpad.composeapp.generated.resources.celebration_first_customer_title
import stitchpad.composeapp.generated.resources.celebration_first_order_body
import stitchpad.composeapp.generated.resources.celebration_first_order_title
import stitchpad.composeapp.generated.resources.celebration_workshop_body
import stitchpad.composeapp.generated.resources.celebration_workshop_button
import stitchpad.composeapp.generated.resources.celebration_workshop_title
import kotlin.random.Random

private const val SCRIM_FADE_MS = 200
private const val MS_PER_SECOND = 1_000
private val CONFETTI_DURATION_MS = (CONFETTI_DURATION_SECONDS * MS_PER_SECOND).toInt()
private const val CARD_DELAY_MS = 150L
private const val EMBLEM_DELAY_MS = 120L
private const val SCRIM_ALPHA_LIGHT = 0.45f
private const val SCRIM_ALPHA_DARK = 0.60f
private const val CARD_START_SCALE = 0.6f

/**
 * App-root host: layered over the NavHost in App.kt so celebrations play over
 * whatever screen the milestone's own navigation lands on — no flow is delayed.
 */
@Composable
fun CelebrationOverlayHost(modifier: Modifier = Modifier) {
    val controller = koinInject<CelebrationController>()
    val milestone by controller.current.collectAsState()
    val current = milestone ?: return
    CelebrationOverlay(
        milestone = current,
        onDismiss = { controller.dismiss(current) },
        modifier = modifier,
    )
}

@Composable
private fun CelebrationOverlay(
    milestone: Milestone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val overlayAlpha = remember(milestone) { Animatable(0f) }
    val confettiProgress = remember(milestone) { Animatable(0f) }
    val cardScale = remember(milestone) { Animatable(if (reduceMotion) 1f else CARD_START_SCALE) }
    val emblemScale = remember(milestone) { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(milestone) {
        launch { overlayAlpha.animateTo(1f, tween(SCRIM_FADE_MS)) }
        if (!reduceMotion) {
            launch {
                confettiProgress.animateTo(
                    1f,
                    tween(CONFETTI_DURATION_MS, easing = LinearEasing),
                )
            }
            launch {
                delay(CARD_DELAY_MS)
                cardScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                delay(CARD_DELAY_MS + EMBLEM_DELAY_MS)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                emblemScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    val scrimAlpha = if (isDark) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color.Black.copy(alpha = scrimAlpha))
            // Keyed on milestone: when a queued celebration promotes in place,
            // the tap handler restarts with the fresh onDismiss closure —
            // pointerInput(Unit) would keep dismissing the stale milestone.
            .pointerInput(milestone) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        if (!reduceMotion) {
            ConfettiField(
                progress = { confettiProgress.value },
                isDark = isDark,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CelebrationCard(
            milestone = milestone,
            emblemScale = { emblemScale.value },
            onDismiss = onDismiss,
            modifier = Modifier.graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
            },
        )
    }
}

@Composable
private fun ConfettiField(
    progress: () -> Float,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    // Dark mode swaps paper-tone pieces for lighter indigo/cream so particles
    // stay visible on the darker scrim (spec: both color modes defined).
    val palette = if (isDark) {
        listOf(
            DesignTokens.indigo200,
            DesignTokens.indigo400,
            DesignTokens.sienna300,
            DesignTokens.paperLight,
        )
    } else {
        listOf(
            DesignTokens.indigo500,
            DesignTokens.indigo400,
            DesignTokens.sienna500,
            DesignTokens.paperLight,
        )
    }
    val saffron = DesignTokens.saffron500
    val particles = remember(isDark) { generateConfetti(Random, palette, saffron) }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val t = progress() * CONFETTI_DURATION_SECONDS
        val alpha = confettiAlphaAt(progress())
        if (alpha <= 0f) return@Canvas
        particles.forEach { particle -> drawParticle(particle, t, alpha) }
    }
}

private fun DrawScope.drawParticle(p: ConfettiParticle, t: Float, alpha: Float) {
    val x = p.xAt(t) * size.width
    val y = p.yAt(t) * size.height
    if (y > size.height) return
    val sizePx = p.sizeFraction * size.width
    val color = p.color.copy(alpha = alpha)
    rotate(degrees = p.rotationAt(t), pivot = Offset(x, y)) {
        when (p.shape) {
            ConfettiShape.FABRIC -> drawRoundRect(
                color = color,
                topLeft = Offset(x - sizePx / 2, y - sizePx / 2),
                size = androidx.compose.ui.geometry.Size(sizePx, sizePx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(sizePx * 0.2f),
            )
            ConfettiShape.BUTTON -> {
                drawCircle(color = color, radius = sizePx / 2, center = Offset(x, y))
                val holeOffset = sizePx * 0.15f
                val holeRadius = sizePx * 0.07f
                val holeColor = Color.Black.copy(alpha = alpha * 0.3f)
                drawCircle(holeColor, holeRadius, Offset(x - holeOffset, y - holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x + holeOffset, y - holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x - holeOffset, y + holeOffset))
                drawCircle(holeColor, holeRadius, Offset(x + holeOffset, y + holeOffset))
            }
            ConfettiShape.THREAD -> {
                val path = Path().apply {
                    moveTo(x - sizePx, y)
                    quadraticTo(x - sizePx / 2, y - sizePx / 2, x, y)
                    quadraticTo(x + sizePx / 2, y + sizePx / 2, x + sizePx, y)
                }
                drawPath(path, color, style = Stroke(width = sizePx * 0.12f))
            }
        }
    }
}

@Composable
private fun CelebrationCard(
    milestone: Milestone,
    emblemScale: () -> Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(fraction = 0.85f)
            // Consume taps so a tap on the card doesn't fall through to the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics(mergeDescendants = true) { },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation3,
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = emblemScale()
                    scaleY = emblemScale()
                },
            ) {
                CelebrationEmblem(milestone)
            }
            Spacer(Modifier.height(DesignTokens.space4))
            Text(
                text = milestone.title(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = milestone.body(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DesignTokens.space6))
            StitchPadButton(
                text = milestone.buttonLabel(),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CelebrationEmblem(milestone: Milestone) {
    when (milestone) {
        is Milestone.WorkshopReady -> StitchPadMark(size = 64.dp)
        is Milestone.FirstCustomer -> Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        is Milestone.FirstOrder -> Icon(
            imageVector = Icons.Outlined.Checkroom,
            contentDescription = null,
            tint = LocalStitchPadColors.current.heritageAccent,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun Milestone.title(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_title)
    is Milestone.FirstCustomer -> stringResource(Res.string.celebration_first_customer_title)
    is Milestone.FirstOrder -> stringResource(Res.string.celebration_first_order_title)
}

@Composable
private fun Milestone.body(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_body)
    is Milestone.FirstCustomer ->
        stringResource(Res.string.celebration_first_customer_body, customerFirstName)
    is Milestone.FirstOrder ->
        stringResource(Res.string.celebration_first_order_body, customerFirstName)
}

@Composable
private fun Milestone.buttonLabel(): String = when (this) {
    is Milestone.WorkshopReady -> stringResource(Res.string.celebration_workshop_button)
    else -> stringResource(Res.string.celebration_continue)
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CelebrationCardPreview() {
    StitchPadTheme {
        CelebrationCard(
            milestone = Milestone.FirstCustomer("Adaeze"),
            emblemScale = { 1f },
            onDismiss = {},
        )
    }
}
