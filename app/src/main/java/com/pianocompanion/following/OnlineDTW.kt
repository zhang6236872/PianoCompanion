package com.pianocompanion.following

import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.DetectedNote
import kotlin.math.abs
import kotlin.math.min

/**
 * Online Dynamic Time Warping (DTW) for real-time score following.
 *
 * Aligns a stream of live-detected notes against a pre-parsed score sequence.
 * Uses a sliding-window DTW approach that processes each new note incrementally,
 * keeping the computation bounded by a fixed search window.
 *
 * Algorithm adapted from "Real-Time Score Following" techniques and the
 * piano-auto-page-turner project's DTW implementation.
 *
 * @param scoreNotes The expected note sequence from the parsed score
 * @param searchWindow Maximum search radius around current position (controls latency vs accuracy)
 */
class OnlineDTW(
    private val scoreNotes: List<ScoreNote>,
    private val searchWindow: Int = 50
) {
    // DTW cost matrix (only stores the current frontier)
    private var currentPos: Int = 0  // Current position in the score
    private val cumulativeCosts: FloatArray = FloatArray(scoreNotes.size + 1) { Float.MAX_VALUE }

    // For pitch tolerance comparison
    private val pitchTolerance: Int = 1  // semitone tolerance for matching

    init {
        cumulativeCosts[0] = 0f
    }

    data class FollowState(
        val scorePosition: Int,        // Index into scoreNotes
        val confidence: Float,         // 0-1 confidence of current position
        val isAhead: Boolean,          // Player is ahead of expected position
        val isBehind: Boolean          // Player is behind / lost
    )

    /**
     * Process a newly detected note and update the score position.
     * @param detected The note detected from live audio
     * @return Updated FollowState with current position in the score
     */
    fun processNote(detected: DetectedNote): FollowState {
        val n = scoreNotes.size
        val newCosts = FloatArray(n + 1) { Float.MAX_VALUE }
        newCosts[0] = cumulativeCosts[0] + localCost(detected, null)

        // Define search window around current position
        val windowStart = maxOf(1, currentPos - searchWindow)
        val windowEnd = minOf(n, currentPos + searchWindow + 5)

        for (j in windowStart..windowEnd) {
            val cost = localCost(detected, scoreNotes[j - 1])
            val match = cumulativeCosts[j - 1] + cost
            val insert = newCosts[j - 1] + cost
            val delete = cumulativeCosts[j] + cost

            newCosts[j] = minOf(match, insert, delete)
        }

        // Find minimum cost position (most likely current position)
        var minIdx = currentPos
        var minCost = Float.MAX_VALUE
        for (j in windowStart..windowEnd) {
            if (newCosts[j] < minCost) {
                minCost = newCosts[j]
                minIdx = j
            }
        }

        currentPos = minIdx
        cumulativeCosts.copyOfInto(newCosts, 0, 0, n + 1)

        val confidence = 1f - (minCost / (abs(minIdx - currentPos) + 1))
        return FollowState(
            scorePosition = currentPos,
            confidence = minOf(1f, maxOf(0f, confidence)),
            isAhead = currentPos > minIdx,
            isBehind = false  // Simplified; could detect if cost is very high
        )
    }

    /**
     * Local cost between a detected note and an expected score note.
     * Lower cost = better match.
     */
    private fun localCost(detected: DetectedNote?, expected: ScoreNote?): Float {
        if (detected == null || expected == null) return 1f  // Insertion/deletion cost

        val pitchDiff = abs(detected.midiNumber - expected.midiNumber)
        return when {
            pitchDiff == 0 -> 0f    // Perfect match
            pitchDiff <= pitchTolerance -> 0.3f  // Close match
            pitchDiff <= 7 -> 0.8f  // Octave-ish error
            else -> 1f              // Wrong note
        }
    }

    /** Reset follower to the beginning of the score. */
    fun reset() {
        currentPos = 0
        cumulativeCosts.fill(Float.MAX_VALUE)
        cumulativeCosts[0] = 0f
    }

    /** Get the current position in the score. */
    fun getCurrentPosition(): Int = currentPos

    /** Total notes in the score. */
    fun getScoreLength(): Int = scoreNotes.size
}
