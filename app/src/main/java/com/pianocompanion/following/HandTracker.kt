package com.pianocompanion.following

import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff

/**
 * Tracks left-hand (bass) and right-hand (treble) progress independently.
 *
 * Strategy: Split score notes by staff assignment, then match detected notes
 * to the correct hand based on MIDI pitch range:
 * - MIDI < 60 (below middle C) → left hand (bass)
 * - MIDI >= 60 → right hand (treble)
 */
class HandTracker(allNotes: List<ScoreNote>) {

    data class HandStats(
        val rightCorrect: Int = 0,
        val rightWrong: Int = 0,
        val leftCorrect: Int = 0,
        val leftWrong: Int = 0,
        val rightPosition: Int = 0,
        val leftPosition: Int = 0
    ) {
        val rightTotal: Int get() = rightCorrect + rightWrong
        val leftTotal: Int get() = leftCorrect + leftWrong
        val rightAccuracy: Float get() = if (rightTotal > 0) rightCorrect.toFloat() / rightTotal else 0f
        val leftAccuracy: Float get() = if (leftTotal > 0) leftCorrect.toFloat() / leftTotal else 0f
    }

    // Split notes by hand. C 谱号(中音/次中音)是单声部旋律乐器(中提琴/大提琴)，
    // 无左右手之分，统一归入右手(主旋律)轨道。
    val rightHandNotes: List<ScoreNote> = allNotes.filter {
        it.staff == Staff.TREBLE || it.staff == Staff.BOTH ||
            it.staff == Staff.ALTO || it.staff == Staff.TENOR
    }
    val leftHandNotes: List<ScoreNote> = allNotes.filter {
        it.staff == Staff.BASS || it.staff == Staff.BOTH
    }

    // Separate indices into the full note list for position tracking
    private val rightHandIndices: List<Int> = allNotes.indices.filter {
        val st = allNotes[it].staff
        st == Staff.TREBLE || st == Staff.BOTH || st == Staff.ALTO || st == Staff.TENOR
    }
    private val leftHandIndices: List<Int> = allNotes.indices.filter {
        allNotes[it].staff == Staff.BASS || allNotes[it].staff == Staff.BOTH
    }

    private var _stats = HandStats()

    /** Current tracking stats */
    val stats: HandStats get() = _stats

    /**
     * Determine which hand a detected MIDI note belongs to.
     * Returns Staff.TREBLE for right hand, Staff.BASS for left hand.
     */
    fun classifyHand(midiNumber: Int): Staff {
        return if (midiNumber >= 60) Staff.TREBLE else Staff.BASS
    }

    /**
     * Record a match result, updating the appropriate hand's stats.
     * Returns the Staff that was matched.
     */
    fun recordMatch(
        detectedMidi: Int,
        expectedNote: ScoreNote?,
        isCorrect: Boolean
    ): Staff {
        val hand = expectedNote?.let {
            when (it.staff) {
                Staff.BOTH -> classifyHand(detectedMidi)
                Staff.ALTO, Staff.TENOR -> Staff.TREBLE // 单声部旋律乐器归右手
                else -> it.staff
            }
        } ?: classifyHand(detectedMidi)

        _stats = if (hand == Staff.TREBLE) {
            if (isCorrect) _stats.copy(
                rightCorrect = _stats.rightCorrect + 1,
                rightPosition = minOf(_stats.rightPosition + 1, rightHandNotes.size)
            )
            else _stats.copy(rightWrong = _stats.rightWrong + 1)
        } else {
            if (isCorrect) _stats.copy(
                leftCorrect = _stats.leftCorrect + 1,
                leftPosition = minOf(_stats.leftPosition + 1, leftHandNotes.size)
            )
            else _stats.copy(leftWrong = _stats.leftWrong + 1)
        }

        return hand
    }

    /**
     * Advance position for a specific hand.
     */
    fun advancePosition(hand: Staff) {
        _stats = when (hand) {
            Staff.TREBLE, Staff.ALTO, Staff.TENOR -> _stats.copy(
                rightPosition = minOf(_stats.rightPosition + 1, rightHandNotes.size)
            )
            Staff.BASS -> _stats.copy(
                leftPosition = minOf(_stats.leftPosition + 1, leftHandNotes.size)
            )
            Staff.BOTH -> {
                _stats.copy(
                    rightPosition = minOf(_stats.rightPosition + 1, rightHandNotes.size),
                    leftPosition = minOf(_stats.leftPosition + 1, leftHandNotes.size)
                )
            }
        }
    }

    fun reset() {
        _stats = HandStats()
    }

    /**
     * Get progress percentage for each hand.
     */
    fun rightHandProgress(): Float {
        return if (rightHandNotes.isNotEmpty())
            _stats.rightPosition.toFloat() / rightHandNotes.size
        else 0f
    }

    fun leftHandProgress(): Float {
        return if (leftHandNotes.isNotEmpty())
            _stats.leftPosition.toFloat() / leftHandNotes.size
        else 0f
    }
}
