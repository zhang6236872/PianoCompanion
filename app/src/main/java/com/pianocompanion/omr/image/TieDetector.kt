package com.pianocompanion.omr.image

/**
 * Detects **ties** (延音线) between adjacent same-pitch noteheads in a cleaned
 * (staff-removed, denoised) binary image.
 *
 * ## Musical semantics
 *
 * A tie is a curved line connecting two noteheads of the **same pitch**. Its
 * musical meaning is to join them into a single sustained note whose duration
 * is the sum of both. Without tie detection the OMR pipeline would produce two
 * separate note events where the performer should only play one, causing the
 * score follower to expect (and mark wrong) a second onset that never happens.
 *
 * ## How ties differ from slurs
 *
 * Visually, ties and slurs look identical — both are thin curved arcs. The only
 * reliable distinction is **pitch**: ties connect same-pitch notes, slurs connect
 * notes of different pitches. Because this detector only examines pairs of
 * noteheads with (approximately) the same Y coordinate (a proxy for same pitch),
 * any detected arc is by definition a tie, never a slur.
 *
 * ## Detection strategy — column-projection coverage
 *
 * A tie curve is a continuous ink path from the right side of the first
 * notehead to the left side of the second. Even though the curve arcs (bulging
 * away from the staff centre), it passes through **every** column between the
 * two notes at some Y offset. We exploit this:
 *
 * 1. For two adjacent same-pitch noteheads at X positions x1 < x2, define the
 *    **gap** as `[x1 + halfWidth, x2 − halfWidth]`.
 * 2. Search a vertical band just **above** the notehead Y (the arc may curve
 *    up) and just **below** (the arc may curve down).
 * 3. For each column in the gap, check whether at least one black pixel exists
 *    in the band.
 * 4. If ≥ [COVERAGE_THRESHOLD] fraction of columns are covered, a continuous
 *    arc is present → **tie**.
 *
 * This is robust against stems (only 1–2 px wide, insufficient column coverage),
 * ledger lines (short horizontal segments that don't span the full gap), and
 * isolated noise specks.
 *
 * ## Known limitations
 *
 * - A slur arc that happens to pass through the tie search band between two
 *   same-pitch notes could be misidentified as a tie. In practice, slur arcs
 *   are positioned further from the noteheads (well above or below the staff),
 *   while tie arcs hug the noteheads, so the search band (≤ 2 line-spacings
 *   from the notehead centre) rarely captures a slur.
 * - Chord ties (e.g. a C-major chord tied across a bar line) are detected
 *   pairwise per notehead; the pipeline merges each tied voice independently.
 */
object TieDetector {

    /**
     * A detected tie connecting notehead [firstNoteIdx] to notehead
     * [secondNoteIdx] (both indices into the notehead list passed to [detect]).
     */
    data class Tie(val firstNoteIdx: Int, val secondNoteIdx: Int)

    // --- Thresholds (as multiples of staff line spacing `s`) ----------------

    /**
     * Maximum vertical distance (in line-spacings) between two notehead centres
     * for them to be considered the same pitch. Ties only connect same-pitch
     * notes. In the OMR pipeline the same pitch maps to the same (or within
     * 1–2 px) Y coordinate, so 0.5 × s is generous.
     */
    private const val PITCH_Y_TOLERANCE_FRAC = 0.5

    /**
     * Minimum horizontal gap (in line-spacings) between notehead centres for a
     * tie to be considered. If two noteheads are closer than this, they are
     * likely an unresolved chord or a single notehead split by noise, not a tie.
     */
    private const val MIN_GAP_FRAC = 1.0

    /**
     * Inner edge of the tie search band, measured from the notehead centre.
     * Ink closer than this is likely the notehead itself or its stem, not a tie.
     */
    private const val BAND_INNER_FRAC = 0.15

    /**
     * Outer edge of the tie search band, measured from the notehead centre.
     * A tie arc typically bulges outward by up to ~1.5 line-spacings; searching
     * to 2.0 × s provides margin without capturing far-away slur arcs.
     */
    private const val BAND_OUTER_FRAC = 2.0

    /**
     * Fraction of gap columns that must contain ink in the search band for a
     * tie to be confirmed. A continuous tie curve covers nearly every column;
     * 0.75 tolerates minor gaps from noise or staff-line-removal artefacts.
     */
    private const val COVERAGE_THRESHOLD = 0.75

    // -----------------------------------------------------------------------

