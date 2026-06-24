package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NoteheadDetector], focusing on beamed-group splitting
 * (column-projection segmentation of a wide connected component that contains
 * several noteheads joined by stems and a shared beam).
 *
 * These tests draw synthetic **de-staffed** images (no staff lines) and feed
 * the connected components directly to the detector, so the splitting logic is
 * exercised in isolation without the full OMR pipeline.
 * 这些测试在合成「去谱线」图像上直接验证符头检测器，无需运行完整 OMR 管线。
 */
class NoteheadDetectorTest {

    /** Image geometry (matches the project's synthetic-test convention). */
    private val width = 420
    private val height = 120
    private val lineSpacing = 10
    private val s = lineSpacing.toDouble()

    private fun blank(): BinaryImage = BinaryImage.blank(width, height)

    private fun ellipse(img: BinaryImage, cx: Int, cy: Int, rx: Int = 4, ry: Int = 3) {
        for (y in (cy - ry)..(cy + ry)) {
            for (x in (cx - rx)..(cx + rx)) {
                if (x !in 0 until width || y !in 0 until height) continue
                val ndx = (x - cx).toDouble() / rx
                val ndy = (y - cy).toDouble() / ry
                if (ndx * ndx + ndy * ndy <= 1.01) img.set(x, y, true)
            }
        }
    }

    /**
     * Draw a beamed group of filled noteheads with stems going **up** to a shared
     * beam, fusing into a single connected component.
     * 绘制符干向上、共享横梁的实心符头连梁组（融合为单个连通块）。
     *
     * @param centers   notehead center x-coordinates (left to right).
     * @param cy        notehead center y.
     * @param beamY     beam top y (stems extend up to here).
     * @param beamLayers number of beam layers (1=eighth, 2=sixteenth).
     */
    private fun beamedUp(
        img: BinaryImage,
        centers: List<Int>,
        cy: Int,
        beamY: Int,
        beamLayers: Int = 1,
    ) {
        for (cx in centers) ellipse(img, cx, cy)
        for (cx in centers) {
            val x = cx + 4
            for (y in (cy - 3) downTo beamY) if (y in 0 until height) img.set(x, y, true)
        }
        val xLeft = centers.min() + 4
        val xRight = centers.max() + 4
        for (layer in 0 until beamLayers) {
            for (y in (beamY + layer * 4)..(beamY + layer * 4 + 1)) {
                if (y !in 0 until height) continue
                for (x in xLeft..xRight) if (x in 0 until width) img.set(x, y, true)
            }
        }
    }

    /**
     * Draw a beamed group with stems going **down** to a shared beam.
     * 绘制符干向下、共享横梁的实心符头连梁组。
     */
    private fun beamedDown(
        img: BinaryImage,
        centers: List<Int>,
        cy: Int,
        beamY: Int,
        beamLayers: Int = 1,
    ) {
        for (cx in centers) ellipse(img, cx, cy)
        for (cx in centers) {
            val x = cx + 4
            for (y in (cy + 3)..beamY) if (y in 0 until height) img.set(x, y, true)
        }
        val xLeft = centers.min() + 4
        val xRight = centers.max() + 4
        for (layer in 0 until beamLayers) {
            for (y in (beamY - layer * 4 - 1)..(beamY - layer * 4)) {
                if (y !in 0 until height) continue
                for (x in xLeft..xRight) if (x in 0 until width) img.set(x, y, true)
            }
        }
    }

    /** Run the detector end-to-end on a de-staffed image. */
    private fun detect(img: BinaryImage): List<Notehead> {
        val blobs = ConnectedComponents.label(img, minPixels = 4)
        return NoteheadDetector.detect(blobs, lineSpacing, img)
    }

    // ---- 基线：充分间隔的连梁组（此前已支持） ---------------------------------

