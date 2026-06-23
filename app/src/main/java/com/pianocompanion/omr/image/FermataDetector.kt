package com.pianocompanion.omr.image

/**
 * 延音记号/停留号(fermata)检测器。
 *
 * Fermata 是乐谱中放在符头上方（正立 `⌒`）或下方（倒立 `⌣`）的半圆形弧线，
 * 弧线中心下方有一个圆点。它指示演奏者在该音符/休止符上停留比记谱时值更长的时间
 * （具体长度由演奏者判断），是重要的表情记号。
 *
 * **检测原理**：
 * 1. 对每个符头，在其上方（正立 fermata）或下方（倒立 fermata）搜索候选墨块
 * 2. 候选墨块必须满足尺寸约束：宽度 0.7~2.5 个谱线间距、高度 0.3~1.5 个间距、
 *    宽高比 ≥ 1.0（弧形比宽于高）
 * 3. **圆顶形状验证**（核心判据）：将墨块宽度分为中心列和两侧边缘列，
 *    分别检查每列的最顶（正立）/最底（倒立）黑像素位置。
 *    - **正立 fermata**：中心列顶部高于两侧边缘顶部（弧形穹顶），
 *      差值 ≥ 墨块高度 × [DOME_THRESHOLD_FRAC]
 *    - **倒立 fermata**：中心列底部低于两侧边缘底部（倒置穹顶），
 *      差值 ≥ 墨块高度 × [DOME_THRESHOLD_FRAC]
 * 4. **水平居中**：墨块中心 X 与符头中心 X 的偏差 ≤ 0.5 个间距
 *
 * **与其他符号的区分**：
 * - 断奏点/保持音/重音：位于符干相反一侧（由 ArticulationDetector 处理），尺寸更小
 * - 连音(slur)/延音线(tie)：跨度远大于 fermata（连接多个音符），且更细
 * - 谱号/调号：位于谱表左侧签名区，不会在符头正上方
 * - 符干：宽度 1-2px，宽高比远小于 1（竖向）
 *
 * Fermata 仅产生提示信息，不修改音符数据模型。
 */
object FermataDetector {

    /**
     * 检测到的延音记号。
     *
     * @param noteIdx 对应的符头在 noteheads 列表中的索引
     * @param centerX 弧线中心 X 坐标
     * @param centerY 弧线中心 Y 坐标
     * @param inverted 是否为倒立 fermata（`⌣`，放在符头下方）
     */
    data class Fermata(
        val noteIdx: Int,
        val centerX: Int,
        val centerY: Int,
        val inverted: Boolean
    )

    /** 候选最小宽度（谱线间距倍数）。 */
    private const val MIN_WIDTH_FRAC = 0.7

    /** 候选最大宽度（谱线间距倍数）。 */
    private const val MAX_WIDTH_FRAC = 2.5

    /** 候选最小高度（谱线间距倍数）。 */
    private const val MIN_HEIGHT_FRAC = 0.3

    /** 候选最大高度（谱线间距倍数）。 */
    private const val MAX_HEIGHT_FRAC = 1.5

    /** 圆顶差值阈值占墨块高度的比例：中心高于边缘的量必须 ≥ 高度 × 此值。 */
    private const val DOME_THRESHOLD_FRAC = 0.20

    /** 符头中心与墨块中心 X 偏差最大值（谱线间距倍数）。 */
    private const val CENTER_X_TOLERANCE_FRAC = 0.5

    /** 搜索区域起始间隙（谱线间距倍数）：从符头边缘向外偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（谱线间距倍数）。 */
    private const val SEARCH_RANGE_FRAC = 3.5

