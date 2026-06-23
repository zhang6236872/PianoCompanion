package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 装饰音(grace note)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 装饰音是出现在主音符**正前方**的小音符，在钢琴及古典音乐中极为常见。分为两种：
 *
 * - **短前倚音(acciaccatura)**：符干上有一条斜线(斜杠)，演奏时极短促、几乎"偷取"
 *   主音符的时间。记谱上符头较小且符干有斜杠。
 * - **长前倚音(appoggiatura)**：无斜线，占用主音符一半（有时全部）的时值。
 *   记谱上符头同样较小但符干无斜杠。
 *
 * ## 检测原理
 *
 * 装饰音的符头显著小于普通音符的符头（通常为普通符头的 50–70%），且紧邻一个
 * 普通音符的左侧。检测器通过以下特征识别装饰音：
 *
 * 1. **相对尺寸**：符头面积显著小于同系统内最大符头面积（面积 < 55% 最大值）。
 *    使用最大值而非中位数是因为装饰音数量通常远少于普通音符，中位数可能被
 *    装饰音拉低；最大值一定是普通音符的大小，作为参考更鲁棒。
 * 2. **邻近性**：紧邻一个普通音符（面积 ≥ 85% 中位数）的右侧，水平间距在
 *    [0.2s, 2.0s] 范围内（s = 谱线间距）。装饰音绝不会孤立存在——它总是依附于
 *    一个主音符。
 * 3. **竖直位置**：与主音符的 Y 差异 ≤ 2.5s（装饰音通常在主音符附近，可能略高
 *    或略低）。
 * 4. **斜线检测**：在装饰音符干区域检测对角线笔画。符干是竖直线（水平跨度
 *    ≈1–2px），斜杠穿过符干时某些行的水平跨度会增大（≥4px）。同时存在"窄行"
 *    （裸符干）和"宽行"（斜杠穿过）即判定为斜线 → 短前倚音(acciaccatura)。
 *
 * ## 与其他小符头的区分
 *
 * - **和弦成员**：和弦成员在同一 X 列（dx ≈ 0），不满足 dx > minGap 条件。
 * - **八分/十六分音符**：它们的符头大小与普通音符相同（不小于中位数），不满足
 *   面积条件。
 * - **孤立小音符**：如果右侧没有更大的音符，不满足邻近性条件。
 */
object GraceNoteDetector {

    /** 装饰音符头面积上限（相对于系统内最大符头面积）。 */
    private const val GRACE_AREA_RATIO = 0.55

    /** 主音符面积下限（相对于系统内最大符头面积）。 */
    private const val MAIN_AREA_RATIO = 0.85

    /** 装饰音与主音符之间的最小水平间距（谱线间距倍数）。 */
    private const val MIN_GAP_FRAC = 0.2

    /** 装饰音与主音符之间的最大水平间距（谱线间距倍数）。 */
    private const val MAX_GAP_FRAC = 2.0

    /** 装饰音与主音符之间的最大竖直差异（谱线间距倍数）。 */
    private const val MAX_Y_DIFF_FRAC = 2.5

    /** 系统内最少符头数才启用尺寸比较（否则无法判断"小"）。 */
    private const val MIN_NOTHEADS_FOR_REF = 2

    /** 斜杠检测：宽行（斜杠穿过）的最小水平跨度（像素）。 */
    private const val SLASH_WIDE_SPAN = 4

    /** 斜杠检测：窄行（裸符干）的最大像素数。 */
    private const val SLASH_NARROW_MAX_PIXELS = 2

    /** 斜杠检测：搜索区域的竖直延伸（谱线间距倍数，覆盖符干长度）。 */
    private const val STEM_SEARCH_FRAC = 2.0

    /** 斜杠检测：搜索区域的水平半宽（谱线间距倍数）。 */
    private const val SLASH_SEARCH_HALF_W_FRAC = 1.0

    /**
     * 单个装饰音的检测结果。
     *
     * @param noteheadIdx     装饰音在 noteheads 列表中的索引。
     * @param mainNoteheadIdx 它所装饰的主音符在 noteheads 列表中的索引。
     * @param hasSlash        true = 短前倚音(acciaccatura，符干有斜杠)；
     *                        false = 长前倚音(appoggiatura，无斜杠)。
     */
    data class GraceNote(
        val noteheadIdx: Int,
        val mainNoteheadIdx: Int,
        val hasSlash: Boolean
    )

