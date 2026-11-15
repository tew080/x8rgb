package com.x8rgb.domain.engine

import com.x8rgb.domain.model.RgbColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Generates RGB frames for lighting effects.
 */
@Singleton
class EffectEngine @Inject constructor() {

    fun compute(effectId: Int, t: Long, base: RgbColor): RgbColor =
        when (effectId) {
            EFFECT_RAINBOW        -> rainbow(t)
            EFFECT_PULSE          -> pulse(t, base)
            EFFECT_BREATHE        -> breathe(t, base)
            EFFECT_COMET          -> comet(t)
            EFFECT_AURORA         -> aurora(t)
            EFFECT_OCEAN          -> ocean(t)
            EFFECT_FIRE           -> fire(t)
            EFFECT_GALAXY         -> galaxy(t)
            EFFECT_LIGHTNING      -> lightning(t)
            EFFECT_NORTHERN_LIGHT -> northernLight(t)
            else                  -> base
        }

    private fun rainbow(t: Long): RgbColor {
        val h = (t * 2 % 360).toFloat()
        return hsvToRgb(h, 1f, 1f)
    }

    private fun pulse(t: Long, base: RgbColor): RgbColor {
        val factor = (sin(t * 0.15) * 0.4 + 0.6).toFloat()
        return scale(base, factor)
    }

    private fun breathe(t: Long, base: RgbColor): RgbColor {
        val factor = (sin(t * 0.05) * 0.5 + 0.5).toFloat()
        return scale(base, factor)
    }

    private fun comet(t: Long): RgbColor {
        val phase = (t % 40).toFloat() / 40f
        return hsvToRgb(240f, 0.8f, phase)
    }

    private fun aurora(t: Long): RgbColor {
        val r = (sin(t * 0.03) * 127 + 128).toInt()
        val g = (sin(t * 0.03 + 2.0) * 127 + 128).toInt()
        val b = (sin(t * 0.03 + 4.0) * 127 + 128).toInt()
        return RgbColor(r, g, b)
    }

    private fun ocean(t: Long): RgbColor {
        val wave = sin(t * 0.04).toFloat() * 0.5f + 0.5f
        return RgbColor(0, (100 + 155 * wave).toInt(), 255)
    }

    private fun fire(t: Long): RgbColor {
        val flicker = (sin(t * 0.2) * 0.2 + 0.8).toFloat()
        return RgbColor(255, (100 * flicker).toInt(), 0)
    }

    private fun galaxy(t: Long): RgbColor {
        val h = (t * 0.5 % 360).toFloat()
        return hsvToRgb(h, 0.7f, 0.8f)
    }

    private fun lightning(t: Long): RgbColor {
        val on = (t % 50L < 2L) || (t % 87L < 1L)
        return if (on) RgbColor(255, 255, 255) else RgbColor.OFF
    }

    private fun northernLight(t: Long): RgbColor {
        val r = (sin(t * 0.02) * 50 + 50).toInt()
        val g = (sin(t * 0.015 + 1.0) * 150 + 100).toInt()
        val b = (sin(t * 0.025 + 2.0) * 100 + 150).toInt()
        return RgbColor(r, g, b)
    }

    private fun scale(c: RgbColor, k: Float) = RgbColor(
        (c.r * k).toInt().coerceIn(0, 255),
        (c.g * k).toInt().coerceIn(0, 255),
        (c.b * k).toInt().coerceIn(0, 255)
    )

    private fun hsvToRgb(h: Float, s: Float, v: Float): RgbColor {
        val c = v * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f  -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else     -> Triple(c, 0f, x)
        }
        return RgbColor(((r1 + m) * 255).toInt(), ((g1 + m) * 255).toInt(), ((b1 + m) * 255).toInt())
    }

    companion object {
        const val EFFECT_RAINBOW        = 0
        const val EFFECT_PULSE          = 1
        const val EFFECT_BREATHE        = 2
        const val EFFECT_COMET          = 3
        const val EFFECT_AURORA         = 4
        const val EFFECT_OCEAN          = 5
        const val EFFECT_FIRE           = 6
        const val EFFECT_GALAXY         = 7
        const val EFFECT_LIGHTNING      = 8
        const val EFFECT_NORTHERN_LIGHT = 9
        const val EFFECT_STATIC         = 10

        val EFFECT_NAMES = listOf(
            "Rainbow", "Pulse", "Breathe", "Comet", "Aurora",
            "Ocean", "Fire", "Galaxy", "Lightning", "Northern Light", "Static"
        )
    }
}
