package com.pianocompanion.omr.image

import com.pianocompanion.data.model.Articulation

/**
 * Detects articulation marks near noteheads in a cleaned (staff-removed, denoised)
 * binary image.
 *
 * ## Supported articulations
 *
 * - **Staccato dot (•)** [Articulation.STACCATO]: a small, compact, nearly square ink
 *   blob placed directly above or below a notehead — always on the **opposite** side
 *   from the stem. Instructs the performer to play short and detached.
 *
 * - **Tenuto line (—)** [Articulation.TENUTO]: a short horizontal line placed above or
 *   below the notehead (opposite side from stem). Instructs the performer to hold the
 *   note for its full value. Distinguished from staccato by a high **aspect ratio**
 *   (width ≫ height): a tenuto line is much wider than it is tall.
 *
 * - **Accent wedge (>)** [Articulation.ACCENT]: a small wedge/triangle mark placed
 *   above or below the notehead. Instructs the performer to emphasize the note.
 *   Distinguished from staccato by a lower **fill ratio**: an accent wedge is a
 *   triangular/hollow shape that fills less of its bounding box than a solid dot.
 *
 * - **Staccatissimo wedge (▼)** [Articulation.STACCATISSIMO]: a small vertical
 *   wedge/spade mark placed above or below the notehead (same position as staccato).
 *   Instructs the performer to play extremely short and detached — even shorter than
 *   staccato. Distinguished from staccato by a moderate **fill ratio** (0.35–0.70):
 *   the wedge's triangular shape has empty space that a solid dot lacks. Distinguished
 *   from accent by its **vertical orientation** (height ≥ width), while accents are
 *   horizontally elongated wedges (width > height).
 *
 * ## Key distinction from augmentation dots (counted by [RhythmAnalyzer])
 *
 * Augmentation dots sit to the **right** of the notehead at the same vertical level,
 * while articulation marks sit **above or below** the notehead in the same horizontal
 * column. By searching only above/below we avoid interference from augmentation dots.
 *
 * ## Search strategy
 *
 * For each notehead the search side is chosen based on stem direction (if known):
 * - Stem up → search **below** the notehead (mark is opposite to stem).
 * - Stem down → search **above**.
 * - No stem (whole note) → search **both** sides.
 *
 * By searching the side opposite the stem we avoid interference from the stem's
 * vertical line, which would otherwise be indistinguishable from a tall mark in a
 * noisy projection.
 */
object ArticulationDetector {

    // --- Thresholds (as multiples of staff line spacing `s`) ----------------

    /** Maximum height of any articulation mark; taller blobs are stems/ledger lines. */
    private const val MARK_MAX_HEIGHT_FRAC = 1.5

    /** Maximum width/height of a staccato dot blob. */
    private const val DOT_MAX_DIM_FRAC = 0.6

    /**
     * Aspect ratio (width / height) at or above which a blob is classified as a
     * tenuto line rather than a compact mark. A tenuto line is clearly wider than
     * tall (AR ≈ 4–8 in practice), while staccato/accent marks have AR ≈ 1–2.
     */
    private const val TENUTO_AR_THRESHOLD = 2.5

    /** Minimum width (in spacing units) for a tenuto line (avoids classifying noise). */
    private const val TENUTO_MIN_WIDTH_FRAC = 0.4

    /**
     * Fill ratio threshold: blobs whose fill ratio is **strictly below** this value
     * are classified as accent (hollow/wedge), those at or above are staccato (solid).
     * A solid dot fills ≈ 0.75–1.0 of its bounding box; a wedge fills ≈ 0.3–0.5.
     */
    private const val ACCENT_FILL_THRESHOLD = 0.55

    /**
     * Minimum total black pixels for any mark (excludes single-pixel noise).
     */
    private const val MIN_PIXELS = 2

    /** Minimum width AND height (in pixels) for an accent mark. */
    private const val ACCENT_MIN_DIM = 3

    /**
     * Fill ratio at or above which a compact blob is a solid staccato dot.
     * Below this, the blob has empty space and is classified as staccatissimo
     * (if vertically oriented) or accent (if horizontally oriented).
     * A solid round dot fills ≈ 0.75–1.0; a triangular wedge fills ≈ 0.40–0.65.
     */
    private const val STACCATO_FILL_THRESHOLD = 0.70

    /**
     * Minimum fill ratio for any small articulation mark. Blobs with fill below
     * this are too sparse to be a real mark (likely noise or a fragmented stem).
     */
    private const val MIN_ARTICULATION_FILL = 0.30

    // -----------------------------------------------------------------------

