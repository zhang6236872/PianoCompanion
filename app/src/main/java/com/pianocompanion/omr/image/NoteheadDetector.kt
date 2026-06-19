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
 *
 * When an optional [BinaryImage] is supplied, two additional passes run:
 *
 *  - **Secondary pass**: recovers a single notehead physically fused with its
 *    stem (a common case in real scores). Such a blob is tall (stem included)
 *    but compact in width; its notehead portion is the widest horizontal band,
 *    whose centroid is reported as the notehead.
 *
 *  - **Beamed-group pass**: splits a *wide* connected component that contains
 *    several noteheads joined by their stems and a shared horizontal beam
 *    (e.g. two beamed eighths). Such a blob is too wide to be a single notehead
 *    and would otherwise be discarded entirely. The beam is located first
 *    (it spans most of the blob width), which reveals the notehead side; a
 *    column projection over the notehead band then yields one peak per
 *    notehead. This directly feeds the existing [RhythmAnalyzer] beam grouping,
 *    so beamed notes are correctly classified as eighths/sixteenths.
 */
object NoteheadDetector {

    /**
     * @param blobs all connected components of the cleaned image.
     * @param lineSpacing staff line spacing (px) used to scale the thresholds.
     * @param image optional cleaned image enabling the notehead+stem and
     *        beamed-group recovery passes.
     */
    fun detect(blobs: List<Blob>, lineSpacing: Int, image: BinaryImage? = null): List<Notehead> {
        if (lineSpacing <= 0) return emptyList()
        val s = lineSpacing.toDouble()

        val minDim = (0.5 * s).toInt().coerceAtLeast(3)
        val maxDim = (2.5 * s).toInt()
        val minArea = (0.25 * s * s).toInt().coerceAtLeast(4)
        val maxStemBlobHeight = (5.0 * s).toInt() // 符头+符干的最大合理高度
        val maxBeamedHeight = (7.0 * s).toInt() // 连梁组（多符头+符干+横梁）的最大合理高度

        val results = ArrayList<Notehead>()

        for (blob in blobs) {
            val compactWidth = blob.width in minDim..maxDim
            val compactHeight = blob.height in minDim..maxDim

            // 主扫描：紧凑的符头形连通块。
            if (compactWidth && compactHeight && blob.area >= minArea && blob.aspectRatio in 0.5..2.0) {
                results += Notehead(blob.centerX, blob.centerY, blob.width, blob.height, blob.area)
                continue
            }

            // 二次扫描：宽度合适但偏高（符头+符干融合块），恢复最宽处的符头中心。
            if (image != null && compactWidth &&
                blob.height in (maxDim + 1)..maxStemBlobHeight
            ) {
                recoverNotehead(image, blob, s)?.let { results += it }
                continue
            }

            // 三次扫描：连梁组（多个符头 + 符干 + 横梁融合成一个宽连通块），
            // 按列投影切分为多个独立符头。仅对"过宽"的连通块触发。
            if (image != null && blob.width > maxDim && blob.height in minDim..maxBeamedHeight) {
                results += splitBeamedGroup(image, blob, s)
            }
        }

        return results.sortedBy { it.centerX }
    }