    @Test
    fun `well-separated beamed pair yields two noteheads`() {
        val img = blank()
        // 中心相距 6s，此前已正确切分。
        beamedUp(img, listOf(100, 160), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `well-separated beamed pair keeps detected centers near drawn centers`() {
        val img = blank()
        beamedUp(img, listOf(100, 160), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals(2, nhs.size)
        // 检测中心应在绘制的中心附近（容差 ≤ 1.5s）。
        val drawn = listOf(100, 160)
        nhs.sortedBy { it.centerX }.forEachIndexed { i, nh ->
            assertTrue("第 $i 个符头中心偏差过大: ${nh.centerX}", kotlin.math.abs(nh.centerX - drawn[i]) <= 15)
        }
    }

    // ---- 拥挤连梁组（本次修复目标） ------------------------------------------

    @Test
    fun `crowded beamed pair with 2s spacing yields two noteheads`() {
        val img = blank()
        // 中心相距 2s = 20px。符头宽 9px，间隙 ~11px。
        beamedUp(img, listOf(100, 120), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("中心相距 2s 的拥挤连梁对应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `crowded beamed pair with 1_5s spacing yields two noteheads`() {
        val img = blank()
        // 中心相距 1.5s = 15px。
        beamedUp(img, listOf(100, 115), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("中心相距 1.5s 的拥挤连梁对应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `very crowded beamed pair touching edges yields two noteheads`() {
        val img = blank()
        // 中心相距 1.1s = 11px，符头边缘几乎相接。
        beamedUp(img, listOf(100, 111), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("中心相距 1.1s 的极拥挤连梁对应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `crowded beamed down-stem pair yields two noteheads`() {
        val img = blank()
        // 符干向下的拥挤连梁组。
        beamedDown(img, listOf(100, 120), cy = 55, beamY = 90)
        val nhs = detect(img)
        assertEquals("向下符干拥挤连梁对应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `three crowded beamed noteheads yield three`() {
        val img = blank()
        // 三个符头，相邻中心相距 1.6s = 16px。
        beamedUp(img, listOf(100, 116, 132), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("三个拥挤连梁符头应切分为 3 个", 3, nhs.size)
    }

    @Test
    fun `crowded beamed sixteenth pair yields two noteheads`() {
        val img = blank()
        // 双横梁（十六分）拥挤连梁组。
        beamedUp(img, listOf(100, 118), cy = 55, beamY = 25, beamLayers = 2)
        val nhs = detect(img)
        assertEquals(2, nhs.size)
    }

    @Test
    fun `crowded beamed pair centers are near the drawn centers`() {
        val img = blank()
        beamedUp(img, listOf(100, 118), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals(2, nhs.size)
        val drawn = listOf(100, 118)
        nhs.sortedBy { it.centerX }.forEachIndexed { i, nh ->
            assertTrue(
                "第 $i 个符头中心 ${nh.centerX} 偏离绘制中心 ${drawn[i]} 过大",
                kotlin.math.abs(nh.centerX - drawn[i]) <= 12
            )
        }
    }

    @Test
    fun `overlapping beamed pair sharing a column yields two noteheads`() {
        val img = blank()
        // 中心相距 0.8s = 8px：两个椭圆在 x=104 共享一列（符头边缘重叠）。
        // 列投影在该列降至 ~2，仍应被切分为两个符头。
        beamedUp(img, listOf(100, 108), cy = 55, beamY = 25)
        val nhs = detect(img)
        assertEquals("边缘重叠的极拥挤连梁对应切分为 2 个符头", 2, nhs.size)
    }

    @Test
    fun `smeared beamed pair with continuous projection splits at valley`() {
        // 模拟真实照片中的墨迹晕染：两个符头之间有一条 3px 高的水平「桥」，
        // 使列投影在两符头之间保持 ≥ peakThreshold（不产生清零间隙），
        // 仅在交界处形成浅凹陷。此时只能依靠山谷检测切分。
        val img = blank()
        // 两个填充矩形（符头）+ 连接桥 + 向上符干 + 横梁。
        fun rect(x0: Int, x1: Int, y0: Int, y1: Int) {
            for (y in y0..y1) for (x in x0..x1) if (x in 0 until width && y in 0 until height) img.set(x, y, true)
        }
        rect(96, 104, 52, 58)   // 符头 1（cx≈100）
        rect(120, 128, 52, 58)  // 符头 2（cx≈124）
        rect(104, 120, 54, 56)  // 3px 高连接桥（模拟晕染），使投影连续
        // 符干 + 横梁（符干向上）
        for (y in 52 downTo 25) { img.set(100, y, true); img.set(124, y, true) }
        rect(100, 124, 25, 26)  // 横梁
        val nhs = detect(img)
        assertEquals("连续投影（带浅凹陷）的晕染连梁对应在山谷处切分为 2 个符头", 2, nhs.size)
    }

    // ---- 不过度切分（回归保护） ----------------------------------------------

    @Test
    fun `single isolated notehead is not split`() {
        val img = blank()
        ellipse(img, 100, 55)
        val nhs = detect(img)
        assertEquals("单个孤立符头不应被切分", 1, nhs.size)
    }

    @Test
    fun `empty image returns no noteheads`() {
        val img = blank()
        assertEquals(0, detect(img).size)
    }

    @Test
    fun `blob without a beam is not split into noteheads`() {
        val img = blank()
        // 两个符头 + 向上符干，但**没有横梁**：各自独立成块，不进入连梁切分路径。
        ellipse(img, 100, 55)
        for (y in 52 downTo 25) img.set(104, y, true)
        ellipse(img, 200, 55)
        for (y in 52 downTo 25) img.set(204, y, true)
        val nhs = detect(img)
        // 两个独立符头+符干块 → 二次扫描各恢复 1 个符头。
        assertEquals(2, nhs.size)
    }

    @Test
    fun `zero line spacing returns empty`() {
        val img = blank()
        val blobs = ConnectedComponents.label(img, minPixels = 1)
        assertEquals(0, NoteheadDetector.detect(blobs, 0, img).size)
    }
}
