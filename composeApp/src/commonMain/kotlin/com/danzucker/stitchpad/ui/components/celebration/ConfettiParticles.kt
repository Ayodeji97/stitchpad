package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Pure confetti model — no Compose runtime, fully unit-testable. All positions
 * and sizes are fractions of the screen (x,size: of width; y: of height) so the
 * physics is density- and screen-size-independent; the overlay scales to px at
 * draw time. Particle position is a closed-form function of elapsed time, so
 * rendering is a cheap pure computation per frame with zero per-frame state.
 */
enum class ConfettiShape { FABRIC, BUTTON, THREAD }

data class ConfettiParticle(
    val shape: ConfettiShape,
    val color: Color,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val sizeFraction: Float,
    val rotation0: Float,
    val spin: Float,
)

const val CONFETTI_COUNT = 70
const val CONFETTI_DURATION_SECONDS = 2.5f
const val CONFETTI_GRAVITY = 1.6f
private const val SAFFRON_EVERY = 12
private const val FABRIC_WEIGHT = 0.45f
private const val BUTTON_WEIGHT = 0.30f

@Suppress("MagicNumber")
fun generateConfetti(
    random: Random,
    palette: List<Color>,
    saffron: Color,
    count: Int = CONFETTI_COUNT,
): List<ConfettiParticle> = List(count) { i ->
    // Three burst origins: top-center, top-left, top-right.
    val origin = i % 3
    val startX = when (origin) {
        0 -> 0.5f
        1 -> 0.08f
        else -> 0.92f
    }
    val velocityX = when (origin) {
        0 -> random.nextFloat() * 0.8f - 0.4f
        1 -> random.nextFloat() * 0.5f
        else -> -(random.nextFloat() * 0.5f)
    }
    val shapeRoll = random.nextFloat()
    val shape = when {
        shapeRoll < FABRIC_WEIGHT -> ConfettiShape.FABRIC
        shapeRoll < FABRIC_WEIGHT + BUTTON_WEIGHT -> ConfettiShape.BUTTON
        else -> ConfettiShape.THREAD
    }
    ConfettiParticle(
        shape = shape,
        color = if (i % SAFFRON_EVERY == 0) saffron else palette[random.nextInt(palette.size)],
        startX = startX,
        startY = 0.18f,
        velocityX = velocityX,
        velocityY = -(0.25f + random.nextFloat() * 0.45f),
        sizeFraction = 0.012f + random.nextFloat() * 0.018f,
        rotation0 = random.nextFloat() * 360f,
        spin = (random.nextFloat() - 0.5f) * 720f,
    )
}

fun ConfettiParticle.xAt(t: Float): Float = startX + velocityX * t

fun ConfettiParticle.yAt(t: Float): Float =
    startY + velocityY * t + 0.5f * CONFETTI_GRAVITY * t * t

fun ConfettiParticle.rotationAt(t: Float): Float = rotation0 + spin * t

private const val FADE_START = 0.7f

/** Fully opaque for the first 70% of the animation, linear fade over the last 30%. */
fun confettiAlphaAt(progress: Float): Float =
    if (progress < FADE_START) 1f else ((1f - progress) / (1f - FADE_START)).coerceIn(0f, 1f)
