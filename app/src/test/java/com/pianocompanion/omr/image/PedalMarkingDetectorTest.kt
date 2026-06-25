package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PedalMarkingDetector] using synthetic binary images.
 *
 * A **pedal marking** (踏板记号) in piano music indicates when to press and release
 * the sustain pedal. The press is marked by "Ped." text below the staff, and the
 * release is marked by a star/flower symbol (✱).
 *
 * These tests render letter templates ('P', 'e', 'd') and release star shapes
 * pixel-by-pixel, then verify that the detector correctly identifies (or rejects)
 * pedal markings based on letter template matching, shape detection, search region
 * constraints, and system boundaries.
 */
class PedalMarkingDetectorTest {

    private val width = 400
    private val height = 140
    private val s = 10 // staff line spacing

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    /** Staff lines at y = 30,40,50,60,70 → spacing = 10. */
    private val lineYs = listOf(30, 40, 50, 60, 70)

    private fun makeSystem(lys: List<Int> = lineYs): StaffSystem =
        StaffSystem(lys.map { StaffLine(it - 1, it + 1, 1.0) })

    /** Draw staff lines into the image. */
    private fun drawStaff(img: BinaryImage, lys: List<Int> = lineYs) {
        for (y in lys) {
            for (x in 0 until width) {
                img.set(x, y, true)
            }
        }
    }

