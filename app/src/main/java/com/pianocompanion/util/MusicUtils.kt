package com.pianocompanion.util

/**
 * Utility functions for music theory calculations.
 */
object MusicUtils {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** A4 = 440 Hz = MIDI note 69 */
    const val A4_MIDI = 69
    const val A4_FREQ = 440.0

    /** Convert MIDI note number to frequency in Hz. */
    fun midiToFrequency(midi: Int): Double {
        return A4_FREQ * Math.pow(2.0, (midi - A4_MIDI).toDouble() / 12.0)
    }

    /** Convert frequency to nearest MIDI note number. */
    fun frequencyToMidi(freq: Double): Int {
        return Math.round(12 * Math.log(freq / A4_FREQ) / Math.log(2.0) + A4_MIDI).toInt()
    }

    /** Convert MIDI note number to note name (e.g., 60 -> "C4"). */
    fun midiToNoteName(midi: Int): String {
        val octave = (midi / 12) - 1
        val noteIndex = midi % 12
        return NOTE_NAMES[noteIndex] + octave.toString()
    }

    /** Convert note name to MIDI number (e.g., "C4" -> 60). */
    fun noteNameToMidi(name: String): Int {
        val match = Regex("([A-Ga-g])(#|b)?(-?\\d)").find(name) ?: return -1
        val (letter, accidental, octaveStr) = match.destructured
        val noteIndex = when (letter.uppercase()) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5
            "G" -> 7; "A" -> 9; "B" -> 11
            else -> 0
        }
        val adjusted = when (accidental) {
            "#" -> noteIndex + 1
            "b" -> noteIndex - 1
            else -> noteIndex
        }
        val octave = octaveStr.toInt()
        return (octave + 1) * 12 + adjusted
    }

    /** Standard piano range: A0 (21) to C8 (108). */
    fun isValidPianoNote(midi: Int): Boolean = midi in 21..108

    /** Cents deviation between two frequencies. */
    fun centsDeviation(actual: Double, expected: Double): Double {
        return 1200 * Math.log(actual / expected) / Math.log(2.0)
    }
}
