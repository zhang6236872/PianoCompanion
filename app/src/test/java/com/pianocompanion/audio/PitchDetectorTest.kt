package com.pianocompanion.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchDetectorTest {

    private val sampleRate = 44100

    @Test
    fun `detects 440Hz sine wave`() {
        val freq = 440.0f
        val buffer = generateSineWave(freq, 4096)
        val detector = PitchDetector(sampleRate)
        val result = detector.detectPitch(buffer)

        assertNotNull(result)
        assertEquals(freq, result!!.frequency, 2.0f)  // within 2Hz tolerance
        assertTrue(result.probability > 0.5f)
    }

    @Test
    fun `detects 261Hz sine wave (middle C)`() {
        val freq = 261.63f
        val buffer = generateSineWave(freq, 4096)
        val detector = PitchDetector(sampleRate)
        val result = detector.detectPitch(buffer)

        assertNotNull(result)
        assertEquals(freq, result!!.frequency, 2.0f)
    }

    @Test
    fun `detects low frequency note A2`() {
        val freq = 110.0f  // A2
        val buffer = generateSineWave(freq, 4096)
        val detector = PitchDetector(sampleRate)
        val result = detector.detectPitch(buffer)

        assertNotNull(result)
        assertEquals(freq, result!!.frequency, 3.0f)
    }

    @Test
    fun `returns null for silence`() {
        val buffer = FloatArray(4096) { 0f }
        val detector = PitchDetector(sampleRate)
        val result = detector.detectPitch(buffer)
        assertNull(result)
    }

    @Test
    fun `returns null for noise`() {
        val random = java.util.Random(42)
        val buffer = FloatArray(4096) { random.nextFloat() * 0.1f }
        val detector = PitchDetector(sampleRate)
        val result = detector.detectPitch(buffer)
        // Noise should have low probability or return null
        if (result != null) {
            assertTrue(result.probability < 0.5f)
        }
    }

    private fun generateSineWave(freq: Float, samples: Int): FloatArray {
        val buffer = FloatArray(samples)
        for (i in buffer.indices) {
            buffer[i] = sin(2.0 * PI * freq * i / sampleRate).toFloat() * 0.8f
        }
        return buffer
    }
}
