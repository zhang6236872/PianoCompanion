package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 装饰音(ornament)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 装饰音(ornament)是古典音乐中常见的即兴加花标记，放在符头上方，指示演奏者在
 * 主音周围快速弹奏一组装饰音符。本检测器处理两类最经典的装饰音：
 *
 * - **波音(mordent)**：标记为一个短小的 **zigzag / 波浪线**（类似 `~`），指示在主音
 *   与上方二度音之间做一次快速的上下交替。
 *   - **顺波音(upper mordent / pralltriller)**：纯粹的 zigzag，无穿越线。
 *   - **逆波音(lower mordent)**：zigzag 中间有一条竖直的穿越线(slash)，指示在主音
 *     与**下方**二度音之间交替。
 *
 * - **回音(turn / gruppetto)**：标记为一个横躺的 **S 形曲线**（`∽`），指示以
 *   上方音 → 主音 → 下方音 → 主音 的顺序快速演奏四个音。
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：对每个符头，在其所属谱表系统的**顶线上方** 0.5~4.0 个谱线间距
 *    的区域内搜索候选墨块（装饰音写在谱表上方）。
 * 2. **尺寸约束**：候选墨块宽度 0.8~3.0 间距、高度 0.3~1.5 间距（装饰音是小型
 *    紧凑符号），且 X 中心与符头中心偏差 ≤ 1.0 间距。
 * 3. **形状分析（核心判据）**：对候选墨块逐列计算垂直中心（topmost 与 bottommost
 *    黑像素 Y 坐标的中点），构成"中心轨迹"：
 *    - **zigzag 判定**：中心轨迹的方向反转次数 ≥ 2 → 波音(mordent)。zigzag 的
 *      "上下交替"模式是装饰音独有的——弧形(fermata)、字母(trill)、点(staccato)
 *      都不会有多次快速反转。
 *    - **S 曲线判定**：方向反转 ≤ 1，且左 1/3 与右 1/3 的平均垂直中心差异 ≥ 阈值
 *      → 回音(turn)。S 曲线从一侧过渡到另一侧（非对称），而 fermata 穹顶左右对称。
 * 4. **波音细分**：在 zigzag 的中心 1/3 区域检测是否存在显著高于周围的竖直穿越列
 *    (slash) → 逆波音(lower mordent)；否则为顺波音(upper mordent)。
 *
 * ## 与其他符号的区分
 *
 * - **延音记号(fermata)**：穹顶形状（中心高于两侧，左右对称），中心轨迹有 1 次反转
 *   但左右对称，不满足 S 曲线的非对称要求，也不满足 zigzag 的 ≥2 次反转。
 * - **颤音(trill)**："tr" 是字母文字，其形状不会产生 zigzag 中心轨迹。
 * - **断奏点(staccato)**：极小（≤0.3 间距），不满足尺寸约束。
 * - **指法数字(fingering)**：紧凑数字字形，中心轨迹至多 1 次反转，无 zigzag。
 * - **连音(slur) / 延音线(tie)**：远比装饰音宽（连接多个音符），不满足宽度约束。
 * - **符干**：细长竖线，宽高比远小于 1。
 *
 * 装饰音仅产生提示信息，不修改音符数据模型（与 fermata、trill 等一致）。
 */
object OrnamentDetector {

    /**
     * 装饰音类型。
     */
    enum class OrnamentType {
        /** 顺波音(upper mordent / pralltriller)：纯 zigzag，无穿越线。 */
        MORDENT_UPPER,

        /** 逆波音(lower mordent)：zigzag + 中央穿越线(slash)。 */
        MORDENT_LOWER,

        /** 回音(turn / gruppetto)：横躺 S 形曲线。 */
        TURN
    }

    /**
     * 检测到的装饰音标记。
     *
     * @param noteIdx   对应的符头在 noteheads 列表中的索引。
     * @param centerX   装饰音符号中心的 X 坐标。
     * @param centerY   装饰音符号中心的 Y 坐标。
     * @param systemIdx 所属谱表系统索引。
     * @param type      装饰音类型。
     */
    data class Ornament(
        val noteIdx: Int,
        val centerX: Int,
        val centerY: Int,
        val systemIdx: Int,
        val type: OrnamentType
    )

