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

            // 二次扫描：宽度合适但偏高（符头+符干融合块）。
            //
            // 注意（v2.38.0 修复）：**拥挤连梁组**——两个（或更多）符头 + 向上/向下
            // 符干 + 共享横梁——在符头中心相距较近（≤ ~1.5 个谱线间距）时，整块宽度
            // 可能 ≤ maxDim，外观与「符头+符干」融合块几乎相同。若直接交给
            // recoverNotehead，会只恢复出**一个**符头（取最宽水平带的质心），导致
            // 第二个符头丢失。
            //
            // 因此先尝试连梁组切分：splitBeamedGroup 内部会验证横梁是否存在（横梁
            // 行数 ≥ 2 才接受），无横梁时返回空列表，此时安全回退到 recoverNotehead
            // 恢复单个符头+符干。这样既不改变非连梁块的既有行为，又能正确切分拥挤连梁组。
            if (image != null && compactWidth &&
                blob.height in (maxDim + 1)..maxStemBlobHeight
            ) {
                val beamed = splitBeamedGroup(image, blob, s)
                if (beamed.isNotEmpty()) {
                    results += beamed
                } else {
                    recoverNotehead(image, blob, s)?.let { results += it }
                }
                continue
            }

            // 三次扫描：连梁组（多个符头 + 符干 + 横梁融合成一个宽连通块），
            // 按列投影切分为多个独立符头。对"过宽"（> maxDim）的连通块触发；
            // 充分间隔的连梁组走此路径。
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

        // 守卫：如果"符头带"高度占连通块总高的绝大部分，说明该连通块没有
        // 明显的"宽符头 + 细符干"结构——例如四分休止符的锯齿形（整条都是
        // 均匀窄带），不应被误判为符头+符干融合块。
        if (nhHeight > blob.height * 0.6) return null

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

        // 0) 最小高度护栏：连梁组至少需要「横梁(≥2行) + 符干区(≥~0.5s) + 符头(≥~0.5s)」，
        //    总高约 ≥ 1.2s。低于此的宽而薄的连通块（如被去谱线切断的连音弧残片、
        //    水平线段）不可能是连梁组，直接返回空，避免从中误产出符头。
        if (blob.height < (1.2 * s).toInt()) return emptyList()

        // 1) 逐行黑像素宽度。
        val rowWidth = IntArray(blob.height)
        for (y in blob.minY..blob.maxY) {
            var c = 0
            for (x in blob.minX..blob.maxX) if (image.isBlack(x, y)) c++
            rowWidth[y - blob.minY] = c
        }

        // 2) 横梁带检测：找出"宽行"（行黑像素宽度 ≥ 连通块宽度的 30%）。
        //
        //    关键（v2.38.0 修复）：**拥挤连梁组**中，多个并排符头所在行的总宽度
        //    也可能很宽，与真正的横梁行难以区分。此前将所有宽行一视同仁地求平均，
        //    会把横梁中心错置于横梁与符头带的正中间，进而误判符头所在侧。
        //
        //    修复要点一：按**连续性**将宽行划分为若干区间；横梁是位于连通块某一端
        //    （顶部或底部）的薄连续带，符头带则在对侧。取距最近边缘最近的宽行区间
        //    作为横梁。双层/三层横梁的各层间隙 < 0.5s，先合并为同一横梁簇。
        //
        //    修复要点二：阈值取连通块宽度的 30%（而非此前的 60%）。拥挤连梁组的
        //    横梁（连接各符干顶端）宽度约为整块的 43%–72%，30% 可可靠覆盖；同时
        //    能排除宽而薄的弧线/连线（如连音弧，行宽仅占整块的个位数百分比），
        //    避免将其误判为横梁。
        val beamThreshold = (0.3 * w).toInt()
        val wideRuns = ArrayList<IntArray>() // 每项 = [startIdx, endIdx]（相对 blob.minY）
        var rs = -1
        for (i in rowWidth.indices) {
            if (rowWidth[i] >= beamThreshold) {
                if (rs < 0) rs = i
            } else if (rs >= 0) {
                wideRuns.add(intArrayOf(rs, i - 1))
                rs = -1
            }
        }
        if (rs >= 0) wideRuns.add(intArrayOf(rs, rowWidth.size - 1))

        // 合并间隙过小的相邻宽行区间：双层/三层横梁的各层之间仅有 1–3 行窄缝，
        // 应视为同一个横梁簇。合并阈值取 0.5 个谱线间距；符头带与横梁簇之间通常有
        // 较长的符干区（远大于此阈值），不会被误合并。
        if (wideRuns.size > 1) {
            val mergeGap = (0.5 * s).toInt().coerceAtLeast(2)
            val merged = ArrayList<IntArray>()
            for (run in wideRuns) {
                if (merged.isNotEmpty()) {
                    val last = merged.last()
                    if (run[0] - last[1] - 1 < mergeGap) {
                        last[1] = run[1] // 扩展上一区间
                        continue
                    }
                }
                merged.add(intArrayOf(run[0], run[1]))
            }
            wideRuns.clear()
            wideRuns.addAll(merged)
        }

        // 横梁簇必须是 ≥ 2 行的连续宽行区间。
        val beamCandidates = wideRuns.filter { it[1] - it[0] + 1 >= 2 }
        if (beamCandidates.isEmpty()) return emptyList() // 无明显横梁，不是连梁组

        // 横梁位于某一端：取距最近边缘最近的宽行区间。
        val topRun = beamCandidates.minByOrNull { it[0] }!!
        val bottomRun = beamCandidates.maxByOrNull { it[1] }!!
        val distTop = topRun[0]
        val distBottom = (rowWidth.size - 1) - bottomRun[1]
        val beamRun = if (distTop <= distBottom) topRun else bottomRun

        val beamStartIdx = beamRun[0]
        val beamEndIdx = beamRun[1]
        val beamCenterIdx = (beamStartIdx + beamEndIdx) / 2
        val beamCenterY = blob.minY + beamCenterIdx
        val noteheadsAtBottom = beamCenterIdx < blob.height / 2 // 横梁在上半部 → 符头在下

        // 3) 符头侧密集带（行宽 ≥ 该侧最大行宽 40%）。取所有满足条件的行的
        //    y 范围作为垂直窗口，可同时覆盖多个不同高度的符头。
        //    注意：仅排除横梁区间本身（beamStartIdx..beamEndIdx），而**不**排除所有
        //    宽行——拥挤连梁组的符头行也可能较宽，必须保留在窗口内。
        var nhSideMax = 0
        for (i in rowWidth.indices) {
            if (i in beamStartIdx..beamEndIdx) continue // 跳过横梁行
            val onNoteheadSide =
                (noteheadsAtBottom && i >= beamCenterIdx) || (!noteheadsAtBottom && i <= beamCenterIdx)
            if (onNoteheadSide && rowWidth[i] > nhSideMax) nhSideMax = rowWidth[i]
        }
        if (nhSideMax < (0.35 * s).toInt().coerceAtLeast(3)) return emptyList()

        val nhBandThreshold = (nhSideMax * 0.4).toInt().coerceAtLeast(2)
        var wyMin = Int.MAX_VALUE
        var wyMax = Int.MIN_VALUE
        for (i in rowWidth.indices) {
            if (i in beamStartIdx..beamEndIdx) continue
            val y = blob.minY + i
            val onNoteheadSide =
                (noteheadsAtBottom && i >= beamCenterIdx) || (!noteheadsAtBottom && i <= beamCenterIdx)
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
        val nhDimMax = (1.5 * s).toInt()
        val nhDimMin = (0.6 * s).toInt().coerceAtLeast(3)

        // 先收集所有"原始峰值段"（列投影值 ≥ peakThreshold 的连续列区间）。
        val rawSegments = ArrayList<IntArray>() // 每项 = [startIdx, endIdx]
        var runStart = -1
        for (xi in colCount.indices) {
            if (colCount[xi] >= peakThreshold) {
                if (runStart < 0) runStart = xi
            } else if (runStart >= 0) {
                rawSegments.add(intArrayOf(runStart, xi - 1))
                runStart = -1
            }
        }
        if (runStart >= 0) rawSegments.add(intArrayOf(runStart, colCount.size - 1))

        // 对过宽的峰值段，在局部极小值（山谷）处进一步切分。
        // 拥挤/重叠的符头在列投影上可能不产生清零间隙（投影值始终 ≥ 阈值），
        // 但会在两符头交界处形成相对凹陷。递归二分：找最深山谷，若其深度相对相邻
        // 峰高足够（≤ 65%），则在此切分，直至每段不超过单个符头宽度。
        val noteheads = ArrayList<Notehead>()
        for (seg in rawSegments) {
            for (sub in splitRunAtValleys(colCount, seg[0], seg[1], s)) {
                addSplitNotehead(image, blob, sub[0], sub[1], wyMin, wyMax, s, nhDimMin, nhDimMax)
                    ?.let { noteheads += it }
            }
        }
        return noteheads
    }

    /**
     * 递归地在列投影峰值段的局部极小值（山谷）处切分。
     * Recursively split a wide column-projection run at its deepest local minimum.
     *
     * 当两个拥挤/重叠符头之间的列投影不产生清零间隙时，它们会合并成一个过宽的
     * 峰值段。此方法在段内寻找最深的局部极小值：若其值 ≤ 较小相邻峰高的 65%，
     * 则在该处切分；递归直至每段宽度不超过 ~1.3 个谱线间距（单个符头宽度）。
     * 太窄的子段（被过度切分）会在 [addSplitNotehead] 中因 nhDimMin 被丢弃。
     */
    private fun splitRunAtValleys(
        colCount: IntArray,
        a: Int,
        b: Int,
        s: Double
    ): List<IntArray> {
        val maxSingle = (1.3 * s).toInt() // 单个符头峰值段的最大宽度
        if (b - a + 1 <= maxSingle) return listOf(intArrayOf(a, b))

        // 在 (a, b) 内寻找最深的局部极小值（至少一侧邻居不低于它）。
        var bestValley = -1
        var bestVal = Int.MAX_VALUE
        for (xi in (a + 1) until b) {
            val isLocalMin = colCount[xi] <= colCount[xi - 1] || colCount[xi] <= colCount[xi + 1]
            if (!isLocalMin) continue
            if (colCount[xi] < bestVal) {
                bestVal = colCount[xi]
                bestValley = xi
            }
        }
        if (bestValley < 0) return listOf(intArrayOf(a, b))

        // 山谷深度判据：≤ 较小相邻峰高的 65% 才切分。
        val leftMax = (a..bestValley).maxOf { colCount[it] }
        val rightMax = (bestValley..b).maxOf { colCount[it] }
        val peakMin = minOf(leftMax, rightMax)
        if (peakMin > 0 && colCount[bestValley] <= peakMin * 0.65) {
            val out = ArrayList<IntArray>()
            out += splitRunAtValleys(colCount, a, bestValley - 1, s)
            out += splitRunAtValleys(colCount, bestValley + 1, b, s)
            return out
        }
        return listOf(intArrayOf(a, b))
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
