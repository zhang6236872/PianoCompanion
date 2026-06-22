package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Detects **slurs** (连音) between adjacent different-pitch noteheads in a
 * cleaned (staff-removed, denoised) binary image.
 *
 * ## Musical semantics
 *
 * A slur is a curved line connecting two or more notes of **different pitches**,
 * indicating that they should be played legato (smoothly connected, without
 * separation). Unlike a tie (which joins same-pitch notes into a single sustained
 * note and changes timing), a slur does not alter note durations or onsets — it is
 * purely an articulation/expressive indication.
 *
 * ## How slurs differ from ties
 *
 * Visually, ties and slurs look identical — both are thin curved arcs. The only
 * reliable distinction is **pitch**: ties connect same-pitch notes, slurs connect
 * notes of different pitches. This detector only examines pairs of noteheads
 * whose Y coordinates differ by more than [PITCH_Y_TOLERANCE_FRAC] × line-spacing
 * (a proxy for different pitch), so any detected arc is by definition a slur.
 *
 * ## Detection strategy — per-column interpolated coverage
 *
 * A slur curve is a continuous ink path from the first notehead to the last.
 * Because the two noteheads are at **different Y positions** (unlike ties), the
 * arc slopes from one height to the other while bulging outward. We use
 * **per-column interpolation** of the notehead Y to create a narrow search band
 * that follows the slope:
 *
 * 1. For two adjacent different-pitch noteheads at positions (x1, y1) and
 *    (x2, y2), define the **gap** as `[x1 + halfWidth, x2 − halfWidth]`.
 * 2. For each column `x` in the gap, compute the interpolated reference Y:
 *    `baseY = lerp(y1, y2, t)` where `t = (x − x1) / (x2 − x1)`.
 * 3. Search a vertical band above (`baseY − outer .. baseY − inner`) and below
 *    (`baseY + inner .. baseY + outer`) the interpolated reference for black ink.
 * 4. If ≥ [COVERAGE_THRESHOLD] fraction of columns have ≥1 black pixel in the
 *    band, a continuous arc is present → **slur segment**.
 *
 * This is robust against stems (1–2 px wide, insufficient coverage), ledger lines
 * (short horizontal segments), and isolated noise specks.
 *
 * ## Multi-note slurs
 *
 * A slur often spans more than two notes (e.g. a melodic phrase of 4 notes under
 * one slur arc). Since the arc passes through every gap between consecutive
 * noteheads in the group, pairwise detection identifies each segment. Consecutive
 * segments are then merged into a single [Slur] from the first to the last
 * notehead.
 *
 * ## Known limitations
 *
 * - A slur arc that passes between two same-pitch notes within the tie search
 *   band could be misidentified as a tie by [TieDetector]. In practice, slur arcs
 *   are positioned further from the noteheads than tie arcs, so the TieDetector's
 *   narrow search band (≤ 2 line-spacings) rarely captures a slur.
 * - Extremely long slurs spanning an entire system may have gaps where the arc
 *   crosses staff-line remnants or other ink; the 75 % coverage threshold
 *   tolerates this.
 * - Slurs that wrap around the staff edge (crossing from above to below) are not
 *   supported; such notation is rare in printed scores.
 */
object SlurDetector {

    /**
     * A detected slur connecting notehead [firstNoteIdx] to notehead
     * [lastNoteIdx] (both indices into the notehead list passed to [detect]).
     * For a 2-note slur, `firstNoteIdx + 1 == lastNoteIdx`. For a multi-note
     * slur, all noteheads between first and last (in X order) are under the slur.
     */
    data class Slur(val firstNoteIdx: Int, val lastNoteIdx: Int)

    // --- Thresholds (as multiples of staff line spacing `s`) ----------------

    /**
     * Maximum vertical distance (in line-spacings) between two notehead centres
     * for them to be considered the **same** pitch. Slurs only connect
     * **different**-pitch notes, so pairs within this tolerance are skipped
     * (they are tie candidates, handled by [TieDetector]).
     */
    private const val PITCH_Y_TOLERANCE_FRAC = 0.5

    /**
     * Minimum horizontal gap (in line-spacings) between notehead centres for a
     * slur to be considered. Closer noteheads are likely chords or noise.
     */
    private const val MIN_GAP_FRAC = 1.0

    /**
     * Inner edge of the slur search band, measured from the interpolated
     * notehead Y. Ink closer than this is likely the notehead itself or its
     * stem, not a slur. Slightly larger than the tie detector's 0.15 to better
     * avoid stem interference (slurs are positioned further from noteheads).
     */
    private const val BAND_INNER_FRAC = 0.2

    /**
     * Outer edge of the slur search band. Slur arcs typically bulge further
     * from the staff than tie arcs (which hug the noteheads), so a slightly
     * larger search radius is used.
     */
    private const val BAND_OUTER_FRAC = 2.5

    /**
     * Fraction of gap columns that must contain ink in the search band for a
     * slur to be confirmed. A continuous slur curve covers nearly every column;
     * 0.75 tolerates minor gaps from noise or staff-line-removal artefacts.
     */
    private const val COVERAGE_THRESHOLD = 0.75

    // -----------------------------------------------------------------------

