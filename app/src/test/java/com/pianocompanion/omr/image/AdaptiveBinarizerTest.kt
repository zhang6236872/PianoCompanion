package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AdaptiveBinarizer] — per-tile Otsu + bilinear interpolation,
 * the module that makes binarization robust to uneven lighting.
 *
 * The key behavioral tests build synthetic grayscale images with a controlled
 * illumination gradient (the failure mode of global Otsu) and assert that:
 *  - adaptive binarization recovers ink across the whole gradient while keeping
 *    the background clean, and
 *  - a single global Otsu threshold provably *cannot* do both at once
 *    (deterministic impossibility proof, not a fragile threshold guess).
 *
 * Pure JVM — no Android dependency, fully exercised with synthetic images.
 */
class AdaptiveBinarizerTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Fill a [width]×[height] grayscale array with a per-column background value. */
    private fun gradientGray(
        width: Int,
        height: Int,
        background: (x: Int) -> Int,
        ink: Map<Int, Int> = emptyMap()
    ): IntArray {
        val gray = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                gray[y * width + x] = ink[x] ?: background(x)
            }
        }
        return gray
    }

    /** Black pixel count in column [x] of the binary image. */
    private fun columnBlack(img: BinaryImage, x: Int): Int {
        var c = 0
        for (y in 0 until img.height) if (img.isBlack(x, y)) c++
        return c
    }

    /** Black pixel count in the horizontal band [x0, x1). */
    private fun bandBlack(img: BinaryImage, x0: Int, x1: Int): Int {
        var c = 0
        for (y in 0 until img.height) for (x in x0 until x1) if (img.isBlack(x, y)) c++
        return c
    }

    // ── basic sanity ──────────────────────────────────────────────────────────

    @Test
    fun uniformWhiteImage_hasNoInk() {
        val w = 80; val h = 40
        val gray = IntArray(w * h) { 255 }
        val img = AdaptiveBinarizer.binarize(w, h, gray)
        assertEquals(0, img.totalBlack())
    }

    @Test
    fun uniformBlackImage_allInk() {
        val w = 80; val h = 40
        val gray = IntArray(w * h) { 0 }
        val img = AdaptiveBinarizer.binarize(w, h, gray)
        assertEquals(w * h, img.totalBlack())
    }

    @Test
    fun returnsCorrectDimensions() {
        val w = 73; val h = 47
        val gray = IntArray(w * h) { 200 }
        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 10)
        assertEquals(w, img.width)
        assertEquals(h, img.height)
    }

    // ── single-tile fallback ──────────────────────────────────────────────────

    @Test
    fun imageSmallerThanTile_fallsBackToGlobalOtsu() {
        val w = 10; val h = 10
        val gray = gradientGray(w, h, { 200 }, ink = mapOf(5 to 30))
        val adaptive = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 40)
        val globalThreshold = OtsuThresholder.threshold(gray)
        val global = BinaryImage.fromGrayscale(w, h, gray, globalThreshold)
        assertTrue("small image must match global Otsu", adaptive == global)
    }

    @Test
    fun imageExactlyOneTile_fallsBackToGlobalOtsu() {
        val w = 40; val h = 40
        val gray = gradientGray(w, h, { 200 }, ink = mapOf(20 to 30))
        val adaptive = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 40)
        val globalThreshold = OtsuThresholder.threshold(gray)
        val global = BinaryImage.fromGrayscale(w, h, gray, globalThreshold)
        assertTrue("single-tile image must match global Otsu", adaptive == global)
    }

    // ── ink on uniform background ─────────────────────────────────────────────

    @Test
    fun inkOnUniformBackground_recoveredAndBackgroundClean() {
        val w = 80; val h = 40
        val inkX = 40 // center column
        val gray = gradientGray(w, h, { 220 }, ink = mapOf(inkX to 10))
        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 40)

        // Every pixel of the ink column is black.
        assertEquals(h, columnBlack(img, inkX))
        // A nearby background column is entirely white.
        assertEquals(0, columnBlack(img, inkX + 5))
    }

    // ── THE core value: lighting gradient ────────────────────────────────────
    // 5 tiles across a horizontal illumination gradient. Each tile has a uniform
    // background that brightens left→right, plus one ink column at its center
    // whose luminance tracks the local background (constant gap), mimicking a
    // real shadow/vignette. A single global threshold provably cannot keep the
    // darkest background clean AND recover ink in the brightest tile at once.

    private val gradientWidth = 200
    private val gradientHeight = 40
    private val tileCenters = listOf(20, 60, 100, 140, 180)
    private val backgrounds = listOf(80, 120, 160, 200, 240) // per-tile background
    private val inkGap = 70

    private fun gradientImage(): IntArray {
        val ink = tileCenters.mapIndexed { i, x -> x to (backgrounds[i] - inkGap) }.toMap()
        return gradientGray(gradientWidth, gradientHeight, { x ->
            val tileIndex = (x / 40).coerceAtMost(backgrounds.lastIndex)
            backgrounds[tileIndex]
        }, ink)
    }

    @Test
    fun horizontalLightingGradient_adaptiveRecoversInkAcrossWholeGradient() {
        val gray = gradientImage()
        val img = AdaptiveBinarizer.binarize(gradientWidth, gradientHeight, gray, tileSize = 40)

        // (a) ink recovered at EVERY tile center, including the brightest tile.
        tileCenters.forEachIndexed { i, x ->
            assertEquals("ink missing at tile $i center x=$x", gradientHeight, columnBlack(img, x))
        }
        // (b) background is clean everywhere except the ink columns.
        val backgroundPixels = gradientWidth * gradientHeight - tileCenters.size * gradientHeight
        val backgroundBlack = img.totalBlack() - tileCenters.size * gradientHeight
        assertEquals("background must be clean across the gradient", 0, backgroundBlack)
    }

    @Test
    fun horizontalLightingGradient_globalOtsuCannotSatisfyBothExtremes() {
        val gray = gradientImage()
        val globalThreshold = OtsuThresholder.threshold(gray)

        val inkBrightestLum = backgrounds.last() - inkGap   // 170
        val bgDarkestLum = backgrounds.first()               // 80

        val inkBrightestIsBlack = inkBrightestLum <= globalThreshold  // needs T >= 170
        val bgDarkestIsWhite = bgDarkestLum > globalThreshold         // needs T <  80

        // Deterministic impossibility: no single threshold T can satisfy
        // (T >= 170) AND (T < 80). This is exactly why adaptive binarization
        // is required for uneven lighting.
        assertFalse(
            "global threshold=$globalThreshold satisfied both extremes " +
                "(ink@bright black=$inkBrightestIsBlack, bg@dark white=$bgDarkestIsWhite) " +
                "— this is mathematically impossible; test logic is wrong",
            inkBrightestIsBlack && bgDarkestIsWhite
        )
    }

    @Test
    fun horizontalLightingGradient_adaptiveSatisfiesBothExtremes() {
        val gray = gradientImage()
        val img = AdaptiveBinarizer.binarize(gradientWidth, gradientHeight, gray, tileSize = 40)

        // Adaptive must recover ink in the brightest tile AND keep the darkest
        // background clean — the thing global Otsu provably cannot do.
        val inkBrightestX = tileCenters.last()
        assertEquals(
            "ink in brightest tile must be recovered",
            gradientHeight, columnBlack(img, inkBrightestX)
        )
        // Darkest background column (not an ink column).
        val bgDarkX = 0
        assertEquals(
            "darkest background must be clean",
            0, columnBlack(img, bgDarkX)
        )
    }

    // ── degenerate-tile fallback ──────────────────────────────────────────────

    @Test
    fun degenerateUniformRegion_fallsBackToGlobalAndStaysClean() {
        // Left tile = uniform gray 200 (no contrast → degenerate).
        // Right tile = ink(50) on background(200).
        val w = 80; val h = 40
        val gray = gradientGray(w, h, { if (it < 40) 200 else 200 }, ink = mapOf(60 to 50))
        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 40)

        // The uniform left half must contain zero black pixels — the degenerate
        // tile fell back to the global threshold rather than producing noise.
        assertEquals("degenerate uniform region must stay clean", 0, bandBlack(img, 0, 40))
        // Ink in the right half is still recovered.
        assertEquals(h, columnBlack(img, 60))
    }

    @Test
    fun pureUniformImage_producesNoSpuriousInk() {
        val w = 80; val h = 80
        val gray = IntArray(w * h) { 130 }
        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 40)
        assertEquals("all-degenerate image must stay clean", 0, img.totalBlack())
    }

    // ── vignette (corner-shadow) pattern ──────────────────────────────────────
    // Realistic case: a dark "shadow" in one corner tile over otherwise bright,
    // uniform paper — the classic phone-shadow failure mode for global Otsu.
    // Ink marks sit at tile centres (no interpolation at the centre) in both the
    // dark and bright tiles.

    @Test
    fun vignettePattern_recoversInkInShadowCornerAndBrightPaper() {
        val w = 160; val h = 160; val tile = 40 // 4×4 grid
        // Corner tile (0,0) is a dark shadow (bg 90); everything else bright (bg 200).
        val cornerBg = 90
        val brightBg = 200

        fun bg(x: Int, y: Int): Int =
            if (x / tile == 0 && y / tile == 0) cornerBg else brightBg

        val gray = IntArray(w * h)
        // Ink mark A: 5×5 block centred at (20,20) in the shadow corner.
        // Ink mark B: 5×5 block centred at (100,100) on bright paper.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val b = bg(x, y)
                val inMarkA = x in 18..22 && y in 18..22
                val inMarkB = x in 98..102 && y in 98..102
                gray[y * w + x] = when {
                    inMarkA -> cornerBg - 70   // ink tracks the local (dark) background
                    inMarkB -> brightBg - 70   // ink tracks the local (bright) background
                    else -> b
                }
            }
        }

        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = tile)

        // Ink recovered in the dark shadow corner (global Otsu would lose this).
        var blackA = 0
        for (y in 18..22) for (x in 18..22) if (img.isBlack(x, y)) blackA++
        assertEquals("shadow-corner ink not recovered", 25, blackA)

        // Ink recovered on the bright paper.
        var blackB = 0
        for (y in 98..102) for (x in 98..102) if (img.isBlack(x, y)) blackB++
        assertEquals("bright-paper ink not recovered", 25, blackB)

        // Bright paper far from any ink/mark stays clean (degenerate tile → global).
        assertEquals("bright paper must stay clean", 0, img.isBlack(140, 140).let { if (it) 1 else 0 })
        var cleanPixels = 0
        for (y in 60 until 90) for (x in 60 until 90) if (img.isBlack(x, y)) cleanPixels++
        assertEquals("bright-paper region must be free of false ink", 0, cleanPixels)
    }

    // ── configurable tile size ────────────────────────────────────────────────

    @Test
    fun smallerTileSize_recoversInkInFineGradient() {
        // A gradient finer than the default tile: use a small tile to resolve it.
        val w = 120; val h = 40
        val ink = mapOf(15 to 10, 55 to 70, 95 to 130)
        val gray = gradientGray(w, h, { x ->
            when (x / 40) { 0 -> 80; 1 -> 140; else -> 200 }
        }, ink)
        val img = AdaptiveBinarizer.binarize(w, h, gray, tileSize = 20)
        ink.keys.forEach { x ->
            assertTrue("ink at x=$x not recovered with fine tile", columnBlack(img, x) > 0)
        }
    }
}
