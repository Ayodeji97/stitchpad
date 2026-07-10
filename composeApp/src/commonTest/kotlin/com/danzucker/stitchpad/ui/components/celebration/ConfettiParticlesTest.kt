package com.danzucker.stitchpad.ui.components.celebration

import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfettiParticlesTest {

    private val palette = listOf(Color.Blue, Color.Red, Color.White)
    private val saffron = Color.Yellow

    @Test
    fun generatesRequestedCount() {
        assertEquals(70, generateConfetti(Random(1), palette, saffron).size)
    }

    @Test
    fun everyTwelfthParticleIsSaffron() {
        val particles = generateConfetti(Random(1), palette, saffron)
        particles.forEachIndexed { i, p ->
            if (i % 12 == 0) {
                assertEquals(saffron, p.color, "particle $i should be saffron")
            }
        }
        // Saffron stays rare: only the forced ones.
        assertEquals(6, particles.count { it.color == saffron })
    }

    @Test
    fun allThreeShapesArePresent() {
        val shapes = generateConfetti(Random(1), palette, saffron).map { it.shape }.toSet()
        assertEquals(ConfettiShape.entries.toSet(), shapes)
    }

    @Test
    fun particlesStartAtOriginAndFollowGravity() {
        val p = generateConfetti(Random(1), palette, saffron).first()
        assertEquals(p.startX, p.xAt(0f))
        assertEquals(p.startY, p.yAt(0f))
        // Burst goes up first...
        assertTrue(p.velocityY < 0f)
        // ...but gravity wins by the end of the animation.
        assertTrue(p.yAt(CONFETTI_DURATION_SECONDS) > p.startY)
    }

    @Test
    fun alphaIsOpaqueThenFadesToZero() {
        assertEquals(1f, confettiAlphaAt(0f))
        assertEquals(1f, confettiAlphaAt(0.69f))
        assertEquals(0f, confettiAlphaAt(1f))
        assertTrue(confettiAlphaAt(0.85f) in 0.01f..0.99f)
    }
}
