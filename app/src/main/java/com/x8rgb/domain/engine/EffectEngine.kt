package com.x8rgb.domain.engine

import com.x8rgb.domain.model.RgbColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

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
            EFFECT_SCAN           -> scan(t, base)
            EFFECT_BLINK          -> blink(t, base)
            EFFECT_STROBE         -> strobe(t, base)
            EFFECT_WIPE           -> wipe(t, base)
            EFFECT_HEARTBEAT      -> heartbeat(t, base)
            EFFECT_SIREN          -> siren(t)
            EFFECT_CHASE          -> chase(t, base)
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

    private fun scan(t: Long, base: RgbColor): RgbColor {
        val phase = (t % 40L).toFloat() / 40f
        val peak = 1f - abs(phase - 0.5f) * 2f
        val k = 0.15f + 0.85f * peak
        return scale(base, k)
    }

    private fun blink(t: Long, base: RgbColor): RgbColor {
        return if ((t / 10L) % 2L == 0L) base else RgbColor.OFF
    }

    private fun strobe(t: Long, base: RgbColor): RgbColor {
        return if (t % 8L < 2L) base else RgbColor.OFF
    }

    private fun wipe(t: Long, base: RgbColor): RgbColor {
        val k = ((t % 50L).toFloat() / 50f)
        return scale(base, k)
    }

    private fun heartbeat(t: Long, base: RgbColor): RgbColor {
        val p = (t % 60L).toInt()
        val k = when {
            p < 6 -> p / 6f
            p < 12 -> (12 - p) / 6f
            p < 18 -> (p - 12) / 6f * 0.7f
            p < 24 -> (24 - p) / 6f * 0.7f
            else -> 0.08f
        }
        return scale(base, k)
    }

    private fun siren(t: Long): RgbColor {
        return if ((t / 12L) % 2L == 0L) RgbColor(255, 0, 0) else RgbColor(0, 80, 255)
    }

    /** Sharp chasing pulse - rotating LEDs one by one */
    private fun chase(t: Long, base: RgbColor): RgbColor {
        val phase = (t % 16L).toInt()
        val k = if (phase < 2) 1.0f else if (phase < 4) 0.3f else 0.05f
        return scale(base, k)
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
        const val EFFECT_SCAN           = 11
        const val EFFECT_BLINK          = 12
        const val EFFECT_STROBE         = 13
        const val EFFECT_WIPE           = 14
        const val EFFECT_HEARTBEAT      = 15
        const val EFFECT_SIREN          = 16
        const val EFFECT_CHASE          = 17

        val EFFECT_NAMES = listOf(
            "Rainbow", "Pulse", "Breathe", "Comet", "Aurora",
            "Ocean", "Fire", "Galaxy", "Lightning", "Northern Light", "Static",
            "Scan L→R", "Blink", "Strobe", "Wipe", "Heartbeat", "Siren", "Chasing"
        )
    }
}