    /**
     * 检测每个符头上方/下方的 fermata。
     *
     * @param image 去谱线+降噪后的二值图像
     * @param blobs 连通块列表（与 image 一致的坐标系）
     * @param noteheads 符头列表
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距
     * @return 检测到的 fermata 列表（按符头索引排序）
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Fermata> {
        if (lineSpacing < 1 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<Fermata>()

        val minWidth = (MIN_WIDTH_FRAC * s).toInt().coerceAtLeast(2)
        val maxWidth = (MAX_WIDTH_FRAC * s).toInt()
        val minHeight = (MIN_HEIGHT_FRAC * s).toInt().coerceAtLeast(2)
        val maxHeight = (MAX_HEIGHT_FRAC * s).toInt()
        val centerXTol = (CENTER_X_TOLERANCE_FRAC * s).toInt().coerceAtLeast(2)
        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()
        val domeThreshold = (DOME_THRESHOLD_FRAC * s)

        // 为每个系统预计算搜索区域的边界
        noteheads.forEachIndexed { idx, nh ->
            val sysIdx = systemIndices.getOrElse(idx) { -1 }
            if (sysIdx < 0 || sysIdx >= systems.size) return@forEachIndexed

            val system = systems[sysIdx]
            val topLineY = system.topLine.center
            val bottomLineY = system.bottomLine.center

            // --- 正立 fermata 搜索（符头上方） ---
            // 搜索区域：从符头顶部（或谱表顶线，取更高者）向上搜索
            val nhTopEdge = nh.centerY - nh.height / 2
            val aboveBottom = minOf(nhTopEdge, topLineY) - searchGap
            val aboveTop = (aboveBottom - searchRange).coerceAtLeast(0)
            if (aboveBottom > aboveTop) {
                val match = findFermataBlob(
                    image, blobs, nh.centerX,
                    aboveTop, aboveBottom,
                    minWidth, maxWidth, minHeight, maxHeight,
                    centerXTol, domeThreshold,
                    inverted = false
                )
                if (match != null) {
                    results += Fermata(idx, match.centerX, match.centerY, inverted = false)
                    return@forEachIndexed  // 每个符头最多一个 fermata
                }
            }

            // --- 倒立 fermata 搜索（符头下方，但仍在谱表内或下方） ---
            // 倒立 fermata 较少见，主要出现在声乐谱或特定乐器谱中
            val nhBotEdge = nh.centerY + nh.height / 2
            val belowTop = maxOf(nhBotEdge, bottomLineY) + searchGap
            val belowBottom = (belowTop + searchRange).coerceAtMost(image.height - 1)
            if (belowBottom > belowTop) {
                val match = findFermataBlob(
                    image, blobs, nh.centerX,
                    belowTop, belowBottom,
                    minWidth, maxWidth, minHeight, maxHeight,
                    centerXTol, domeThreshold,
                    inverted = true
                )
                if (match != null) {
                    results += Fermata(idx, match.centerX, match.centerY, inverted = true)
                }
            }
        }

        return results
    }

    /**
     * 在指定区域内搜索符合 fermata 形状的墨块。
     *
     * @param inverted false=正立（弧顶朝上），true=倒立（弧顶朝下）
     * @return 匹配的 Blob，或 null
     */
    private fun findFermataBlob(
        image: BinaryImage,
        blobs: List<Blob>,
        targetCenterX: Int,
        yStart: Int,
        yEnd: Int,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int,
        centerXTol: Int,
        domeThreshold: Double,
        inverted: Boolean
    ): Blob? {
        // 按面积降序排序候选（大的优先，通常 fermata 弧比小杂点大）
        val candidates = blobs.filter { blob ->
            blob.centerY in yStart..yEnd &&
                blob.minY >= yStart - maxHeight / 2 &&
                blob.maxY <= yEnd + maxHeight / 2 &&
                blob.width in minWidth..maxWidth &&
                blob.height in minHeight..maxHeight &&
                blob.width >= blob.height &&  // 宽于高（弧形特征）
                kotlin.math.abs(blob.centerX - targetCenterX) <= centerXTol
        }.sortedByDescending { it.area }

        for (blob in candidates) {
            if (isFermataShape(image, blob, domeThreshold, inverted)) {
                return blob
            }
        }
        return null
    }

