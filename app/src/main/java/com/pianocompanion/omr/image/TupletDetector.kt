package com.pianocompanion.omr.image

/**
 * 三连音/连音组(tuplet)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 连音组(tuplet)是指在正常应该容纳 M 个音符的时间段内挤入 N 个等时值音符的记法。
 * 最常见的是**三连音(triplet)**：在原本 2 个音符的时间内挤入 3 个音符。
 * 在乐谱中，连音组通常以一个数字标注在音符组上方（有时带方括号），例如：
 *
 * ```
 *        3
 *   ♪ ♪ ♪       ← 三个八分音符在三连音中占据两个八分音符的时长
 * ```
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：在谱表顶线上方 0.5~3.0 个谱线间距的区域内搜索数字墨块
 *    （连音组数字写在谱表上方，与力度记号区域不重叠）。
 * 2. **数字识别**：将候选墨块降采样到 5×7 网格，复用 [SignatureDetector.DIGIT_TEMPLATES]
 *    做汉明距离匹配，识别 2/3/4/5/6/7 等常见连音组数字。
 * 3. **成员识别**：从数字中心 X 出发，在该系统的符头列表（按 X 排序）中，
 *    找到连续 N 个符头，使其几何中心最接近数字 X——即数字标注在音符组正上方。
 * 4. **比例计算**：根据连音组数字 N 计算时值缩放比例（见 [tupletRatio]）。
 *
 * ## 与其他符号的区分
 *
 * - **拍号数字**：拍号在签名区（谱线之间），而连音组数字在谱线上方，搜索区域不重叠。
 * - **跳房子序号**：跳房子序号在顶线上方方括号内，与连音组数字的搜索区域可能重叠，
 *   但跳房子序号通常为 1/2，且在反复结束小节线(:‖)上方——通过检查候选数字是否为
 *   2（跳房子可能）时要求附近有 2 个符头来确认。
 * - **指法数字**：指法数字 1-5 通常在谱线之间紧贴符头上方，而连音组数字在顶线上方
 *   较远处（0.5 个间距以上）。
 * - **小节号**：小节号在谱表左侧边缘外侧，不在符头附近。
 *
 * 连音组会修改音符数据模型：被检测为连音组成员的音符其时值会按比例缩减/增加。
 */
object TupletDetector {

    /**
     * 检测到的连音组。
     *
     * @param number           连音组数字（如三连音=3、二连音=2、五连音=5）。
     * @param noteheadIndices  属于该连音组的符头在 noteheads 列表中的索引集合。
     * @param centerX          数字中心的 X 坐标。
     * @param centerY          数字中心的 Y 坐标。
     * @param systemIdx        所属谱表系统索引。
     * @param hasBracket       是否检测到方括号（bracket）。
     */
    data class Tuplet(
        val number: Int,
        val noteheadIndices: List<Int>,
        val centerX: Int,
        val centerY: Int,
        val systemIdx: Int,
        val hasBracket: Boolean
    )

    // ---- 尺寸约束（谱线间距倍数） -------------------------------------------

    /** 搜索区域起始间隙：从谱表顶线向上偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向上搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 3.0

    /** 候选数字墨块最大宽度（谱线间距倍数）。 */
    private const val MAX_BLOB_WIDTH_FRAC = 1.5

    /** 候选数字墨块最大高度（谱线间距倍数）。 */
    private const val MAX_BLOB_HEIGHT_FRAC = 1.8

    /** 候选数字墨块最小宽度（排除噪点）。 */
    private const val MIN_BLOB_WIDTH_FRAC = 0.3

    /** 候选数字墨块最小高度（排除噪点）。 */
    private const val MIN_BLOB_HEIGHT_FRAC = 0.5

    /**
     * 连音组成员搜索的最大水平跨度（谱线间距倍数）。
     * 3 个八分三连音约占 2 个间距，留足余量。
     */
    private const val MAX_GROUP_SPAN_FRAC = 6.0

    /** 多系统安全：搜索带上界不低于上一个系统底线 + 此间距。 */
    private const val UPPER_GAP_FRAC = 1.0