    /**
     * Detects articulation marks for all noteheads and returns a map of
     * notehead-index → [Articulation] (only entries with a detected mark are included).
     *
     * @param image       cleaned binary image (staff lines removed, denoised).
     * @param noteheads   all detected noteheads (same list/order used by the pipeline).
     * @param rhythms     parallel rhythm features (for stem direction); must be the same
     *                    length as [noteheads], or empty to search both sides for all.
     * @param lineSpacing staff line spacing in pixels.
     * @return map from notehead index to its detected articulation (excluding [Articulation.NONE]).
     */
    fun detectArticulations(
        image: BinaryImage,
        noteheads: List<Notehead>,
        rhythms: List<RhythmFeatures>,
        lineSpacing: Int
    ): Map<Int, Articulation> {
        if (lineSpacing <= 0 || noteheads.isEmpty()) return emptyMap()
        val s = lineSpacing.toDouble()

        val maxMarkHeight = (MARK_MAX_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val searchGap = (0.4 * s).toInt().coerceAtLeast(2)
        val searchRange = (2.0 * s).toInt()

        val result = HashMap<Int, Articulation>()

        noteheads.forEachIndexed { idx, nh ->
            // X-window centered on notehead, at least 0.6s half-width (wider than
            // staccato-only to capture accent marks that extend slightly beyond notehead).
            val halfW = (maxOf(nh.width / 2, (0.6 * s).toInt())).coerceAtLeast(3)
            val xLo = (nh.centerX - halfW).coerceIn(0, image.width - 1)
            val xHi = (nh.centerX + halfW).coerceIn(xLo, image.width - 1)
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

            // Search below first (primary side for stem-up notes).
            var found: Articulation = Articulation.NONE
            if (searchBelow) {
                val yStart = (botEdge + searchGap).coerceAtLeast(0)
                val yEnd = minOf(botEdge + searchGap + searchRange, image.height - 1)
                found = findAndClassify(image, xLo, xHi, yStart, yEnd, maxMarkHeight, s)
            }
            // Search above only if nothing found below.
            if (found == Articulation.NONE && searchAbove) {
                val yStart = maxOf(topEdge - searchGap - searchRange, 0)
                val yEnd = minOf(topEdge - searchGap, image.height - 1)
                found = findAndClassify(image, xLo, xHi, yStart, yEnd, maxMarkHeight, s)
            }

            if (found != Articulation.NONE) {
                result[idx] = found
            }
        }

        return result
    }

    /**
     * Detects which noteheads have a staccato dot nearby.
     *
     * Convenience wrapper around [detectArticulations] that returns only staccato
     * indices. Retained for backward compatibility.
     *
     * @return set of indices into [noteheads] that have a staccato dot.
     */
    fun detectStaccato(
        image: BinaryImage,
        noteheads: List<Notehead>,
        rhythms: List<RhythmFeatures>,
        lineSpacing: Int
    ): Set<Int> {
        return detectArticulations(image, noteheads, rhythms, lineSpacing)
            .filterValues { it == Articulation.STACCATO }
            .keys
    }

    // -----------------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Scans a rectangular region for the first articulation-like blob and classifies it.
     *
     * @return the classified [Articulation], or [Articulation.NONE] if no mark found.
     */
    private fun findAndClassify(
        image: BinaryImage,
        xLo: Int,
        xHi: Int,
        yStart: Int,
        yEnd: Int,
        maxMarkHeight: Int,
        s: Double
    ): Articulation {
        if (yStart > yEnd || xLo > xHi) return Articulation.NONE

        // Row-by-row scan: track per-row black pixel count and horizontal extent.
        data class RowInfo(val count: Int, val minX: Int, val maxX: Int)

        // Build row info list (only for rows in [yStart, yEnd]).
        val rowInfos = ArrayList<RowInfo>(yEnd - yStart + 1)
        for (y in yStart..yEnd) {
            var count = 0
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            for (x in xLo..xHi) {
                if (image.isBlack(x, y)) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
            rowInfos.add(RowInfo(count, if (count > 0) minX else 0, if (count > 0) maxX else 0))
        }

        // Find groups of consecutive rows that have ink. Return the classification
        // of the first group that is short enough to be an articulation mark (not a
        // stem or ledger line).
        var i = 0
        while (i < rowInfos.size) {
            if (rowInfos[i].count == 0) { i++; continue }
            // Start of an ink group.
            val groupStartRow = i
            var groupPixels = 0
            var groupMinX = Int.MAX_VALUE
            var groupMaxX = Int.MIN_VALUE
            while (i < rowInfos.size && rowInfos[i].count > 0) {
                groupPixels += rowInfos[i].count
                if (rowInfos[i].minX < groupMinX) groupMinX = rowInfos[i].minX
                if (rowInfos[i].maxX > groupMaxX) groupMaxX = rowInfos[i].maxX
                i++
            }
            val groupHeight = i - groupStartRow
            val groupWidth = if (groupPixels > 0) groupMaxX - groupMinX + 1 else 0

            // Skip groups that are too tall (stems, ledger lines).
            if (groupHeight > maxMarkHeight) continue
            // Skip groups with too few pixels (noise).
            if (groupPixels < MIN_PIXELS) continue

            // Classify this blob. Continue to the next group if it doesn't match
            // any articulation shape (could be noise or a non-articulation mark).
            val blob = MarkBlob(groupWidth, groupHeight, groupPixels)
            val art = classifyMark(blob, s)
            if (art != Articulation.NONE) return art
        }

        return Articulation.NONE
    }

    /**
     * Classifies an ink blob as staccato, tenuto, accent, or staccatissimo based on
     * its geometry.
     *
     * Decision tree:
     * 1. **Tenuto**: aspect ratio ≥ [TENUTO_AR_THRESHOLD] and width ≥ [TENUTO_MIN_WIDTH_FRAC]×s
     *    → clearly horizontal line (much wider than tall).
     * 2. **Compact blobs** (both dims ≤ [DOT_MAX_DIM_FRAC]×s):
     *    a. **Staccato**: fill ≥ [STACCATO_FILL_THRESHOLD] → solid dot.
     *    b. **Staccatissimo**: fill ≥ [MIN_ARTICULATION_FILL] and height ≥ width
     *       → vertical wedge/spade (moderate fill, taller than wide).
     *    c. **Accent**: fill < [ACCENT_FILL_THRESHOLD] and dims ≥ [ACCENT_MIN_DIM]
     *       → small horizontal wedge (very sparse, wider than tall).
     *    d. Remaining compact blobs → **Staccato** (slightly noisy solid dot).
     * 3. **Larger blobs**: **Accent** if fill < [ACCENT_FILL_THRESHOLD] and dims ≥ [ACCENT_MIN_DIM].
     * 4. Anything else → [Articulation.NONE].
     */
    private fun classifyMark(blob: MarkBlob, s: Double): Articulation {
        val ar = blob.aspectRatio
        val fill = blob.fillRatio
        val dotMaxDim = (DOT_MAX_DIM_FRAC * s).toInt().coerceAtLeast(2)

        // Tenuto: clearly horizontal line (width >> height).
        if (ar >= TENUTO_AR_THRESHOLD && blob.width >= (TENUTO_MIN_WIDTH_FRAC * s)) {
            return Articulation.TENUTO
        }

        val isCompact = blob.width <= dotMaxDim && blob.height <= dotMaxDim

        if (isCompact) {
            // Staccato: solid dot with high fill ratio.
            if (fill >= STACCATO_FILL_THRESHOLD) {
                return Articulation.STACCATO
            }
            // Staccatissimo: vertical wedge/spade with moderate fill (height ≥ width).
            if (fill >= MIN_ARTICULATION_FILL && blob.height >= blob.width) {
                return Articulation.STACCATISSIMO
            }
            // Accent: very sparse horizontal wedge (even when compact).
            if (fill < ACCENT_FILL_THRESHOLD && blob.width >= ACCENT_MIN_DIM && blob.height >= ACCENT_MIN_DIM) {
                return Articulation.ACCENT
            }
            // Remaining compact blobs: treat as staccato (slightly noisy solid dot).
            return Articulation.STACCATO
        }

        // Non-compact: Accent (larger hollow wedge).
        if (blob.width >= ACCENT_MIN_DIM && blob.height >= ACCENT_MIN_DIM && fill < ACCENT_FILL_THRESHOLD) {
            return Articulation.ACCENT
        }

        // Anything else (too large/tall or doesn't match any shape) → not an articulation.
        return Articulation.NONE
    }

    /**
     * Geometric description of an ink blob found in a search region.
     *
     * @param width       bounding-box width (maxX - minX + 1).
     * @param height      bounding-box height (consecutive ink rows).
     * @param pixelCount  total black pixels in the blob.
     */
    private data class MarkBlob(val width: Int, val height: Int, val pixelCount: Int) {
        /** Width / height ratio. ≥ 1 means wider than tall. */
        val aspectRatio: Double get() = if (height > 0) width.toDouble() / height else 0.0

        /** Black pixel density within the bounding box. 1.0 = fully solid. */
        val fillRatio: Double get() =
            if (width > 0 && height > 0) pixelCount.toDouble() / (width * height) else 0.0
    }
}