    // ---- 尺寸约束（谱线间距倍数） -------------------------------------------

    /** 搜索区域起始间隙：从谱表顶线向上偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向上搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 4.0

    /** 候选墨块最小宽度（谱线间距倍数）。 */
    private const val MIN_WIDTH_FRAC = 0.8

    /** 候选墨块最大宽度（谱线间距倍数）。 */
    private const val MAX_WIDTH_FRAC = 3.0

    /** 候选墨块最小高度（谱线间距倍数）。 */
    private const val MIN_HEIGHT_FRAC = 0.3

    /** 候选墨块最大高度（谱线间距倍数）。 */
    private const val MAX_HEIGHT_FRAC = 1.5

    /** 符头中心与装饰音中心 X 偏差最大值（谱线间距倍数）。 */
    private const val CENTER_X_TOLERANCE_FRAC = 1.0

    /** zigzag 判定的最少方向反转次数。 */
    private const val MIN_ZIGZAG_REVERSALS = 2

    /** S 曲线判定：左右 1/3 平均中心差异占墨块高度的最小比例。 */
    private const val TURN_ASYMMETRY_FRAC = 0.30

    /** 穹顶排除：左右 1/3 平均中心差异占墨块高度的最大比例（超过此值才不是穹顶）。 */
    private const val DOME_MAX_SYMMETRY_FRAC = 0.20

    /** 波音穿越线(slash)检测：中心列高度需达到中位列高度的多少倍。 */
    private const val SLASH_HEIGHT_RATIO = 1.6

    /** 波音穿越线(slash)检测：穿越线高度至少占墨块高度的比例。 */
    private const val SLASH_MIN_HEIGHT_FRAC = 0.60

    /** 方向变化的最小有意义差值（像素），低于此值视为平坦。 */
    private const val MIN_DIRECTION_DELTA = 0.5

