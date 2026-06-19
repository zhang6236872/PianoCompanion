package com.pianocompanion.omr.image

/**
 * A localized notehead candidate.
 */
data class Notehead(
    val centerX: Int,
    val centerY: Int,
    val width: Int,
    val height: Int,
    val area: Int
)

/**
 * Selects notehead-like blobs from a de-staffed binary image.
 *
 * Heuristics (scaled by staff line spacing `s`):
 *  - a filled notehead is roughly one staff space wide (~ `s`);
 *  - its bounding box is compact (not a long stem or beam);
 *  - its aspect ratio is close to 1.
 */
object NoteheadDetector {

    /**
     * @param blobs all connected components of the cleaned image.
     * @param lineSpacing staff line spacing (px) used to scale the thresholds.
     */
    fun detect(blobs: List<Blob>, lineSpacing: Int): List<Notehead> {
        if (lineSpacing <= 0) return emptyList()
        val s = lineSpacing.toDouble()

        val minDim = (0.5 * s).toInt().coerceAtLeast(3)
        val maxDim = (2.5 * s).toInt()
        val minArea = (0.25 * s * s).toInt().coerceAtLeast(4)

        return blobs
            .asSequence()
            .filter { b -> b.width in minDim..maxDim }
            .filter { b -> b.height in minDim..maxDim }
            .filter { b -> b.area >= minArea }
            .filter { b -> b.aspectRatio in 0.5..2.0 }
            .map { Notehead(it.centerX, it.centerY, it.width, it.height, it.area) }
            .sortedBy { it.centerX }
            .toList()
    }
}
