package com.pianocompanion.following

import com.pianocompanion.audio.NoteDetector
import com.pianocompanion.audio.PitchDetector
import com.pianocompanion.data.model.*
import com.pianocompanion.util.MusicUtils

/**
 * High-level score following coordinator.
 *
 * Combines audio capture, note detection, DTW alignment, and error detection
 * into a single real-time pipeline. Emits events for UI updates.
 */
class ScoreFollower(
    private val score: Score,
    private val sampleRate: Int = 44100
) {
    private val pitchDetector = PitchDetector(sampleRate)
    private val noteDetector = NoteDetector(pitchDetector, sampleRate)
    private val dtw: OnlineDTW

    // Current following state
    private var isRunning: Boolean = false
    private var currentMeasure: Int = 0
    private var currentPage: Int = 0
    private val notesPerPage: Int = 32  // Approximate notes per page (configurable)

    // Error tracking
    private val errorPositions = mutableListOf<ErrorPosition>()
    private var correctCount: Int = 0
    private var wrongCount: Int = 0
    private var missedCount: Int = 0
    private var extraCount: Int = 0

    init {
        dtw = OnlineDTW(score.notes)
        setupCallbacks()
    }

    // === Event callbacks for UI ===
    var onPositionUpdate: ((Int, Int, Int) -> Unit)? = null  // (noteIdx, measureIdx, pageIdx)
    var onPageTurn: ((Int) -> Unit)? = null  // newPageIndex
    var onNoteMatch: ((MatchResult) -> Unit)? = null
    var onErrorDetected: ((ErrorPosition) -> Unit)? = null

    private fun setupCallbacks() {
        noteDetector.onNoteOnset = { midi, freq, timeMs ->
            if (!isRunning) return@onNoteOnset

            val detected = DetectedNote(
                midiNumber = midi,
                frequency = freq.toDouble(),
                startTime = timeMs,
                confidence = 0.8f
            )

            val state = dtw.processNote(detected)
            val scoreIdx = state.scorePosition

            // Update measure tracking
            if (scoreIdx < score.notes.size) {
                val newMeasure = score.notes[scoreIdx].measureIndex
                if (newMeasure != currentMeasure) {
                    currentMeasure = newMeasure
                }

                // Check for page turn
                val newPage = scoreIdx / notesPerPage
                if (newPage != currentPage) {
                    currentPage = newPage
                    onPageTurn?.invoke(newPage)
                }

                // Error detection
                val expected = score.notes.getOrNull(scoreIdx)
                val result = checkNote(detected, expected)
                onNoteMatch?.invoke(result)

                when (result.status) {
                    MatchStatus.CORRECT -> correctCount++
                    MatchStatus.WRONG_PITCH -> {
                        wrongCount++
                        recordError(result, expected, detected)
                    }
                    MatchStatus.EXTRA_NOTE -> {
                        extraCount++
                        recordError(result, null, detected)
                    }
                    MatchStatus.MISSING_NOTE -> {
                        missedCount++
                        recordError(result, expected, null)
                    }
                    MatchStatus.RHYTHM_ERROR -> {
                        wrongCount++
                        recordError(result, expected, detected)
                    }
                }

                onPositionUpdate?.invoke(scoreIdx, currentMeasure, currentPage)
            }
        }
    }

    private fun checkNote(detected: DetectedNote, expected: ScoreNote?): MatchResult {
        if (expected == null) {
            return MatchResult(null, detected, MatchStatus.EXTRA_NOTE)
        }

        val pitchDiff = Math.abs(detected.midiNumber - expected.midiNumber)
        return when {
            pitchDiff == 0 -> MatchResult(expected, detected, MatchStatus.CORRECT)
            pitchDiff <= 1 -> MatchResult(expected, detected, MatchStatus.CORRECT)  // Near match
            pitchDiff == 12 -> MatchResult(expected, detected, MatchStatus.WRONG_PITCH) // Octave error
            else -> MatchResult(expected, detected, MatchStatus.WRONG_PITCH)
        }
    }

    private fun recordError(
        result: MatchResult,
        expected: ScoreNote?,
        detected: DetectedNote?
    ) {
        val error = ErrorPosition(
            measureIndex = currentMeasure,
            expectedNote = expected?.noteName ?: "—",
            detectedNote = detected?.noteName ?: "(未弹)",
            errorType = result.status,
            timestamp = System.currentTimeMillis()
        )
        errorPositions.add(error)
        onErrorDetected?.invoke(error)
    }

    /** Process raw audio samples from the microphone. */
    fun processAudio(samples: FloatArray) {
        if (isRunning) {
            noteDetector.process(samples)
        }
    }

    fun start() {
        isRunning = true
        noteDetector.reset()
        dtw.reset()
        correctCount = 0
        wrongCount = 0
        missedCount = 0
        extraCount = 0
        errorPositions.clear()
    }

    fun stop() {
        isRunning = false
    }

    fun isRunning(): Boolean = isRunning

    fun getStats(): PracticeStats {
        val total = correctCount + wrongCount + missedCount + extraCount
        return PracticeStats(
            totalNotes = total,
            correctNotes = correctCount,
            wrongNotes = wrongCount,
            missedNotes = missedCount,
            extraNotes = extraCount,
            accuracy = if (total > 0) correctCount.toFloat() / total else 0f
        )
    }

    data class PracticeStats(
        val totalNotes: Int,
        val correctNotes: Int,
        val wrongNotes: Int,
        val missedNotes: Int,
        val extraNotes: Int,
        val accuracy: Float
    )
}