    /**
     * Detects slurs between adjacent different-pitch noteheads and merges
     * consecutive segments into multi-note slur groups.
     *
     * @param image         cleaned binary image (staff lines removed, denoised).
     * @param noteheads     all detected noteheads (same list/order used by the
     *                      pipeline).
     * @param systemIndices parallel list of system indices (same length as
     *                      [noteheads]); slurs are only sought within a single
     *                      system. If empty or shorter, all noteheads are
     *                      treated as belonging to system 0.
     * @param lineSpacing   average staff line spacing in pixels.
     * @return list of [Slur]s (each spanning from first to last notehead index).
     */
    fun detect(
        image: BinaryImage,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        lineSpacing: Int
    ): List<Slur> {
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

        // For each adjacent different-pitch pair, check for slur arc.
        // Store pairs of original notehead indices where an arc was detected.
        val connectedPairs = ArrayList<Pair<Int, Int>>()

        for ((_, indices) in bySystem) {
            if (indices.size < 2) continue
            val sorted = indices
                .map { IndexedNh(it, noteheads[it]) }
                .sortedBy { it.nh.centerX }

            for (k in sorted.indices) {
                if (k + 1 >= sorted.size) break
                val a = sorted[k]
                val b = sorted[k + 1]

                // Slurs connect DIFFERENT-pitch notes (complement of TieDetector).
                if (abs(a.nh.centerY - b.nh.centerY) <= pitchTol) continue

                // Gap between the noteheads' facing edges.
                val x1 = a.nh.centerX + a.nh.width / 2  // right edge of a
                val x2 = b.nh.centerX - b.nh.width / 2  // left edge of b
                if (x2 - x1 < minGap) continue

                // Check for slur arc using per-column interpolated Y band.
                val hasArcAbove = hasSlurCurve(
                    image, x1, x2,
                    a.nh.centerY, b.nh.centerY,
                    bandInner, bandOuter, above = true
                )
                val hasArcBelow = hasSlurCurve(
                    image, x1, x2,
                    a.nh.centerY, b.nh.centerY,
                    bandInner, bandOuter, above = false
                )

                if (hasArcAbove || hasArcBelow) {
                    connectedPairs += a.idx to b.idx
                }
            }
        }

        return mergeIntoGroups(connectedPairs)
    }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Checks whether a continuous ink arc spans the gap `[x1, x2]` in a band
     * above (or below) the line interpolated from `(x1, y1)` to `(x2, y2)`.
     *
     * Because the two noteheads are at different Y positions, the search band
     * follows the slope: for each column `x`, the reference Y is linearly
     * interpolated between `y1` and `y2`. The band then extends outward (above
     * or below) from this reference by `[bandInner, bandOuter]` pixels.
     *
     * @param above if `true`, search above the interpolated line;
     *              otherwise search below.
     * @return `true` if ≥ [COVERAGE_THRESHOLD] of gap columns have ≥1 black
     *         pixel in the per-column search band.
     */
    private fun hasSlurCurve(
        image: BinaryImage,
        x1: Int,
        x2: Int,
        y1: Int,
        y2: Int,
        bandInner: Int,
        bandOuter: Int,
        above: Boolean
    ): Boolean {
        val xLo = x1.coerceIn(0, image.width - 1)
        val xHi = x2.coerceIn(0, image.width - 1)
        if (xHi <= xLo) return false

        val span = (xHi - xLo).coerceAtLeast(1)
        val totalColumns = xHi - xLo + 1
        var coveredColumns = 0

        for (x in xLo..xHi) {
            // Interpolated reference Y at this column (follows the slope).
            val t = (x - xLo).toDouble() / span
            val baseY = (y1 + (y2 - y1) * t).roundToInt()

            val yLo: Int
            val yHi: Int
            if (above) {
                yHi = (baseY - bandInner).coerceIn(0, image.height - 1)
                yLo = (baseY - bandOuter).coerceIn(0, yHi)
            } else {
                yLo = (baseY + bandInner).coerceIn(0, image.height - 1)
                yHi = (baseY + bandOuter).coerceIn(yLo, image.height - 1)
            }
            if (yHi < yLo) continue

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

    /**
     * Merges consecutive arc-connected pairs into multi-note slur groups.
     *
     * Example: pairs (0,1), (1,2), (2,3) → Slur(0, 3).
     * Disconnected pairs become individual 2-note slurs.
     */
    private fun mergeIntoGroups(pairs: List<Pair<Int, Int>>): List<Slur> {
        if (pairs.isEmpty()) return emptyList()

        // Build forward adjacency: notehead → next notehead in a slur.
        val nextOf = LinkedHashMap<Int, Int>()
        for ((first, second) in pairs) {
            // Each notehead can be the start of at most one pair (adjacent in
            // sorted X order). Keep the first mapping.
            if (first !in nextOf) nextOf[first] = second
        }

        // Chain starts: noteheads that are never a 'second' (no incoming edge).
        val seconds = pairs.map { it.second }.toSet()
        val starts = pairs.map { it.first }
            .filter { it !in seconds }
            .distinct()

        val slurs = ArrayList<Slur>()
        val visited = HashSet<Int>()

        for (start in starts) {
            if (start in visited) continue
            var current = start
            visited += current
            while (nextOf.containsKey(current)) {
                current = nextOf[current]!!
                visited += current
            }
            if (current != start) {
                slurs += Slur(start, current)
            }
        }

        // Handle any remaining unvisited pairs (defensive — shouldn't happen
        // with well-formed sorted-adjacent input, but protects against cycles).
        for ((first, second) in pairs) {
            if (first !in visited || second !in visited) {
                slurs += Slur(first, second)
                visited += first
                visited += second
            }
        }

        return slurs
    }
}