    /**
     * 在一个"符头+符干"融合块中，定位最宽的水平带（符头所在）并以其几何中心
     * 作为符头。符干很细，最宽带必然落在符头上。
     */
    /**
     * 在一个"符头+符干"融合块中，定位最宽的水平带（符头所在）并以其几何中心
     * 作为符头。符干很细，最宽带必然落在符头上。
     *
     * 注意：空心符头(环状)每行的黑像素较少，因此最宽阈值用 0.35 个谱线间距
     * （仍远高于符干的 ~1px 行宽），以同时兼容实心与空心符头。
     */
    private fun recoverNotehead(image: BinaryImage, blob: Blob, s: Double): Notehead? {
        val rowWidth = IntArray(blob.height)
        for (y in blob.minY..blob.maxY) {
            var c = 0
            for (x in blob.minX..blob.maxX) {
                if (image.isBlack(x, y)) c++
            }
            rowWidth[y - blob.minY] = c
        }
        val maxCount = rowWidth.maxOrNull() ?: return null
        val minNoteheadWidth = (0.35 * s).toInt().coerceAtLeast(3)
        if (maxCount < minNoteheadWidth) return null // 最宽处仍太窄，不像符头

        // 符头带 = 宽度 ≥ 50% 最大宽度的连续行。
        val bandThreshold = (maxCount * 0.5).toInt().coerceAtLeast(2)
        var bandStart = -1
        var bandEnd = -1
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= bandThreshold) {
                if (bandStart < 0) bandStart = i
                bandEnd = i
            }
        }
        if (bandStart < 0) return null

        val nhHeight = (bandEnd - bandStart + 1).coerceAtLeast(1)
        val centerY = blob.minY + (bandStart + bandEnd) / 2

        // 取最宽行作为符头水平中心，避免符干把 x 重心带偏。
        var bestRow = bandStart
        for (i in bandStart..bandEnd) {
            if (rowWidth[i] > rowWidth[bestRow]) bestRow = i
        }
        val yBest = blob.minY + bestRow
        var sumX = 0
        var cnt = 0
        for (x in blob.minX..blob.maxX) {
            if (image.isBlack(x, yBest)) {
                sumX += x; cnt++
            }
        }
        if (cnt == 0) return null
        val centerX = sumX / cnt
        val nhWidth = rowWidth[bestRow].coerceAtLeast(1)

        return Notehead(centerX, centerY, nhWidth, nhHeight, nhWidth * nhHeight)
    }

    // ---- 连梁组切分（三次扫描）------------------------------------------------

    /**
     * 将一个"连梁组"宽连通块（多个符头 + 符干 + 共享横梁融合为一体）切分为
     * 多个独立符头。
     *
     * 算法：
     *  1. 逐行统计黑像素宽度 [rowWidth]。
     *  2. 定位横梁带——行宽 ≥ 60% 连通块宽度的行（横梁横跨几乎整个宽度）。
     *     横梁位置决定符头所在的一侧（横梁在上→符头在下；反之亦然）。
     *  3. 在符头侧的行中，找出"密集带"（行宽 ≥ 该侧最大行宽的 40%）作为
     *     符头垂直窗口（可覆盖不同高度的多个符头）。
     *  4. 在该窗口内做列投影 [colCount]：每个符头在水平方向形成一段较宽的
     *     高值区间；符干虽也贡献，但仅 1~2px 宽，会被最小宽度过滤掉。
     *  5. 提取宽度 ≥ 0.4 个谱线间距的连续峰值，每个峰值即一个符头。
     *
     * 全程只读取 [BinaryImage] 像素，不修改连通块拓扑。
     */
    private fun splitBeamedGroup(image: BinaryImage, blob: Blob, s: Double): List<Notehead> {
        val w = blob.width

        // 1) 逐行黑像素宽度。
        val rowWidth = IntArray(blob.height)
        for (y in blob.minY..blob.maxY) {
            var c = 0
            for (x in blob.minX..blob.maxX) if (image.isBlack(x, y)) c++
            rowWidth[y - blob.minY] = c
        }

        // 2) 横梁带：行宽跨越大部分连通块宽度的行。
        val beamThreshold = (0.6 * w).toInt()
        var beamCount = 0
        var beamYSum = 0
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= beamThreshold) {
                beamCount++
                beamYSum += blob.minY + i
            }
        }
        if (beamCount < 2) return emptyList() // 无明显横梁，不是连梁组

        val beamCenterY = beamYSum / beamCount
        val noteheadsAtBottom = beamCenterY < blob.centerY

        // 3) 符头侧密集带（行宽 ≥ 该侧最大行宽 40%）。取所有满足条件的行的
        //    y 范围作为垂直窗口，可同时覆盖多个不同高度的符头。
        var nhSideMax = 0
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= beamThreshold) continue // 跳过横梁行
            val y = blob.minY + i
            val onNoteheadSide =
                (noteheadsAtBottom && y >= beamCenterY) || (!noteheadsAtBottom && y <= beamCenterY)
            if (onNoteheadSide && rowWidth[i] > nhSideMax) nhSideMax = rowWidth[i]
        }
        if (nhSideMax < (0.35 * s).toInt().coerceAtLeast(3)) return emptyList()

        val nhBandThreshold = (nhSideMax * 0.4).toInt().coerceAtLeast(2)
        var wyMin = Int.MAX_VALUE
        var wyMax = Int.MIN_VALUE
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= beamThreshold) continue
            val y = blob.minY + i
            val onNoteheadSide =
                (noteheadsAtBottom && y >= beamCenterY) || (!noteheadsAtBottom && y <= beamCenterY)
            if (onNoteheadSide && rowWidth[i] >= nhBandThreshold) {
                if (y < wyMin) wyMin = y
                if (y > wyMax) wyMax = y
            }
        }
        if (wyMin == Int.MAX_VALUE) return emptyList()

        // 4) 符头窗口内的列投影。
        val colCount = IntArray(w)
        for (x in blob.minX..blob.maxX) {
            var c = 0
            for (y in wyMin..wyMax) if (image.isBlack(x, y)) c++
            colCount[x - blob.minX] = c
        }

        // 5) 提取足够宽的连续峰值 → 符头。
        val peakThreshold = (0.3 * s).toInt().coerceAtLeast(2)
        val noteheads = ArrayList<Notehead>()
        var runStart = -1
        val nhDimMax = (1.5 * s).toInt()
        val nhDimMin = (0.6 * s).toInt().coerceAtLeast(3)
        for (xi in colCount.indices) {
            if (colCount[xi] >= peakThreshold) {
                if (runStart < 0) runStart = xi
            } else {
                if (runStart >= 0) {
                    addSplitNotehead(image, blob, runStart, xi - 1, wyMin, wyMax, s, nhDimMin, nhDimMax)
                        ?.let { noteheads += it }
                    runStart = -1
                }
            }
        }
        if (runStart >= 0) {
            addSplitNotehead(image, blob, runStart, colCount.size - 1, wyMin, wyMax, s, nhDimMin, nhDimMax)
                ?.let { noteheads += it }
        }
        return noteheads
    }

    /** 由一个列投影峰值构造符头：水平中心取峰中点，垂直中心取峰值范围内黑像素质心。 */
    private fun addSplitNotehead(
        image: BinaryImage,
        blob: Blob,
        runStartIdx: Int,
        runEndIdx: Int,
        wyMin: Int,
        wyMax: Int,
        s: Double,
        nhDimMin: Int,
        nhDimMax: Int
    ): Notehead? {
        val x0 = blob.minX + runStartIdx
        val x1 = blob.minX + runEndIdx
        if (x1 - x0 + 1 < nhDimMin) return null // 太窄，可能是符干
        val centerX = (x0 + x1) / 2
        // 垂直质心：峰值 x 范围内的黑像素 y 均值。
        var sy = 0
        var sc = 0
        for (y in wyMin..wyMax) {
            for (x in x0..x1) {
                if (image.isBlack(x, y)) { sy += y; sc++ }
            }
        }
        if (sc == 0) return null
        val centerY = sy / sc
        val width = (x1 - x0 + 1).coerceIn(nhDimMin, nhDimMax)
        val height = (wyMax - wyMin + 1).coerceIn(nhDimMin, nhDimMax)
        return Notehead(centerX, centerY, width, height, width * height)
    }
}