    /**
     * Detects ties between adjacent same-pitch noteheads.
     *
     * @param image         cleaned binary image (staff lines removed, denoised).
     * @param noteheads     all detected noteheads (same list/order used by the
     *                      pipeline).
     * @param systemIndices parallel list of system indices (same length as
     *                      [noteheads]); ties are only sought within a single
     *                      system. If empty or shorter, all noteheads are
     *                      treated as belonging to system 0.
     * @param lineSpacing   average staff line spacing in pixels.
     * @return list of [Tie]s (each pair of notehead indices connected by a tie).
     */
    fun detect(
        image: BinaryImage,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        lineSpacing: Int
    ): List<Tie> {
        if (noteheads.size < 2 || lineSpacing <= 0) return emptyList()

        val s = lineSpacing.toDouble()
        val pitchTol = (PITCH_Y_TOLERANCE_FRAC * s).toInt().coerceAtLeast(2)
        val minGap = (MIN_GAP_FRAC * s).toInt()
        val bandInner = (BAND_INNER_FRAC * s).toInt().coerceAtLeast(1)
        val bandOuter = (BAND_OUTER_FRAC * s).toInt()

        // Group noteheads by system, sort by X within each system.
        data class IndexedNh(val idx: Int, val nh: Notehead)
        val bySystem = noteheads.indices
            .groupBy { systemIndices.getOrElse(it) { 0 } }

        val ties = ArrayList<Tie>()
        for ((_, indices) in bySystem) {
            if (indices.size < 2) continue
            val sorted = indices
                .map { IndexedNh(it, noteheads[it]) }
                .sortedBy { it.nh.centerX }

            for (k in sorted.indices) {
                if (k + 1 >= sorted.size) break
                val a = sorted[k]
                val b = sorted[k + 1]

                // Same pitch? (Y proximity is a proxy for pitch.)
                if (kotlin.math.abs(a.nh.centerY - b.nh.centerY) > pitchTol) continue

                // Gap between the noteheads' facing edges.
                val x1 = a.nh.centerX + a.nh.width / 2  // right edge of a
                val x2 = b.nh.centerX - b.nh.width / 2  // left edge of b
                if (x2 - x1 < minGap) continue

                val midY = (a.nh.centerY + b.nh.centerY) / 2

                // Search above and below for a connecting tie curve.
                val hasTieAbove = hasTieCurve(image, x1, x2, midY, bandInner, bandOuter, above = true)
                val hasTieBelow = hasTieCurve(image, x1, x2, midY, bandInner, bandOuter, above = false)

                if (hasTieAbove || hasTieBelow) {
                    ties += Tie(a.idx, b.idx)
                }
            }
        }

        return ties
    }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Checks whether a continuous ink arc spans the gap `[x1, x2]` in the band
     * above (or below) [centerY].
     *
     * @param above if `true`, search the band `[centerY − outer, centerY − inner]`;
     *              otherwise search `[centerY + inner, centerY + outer]`.
     * @return `true` if ≥ [COVERAGE_THRESHOLD] of gap columns have ≥1 black pixel
     *         in the search band.
     */
    private fun hasTieCurve(
        image: BinaryImage,
        x1: Int,
        x2: Int,
        centerY: Int,
        bandInner: Int,
        bandOuter: Int,
        above: Boolean
    ): Boolean {
        val xLo = x1.coerceIn(0, image.width - 1)
        val xHi = x2.coerceIn(0, image.width - 1)
        if (xHi <= xLo) return false

        val yLo: Int
        val yHi: Int
        if (above) {
            yHi = (centerY - bandInner).coerceIn(0, image.height - 1)
            yLo = (centerY - bandOuter).coerceIn(0, yHi)
        } else {
            yLo = (centerY + bandInner).coerceIn(0, image.height - 1)
            yHi = (centerY + bandOuter).coerceIn(yLo, image.height - 1)
        }
        if (yHi < yLo) return false

        // Column-projection: for each column in the gap, check if any pixel in
        // the band is black.
        val totalColumns = xHi - xLo + 1
        var coveredColumns = 0
        for (x in xLo..xHi) {
            var found = false
            var y = yLo
            while (y <= yHi && !found) {
                if (image.isBlack(x, y)) found = true
                y++
            }
            if (found) coveredColumns++
        }

        return coveredColumns.toDouble() / totalColumns >= COVERAGE_THRESHOLD
    }
}
