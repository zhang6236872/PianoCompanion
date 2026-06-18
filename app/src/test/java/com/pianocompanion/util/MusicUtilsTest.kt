package com.pianocompanion.util

import org.junit.Assert.*
import org.junit.Test

class MusicUtilsTest {

    @Test
    fun `midi to frequency converts A4 correctly`() {
        val freq = MusicUtils.midiToFrequency(69)
        assertEquals(440.0, freq, 0.1)
    }

    @Test
    fun `midi to frequency converts C4 correctly`() {
        val freq = MusicUtils.midiToFrequency(60)
        assertEquals(261.63, freq, 0.1)
    }

    @Test
    fun `frequency to midi converts 440Hz correctly`() {
        val midi = MusicUtils.frequencyToMidi(440.0)
        assertEquals(69, midi)
    }

    @Test
    fun `midi to note name converts middle C`() {
        assertEquals("C4", MusicUtils.midiToNoteName(60))
    }

    @Test
    fun `midi to note name converts A4`() {
        assertEquals("A4", MusicUtils.midiToNoteName(69))
    }

    @Test
    fun `note name to midi converts C4`() {
        assertEquals(60, MusicUtils.noteNameToMidi("C4"))
    }

    @Test
    fun `note name to midi converts F sharp 5`() {
        assertEquals(78, MusicUtils.noteNameToMidi("F#5"))
    }

    @Test
    fun `is valid piano note accepts valid range`() {
        assertTrue(MusicUtils.isValidPianoNote(21))   // A0
        assertTrue(MusicUtils.isValidPianoNote(108))  // C8
        assertTrue(MusicUtils.isValidPianoNote(60))   // C4
        assertFalse(MusicUtils.isValidPianoNote(0))
        assertFalse(MusicUtils.isValidPianoNote(200))
    }

    @Test
    fun `cents deviation calculates correctly`() {
        val cents = MusicUtils.centsDeviation(445.0, 440.0)
        assertTrue(cents > 19 && cents < 20)  // ~19.6 cents
    }
}