    /**
     * 检测每个符头上方的装饰音标记（波音/回音）。
     *
     * @param image         去谱线+降噪后的二值图像。
     * @param blobs         连通块列表（与 image 一致的坐标系）。
     * @param noteheads     符头列表。
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）。
     * @param systems       谱表系统列表。
     * @param lineSpacing   平均谱线间距。
     * @return 检测到的装饰音列表（按符头索引排序）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Ornament> {
        if (lineSpacing < 1 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<Ornament>()

        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()
        val minWidth = (MIN_WIDTH_FRAC * s).toInt().coerceAtLeast(3)
        val maxWidth = (MAX_WIDTH_FRAC * s).toInt()
        val minHeight = (MIN_HEIGHT_FRAC * s).toInt().coerceAtLeast(2)
        val maxHeight = (MAX_HEIGHT_FRAC * s).toInt()
        val centerXTol = (CENTER_X_TOLERANCE_FRAC * s).toInt().coerceAtLeast(4)

        noteheads.forEachIndexed { idx, nh ->
            val sysIdx = systemIndices.getOrElse(idx) { -1 }
            if (sysIdx < 0 || sysIdx >= systems.size) return@forEachIndexed

            val system = systems[sysIdx]
            val topLineY = system.topLine.center

            // 搜索区域：从谱表顶线向上搜索。
            val searchBottom = topLineY - searchGap
            val searchTop = (searchBottom - searchRange).coerceAtLeast(0)
            if (searchBottom <= searchTop) return@forEachIndexed

            // 过滤搜索区域内的候选墨块：在 Y 范围内、且 X 接近符头中心。
            val candidates = blobs.filter { blob ->
                blob.centerY in searchTop..searchBottom &&
                    blob.minY >= searchTop - maxHeight / 2 &&
                    blob.maxY <= searchBottom + maxHeight / 2 &&
                    blob.width in minWidth..maxWidth &&
                    blob.height in minHeight..maxHeight &&
                    abs(blob.centerX - nh.centerX) <= centerXTol
            }.sortedByDescending { it.area } // 大的优先

            for (blob in candidates) {
                val type = classifyOrnament(image, blob)
                if (type != null) {
                    results += Ornament(
                        noteIdx = idx,
                        centerX = blob.centerX,
                        centerY = blob.centerY,
                        systemIdx = sysIdx,
                        type = type
                    )
                    break // 每个符头最多一个装饰音标记
                }
            }
        }

        return results
    }

    /**
     * 对单个候选墨块进行形状分析，判定是否为装饰音及其类型。
     *
     * @return 装饰音类型，或 null（不是装饰音）。
     */
    private fun classifyOrnament(image: BinaryImage, blob: Blob): OrnamentType? {
        val bw = blob.width
        val bh = blob.height
        if (bw < 3 || bh < 2) return null

        // 逐列计算垂直中心（topmost 与 bottommost 黑像素 Y 的中点）。
        val centers = ArrayList<Double>(bw)
        val columnHeights = ArrayList<Int>(bw)
        for (x in blob.minX..blob.maxX) {
            var topY = Int.MAX_VALUE
            var botY = Int.MIN_VALUE
            for (y in blob.minY..blob.maxY) {
                if (image.isBlack(x, y)) {
                    if (y < topY) topY = y
                    if (y > botY) botY = y
                }
            }
            if (topY == Int.MAX_VALUE) {
                // 该列无黑像素——用相邻列插值
                centers.add(Double.NaN)
                columnHeights.add(0)
            } else {
                centers.add((topY + botY) / 2.0)
                columnHeights.add(botY - topY + 1)
            }
        }

        // 插值填补 NaN 列（简单线性插值）。
        interpolateNaN(centers)

        // 平滑中心轨迹（移动平均，窗口 3），减少单像素噪声引起的伪反转。
        val smoothed = smoothCenters(centers)

        // 计算方向反转次数。
        val reversals = countReversals(smoothed)

        // 计算 zigzag 的振幅（最大与最小中心之差）。
        val validCenters = smoothed.filter { !it.isNaN() }
        if (validCenters.isEmpty()) return null
        val centerMin = validCenters.min()
        val centerMax = validCenters.max()
        val amplitude = centerMax - centerMin

        // 振幅太小（<1px）说明墨块几乎是水平的线，不是装饰音。
        if (amplitude < 1.0) return null

        // --- zigzag 判定：方向反转 ≥ 2 → 波音(mordent) ---
        if (reversals >= MIN_ZIGZAG_REVERSALS) {
            return if (hasVerticalSlash(image, blob, columnHeights)) {
                OrnamentType.MORDENT_LOWER
            } else {
                OrnamentType.MORDENT_UPPER
            }
        }

        // --- S 曲线判定：方向反转 ≤ 1 且左右非对称 → 回音(turn) ---
        if (reversals <= 1) {
            val turnType = classifyTurn(smoothed, bw, bh)
            if (turnType != null) return turnType
        }

        return null
    }

    /**
     * 简单线性插值填补 NaN 中心值（无黑像素的列）。
     * 端点处的 NaN 用最近的非 NaN 值填充。
     */
    private fun interpolateNaN(centers: ArrayList<Double>) {
        if (centers.isEmpty()) return
        // 前导 NaN
        var firstValid = -1
        for (i in centers.indices) {
            if (!centers[i].isNaN()) { firstValid = i; break }
        }
        if (firstValid < 0) return // 全 NaN
        for (i in 0 until firstValid) centers[i] = centers[firstValid]
        // 中间 NaN
        var i = firstValid + 1
        while (i < centers.size) {
            if (centers[i].isNaN()) {
                var j = i
                while (j < centers.size && centers[j].isNaN()) j++
                val leftVal = centers[i - 1]
                val rightVal = if (j < centers.size) centers[j] else leftVal
                for (k in i until j) {
                    val t = (k - i + 1).toDouble() / (j - i + 1)
                    centers[k] = leftVal + (rightVal - leftVal) * t
                }
                i = j
            } else {
                i++
            }
        }
        // 尾随 NaN
        val lastIdx = centers.size - 1
        if (centers[lastIdx].isNaN()) {
            var lastValid = lastIdx
            while (lastValid >= 0 && centers[lastValid].isNaN()) lastValid--
            for (k in (lastValid + 1)..lastIdx) centers[k] = centers[lastValid]
        }
    }

    /**
     * 对中心轨迹做窗口为 3 的移动平均平滑。
     */
    private fun smoothCenters(centers: List<Double>): List<Double> {
        if (centers.size < 3) return centers
        val out = ArrayList<Double>(centers.size)
        for (i in centers.indices) {
            val lo = max(0, i - 1)
            val hi = min(centers.size - 1, i + 1)
            var sum = 0.0
            var count = 0
            for (j in lo..hi) {
                if (!centers[j].isNaN()) { sum += centers[j]; count++ }
            }
            out.add(if (count > 0) sum / count else centers[i])
        }
        return out
    }

