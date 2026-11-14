package com.x8rgb.domain.engine

import com.x8rgb.domain.model.RgbColor
import org.jtransforms.fft.FloatFFT_1D
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wraps JTransforms FloatFFT_1D for music-reactive RGB mapping.
 *
 * Not thread-safe — call only from a single coroutine dispatcher (IO recommended).
 * FloatFFT_1D is reused per buffer size; recreated only when size changes.
 *
 * Frequency → Color mapping:
 *   Bass  (0–300 Hz)   → Red intensity
 *   Mid   (300–2kHz)   → Green intensity
 *   Treble(2k–8kHz)    → Blue intensity
 */
@Singleton
class FftProcessor @Inject constructor() {

    private var fft: FloatFFT_1D? = null
    private var lastSize: Int = 0

    /**
     * @param pcmBuffer   Raw 16-bit PCM samples (mono) as FloatArray in [-1,1]
     * @param sampleRate  Device sample rate (typically 44100 or 48000)
     * @return RgbColor representing frequency energy distribution
     */
    fun process(pcmBuffer: FloatArray, sampleRate: Int = 44100): RgbColor {
        val n = pcmBuffer.size
        if (n == 0) return RgbColor.OFF

        // Reuse FFT instance if same size; avoid allocation on hot path
        if (n != lastSize) {
            fft = FloatFFT_1D(n.toLong())
            lastSize = n
        }

        // Copy to avoid mutating caller's buffer
        val data = pcmBuffer.copyOf()

        // Apply Hann window to reduce spectral leakage
        for (i in data.indices) {
            val window = 0.5f * (1f - kotlin.math.cos(2.0 * Math.PI * i / (n - 1)).toFloat())
            data[i] *= window
        }

        fft!!.realForward(data)   // in-place; output is [Re0, Im0, Re1, Im1, ...]

        val binHz = sampleRate.toFloat() / n
        var bassEnergy  = 0f
        var midEnergy   = 0f
        var trebleEnergy = 0f

        // Iterate only first half (Nyquist limit)
        for (k in 1 until n / 2) {
            val re  = data[2 * k]
            val im  = data[2 * k + 1]
            val mag = sqrt(re * re + im * im)
            val hz  = k * binHz

            when {
                hz < 300f  -> bassEnergy   += mag
                hz < 2000f -> midEnergy    += mag
                hz < 8000f -> trebleEnergy += mag
            }
        }

        val maxE = maxOf(bassEnergy, midEnergy, trebleEnergy, 1f)  // avoid /0
        return RgbColor(
            r = (255 * (bassEnergy   / maxE)).toInt().coerceIn(0, 255),
            g = (255 * (midEnergy    / maxE)).toInt().coerceIn(0, 255),
            b = (255 * (trebleEnergy / maxE)).toInt().coerceIn(0, 255),
        )
    }
}
