package com.pianocompanion.omr.image

/**
 * Detects articulation marks near noteheads in a cleaned (staff-removed, denoised)
 * binary image.
 *
 * ## Staccato dot detection
 *
 * A staccato dot (•) is a small, compact ink mark placed directly above or below
 * a notehead — always on the **opposite** side from the stem. It instructs the
 * performer to play the note short and detached.
 *
 * **Key distinction from augmentation dots** (counted by [RhythmAnalyzer]):
 * - Augmentation dot: to the **right** of the notehead, at the same vertical level.
 * - Staccato dot: **above or below** the notehead, in the same horizontal column.
 *
 * **Search strategy**: for each notehead the search side is chosen based on stem
 * direction (if known):
 * - Stem up → search **below** the notehead (dot is opposite to stem).
 * - Stem down → search **above**.
 * - No stem (whole note) → search **both** sides.
 *
 * By searching the side opposite the stem we avoid interference from the stem's
 * vertical line, which would otherwise be indistinguishable from a small dot in
 * a noisy projection.
 *
 * Within the search region the detector looks for a compact blob whose horizontal
 * and vertical extents are both ≤ `0.6 × s` (staff line spacing) — too small to be
 * a notehead, too compact to be a stem or ledger line.
 */
object ArticulationDetector {

    /**
     * Detects which noteheads have a staccato dot nearby.
     *
     * @param image       cleaned binary image (staff lines removed, denoised).
     * @param noteheads   all detected noteheads (same list/order used by the pipeline).
     * @param rhythms     parallel rhythm features (for stem direction); must be the same
     *                    length as [noteheads], or empty to search both sides for all.
     * @param lineSpacing staff line spacing in pixels.
     * @return set of indices into [noteheads] that have a staccato dot.
     */
    fun detectStaccato(
        image: BinaryImage,
        noteheads: List<Notehead>,
        rhythms: List<RhythmFeatures>,
        lineSpacing: Int
    ): Set<Int> {
        if (lineSpacing <= 0 || noteheads.isEmpty()) return emptySet()
        val s = lineSpacing.toDouble()

        val maxDotDim = (0.6 * s).toInt().coerceAtLeast(2)   // max width/height of a dot blob
        val minPixels = 2                                     // at least 2 black pixels to qualify
        val searchGap = (0.4 * s).toInt().coerceAtLeast(2)    // gap between notehead edge and search start
        val searchRange = (2.0 * s).toInt()                   // how far to search
        val xHalf = (maxOf(noteheads[0].width, 0) / 2.0)      // will be recomputed per notehead

        val result = HashSet<Int>()

        noteheads.forEachIndexed { idx, nh ->
            val halfW = (maxOf(nh.width / 2, (0.4 * s).toInt())).coerceAtLeast(2)
            val xLo = (nh.centerX - halfW).coerceAtLeast(0)
            val xHi = (nh.centerX + halfW).coerceAtLeast(xLo)
            val topEdge = nh.centerY - nh.height / 2
            val botEdge = nh.centerY + nh.height / 2

            // Determine which sides to search based on stem direction.
            val rhythm = rhythms.getOrNull(idx)
            val searchBelow: Boolean
            val searchAbove: Boolean
            when {
                rhythm == null || !rhythm.hasStem -> { searchBelow = true; searchAbove = true }
                rhythm.stemUp -> { searchBelow = true; searchAbove = false }
                else -> { searchBelow = false; searchAbove = true }
            }

            val foundBelow = if (searchBelow) {
                val yStart = (botEdge + searchGap).coerceAtLeast(0)
                val yEnd = minOf(botEdge + searchGap + searchRange, image.height - 1)
                hasCompactDot(image, xLo, xHi, yStart, yEnd, maxDotDim, minPixels)
            } else false

            val foundAbove = if (searchAbove && !foundBelow) {
                val yStart = maxOf(topEdge - searchGap - searchRange, 0)
                val yEnd = minOf(topEdge - searchGap, image.height - 1)
                hasCompactDot(image, xLo, xHi, yStart, yEnd, maxDotDim, minPixels)
            } else false

            if (foundBelow || foundAbove) {
                result += idx
            }
        }

        return result
    }

    /**
     * Scans a rectangular region for a compact ink blob that looks like a staccato dot.
     *
     * A dot is characterised by:
     * - At least [minPixels] black pixels in the region.
     * - The ink is confined to a small number of consecutive rows (height ≤ [maxDim]).
     * - The widest ink row has ≤ [maxDim] pixels (compact, not a wide line).
     *
     * @return `true` if a dot-like blob is found.
     */
    private fun hasCompactDot(
        image: BinaryImage,
        xLo: Int,
        xHi: Int,
        yStart: Int,
        yEnd: Int,
        maxDim: Int,
        minPixels: Int
    ): Boolean {
        if (yStart > yEnd || xLo > xHi) return false

        // Row projection: black pixel count per row within the X window.
        val rowCounts = IntArray(yEnd - yStart + 1)
        var totalPixels = 0
        for ((ri, y) in (yStart..yEnd).withIndex()) {
            var c = 0
            for (x in xLo..xHi) {
                if (image.isBlack(x, y)) c++
            }
            rowCounts[ri] = c
            totalPixels += c
        }
        if (totalPixels < minPixels) return false

        // Find groups of consecutive rows that have ink.
        // A staccato dot creates a compact group (height ≤ maxDim);
        // a stem or ledger line creates a tall group.
        var i = 0
        while (i < rowCounts.size) {
            if (rowCounts[i] == 0) { i++; continue }
            // Start of an ink group.
            var groupStart = i
            var groupPixels = 0
            var maxRowInGroup = 0
            while (i < rowCounts.size && rowCounts[i] > 0) {
                groupPixels += rowCounts[i]
                if (rowCounts[i] > maxRowInGroup) maxRowInGroup = rowCounts[i]
                i++
            }
            val groupHeight = i - groupStart

            // Staccato dot: compact (height ≤ maxDim, width ≤ maxDim), enough pixels.
            if (groupHeight in 1..maxDim && maxRowInGroup in 1..maxDim && groupPixels >= minPixels) {
                return true
            }
            // Otherwise this group is too tall (stem/line) or too wide — skip it.
        }
        return false
    }
}