    /**
     * 支持的连音组数字及其对应的时值缩放比例。
     *
     * 连音组规则：N 个音符占据 (N-1) 或 (N+1) 个同类型音符的时间。
     * - 3→三连音(triplet): 3 in 2, 比例 2/3 ≈ 0.667
     * - 2→二连音(duplet): 2 in 3, 比例 3/2 = 1.5（复合拍子中使用）
     * - 4→四连音(quadruplet): 4 in 3, 比例 3/4 = 0.75
     * - 5→五连音(quintuplet): 5 in 4, 比例 4/5 = 0.8
     * - 6→六连音(sextuplet): 6 in 4, 比例 4/6 ≈ 0.667
     * - 7→七连音(septuplet): 7 in 4, 比例 4/7 ≈ 0.571
     */
    private val TUPLET_RATIOS = mapOf(
        2 to 3.0 / 2.0,
        3 to 2.0 / 3.0,
        4 to 3.0 / 4.0,
        5 to 4.0 / 5.0,
        6 to 4.0 / 6.0,
        7 to 4.0 / 7.0
    )

    /**
     * 计算连音组数字对应的时值缩放比例。
     *
     * 三连音(3): 每个音符时值 × 2/3
     * 二连音(2): 每个音符时值 × 3/2
     * 等等。
     *
     * @return 缩放比例；不支持的数字返回 1.0（不修改时值）。
     */
    fun tupletRatio(number: Int): Double = TUPLET_RATIOS[number] ?: 1.0

    // ---- 方括号检测 ----------------------------------------------------------

    /**
     * 在数字左右两侧（±0.5 个间距）的上方搜索方括号水平线。
     * 方括号是一条从连音组左侧延伸到右侧的水平墨线（可能带向下竖钩）。
     *
     * 检测方法：在数字 Y 上方 0~0.5 个间距的区域内，逐行统计水平连续黑像素长度。
     * 若存在长度 ≥ 2 个谱线间距的水平墨线，判定为方括号。
     */
    private const val BRACKET_HALF_GAP_FRAC = 0.5
    private const val BRACKET_MIN_RUN_FRAC = 2.0
    private const val BRACKET_SEARCH_ABOVE_FRAC = 0.5

    // ---- 主检测方法 ----------------------------------------------------------

