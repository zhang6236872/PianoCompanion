package com.pianocompanion.following

import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.DetectedNote
import org.junit.Assert.*
import org.junit.Test

class DtwConfigTest {

    private fun makeScore(vararg midis: Int): List<ScoreNote> {
        return midis.mapIndexed { i, midi ->
            ScoreNote(midiNumber = midi, noteName = "n$i", startTime = i * 500L, duration = 400L, staff = com.pianocompanion.data.model.Staff.TREBLE)
        }
    }

    private fun makeDetected(midi: Int) = DetectedNote(
        midiNumber = midi, frequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0),
        startTime = System.currentTimeMillis(), confidence = 0.9f
    )

    @Test
    fun `presets are distinct`() {
        assertNotEquals(DtwConfig.DEFAULT, DtwConfig.RELAXED)
        assertNotEquals(DtwConfig.DEFAULT, DtwConfig.STRICT)
        assertNotEquals(DtwConfig.RELAXED, DtwConfig.STRICT)
    }

    @Test
    fun `strict preset has zero tolerance`() {
        assertEquals(0, DtwConfig.STRICT.pitchTolerance)
    }

    @Test
    fun `relaxed preset has larger window`() {
        assertTrue(DtwConfig.RELAXED.searchWindow > DtwConfig.DEFAULT.searchWindow)
        assertTrue(DtwConfig.STRICT.searchWindow < DtwConfig.DEFAULT.searchWindow)
    }

    @Test
    fun `DTW with custom config processes notes`() {
        val score = makeScore(60, 62, 64, 65, 67, 69, 71, 72)
        val dtw = OnlineDTW(score, DtwConfig(pitchTolerance = 1, searchWindow = 20))

        dtw.processNote(makeDetected(60))
        val pos1 = dtw.getCurrentPosition()
        assertTrue(pos1 >= 0)

        dtw.processNote(makeDetected(62))
        val pos2 = dtw.getCurrentPosition()
        assertTrue(pos2 >= pos1)
    }

    @Test
    fun `DTW handles wrong notes with different costs`() {
        val score = makeScore(60, 62, 64)

        // With relaxed config, wrong note should still process
        val dtwRelaxed = OnlineDTW(score, DtwConfig.RELAXED)
        dtwRelaxed.processNote(makeDetected(60))
        dtwRelaxed.processNote(makeDetected(99))  // Very wrong note
        val posRelaxed = dtwRelaxed.getCurrentPosition()

        // With strict config
        val dtwStrict = OnlineDTW(score, DtwConfig.STRICT)
        dtwStrict.processNote(makeDetected(60))
        dtwStrict.processNote(makeDetected(99))
        val posStrict = dtwStrict.getCurrentPosition()

        // Both should not crash and produce valid positions
        assertTrue(posRelaxed >= 0)
        assertTrue(posStrict >= 0)
    }

    @Test
    fun `config can be copied and modified`() {
        val original = DtwConfig.DEFAULT
        val modified = original.copy(pitchTolerance = 2, searchWindow = 40)
        assertEquals(1, original.pitchTolerance)
        assertEquals(2, modified.pitchTolerance)
        assertEquals(40, modified.searchWindow)
    }
}