    /**
     * 验证墨块是否具有 fermata 弧形特征。
     *
     * 正立 fermata `⌒`：中心列的顶部（最顶黑像素）应高于两侧边缘列的顶部，
     * 形成穹顶形状。差值 ≥ domeThreshold。
     *
     * 倒立 fermata `⌣`：中心列的底部（最底黑像素）应低于两侧边缘列的底部，
     * 形成倒置穹顶形状。
     */
    private fun isFermataShape(
        image: BinaryImage,
        blob: Blob,
        domeThreshold: Double,
        inverted: Boolean
    ): Boolean {
        val bw = blob.width

        // 将宽度分为三段：左1/4、中心1/2、右1/4
        // 使用外侧 1/4 而非极端边缘列，避免单像素噪声
        val leftStart = blob.minX
        val leftEnd = blob.minX + bw / 4
        val centerStart = blob.minX + bw / 4
        val centerEnd = blob.maxX - bw / 4
        val rightStart = blob.maxX - bw / 4 + 1
        val rightEnd = blob.maxX

        if (!inverted) {
            // 正立 fermata：检查顶部穹顶
            val leftTop = topmostBlackY(image, leftStart, leftEnd, blob.minY, blob.maxY)
            val centerTop = topmostBlackY(image, centerStart, centerEnd, blob.minY, blob.maxY)
            val rightTop = topmostBlackY(image, rightStart, rightEnd, blob.minY, blob.maxY)

            // 三段都必须有有效墨迹
            if (leftTop == Int.MAX_VALUE || centerTop == Int.MAX_VALUE || rightTop == Int.MAX_VALUE) {
                return false
            }

            val edgeAvg = (leftTop + rightTop) / 2.0
            // 中心顶部应高于边缘顶部（Y 值更小）
            return (edgeAvg - centerTop) >= domeThreshold
        } else {
            // 倒立 fermata：检查底部倒置穹顶
            val leftBot = bottommostBlackY(image, leftStart, leftEnd, blob.minY, blob.maxY)
            val centerBot = bottommostBlackY(image, centerStart, centerEnd, blob.minY, blob.maxY)
            val rightBot = bottommostBlackY(image, rightStart, rightEnd, blob.minY, blob.maxY)

            if (leftBot == Int.MIN_VALUE || centerBot == Int.MIN_VALUE || rightBot == Int.MIN_VALUE) {
                return false
            }

            val edgeAvg = (leftBot + rightBot) / 2.0
            // 中心底部应低于边缘底部（Y 值更大）
            return (centerBot - edgeAvg) >= domeThreshold
        }
    }

    /**
     * 在 [xStart..xEnd] × [yMin..yMax] 范围内找到最顶（最小 Y）的黑像素 Y 坐标。
     * 无黑像素时返回 [Int.MAX_VALUE]。
     */
    private fun topmostBlackY(
        image: BinaryImage,
        xStart: Int,
        xEnd: Int,
        yMin: Int,
        yMax: Int
    ): Int {
        var topY = Int.MAX_VALUE
        for (y in yMin..yMax) {
            for (x in xStart..xEnd) {
                if (image.isBlack(x, y)) {
                    if (y < topY) topY = y
                    return topY  // 从上往下扫描，找到第一个就返回
                }
            }
        }
        return topY
    }

    /**
     * 在 [xStart..xEnd] × [yMin..yMax] 范围内找到最底（最大 Y）的黑像素 Y 坐标。
     * 无黑像素时返回 [Int.MIN_VALUE]。
     */
    private fun bottommostBlackY(
        image: BinaryImage,
        xStart: Int,
        xEnd: Int,
        yMin: Int,
        yMax: Int
    ): Int {
        var botY = Int.MIN_VALUE
        for (y in yMax downTo yMin) {
            for (x in xStart..xEnd) {
                if (image.isBlack(x, y)) {
                    if (y > botY) botY = y
                    return botY  // 从下往上扫描，找到第一个就返回
                }
            }
        }
        return botY
    }
}