    /**
     * 检测谱表上方的连音组(tuplet)数字标注。
     *
     * @param image         去谱线+降噪后的二值图像。
     * @param blobs         连通块列表（与 image 一致的坐标系）。
     * @param noteheads     符头列表。
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）。
     * @param systems       谱表系统列表。
     * @param lineSpacing   平均谱线间距。
     * @return 检测到的连音组列表。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Tuplet> {
        if (lineSpacing < 1 || noteheads.isEmpty() || systems.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<Tuplet>()

        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()
        val maxBlobW = (MAX_BLOB_WIDTH_FRAC * s).toInt()
        val maxBlobH = (MAX_BLOB_HEIGHT_FRAC * s).toInt()
        val minBlobW = (MIN_BLOB_WIDTH_FRAC * s).toInt().coerceAtLeast(2)
        val minBlobH = (MIN_BLOB_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val maxGroupSpan = (MAX_GROUP_SPAN_FRAC * s).toInt()

        // 按系统分组符头索引（保持原始 noteheads 索引），并排序。
        val noteheadsBySystem = HashMap<Int, MutableList<Int>>()
        noteheads.forEachIndexed { idx, _ ->
            val sysIdx = systemIndices.getOrElse(idx) { -1 }
            if (sysIdx >= 0 && sysIdx < systems.size) {
                noteheadsBySystem.getOrPut(sysIdx) { ArrayList() }.add(idx)
            }
        }

        // 已被分配到连音组的符头索引，避免重复分配。
        val assignedNoteheads = HashSet<Int>()

        // 逐系统检测连音组数字。
        systems.forEachIndexed { sysIdx, system ->
            val sysNhs = noteheadsBySystem[sysIdx]
            if (sysNhs.isNullOrEmpty()) return@forEachIndexed

            // 按符头 X 排序的索引列表。
            val sortedNhIndices = sysNhs.sortedBy { noteheads[it].centerX }

            val topLineY = system.topLine.center
            val searchBottom = topLineY - searchGap
            val upperLimit = if (sysIdx > 0) {
                systems[sysIdx - 1].bottomLine.center + (UPPER_GAP_FRAC * s).toInt()
            } else {
                0
            }
            val searchTop = (searchBottom - searchRange).coerceAtLeast(upperLimit).coerceAtLeast(0)
            if (searchBottom <= searchTop) return@forEachIndexed

            // 在搜索区域内找候选数字墨块。
            val digitBlobs = blobs.filter { blob ->
                blob.centerY in searchTop..searchBottom &&
                    blob.minY >= searchTop &&
                    blob.maxY <= searchBottom + maxBlobH / 2 &&
                    blob.width in minBlobW..maxBlobW &&
                    blob.height in minBlobH..maxBlobH
            }.sortedBy { it.minX }

            for (blob in digitBlobs) {
                val digit = SignatureDetector.classifyDigit(image, blob) ?: continue
                if (digit !in TUPLET_RATIOS) continue

                // 找到该系统中最接近数字 X 的连续 N 个符头。
                val tupletMembers = findTupletMembers(
                    sortedNhIndices, noteheads, blob.centerX, digit,
                    maxGroupSpan, assignedNoteheads
                )

                if (tupletMembers.size == digit) {
                    val hasBracket = detectBracket(image, blob, s.toInt())

                    // 标记这些符头已分配。
                    assignedNoteheads.addAll(tupletMembers)

                    results += Tuplet(
                        number = digit,
                        noteheadIndices = tupletMembers,
                        centerX = blob.centerX,
                        centerY = blob.centerY,
                        systemIdx = sysIdx,
                        hasBracket = hasBracket
                    )
                }
            }
        }

        return results
    }

    /**
     * 从按 X 排序的符头索引列表中，找到连续 [n] 个符头，使其几何中心最接近 [digitX]。
     *
     * 算法：对每个可能的起始位置 i（滑动窗口大小 n），计算窗口内 n 个符头 X 中心
     * 与 [digitX] 的偏差；取偏差最小且不超过 [maxSpan] 的窗口。
     *
     * 排除已被分配到其他连音组的符头（在 [assigned] 中）。
     */
    private fun findTupletMembers(
        sortedIndices: List<Int>,
        noteheads: List<Notehead>,
        digitX: Int,
        n: Int,
        maxSpan: Int,
        assigned: Set<Int>
    ): List<Int> {
        if (sortedIndices.size < n) return emptyList()

        var bestStart = -1
        var bestOffset = Int.MAX_VALUE

        for (i in 0..sortedIndices.size - n) {
            val window = sortedIndices.subList(i, i + n)
            // 跳过包含已分配符头的窗口。
            if (window.any { it in assigned }) continue

            val firstX = noteheads[window.first()].centerX
            val lastX = noteheads[window.last()].centerX
            val span = lastX - firstX
            if (span > maxSpan) continue

            val centerX = (firstX + lastX) / 2
            val offset = kotlin.math.abs(centerX - digitX)
            if (offset < bestOffset) {
                bestOffset = offset
                bestStart = i
            }
        }

        return if (bestStart >= 0) {
            sortedIndices.subList(bestStart, bestStart + n).toList()
        } else {
            emptyList()
        }
    }

    /**
     * 检测数字上方是否有方括号(bracket)水平线。
     *
     * 方括号是一条从连音组左端延伸到右端的水平墨线，通常在数字上方或数字 Y 附近。
     * 检测方法：在数字 Y 上方 0~0.5 个间距的区域内，逐行统计从数字中心 X 向左右
     * 延伸的最长连续水平墨迹长度。若存在 ≥ 2 个谱线间距的水平墨线，判定为方括号。
     */
    private fun detectBracket(
        image: BinaryImage,
        digitBlob: Blob,
        s: Int
    ): Boolean {
        val halfGap = (BRACKET_HALF_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val minRun = (BRACKET_MIN_RUN_FRAC * s).toInt().coerceAtLeast(8)
        val searchAbove = (BRACKET_SEARCH_ABOVE_FRAC * s).toInt().coerceAtLeast(3)

        val yStart = (digitBlob.minY - searchAbove).coerceAtLeast(0)
        val yEnd = digitBlob.minY - 1

        for (y in yStart..yEnd) {
            val leftRun = horizontalRun(image, y, digitBlob.centerX, -1)
            val rightRun = horizontalRun(image, y, digitBlob.centerX, +1)
            val totalRun = leftRun + rightRun
            if (totalRun >= minRun) return true
        }
        return false
    }

    /** 从 [startX] 沿 [dir] 方向扫描连续水平墨迹长度。 */
    private fun horizontalRun(image: BinaryImage, y: Int, startX: Int, dir: Int): Int {
        var len = 0
        var x = startX
        while (x in 0 until image.width) {
            if (image.isBlack(x, y)) len++ else break
            x += dir
        }
        return len
    }
}
