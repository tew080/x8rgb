package com.x8rgb.domain.engine

import com.x8rgb.domain.model.RgbColor
import org.jtransforms.fft.FloatFFT_1D
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Wraps JTransforms FloatFFT_1D for music-reactive RGB mapping.
 *
 * Frequency → Color mapping:
 *   Bass  (0–300 Hz)   → Red
 *   Mid   (300–2kHz)   → Green
 *   Treble(2k–8kHz)    → Blue
 */
@Singleton
class FftProcessor @Inject constructor() {

    private var fft: FloatFFT_1D? = null
    private var lastSize: Int = 0

    fun process(pcmBuffer: FloatArray, sampleRate: Int = 44100): RgbColor {
        val n = pcmBuffer.size
        if (n < 2) return RgbColor.OFF

        if (n != lastSize) {
            fft = FloatFFT_1D(n.toLong())
            lastSize = n
        }

        val data = pcmBuffer.copyOf()

        // Apply Hann window
        for (i in data.indices) {
            val window = 0.5f * (1f - cos(2.0 * Math.PI * i / (n - 1)).toFloat())
            data[i] *= window
        }

        fft!!.realForward(data)

        val binHz = sampleRate.toFloat() / n
        var bass  = 0f
        var mid   = 0f
        var high  = 0f

        for (k in 1 until n / 2) {
            val re  = data[2 * k]
            val im  = data[2 * k + 1]
            val mag = sqrt(re * re + im * im)
            val hz  = k * binHz

            when {
                hz < 300f  -> bass += mag
                hz < 2000f -> mid  += mag
                hz < 8000f -> high += mag
            }
        }

        // Calculate average amplitude (rough volume)
        val sum = (bass + mid + high) / n
        val sensitivity = 50f 
        val gain = (sum * sensitivity).coerceIn(0f, 1f)

        val maxE = maxOf(bass, mid, high, 1e-6f)
        
        return RgbColor(
            r = (255 * (bass / maxE) * gain).toInt().coerceIn(0, 255),
            g = (255 * (mid  / maxE) * gain).toInt().coerceIn(0, 255),
            b = (255 * (high / maxE) * gain).toInt().coerceIn(0, 255),
        )
    }
}