    /**
     * 统计中心轨迹的方向反转次数。
     *
     * 反转 = 相邻段方向从上升变为下降（或反之），跳过平坦段。
     */
    private fun countReversals(smoothed: List<Double>): Int {
        if (smoothed.size < 3) return 0
        var reversals = 0
        var prevDir = 0 // -1=下降, 0=平坦, 1=上升
        for (i in 1 until smoothed.size) {
            val diff = smoothed[i] - smoothed[i - 1]
            val dir = when {
                diff > MIN_DIRECTION_DELTA -> 1
                diff < -MIN_DIRECTION_DELTA -> -1
                else -> 0
            }
            if (dir != 0 && prevDir != 0 && dir != prevDir) {
                reversals++
            }
            if (dir != 0) prevDir = dir
        }
        return reversals
    }

    /**
     * 检测波音 zigzag 中是否有竖直穿越线(slash)，用于区分顺波音与逆波音。
     *
     * 逆波音的穿越线是一条竖直笔画，使中心区域的某列显著高于周围列。
     *
     * 判据：在中心 1/3 区域内，存在某列高度 ≥ 中位列高度的 [SLASH_HEIGHT_RATIO] 倍，
     * 且该列高度 ≥ 墨块高度的 [SLASH_MIN_HEIGHT_FRAC]。
     */
    private fun hasVerticalSlash(
        image: BinaryImage,
        blob: Blob,
        columnHeights: List<Int>
    ): Boolean {
        val bw = blob.width
        val bh = blob.height
        if (bw < 4 || columnHeights.isEmpty()) return false

        val centerStart = bw / 3
        val centerEnd = bw * 2 / 3
        if (centerEnd <= centerStart) return false

        // 全列中位高度
        val sorted = columnHeights.filter { it > 0 }.sorted()
        if (sorted.isEmpty()) return false
        val medianHeight = sorted[sorted.size / 2]

        // 中心区域最高列
        var centerMaxHeight = 0
        for (i in centerStart..centerEnd) {
            if (i < columnHeights.size && columnHeights[i] > centerMaxHeight) {
                centerMaxHeight = columnHeights[i]
            }
        }

        if (medianHeight <= 0) return false
        val slashHeightThreshold = (medianHeight * SLASH_HEIGHT_RATIO).toInt()

        return centerMaxHeight >= slashHeightThreshold &&
            centerMaxHeight >= (bh * SLASH_MIN_HEIGHT_FRAC).toInt()
    }

    /**
     * 判定 S 曲线是否构成回音(turn)。
     *
     * 回音 ∽ 的中心轨迹从一侧平滑过渡到另一侧（左右非对称）。
     * 穹顶(fermata)的左右对称，不会通过此测试。
     *
     * @param smoothed 平滑后的中心轨迹。
     * @param bw       墨块宽度（列数）。
     * @param bh       墨块高度。
     * @return OrnamentType.TURN 或 null。
     */
    private fun classifyTurn(smoothed: List<Double>, bw: Int, bh: Int): OrnamentType? {
        if (smoothed.size < 4 || bh < 2) return null

        val third = max(1, bw / 3)
        val leftEnd = min(third, smoothed.size)
        val rightStart = max(0, smoothed.size - third)
        if (rightStart <= leftEnd) return null

        val leftMean = smoothed.subList(0, leftEnd).filter { !it.isNaN() }.let {
            if (it.isEmpty()) return null else it.average()
        }
        val rightMean = smoothed.subList(rightStart, smoothed.size).filter { !it.isNaN() }.let {
            if (it.isEmpty()) return null else it.average()
        }

        val leftRightDiff = abs(leftMean - rightMean)
        // 左右差异需足够显著（非对称 S 曲线的特征）
        if (leftRightDiff < bh * TURN_ASYMMETRY_FRAC) return null

        // 额外检查：S 曲线过渡应较平滑（整体振幅不超过墨块高度）
        // 已通过尺寸约束保证，此处不额外限制

        return OrnamentType.TURN
    }
}
