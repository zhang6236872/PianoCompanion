package com.pianocompanion.following

import com.pianocompanion.data.model.DetectedNote
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import org.junit.Assert.*
import org.junit.Test

class OnlineDTWTest {

    private fun createScoreNotes(vararg midis: Int): List<ScoreNote> {
        return midis.mapIndexed { idx, midi ->
            ScoreNote(
                midiNumber = midi,
                noteName = "N$midi",
                startTime = idx * 500L,
                duration = 400L,
                staff = Staff.TREBLE
            )
        }
    }

    private fun createDetectedNote(midi: Int, time: Long = 0): DetectedNote {
        return DetectedNote(
            midiNumber = midi,
            frequency = 440.0 * Math.pow(2.0, (midi - 69) / 12.0),
            startTime = time
        )
    }

    @Test
    fun `follows correct sequence`() {
        val scoreNotes = createScoreNotes(60, 62, 64, 65, 67)  // C D E F G
        val dtw = OnlineDTW(scoreNotes, config = DtwConfig(searchWindow = 10))

        // Feed the same notes
        var state = dtw.processNote(createDetectedNote(60))
        assertTrue(state.scorePosition >= 0)  // first note should be at or near start

        state = dtw.processNote(createDetectedNote(62))
        assertTrue(state.scorePosition >= 0)

        state = dtw.processNote(createDetectedNote(64))
        assertTrue(state.scorePosition >= 1)
    }

    @Test
    fun `handles wrong note gracefully`() {
        val scoreNotes = createScoreNotes(60, 62, 64)
        val dtw = OnlineDTW(scoreNotes, config = DtwConfig(searchWindow = 10))

        // Feed wrong note
        val state = dtw.processNote(createDetectedNote(72))  // C5 instead of C4
        // Should not crash, position may not advance much
        assertTrue(state.scorePosition >= 0)
    }

    @Test
    fun `handles extra notes`() {
        val scoreNotes = createScoreNotes(60, 62, 64)
        val dtw = OnlineDTW(scoreNotes, config = DtwConfig(searchWindow = 10))

        dtw.processNote(createDetectedNote(60))
        dtw.processNote(createDetectedNote(61))  // Extra note
        val state = dtw.processNote(createDetectedNote(62))

        // Should still be able to follow despite extra note
        assertTrue(state.scorePosition >= 1)
    }

    @Test
    fun `reset returns to start`() {
        val scoreNotes = createScoreNotes(60, 62, 64)
        val dtw = OnlineDTW(scoreNotes)

        dtw.processNote(createDetectedNote(60))
        dtw.processNote(createDetectedNote(62))
        assertTrue(dtw.getCurrentPosition() > 0)

        dtw.reset()
        assertEquals(0, dtw.getCurrentPosition())
    }

    @Test
    fun `reports correct score length`() {
        val scoreNotes = createScoreNotes(60, 62, 64, 65, 67)
        val dtw = OnlineDTW(scoreNotes)
        assertEquals(5, dtw.getScoreLength())
    }
}
