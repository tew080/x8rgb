package com.x8rgb.domain.engine

import com.x8rgb.domain.model.RgbColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class EffectEngine @Inject constructor() {

    /** Maps any stored/unknown effect ID to a valid one (falls back to Static). */
    fun sanitize(id: Int): Int = if (id in SELECTABLE_IDS) id else EFFECT_STATIC

    /** Single-colour output (whole ring). Pixel effects are averaged down. */
    fun compute(effectId: Int, t: Long, base: RgbColor): RgbColor =
        if (isPixelEffect(effectId)) averagePixels(computePixels(effectId, t, base, LED_COUNT))
        else when (effectId) {
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
            EFFECT_CANDLE         -> candle(t)
            EFFECT_POLICE         -> police(t)
            EFFECT_SUNRISE        -> sunrise(t)
            EFFECT_TWINKLE        -> twinkle(t, base)
            EFFECT_DISCO          -> disco(t)
            EFFECT_NEON           -> neon(t)
            EFFECT_MATRIX         -> matrix(t)
            EFFECT_LAVA           -> lava(t)
            EFFECT_RAINDROP       -> raindrop(t, base)
            EFFECT_SOS            -> sos(t, base)
            EFFECT_DOUBLE_BLINK   -> doubleBlink(t, base)
            else                  -> base
        }

    // ───────────────────────── Per-LED (pixel) effects ─────────────────────────

    fun isPixelEffect(id: Int) = id in EFFECT_PX_RAINBOW_WHEEL..EFFECT_PX_FIRE_RING

    /** Returns [n] colours packed as 0xRRGGBB, index 0 = first LED. */
    fun computePixels(effectId: Int, t: Long, base: RgbColor, n: Int = LED_COUNT): IntArray {
        val px = IntArray(n)
        val b = if (base == RgbColor.OFF) RgbColor(255, 255, 255) else base
        when (effectId) {
            EFFECT_PX_RAINBOW_WHEEL -> for (i in 0 until n) {
                val h = ((t * 3 + i * 360f / n) % 360f)
                px[i] = pack(hsvToRgb(h, 1f, 1f))
            }
            EFFECT_PX_CHASE_DOT -> {
                val pos = ((t / 2) % n).toInt()
                for (i in 0 until n) px[i] = pack(scale(b, if (i == pos) 1f else 0.04f))
            }
            EFFECT_PX_COMET_TAIL -> {
                val head = ((t / 2) % n).toInt()
                val tail = (n / 2).coerceAtLeast(3)
                for (i in 0 until n) {
                    val d = (head - i + n) % n
                    val k = if (d < tail) (1f - d / tail.toFloat()).pow(2) else 0.02f
                    px[i] = pack(scale(b, k.coerceAtLeast(0.02f)))
                }
            }
            EFFECT_PX_KNIGHT_RIDER -> {
                val period = (2 * (n - 1)).coerceAtLeast(1)
                val p = ((t / 2) % period).toInt()
                val pos = if (p < n) p else period - p
                for (i in 0 until n) {
                    val k = (1f - abs(i - pos) * 0.4f).coerceAtLeast(0.03f)
                    px[i] = pack(scale(b, k))
                }
            }
            EFFECT_PX_THEATER -> {
                val shift = ((t / 4) % 3).toInt()
                for (i in 0 until n) px[i] = if ((i + shift) % 3 == 0) pack(b) else 0
            }
            EFFECT_PX_SPARKLE -> for (i in 0 until n) {
                val r = hash(i * 7919L + t / 2)
                px[i] = if (r > 0.88f) pack(scale(b, 1f)) else pack(scale(b, 0.03f))
            }
            EFFECT_PX_SPIN_DUAL -> {
                val a = ((t / 2) % n).toInt()
                val c = (a + n / 2) % n
                for (i in 0 until n) {
                    val d1 = (a - i + n) % n
                    val d2 = (c - i + n) % n
                    val d = min(d1, d2)
                    val k = if (d < 3) 1f - d / 3f else 0.03f
                    px[i] = pack(scale(b, k.coerceAtLeast(0.03f)))
                }
            }
            EFFECT_PX_FILL_UNFILL -> {
                val p = ((t / 2) % (2 * n)).toInt()
                val lit = if (p < n) p + 1 else 2 * n - p
                for (i in 0 until n) px[i] = if (i < lit) pack(b) else 0
            }
            EFFECT_PX_WAVE -> for (i in 0 until n) {
                val k = (sin(t * 0.12 - i * 2 * PI / n) * 0.5 + 0.5).toFloat()
                px[i] = pack(scale(b, 0.05f + 0.95f * k))
            }
            EFFECT_PX_FIRE_RING -> for (i in 0 until n) {
                val k = 0.4f + 0.6f * hash(i * 104729L + t / 2)
                px[i] = pack(RgbColor((255 * k).toInt(), (90 * k * k).toInt(), 0))
            }
        }
        return px
    }

    private fun averagePixels(px: IntArray): RgbColor {
        if (px.isEmpty()) return RgbColor.OFF
        var r = 0; var g = 0; var b = 0
        for (c in px) { r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF }
        return RgbColor(r / px.size, g / px.size, b / px.size)
    }

    private fun pack(c: RgbColor) = (c.r shl 16) or (c.g shl 8) or c.b

    // ───────────────────────── Original effects ─────────────────────────

    private fun rainbow(t: Long): RgbColor = hsvToRgb((t * 2 % 360).toFloat(), 1f, 1f)

    private fun pulse(t: Long, base: RgbColor): RgbColor =
        scale(base, (sin(t * 0.15) * 0.4 + 0.6).toFloat())

    private fun breathe(t: Long, base: RgbColor): RgbColor =
        scale(base, (sin(t * 0.05) * 0.5 + 0.5).toFloat())

    private fun comet(t: Long): RgbColor =
        hsvToRgb(240f, 0.8f, (t % 40).toFloat() / 40f)

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

    private fun galaxy(t: Long): RgbColor = hsvToRgb((t * 0.5 % 360).toFloat(), 0.7f, 0.8f)

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
        return scale(base, 0.15f + 0.85f * peak)
    }

    private fun blink(t: Long, base: RgbColor) =
        if ((t / 10L) % 2L == 0L) base else RgbColor.OFF

    private fun strobe(t: Long, base: RgbColor) =
        if (t % 8L < 2L) base else RgbColor.OFF

    private fun wipe(t: Long, base: RgbColor) = scale(base, (t % 50L).toFloat() / 50f)

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

    private fun siren(t: Long) =
        if ((t / 12L) % 2L == 0L) RgbColor(255, 0, 0) else RgbColor(0, 80, 255)

    private fun chase(t: Long, base: RgbColor): RgbColor {
        val phase = (t % 16L).toInt()
        return scale(base, if (phase < 2) 1.0f else if (phase < 4) 0.3f else 0.05f)
    }

    // ───────────────────────── New whole-ring effects ─────────────────────────

    /** Warm, randomly flickering candle. */
    private fun candle(t: Long): RgbColor {
        val f = (0.6 + 0.25 * sin(t * 0.31) + 0.15 * hash(t / 2)).toFloat()
        return scale(RgbColor(255, 120, 20), f)
    }

    /** Police-style double flashes: red x3 then blue x3. */
    private fun police(t: Long): RgbColor = when ((t % 24L).toInt()) {
        0, 1, 4, 5, 8, 9       -> RgbColor(255, 0, 0)
        12, 13, 16, 17, 20, 21 -> RgbColor(0, 60, 255)
        else                   -> RgbColor.OFF
    }

    /** Slow dawn: deep purple -> orange -> warm white, ~20 s loop. */
    private fun sunrise(t: Long): RgbColor {
        val x = (t % 400L).toFloat() / 400f
        val a = RgbColor(40, 0, 60)
        val b = RgbColor(255, 90, 0)
        val c = RgbColor(255, 230, 150)
        return if (x < 0.5f) lerp(a, b, x * 2f) else lerp(b, c, (x - 0.5f) * 2f)
    }

    /** Random brightness twinkles of the base colour. */
    private fun twinkle(t: Long, base: RgbColor): RgbColor {
        val r = hash(t / 2)
        return scale(base, 0.1f + 0.9f * r * r * r)
    }

    /** Random hue every 6 ticks. */
    private fun disco(t: Long): RgbColor = hsvToRgb(hash(t / 6) * 360f, 1f, 1f)

    /** Smooth cycle: hot pink -> cyan -> violet. */
    private fun neon(t: Long): RgbColor {
        val pal = arrayOf(RgbColor(255, 0, 180), RgbColor(0, 255, 255), RgbColor(150, 0, 255))
        val x = (t % 180L).toFloat() / 60f
        val i = x.toInt() % 3
        return lerp(pal[i], pal[(i + 1) % 3], x - floor(x))
    }

    /** Green falling-code pulses. */
    private fun matrix(t: Long): RgbColor {
        val drop = (t % 30L).toFloat() / 30f
        val k = (1f - drop).pow(2) * 0.9f + 0.1f
        return scale(RgbColor(0, 255, 70), k)
    }

    /** Slow red/orange magma. */
    private fun lava(t: Long): RgbColor {
        val f = (sin(t * 0.02) * 0.5 + 0.5).toFloat()
        val k = (0.6 + 0.4 * sin(t * 0.05 + 1.0)).toFloat()
        return scale(RgbColor(255, (20 + 90 * f).toInt(), 0), k)
    }

    /** Sharp hit then exponential decay, like a drop landing. */
    private fun raindrop(t: Long, base: RgbColor): RgbColor =
        scale(base, exp(-(t % 50L) / 8.0).toFloat().coerceAtLeast(0.04f))

    /** Morse "... --- ..." (3 ticks per unit). */
    private fun sos(t: Long, base: RgbColor): RgbColor =
        if (SOS_PATTERN[((t / 3) % SOS_PATTERN.size).toInt()]) base else RgbColor.OFF

    private fun doubleBlink(t: Long, base: RgbColor): RgbColor {
        val p = (t % 40L).toInt()
        return if (p < 3 || p in 6..8) base else RgbColor.OFF
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** Deterministic pseudo-random 0..1 from a Long. */
    private fun hash(n: Long): Float {
        var x = n * -7046029254386353131L
        x = x xor (x ushr 32)
        x *= 0x45d9f3bL
        x = x xor (x ushr 29)
        return ((x ushr 8) and 0xFFFF).toFloat() / 65535f
    }

    private fun lerp(a: RgbColor, b: RgbColor, k: Float) = RgbColor(
        (a.r + (b.r - a.r) * k).toInt(),
        (a.g + (b.g - a.g) * k).toInt(),
        (a.b + (b.b - a.b) * k).toInt()
    )

    private fun scale(c: RgbColor, k: Float) = RgbColor(
        (c.r * k).toInt().coerceIn(0, 255),
        (c.g * k).toInt().coerceIn(0, 255),
        (c.b * k).toInt().coerceIn(0, 255)
    )

    private fun hsvToRgb(h0: Float, s: Float, v: Float): RgbColor {
        val h = ((h0 % 360f) + 360f) % 360f
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
        /** POCO X8 Pro: 2 rings x 4 segments. Segments are NOT individually addressable via the known binder call. */
        const val LED_COUNT = 4

        /** Pixel effects (ID 29-38) are hidden until per-segment control is confirmed (see DebugTestRunner "probe"). */
        const val PIXEL_EFFECTS_ENABLED = false

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

        // New whole-ring effects
        const val EFFECT_CANDLE         = 18
        const val EFFECT_POLICE         = 19
        const val EFFECT_SUNRISE        = 20
        const val EFFECT_TWINKLE        = 21
        const val EFFECT_DISCO          = 22
        const val EFFECT_NEON           = 23
        const val EFFECT_MATRIX         = 24
        const val EFFECT_LAVA           = 25
        const val EFFECT_RAINDROP       = 26
        const val EFFECT_SOS            = 27
        const val EFFECT_DOUBLE_BLINK   = 28

        // Per-LED effects (need per-LED binder support to look right)
        const val EFFECT_PX_RAINBOW_WHEEL = 29
        const val EFFECT_PX_CHASE_DOT     = 30
        const val EFFECT_PX_COMET_TAIL    = 31
        const val EFFECT_PX_KNIGHT_RIDER  = 32
        const val EFFECT_PX_THEATER       = 33
        const val EFFECT_PX_SPARKLE       = 34
        const val EFFECT_PX_SPIN_DUAL     = 35
        const val EFFECT_PX_FILL_UNFILL   = 36
        const val EFFECT_PX_WAVE          = 37
        const val EFFECT_PX_FIRE_RING     = 38

        private val SOS_PATTERN: BooleanArray = buildList {
            fun sym(on: Int) { repeat(on) { add(true) }; add(false) }
            repeat(3) { sym(1) }; add(false); add(false)
            repeat(3) { sym(3) }; add(false); add(false)
            repeat(3) { sym(1) }; repeat(6) { add(false) }
        }.toBooleanArray()

        private val BASE_NAMES = listOf(
            "Rainbow", "Pulse", "Breathe", "Comet", "Aurora",
            "Ocean", "Fire", "Galaxy", "Lightning", "Northern Light", "Static",
            "Scan L→R", "Blink", "Strobe", "Wipe", "Heartbeat", "Siren", "Chasing",
            "Candle", "Police", "Sunrise", "Twinkle", "Disco", "Neon", "Matrix",
            "Lava", "Raindrop", "SOS", "Double Blink"
        )

        private val PIXEL_NAMES = listOf(
            "Rainbow Wheel", "Chase Dot", "Comet Tail", "Knight Rider", "Theater",
            "Sparkle", "Dual Spin", "Fill & Unfill", "Wave", "Fire Ring"
        )

        val EFFECT_NAMES: List<String> =
            if (PIXEL_EFFECTS_ENABLED) BASE_NAMES + PIXEL_NAMES else BASE_NAMES

        /** IDs shown in the effect picker dialogs (every ID that has a name). */
        val SELECTABLE_IDS: List<Int> = EFFECT_NAMES.indices.toList()
    }
}