    /**
     * Render a letter template at the given position and integer scale.
     * Each template cell is drawn as a scale×scale block of black pixels.
     */
    private fun renderLetter(img: BinaryImage, char: Char, x: Int, y: Int, scale: Int) {
        val tmpl = PedalMarkingDetector.LETTER_TEMPLATES[char] ?: return
        for (r in 0 until 7) {
            for (c in 0 until 5) {
                if (tmpl[r * 5 + c]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = x + c * scale + dx
                            val py = y + r * scale + dy
                            if (px in 0 until img.width && py in 0 until img.height) {
                                img.set(px, py, true)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Render the release star template at the given position and integer scale.
     */
    private fun renderReleaseStar(img: BinaryImage, x: Int, y: Int, scale: Int) {
        val tmpl = PedalMarkingDetector.RELEASE_TEMPLATE
        for (r in 0 until 7) {
            for (c in 0 until 5) {
                if (tmpl[r * 5 + c]) {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = x + c * scale + dx
                            val py = y + r * scale + dy
                            if (px in 0 until img.width && py in 0 until img.height) {
                                img.set(px, py, true)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Draw a small period/dot at the given position. */
    private fun renderPeriod(img: BinaryImage, x: Int, y: Int, size: Int = 3) {
        for (dy in 0 until size) {
            for (dx in 0 until size) {
                if (x + dx in 0 until img.width && y + dy in 0 until img.height) {
                    img.set(x + dx, y + dy, true)
                }
            }
        }
    }

    /** Convenience: build blobs from image. */
    private fun blobs(img: BinaryImage): List<Blob> =
        ConnectedComponents.label(img, minPixels = 4)

    /**
     * Render "Ped." text at position (x, y) with the given scale.
     * Letters are spaced with a small gap between them.
     */
    private fun renderPedText(img: BinaryImage, x: Int, y: Int, scale: Int = 2) {
        // P: 5 cells wide → 5*scale px wide
        renderLetter(img, 'P', x, y, scale)
        // e: starts after P + gap of 2*scale
        val gap = scale * 2
        renderLetter(img, 'e', x + 5 * scale + gap, y, scale)
        // d: starts after e + gap
        renderLetter(img, 'd', x + 10 * scale + 2 * gap, y, scale)
        // period: after d + smaller gap
        renderPeriod(img, x + 15 * scale + 2 * gap + scale, y + 6 * scale, 3)
    }

    // ---- Ped. text detection ----------------------------------------------

    @Test
    fun `Ped text below staff is detected as PRESS`() {
        val img = blank()
        drawStaff(img)
        // Render "Ped." at x=100, y=80 (below bottom line at y=70)
        renderPedText(img, 100, 80, scale = 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertEquals("Should detect exactly 1 Ped. marking", 1, presses.size)
        // Center X should be near x=100 + half the text width
        assertTrue("Press centerX should be around 100-130", presses[0].centerX in 95..135)
    }

    @Test
    fun `Ped without period is still detected`() {
        val img = blank()
        drawStaff(img)
        // Render "Ped" without period
        renderLetter(img, 'P', 100, 80, 2)
        renderLetter(img, 'e', 100 + 14, 80, 2) // 5*2 + 2*2 gap = 14
        renderLetter(img, 'd', 100 + 28, 80, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertEquals(1, presses.size)
    }

    @Test
    fun `no pedal marking returns empty`() {
        val img = blank()
        drawStaff(img)
        // No pedal text at all
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("Should find no pedal markings", results.isEmpty())
    }

    // ---- Release mark detection -------------------------------------------

    @Test
    fun `release star below staff is detected as RELEASE`() {
        val img = blank()
        drawStaff(img)
        // Render a release star at x=200, y=82
        renderReleaseStar(img, 200, 82, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val releases = results.filter { it.type == PedalMarkingDetector.PedalType.RELEASE }
        assertEquals("Should detect exactly 1 release mark", 1, releases.size)
    }

    @Test
    fun `Ped and release star both detected`() {
        val img = blank()
        drawStaff(img)
        // Render "Ped." at x=80
        renderPedText(img, 80, 80, scale = 2)
        // Render release star at x=200
        renderReleaseStar(img, 200, 82, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        val releases = results.filter { it.type == PedalMarkingDetector.PedalType.RELEASE }
        assertEquals(1, presses.size)
        assertEquals(1, releases.size)
        // Results should be sorted by X (press before release)
        assertTrue(presses[0].centerX < releases[0].centerX)
    }

    @Test
    fun `multiple pedal press and release cycle detected`() {
        val img = blank()
        drawStaff(img)
        // First cycle: Ped. at x=50, release at x=120
        renderPedText(img, 50, 80, scale = 2)
        renderReleaseStar(img, 120, 82, 2)
        // Second cycle: Ped. at x=200, release at x=280
        renderPedText(img, 200, 80, scale = 2)
        renderReleaseStar(img, 280, 82, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        val releases = results.filter { it.type == PedalMarkingDetector.PedalType.RELEASE }
        assertEquals(2, presses.size)
        assertEquals(2, releases.size)
    }

    // ---- Search region constraints ----------------------------------------

    @Test
    fun `Ped text above staff is not detected`() {
        val img = blank()
        drawStaff(img)
        // Render "Ped." above the staff (y = 5, above top line at y=30)
        renderPedText(img, 100, 5, scale = 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("Ped. above staff should not be detected", results.isEmpty())
    }

    @Test
    fun `Ped text too far below staff is not detected`() {
        val img = blank()
        drawStaff(img)
        // Render "Ped." very far below (beyond 4*spacing = 40 + 70 = 110)
        // Place at y=115 (centerY would be ~122, beyond searchBottom=110)
        renderPedText(img, 100, 115, scale = 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertTrue("Ped. too far below should not be detected", presses.isEmpty())
    }

    // ---- Multi-system ------------------------------------------------------

    @Test
    fun `pedal marking in different systems detected independently`() {
        // System 1: y = 30-70 (bottom line at 70)
        // System 2: y = 110-150 (bottom line at 150)
        // 注意：两个系统之间需留出足够空间，避免系统1的踏板文字与系统2的
        // 谱线在二值图中合并为同一连通块（合并后宽度超限会被过滤掉）。
        val sys1Ys = listOf(30, 40, 50, 60, 70)
        val sys2Ys = listOf(110, 120, 130, 140, 150)
        val img = BinaryImage.blank(width, 220)
        // Draw both staff systems
        for (y in sys1Ys) for (x in 0 until width) img.set(x, y, true)
        for (y in sys2Ys) for (x in 0 until width) img.set(x, y, true)
        // Ped. below system 1 at y=78 (search region: 75–110, text spans 78–91)
        renderPedText(img, 80, 78, scale = 2)
        // Ped. below system 2 at y=158 (search region: 155–190, text spans 158–171)
        renderPedText(img, 200, 158, scale = 2)

        val systems = listOf(makeSystem(sys1Ys), makeSystem(sys2Ys))
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, systems, s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertEquals(2, presses.size)
        // Each should belong to a different system
        assertEquals(0, presses[0].systemIdx)
        assertEquals(1, presses[1].systemIdx)
    }

    @Test
    fun `pedal marking does not match across systems`() {
        // System 1: y = 30-70 (bottom line at 70)
        // System 2: y = 110-150 (bottom line at 150)
        val sys1Ys = listOf(30, 40, 50, 60, 70)
        val sys2Ys = listOf(110, 120, 130, 140, 150)
        val img = BinaryImage.blank(width, 220)
        for (y in sys1Ys) for (x in 0 until width) img.set(x, y, true)
        for (y in sys2Ys) for (x in 0 until width) img.set(x, y, true)
        // Ped. only below system 1
        renderPedText(img, 80, 78, scale = 2)

        val systems = listOf(makeSystem(sys1Ys), makeSystem(sys2Ys))
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, systems, s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertEquals(1, presses.size)
        assertEquals(0, presses[0].systemIdx)
    }

    // ---- Edge cases --------------------------------------------------------

    @Test
    fun `empty blobs returns empty`() {
        val img = blank()
        drawStaff(img)
        val results = PedalMarkingDetector.detect(img, emptyList(), listOf(makeSystem()), s)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `empty systems returns empty`() {
        val img = blank()
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, emptyList(), s)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        drawStaff(img)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), 0)
        assertTrue(results.isEmpty())
    }

    // ---- Template validation -----------------------------------------------

    @Test
    fun `letter templates exist for P, e, d`() {
        assertNotNull("P template should exist", PedalMarkingDetector.LETTER_TEMPLATES['P'])
        assertNotNull("e template should exist", PedalMarkingDetector.LETTER_TEMPLATES['e'])
        assertNotNull("d template should exist", PedalMarkingDetector.LETTER_TEMPLATES['d'])
    }

    @Test
    fun `letter templates have correct dimensions`() {
        for ((char, tmpl) in PedalMarkingDetector.LETTER_TEMPLATES) {
            assertEquals("Template '$char' should have 35 cells (5×7)", 35, tmpl.size)
        }
    }

    @Test
    fun `letter templates are mutually distinct`() {
        val templates = PedalMarkingDetector.LETTER_TEMPLATES.toList()
        for (i in templates.indices) {
            for (j in i + 1 until templates.size) {
                val (c1, t1) = templates[i]
                val (c2, t2) = templates[j]
                var diff = 0
                for (k in t1.indices) if (t1[k] != t2[k]) diff++
                assertTrue(
                    "Templates '$c1' and '$c2' should be distinct (hamming distance=$diff)",
                    diff >= 5
                )
            }
        }
    }

    @Test
    fun `each template column and row has at least one pixel`() {
        for ((char, tmpl) in PedalMarkingDetector.LETTER_TEMPLATES) {
            // Check each column has at least 1 pixel
            for (c in 0 until 5) {
                var hasPixel = false
                for (r in 0 until 7) {
                    if (tmpl[r * 5 + c]) hasPixel = true
                }
                assertTrue(
                    "Template '$char' column $c should have at least 1 pixel",
                    hasPixel
                )
            }
            // Check each row has at least 1 pixel
            for (r in 0 until 7) {
                var hasPixel = false
                for (c in 0 until 5) {
                    if (tmpl[r * 5 + c]) hasPixel = true
                }
                assertTrue(
                    "Template '$char' row $r should have at least 1 pixel",
                    hasPixel
                )
            }
        }
    }

    @Test
    fun `release template exists and has correct dimensions`() {
        assertEquals(35, PedalMarkingDetector.RELEASE_TEMPLATE.size)
    }

    @Test
    fun `release template is distinct from letter templates`() {
        val releaseTmpl = PedalMarkingDetector.RELEASE_TEMPLATE
        for ((char, letterTmpl) in PedalMarkingDetector.LETTER_TEMPLATES) {
            var diff = 0
            for (i in releaseTmpl.indices) if (releaseTmpl[i] != letterTmpl[i]) diff++
            assertTrue(
                "Release template should differ from letter '$char' (distance=$diff)",
                diff >= 5
            )
        }
    }

    @Test
    fun `self-matching each letter template yields zero distance`() {
        // Verify that rendering then downsampling produces zero hamming distance.
        // This validates the template round-trip property.
        val img = blank()
        val scale = 3
        for ((char, tmpl) in PedalMarkingDetector.LETTER_TEMPLATES) {
            val renderImg = blank()
            renderLetter(renderImg, char, 50, 50, scale)
            val bl = blobs(renderImg)
            // Find the blob at (50,50)
            val targetBlob = bl.find { it.minX >= 49 && it.minY >= 49 }
            assertNotNull("Should find rendered blob for '$char'", targetBlob)
            if (targetBlob != null) {
                val grid = downsampleGrid(renderImg, targetBlob)
                var d = 0
                for (i in grid.indices) if (grid[i] != tmpl[i]) d++
                assertEquals(
                    "Self-match for '$char' should have zero distance",
                    0, d
                )
            }
        }
    }

    /** Helper: downsample a blob region to 5×7 grid (mirror of PedalMarkingDetector's private method). */
    private fun downsampleGrid(img: BinaryImage, blob: Blob): BooleanArray {
        val cols = 5
        val rows = 7
        val bw = blob.width
        val bh = blob.height
        val out = BooleanArray(cols * rows)
        for (r in 0 until rows) {
            val ry0 = blob.minY + bh * r / rows
            val ry1 = (blob.minY + bh * (r + 1) / rows).coerceAtMost(blob.maxY + 1)
            for (c in 0 until cols) {
                val rx0 = blob.minX + bw * c / cols
                val rx1 = (blob.minX + bw * (c + 1) / cols).coerceAtMost(blob.maxX + 1)
                var black = 0
                var total = 0
                for (y in ry0 until ry1) for (x in rx0 until rx1) {
                    if (img.isBlack(x, y)) black++
                    total++
                }
                out[r * cols + c] = total > 0 && black.toDouble() / total >= 0.4
            }
        }
        return out
    }

    // ---- Non-letter shapes should not match --------------------------------

    @Test
    fun `random shape is not detected as pedal marking`() {
        val img = blank()
        drawStaff(img)
        // Draw a random blob below the staff (not a letter or star)
        for (y in 80..90) for (x in 100..115) img.set(x, y, true)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        assertTrue("Random shape should not be detected as pedal marking", results.isEmpty())
    }

    @Test
    fun `single letter P alone is not detected as pedal`() {
        val img = blank()
        drawStaff(img)
        // Only render 'P' without 'e' and 'd'
        renderLetter(img, 'P', 100, 80, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertTrue("Single 'P' without 'ed' should not be detected as Ped.", presses.isEmpty())
    }

    @Test
    fun `wrong letter order is not detected as Ped`() {
        val img = blank()
        drawStaff(img)
        // Render 'd', 'e', 'P' in wrong order
        renderLetter(img, 'd', 100, 80, 2)
        renderLetter(img, 'e', 100 + 14, 80, 2)
        renderLetter(img, 'P', 100 + 28, 80, 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        val presses = results.filter { it.type == PedalMarkingDetector.PedalType.PRESS }
        assertTrue("Wrong letter order 'deP' should not be detected as Ped.", presses.isEmpty())
    }

    // ---- Results sorting ---------------------------------------------------

    @Test
    fun `results are sorted by centerX`() {
        val img = blank()
        drawStaff(img)
        // Render release at x=300 (before Ped at x=80 in image coordinates,
        // but we want to test that results are sorted by X)
        renderReleaseStar(img, 300, 82, 2)
        renderPedText(img, 80, 80, scale = 2)
        val bl = blobs(img)
        val results = PedalMarkingDetector.detect(img, bl, listOf(makeSystem()), s)
        // Should be sorted by centerX
        for (i in 1 until results.size) {
            assertTrue(
                "Results should be sorted by centerX",
                results[i - 1].centerX <= results[i].centerX
            )
        }
    }
}
