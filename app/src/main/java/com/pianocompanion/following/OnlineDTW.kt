package com.pianocompanion.following

import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.DetectedNote
import kotlin.math.abs

/**
 * Online Dynamic Time Warping (DTW) for real-time score following.
 *
 * Aligns a stream of live-detected notes against a pre-parsed score sequence.
 * Uses a sliding-window DTW approach that processes each new note incrementally,
 * keeping the computation bounded by a fixed search window.
 *
 * @param scoreNotes The expected note sequence from the parsed score
 * @param config      DTW tuning parameters (tolerance, costs, window size)
 */
class OnlineDTW(
    private val scoreNotes: List<ScoreNote>,
    private val config: DtwConfig = DtwConfig.DEFAULT
) {
    private var currentPos: Int = 0
    private val cumulativeCosts: FloatArray = FloatArray(scoreNotes.size + 1) { Float.MAX_VALUE }

    init {
        cumulativeCosts[0] = 0f
    }

    data class FollowState(
        val scorePosition: Int,
        val confidence: Float,
        val isAhead: Boolean,
        val isBehind: Boolean
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

        val windowStart = maxOf(1, currentPos - config.searchWindow)
        val windowEnd = minOf(n, currentPos + config.searchWindow + 5)

        for (j in windowStart..windowEnd) {
            val cost = localCost(detected, scoreNotes[j - 1])
            val match = cumulativeCosts[j - 1] + cost
            val insert = newCosts[j - 1] + cost * config.insertCost
            val delete = cumulativeCosts[j] + cost * config.deleteCost

            newCosts[j] = minOf(match, insert, delete)
        }

        // Find minimum cost position
        var minIdx = currentPos
        var minCost = Float.MAX_VALUE
        for (j in windowStart..windowEnd) {
            if (newCosts[j] < minCost) {
                minCost = newCosts[j]
                minIdx = j
            }
        }

        val prevPos = currentPos
        currentPos = minIdx
        cumulativeCosts.copyInto(newCosts, 0, 0, n + 1)

        val confidence = 1f - (minCost / (abs(minIdx - prevPos).coerceAtLeast(1)))
        val isBehind = minCost > 2.0f  // High cost = lost

        return FollowState(
            scorePosition = currentPos,
            confidence = minOf(1f, maxOf(0f, confidence)),
            isAhead = currentPos > prevPos,
            isBehind = isBehind
        )
    }

    /**
     * Local cost between a detected note and an expected score note.
     * Uses config-defined costs for fine-grained tuning.
     */
    private fun localCost(detected: DetectedNote?, expected: ScoreNote?): Float {
        if (detected == null || expected == null) return config.insertCost

        val pitchDiff = abs(detected.midiNumber - expected.midiNumber)
        return when {
            pitchDiff == 0 -> 0f                                    // Perfect match
            pitchDiff <= config.pitchTolerance -> config.matchCost  // Close match
            pitchDiff == 12 -> config.octaveCost                    // Octave error
            pitchDiff <= 7 -> config.wrongNoteCost * 0.8f          // Interval error
            else -> config.wrongNoteCost                            // Completely wrong
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
