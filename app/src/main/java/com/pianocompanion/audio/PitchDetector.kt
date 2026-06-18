package com.pianocompanion.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * YIN pitch detection algorithm.
 *
 * A fundamental frequency estimation algorithm based on the difference function,
 * which is a modification of autocorrelation. Well-suited for monophonic instruments
 * and performs well on piano notes.
 *
 * Reference: "YIN, a fundamental frequency estimator for speech and music" (2002)
 * by Alain de Cheveigné and Hideki Kawahara.
 *
 * @param sampleRate Audio sample rate in Hz (e.g., 44100)
 * @param threshold Absolute threshold for the cumulative mean normalized difference (0.1-0.15 typical)
 */
class PitchDetector(
    private val sampleRate: Int = 44100,
    private val threshold: Float = 0.15f
) {
    companion object {
        private const val MIN_FREQ = 27.5f  // A0
        private const val MAX_FREQ = 4186.0f // C8
    }

    data class PitchResult(
        val frequency: Float,
        val probability: Float  // 0.0 to 1.0 confidence
    )

    /**
     * Detect the fundamental frequency of an audio buffer.
     * @param buffer PCM audio samples (mono, float -1.0 to 1.0)
     * @return PitchResult or null if no clear pitch detected
     */
    fun detectPitch(buffer: FloatArray): PitchResult? {
        val bufferSize = buffer.size
        val halfSize = bufferSize / 2
        val minTau = (sampleRate / MAX_FREQ).toInt()
        val maxTau = minOf(halfSize, (sampleRate / MIN_FREQ).toInt())

        if (maxTau <= minTau) return null

        // Step 1: Difference function
        val diff = FloatArray(maxTau)
        for (tau in 0 until maxTau) {
            var sum = 0f
            for (i in 0 until halfSize) {
                val delta = buffer[i] - buffer[i + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        // Step 2: Cumulative mean normalized difference function
        val cmndf = FloatArray(maxTau)
        cmndf[0] = 1f
        var runningSum = 0f
        for (tau in 1 until maxTau) {
            runningSum += diff[tau]
            cmndf[tau] = if (runningSum > 0) diff[tau] * tau / runningSum else 1f
        }

        // Step 3: Absolute threshold
        var tau = minTau
        while (tau < maxTau - 1) {
            if (cmndf[tau] < threshold) {
                // Found a dip below threshold, descend to local minimum
                while (tau + 1 < maxTau && cmndf[tau + 1] < cmndf[tau]) {
                    tau++
                }
                val frequency = sampleRate.toFloat() / tau
                if (frequency in MIN_FREQ..MAX_FREQ) {
                    val probability = 1f - cmndf[tau]
                    // Step 4: Parabolic interpolation for better precision
                    val refinedFreq = parabolicInterpolation(cmndf, tau, sampleRate)
                    return PitchResult(refinedFreq, probability)
                }
                return null
            }
            tau++
        }

        return null
    }

    /**
     * Parabolic interpolation to refine the period estimate.
     */
    private fun parabolicInterpolation(cmndf: FloatArray, tau: Int, sampleRate: Int): Float {
        if (tau <= 0 || tau >= cmndf.size - 1) {
            return sampleRate.toFloat() / tau
        }
        val s0 = cmndf[tau - 1]
        val s1 = cmndf[tau]
        val s2 = cmndf[tau + 1]

        val denominator = 2f * (2f * s1 - s2 - s0)
        if (denominator == 0f) return sampleRate.toFloat() / tau

        val shift = (s2 - s0) / denominator
        return sampleRate.toFloat() / (tau + shift)
    }
}
