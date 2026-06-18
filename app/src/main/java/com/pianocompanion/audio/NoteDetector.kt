package com.pianocompanion.audio

import com.pianocompanion.data.model.DetectedNote
import com.pianocompanion.util.MusicUtils

/**
 * Detects individual notes from an audio stream by combining onset detection
 * with pitch detection. Produces a stream of DetectedNote events.
 *
 * Strategy:
 * 1. Detect note onsets using spectral flux (energy increase in the signal)
 * 2. At each onset, run pitch detection (YIN) on the subsequent frame
 * 3. Track the note until energy drops below threshold (offset)
 */
class NoteDetector(
    private val pitchDetector: PitchDetector = PitchDetector(),
    private val sampleRate: Int = 44100,
    private val frameSize: Int = 2048,
    private val hopSize: Int = 512,
    private val onsetThreshold: Float = 0.15f,
    private val minNoteDurationMs: Long = 50
) {
    private var previousSpectralFlux: Float = 0f
    private var isInNote: Boolean = false
    private var currentNoteStart: Long = 0
    private var currentPitch: Float = 0f
    private var startTimeMs: Long = 0

    /** Callback when a complete note is detected (onset + offset). */
    var onNoteDetected: ((DetectedNote) -> Unit)? = null

    /** Callback when a note onset is detected (for real-time following). */
    var onNoteOnset: ((Int, Float, Long) -> Unit)? = null

    fun reset() {
        previousSpectralFlux = 0f
        isInNote = false
        currentNoteStart = 0
        currentPitch = 0f
        startTimeMs = System.currentTimeMillis()
    }

    /**
     * Process a chunk of audio samples.
     * @param samples PCM float audio (mono)
     */
    fun process(samples: FloatArray) {
        val now = System.currentTimeMillis() - startTimeMs

        // Calculate spectral flux (simplified: RMS energy)
        val rms = calculateRMS(samples)

        // Onset detection via energy increase
        val flux = rms - previousSpectralFlux
        previousSpectralFlux = rms

        if (!isInNote && flux > onsetThreshold) {
            // Note onset detected
            val pitchResult = pitchDetector.detectPitch(samples)
            if (pitchResult != null && pitchResult.probability > 0.3f) {
                val midi = MusicUtils.frequencyToMidi(pitchResult.frequency.toDouble())
                if (MusicUtils.isValidPianoNote(midi)) {
                    isInNote = true
                    currentNoteStart = now
                    currentPitch = pitchResult.frequency
                    onNoteOnset?.invoke(midi, pitchResult.frequency, now)
                }
            }
        } else if (isInNote && rms < 0.01f) {
            // Note offset detected (energy dropped)
            val duration = now - currentNoteStart
            if (duration >= minNoteDurationMs) {
                val midi = MusicUtils.frequencyToMidi(currentPitch.toDouble())
                val note = DetectedNote(
                    midiNumber = midi,
                    frequency = currentPitch.toDouble(),
                    startTime = currentNoteStart,
                    duration = duration,
                    confidence = 0.8f
                )
                onNoteDetected?.invoke(note)
            }
            isInNote = false
        }
    }

    private fun calculateRMS(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) {
            sum += (s * s).toDouble()
        }
        return Math.sqrt(sum / samples.size).toFloat()
    }
}
