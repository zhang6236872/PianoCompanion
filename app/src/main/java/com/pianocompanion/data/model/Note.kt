package com.pianocompanion.data.model

import com.pianocompanion.util.MusicUtils

/**
 * Represents a single musical note with pitch, timing, and duration.
 *
 * @param midiNumber MIDI note number (0-127). Middle C (C4) = 60.
 * @param noteName Human-readable name e.g. "C4", "F#5"
 * @param startTime Onset time in milliseconds from score start.
 * @param duration Note duration in milliseconds.
 * @param velocity MIDI velocity (0-127), used for dynamics.
 * @param staff Which staff (treble/bass) for piano.
 */
data class ScoreNote(
    val midiNumber: Int,
    val noteName: String,
    val startTime: Long,
    val duration: Long,
    val velocity: Int = 64,
    val staff: Staff = Staff.TREBLE,
    val measureIndex: Int = 0,
    val isGraceNote: Boolean = false
) {
    val endTime: Long get() = startTime + duration
    val frequency: Double get() = MusicUtils.midiToFrequency(midiNumber)
}

data class DetectedNote(
    val midiNumber: Int,
    val frequency: Double,
    val startTime: Long,
    val duration: Long = 0,
    val confidence: Float = 0f
) {
    val noteName: String get() = MusicUtils.midiToNoteName(midiNumber)
    val endTime: Long get() = startTime + duration
}

enum class Staff { TREBLE, BASS }

/**
 * Represents a complete musical score.
 */
data class Score(
    val id: String,
    val title: String,
    val composer: String,
    val notes: List<ScoreNote>,
    val tempo: Int = 120, // BPM
    val timeSignature: String = "4/4",
    val source: ScoreSource = ScoreSource.MUSIC_XML
)

enum class ScoreSource { MUSIC_XML, MIDI, OMR }

/**
 * Result of comparing a detected note against the expected score.
 */
data class MatchResult(
    val expectedNote: ScoreNote?,
    val detectedNote: DetectedNote?,
    val status: MatchStatus,
    val deviationMs: Long = 0
)

enum class MatchStatus {
    CORRECT,       // Note matches
    WRONG_PITCH,   // Played a different note
    EXTRA_NOTE,    // Played something not in score
    MISSING_NOTE,  // Expected note not played
    RHYTHM_ERROR   // Right pitch, wrong timing
}