    /**
     * 检测所有装饰音。
     *
     * @param noteheads     所有已检测的符头（与管线中使用相同的列表/顺序）。
     * @param systemIndices 与 [noteheads] 平行的系统索引列表（每个符头所属的系统）。
     *                      长度应与 [noteheads] 相同；不匹配时默认系统 0。
     * @param image         去谱线后的二值图（用于斜线检测）。
     * @param lineSpacing   谱线间距（像素）。
     * @return 检测到的装饰音列表（按 noteheadIdx 升序）。
     */
    fun detect(
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        image: BinaryImage,
        lineSpacing: Int
    ): List<GraceNote> {
        if (lineSpacing <= 0 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()

        // 按系统分组，计算每个系统的最大符头面积（最大符头一定是普通音符）。
        val bySystem = noteheads.indices.groupBy { systemIndices.getOrElse(it) { 0 } }
        val maxAreaBySystem = HashMap<Int, Int>()
        for ((sysIdx, indices) in bySystem) {
            if (indices.size < MIN_NOTHEADS_FOR_REF) continue
            maxAreaBySystem[sysIdx] = indices.maxOf { noteheads[it].width * noteheads[it].height }
        }

        val results = ArrayList<GraceNote>()
        val minGap = (MIN_GAP_FRAC * s).toInt().coerceAtLeast(1)
        val maxGap = (MAX_GAP_FRAC * s).toInt()
        val maxYDiff = (MAX_Y_DIFF_FRAC * s).toInt()

        for (i in noteheads.indices) {
            val sysIdx = systemIndices.getOrElse(i) { 0 }
            val maxArea = maxAreaBySystem[sysIdx] ?: continue
            val nhArea = noteheads[i].width * noteheads[i].height

            // 候选装饰音：面积显著小于系统内最大符头。
            if (nhArea >= GRACE_AREA_RATIO * maxArea) continue

            // 寻找右侧最近的普通音符（面积 ≥ 最大值的 85%）。
            var bestMain = -1
            var bestDist = Int.MAX_VALUE
            for (j in noteheads.indices) {
                if (j == i) continue
                if (systemIndices.getOrElse(j) { 0 } != sysIdx) continue
                val mainArea = noteheads[j].width * noteheads[j].height
                if (mainArea < MAIN_AREA_RATIO * maxArea) continue
                val dx = noteheads[j].centerX - noteheads[i].centerX
                if (dx <= 0) continue // 必须在右侧
                if (dx < minGap || dx > maxGap) continue
                val dy = abs(noteheads[j].centerY - noteheads[i].centerY)
                if (dy > maxYDiff) continue
                if (dx < bestDist) {
                    bestDist = dx
                    bestMain = j
                }
            }

            if (bestMain >= 0) {
                val hasSlash = detectSlash(image, noteheads[i], s)
                results += GraceNote(i, bestMain, hasSlash)
            }
        }

        return results
    }

    /**
     * 检测装饰音符干上的斜线（短前倚音 acciaccatura 标志）。
     *
     * 斜杠是穿过符干的对角线笔画。在符头上方和下方的符干搜索区域内：
     * - **裸符干**：每行黑像素水平跨度 ≈1–2px（竖直线）
     * - **斜杠穿过**：斜杠所在行的水平跨度 ≥4px（对角线增加了水平范围）
     *
     * 同时存在"窄行"（裸符干部分）和"宽行"（斜杠穿过部分）即判定为斜线。
     * 纯粹的宽连通块（如实心块或粗笔画）只有宽行没有窄行，不会被误判。
     *
     * @return true 如果检测到斜线（短前倚音）。
     */
    private fun detectSlash(image: BinaryImage, nh: Notehead, s: Double): Boolean {
        val stemSearch = (STEM_SEARCH_FRAC * s).toInt().coerceAtLeast(6)
        val halfW = (SLASH_SEARCH_HALF_W_FRAC * s).toInt().coerceAtLeast(4)
        val xLo = (nh.centerX - halfW).coerceIn(0, image.width - 1)
        val xHi = (nh.centerX + halfW).coerceIn(xLo, image.width - 1)

        // 符头上方区域（stem-up 符干向上延伸）。
        val yAboveLo = (nh.centerY - nh.height / 2 - stemSearch).coerceIn(0, image.height - 1)
        val yAboveHi = (nh.centerY - nh.height / 2 - 1).coerceIn(0, image.height - 1)

        // 符头下方区域（stem-down 符干向下延伸）。
        val yBelowLo = (nh.centerY + nh.height / 2 + 1).coerceIn(0, image.height - 1)
        val yBelowHi = (nh.centerY + nh.height / 2 + stemSearch).coerceIn(0, image.height - 1)

        return hasDiagonalStroke(image, xLo, xHi, yAboveLo, yAboveHi) ||
            hasDiagonalStroke(image, xLo, xHi, yBelowLo, yBelowHi)
    }

    /**
     * 在矩形区域内检测"斜杠穿过竖线"模式：同时存在窄行（裸符干）和宽行（斜杠穿过）。
     */
    private fun hasDiagonalStroke(
        image: BinaryImage,
        xLo: Int,
        xHi: Int,
        yLo: Int,
        yHi: Int
    ): Boolean {
        if (yLo > yHi || xLo > xHi) return false
        var hasWideRow = false
        var hasNarrowRow = false
        for (y in yLo..yHi) {
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var count = 0
            for (x in xLo..xHi) {
                if (image.isBlack(x, y)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    count++
                }
            }
            if (count == 0) continue
            val span = maxX - minX + 1
            if (span >= SLASH_WIDE_SPAN) hasWideRow = true
            if (count <= SLASH_NARROW_MAX_PIXELS) hasNarrowRow = true
        }
        // 斜杠：既有宽行（斜杠穿过）又有窄行（裸符干）。
        return hasWideRow && hasNarrowRow
    }
}
