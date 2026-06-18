package com.pianocompanion.following

/**
 * Configuration parameters for OnlineDTW, tuned for piano score following.
 *
 * @param searchWindow   Search radius around current position. Larger = more forgiving
 *                       for jumps/repeats, but slower. Range: 10-100
 * @param pitchTolerance Semitone tolerance for "close match". 0 = exact only, 1 = ±1 semitone.
 *                       Range: 0-3
 * @param insertCost     Cost penalty for insertion (extra note played). Range: 0.5-2.0
 * @param deleteCost     Cost penalty for deletion (missed note). Range: 0.5-2.0
 * @param matchCost      Base cost for close-but-wrong pitch within tolerance. Range: 0.1-0.5
 * @param octaveCost     Cost for octave errors (pitch diff = 12). Range: 0.3-1.0
 * @param wrongNoteCost  Cost for completely wrong pitch. Range: 0.7-1.0
 */
data class DtwConfig(
    val searchWindow: Int = 30,
    val pitchTolerance: Int = 1,
    val insertCost: Float = 1.0f,
    val deleteCost: Float = 1.0f,
    val matchCost: Float = 0.3f,
    val octaveCost: Float = 0.6f,
    val wrongNoteCost: Float = 0.9f
) {
    companion object {
        /** Relaxed preset — forgiving for beginners, tolerates mistakes */
        val RELAXED = DtwConfig(
            searchWindow = 50,
            pitchTolerance = 2,
            insertCost = 0.7f,
            deleteCost = 0.7f,
            matchCost = 0.2f,
            octaveCost = 0.4f,
            wrongNoteCost = 0.8f
        )

        /** Strict preset — precise matching for advanced players / exam mode */
        val STRICT = DtwConfig(
            searchWindow = 15,
            pitchTolerance = 0,
            insertCost = 1.5f,
            deleteCost = 1.5f,
            matchCost = 0.4f,
            octaveCost = 0.9f,
            wrongNoteCost = 1.0f
        )

        /** Default — balanced for normal practice */
        val DEFAULT = DtwConfig()
    }
}
