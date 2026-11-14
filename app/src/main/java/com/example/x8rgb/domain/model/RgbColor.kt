package com.x8rgb.domain.model

/**
 * Immutable RGB color with system brightness.
 * brightness: 80–255 (HyperOS lamp_effect_brightness scale).
 * Stored as a single Long in Room for atomic read/write.
 */
@JvmInline
value class RgbColor(val packed: Long) {

    constructor(r: Int, g: Int, b: Int, brightness: Int = DEFAULT_BRIGHTNESS) : this(
        pack(
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255),
            brightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        )
    )

    val r: Int          get() = ((packed shr 24) and 0xFF).toInt()
    val g: Int          get() = ((packed shr 16) and 0xFF).toInt()
    val b: Int          get() = ((packed shr 8)  and 0xFF).toInt()
    val brightness: Int get() = (packed and 0xFF).toInt()

    /** Compose-compatible ARGB with brightness applied as alpha scaling. */
    fun toArgb(): Int {
        val scale = brightness / 255f
        return (0xFF shl 24) or
               ((r * scale).toInt() shl 16) or
               ((g * scale).toInt() shl 8)  or
               (b * scale).toInt()
    }

    companion object {
        const val BRIGHTNESS_MIN     = 80
        const val BRIGHTNESS_MAX     = 255
        const val DEFAULT_BRIGHTNESS = 130

        val OFF = RgbColor(0, 0, 0, BRIGHTNESS_MIN)

        private fun pack(r: Int, g: Int, b: Int, brightness: Int): Long =
            (r.toLong() shl 24) or (g.toLong() shl 16) or (b.toLong() shl 8) or brightness.toLong()
    }
}
