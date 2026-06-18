package com.pianocompanion.following

import com.pianocompanion.audio.NoteDetector
import com.pianocompanion.audio.PitchDetector
import com.pianocompanion.data.model.*
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

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

    private var running: Boolean = false
    private var currentMeasure: Int = 0
    private var currentPage: Int = 0
    private val notesPerPage: Int = 32

    private val errorPositions = mutableListOf<ErrorPosition>()
    private var correctCount: Int = 0
    private var wrongCount: Int = 0
    private var missedCount: Int = 0
    private var extraCount: Int = 0

    init {
        dtw = OnlineDTW(score.notes)
        setupCallbacks()
    }

    var onPositionUpdate: ((Int, Int, Int) -> Unit)? = null
    var onPageTurn: ((Int) -> Unit)? = null
    var onNoteMatch: ((MatchResult) -> Unit)? = null
    var onErrorDetected: ((ErrorPosition) -> Unit)? = null

    private fun setupCallbacks() {
        noteDetector.onNoteOnset = { midi, freq, timeMs ->
            if (running) {
                val detected = DetectedNote(
                    midiNumber = midi,
                    frequency = freq.toDouble(),
                    startTime = timeMs,
                    confidence = 0.8f
                )

                val state = dtw.processNote(detected)
                val scoreIdx = state.scorePosition

                if (scoreIdx < score.notes.size) {
                    val newMeasure = score.notes[scoreIdx].measureIndex
                    if (newMeasure != currentMeasure) {
                        currentMeasure = newMeasure
                    }

                    val newPage = scoreIdx / notesPerPage
                    if (newPage != currentPage) {
                        currentPage = newPage
                        onPageTurn?.invoke(newPage)
                    }

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
    }

    private fun checkNote(detected: DetectedNote, expected: ScoreNote?): MatchResult {
        if (expected == null) {
            return MatchResult(null, detected, MatchStatus.EXTRA_NOTE)
        }

        val pitchDiff = abs(detected.midiNumber - expected.midiNumber)
        val status = when {
            pitchDiff == 0 -> MatchStatus.CORRECT
            pitchDiff <= 1 -> MatchStatus.CORRECT
            pitchDiff == 12 -> MatchStatus.WRONG_PITCH
            else -> MatchStatus.WRONG_PITCH
        }
        return MatchResult(expected, detected, status)
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

    fun processAudio(samples: FloatArray) {
        if (running) {
            noteDetector.process(samples)
        }
    }

    fun start() {
        running = true
        noteDetector.reset()
        dtw.reset()
        correctCount = 0
        wrongCount = 0
        missedCount = 0
        extraCount = 0
        errorPositions.clear()
    }

    fun stop() {
        running = false
    }

    fun isRunning(): Boolean = running

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
