package com.pianocompanion.omr.image

/**
 * 颤音(trill)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 颤音(trill)是钢琴及古典音乐中最常见的装饰音之一，标记为符头上方的
 * 字母 **"tr"**（trillo 的缩写），指示演奏者在主音与上方二度音之间快速交替。
 * 在许多乐谱中，"tr" 之后还跟随一条**波浪线**(wavy line `~~~~`)，表示颤音
 * 持续的长度。
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：对每个符头，在其所属谱表系统的**顶线上方** 0.5~4.0 个
 *    谱线间距的区域内搜索文字类墨块（颤音标记写在谱表上方）。
 * 2. **字母模板匹配**：将候选墨块降采样到 5×7 网格，与 't' 和 'r' 字母模板
 *    做汉明距离匹配。将水平方向上相邻的墨块分组为字母序列。
 * 3. **颤音判定**：如果一组字母序列的前两个字母依次匹配 't' 和 'r'，则判定
 *    为颤音标记。
 * 4. **波浪线检测**：检测 "tr" 文字右侧是否有持续的墨迹（颤音波浪线），
 *    判断颤音是否带有持续时间指示。
 *
 * ## 与其他符号的区分
 *
 * - **谱表下方的力度记号**：DynamicMarkingDetector 在谱表下方搜索，而颤音在上方，
 *   两者搜索区域不重叠。
 * - **延音记号(fermata)**：fermata 是弧形（宽于高），不会匹配字母模板。
 * - **符干**：符干是细长竖线（1-2px 宽），降采样后与字母模板的汉明距离很大。
 * - **小节号/指法数字**：这些是数字而非字母 "tr"，不会误匹配。
 * - **跳房子序号**：位于签名区附近，与符头 X 不对齐。
 *
 * 颤音仅产生提示信息，不修改音符数据模型（与 fermata、slur 等一致）。
 */
object TrillDetector {

    /**
     * 检测到的颤音标记。
     *
     * @param noteIdx     对应的符头在 noteheads 列表中的索引。
     * @param centerX     "tr" 文字中心的 X 坐标。
     * @param centerY     "tr" 文字中心的 Y 坐标。
     * @param systemIdx   所属谱表系统索引。
     * @param hasWavyLine 是否检测到波浪线（颤音持续时间指示）。
     */
    data class Trill(
        val noteIdx: Int,
        val centerX: Int,
        val centerY: Int,
        val systemIdx: Int,
        val hasWavyLine: Boolean
    )

    // ---- 尺寸约束（谱线间距倍数） -------------------------------------------

    /** 搜索区域起始间隙：从谱表顶线向上偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向上搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 4.0

    /** 符头中心与 "tr" 文字中心 X 偏差最大值。 */
    private const val CENTER_X_TOLERANCE_FRAC = 1.5

    /** 候选墨块最大宽度（单个字母）。 */
    private const val MAX_BLOB_WIDTH_FRAC = 2.0

    /** 候选墨块最大高度（单个字母）。 */
    private const val MAX_BLOB_HEIGHT_FRAC = 2.0

    /** 水平相邻墨块分组的最大间距。 */
    private const val GROUP_GAP_FRAC = 0.8

    /** 波浪线检测：连续墨迹列的最小跨度（谱线间距倍数）。 */
    private const val WAVY_MIN_SPAN_FRAC = 1.5

    /** 波浪线检测：垂直搜索带的半高（谱线间距倍数，围绕 "tr" 中心）。 */
    private const val WAVY_HALF_HEIGHT_FRAC = 0.8

    /** 波浪线检测：搜索向右延伸的最大距离（谱线间距倍数）。 */
    private const val WAVY_SEARCH_EXTENT_FRAC = 6.0

    /** 波浪线检测：墨迹列覆盖率阈值。 */
    private const val WAVY_COVERAGE_THRESHOLD = 0.45

    private const val GRID_W = 5
    private const val GRID_H = 7

    /** 字母匹配最大可接受汉明距离（35 个格点中的比例）。 */
    private const val MAX_ACCEPT_RATIO = 0.35

    /**
     * 't' 和 'r' 的 5×7 点阵模板。
     *
     * 模板设计原则：所有 7 行和 5 列都至少有 1 个填充像素，确保渲染后
     * 边界框恰好是 5×scale × 7×scale，降采样回去 hamming 距离 = 0。
     *
     * - **t**：顶部竖线起点 + 全宽横线(crossbar) + 竖笔画 + 底部微倾
     * - **r**：顶部小弯钩 + 肩膀 + 竖笔画 + 底部微倾
     */
    val LETTER_TEMPLATES: Map<Char, BooleanArray> by lazy { buildLetterTemplates() }

    /**
     * 检测每个符头上方的颤音标记 "tr"。
     *
     * @param image         去谱线+降噪后的二值图像。
     * @param blobs         连通块列表（与 image 一致的坐标系）。
     * @param noteheads     符头列表。
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）。
     * @param systems       谱表系统列表。
     * @param lineSpacing   平均谱线间距。
     * @return 检测到的颤音列表（按符头索引排序）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Trill> {
        if (lineSpacing < 1 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<Trill>()

        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()
        val centerXTol = (CENTER_X_TOLERANCE_FRAC * s).toInt().coerceAtLeast(4)
        val maxBlobW = (MAX_BLOB_WIDTH_FRAC * s).toInt()
        val maxBlobH = (MAX_BLOB_HEIGHT_FRAC * s).toInt()
        val groupGap = (GROUP_GAP_FRAC * s).toInt().coerceAtLeast(3)

        // 按系统预计算搜索区域的边界，供该系统所有符头共享。
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
                    blob.minY >= searchTop - maxBlobH / 2 &&
                    blob.maxY <= searchBottom + maxBlobH / 2 &&
                    blob.width in 2..maxBlobW &&
                    blob.height in 3..maxBlobH &&
                    kotlin.math.abs(blob.centerX - nh.centerX) <= centerXTol
            }.sortedBy { it.minX }

            if (candidates.isEmpty()) return@forEachIndexed

            // 将水平方向上相邻的墨块分组为字母序列。
            val groups = ArrayList<MutableList<Blob>>()
            for (blob in candidates) {
                if (groups.isNotEmpty()) {
                    val lastBlob = groups.last().last()
                    if (blob.minX - lastBlob.maxX <= groupGap) {
                        groups.last().add(blob)
                        continue
                    }
                }
                groups.add(ArrayList(listOf(blob)))
            }

            // 对每组字母序列检查是否以 "tr" 开头。
            for (group in groups) {
                if (group.size < 2) continue

                val char0 = matchLetter(image, group[0], lineSpacing)
                val char1 = matchLetter(image, group[1], lineSpacing)

                if (char0 == 't' && char1 == 'r') {
                    val groupCenterX = (group.first().minX + group.last().maxX) / 2
                    val groupCenterY = (group.first().minY + group.first().maxY +
                        group.last().minY + group.last().maxY) / 4

                    // 检测右侧是否有波浪线（颤音持续时间指示）。
                    val wavy = hasWavyLine(
                        image, group.last().maxX + 1,
                        groupCenterY, lineSpacing
                    )

                    results += Trill(
                        noteIdx = idx,
                        centerX = groupCenterX,
                        centerY = groupCenterY,
                        systemIdx = sysIdx,
                        hasWavyLine = wavy
                    )
                    break // 每个符头最多一个颤音标记
                }
            }
        }

        return results
    }

    /**
     * 将单个墨块降采样到 5×7 网格后与字母模板做汉明距离匹配。
     * 要求最近距离足够小、且与次近拉开差距。
     */
    private fun matchLetter(image: BinaryImage, blob: Blob, lineSpacing: Int): Char? {
        if (blob.width > lineSpacing * 2 || blob.height > lineSpacing * 2) return null
        if (blob.width < 2 || blob.height < 3) return null

        val grid = downsampleRegion(
            image, blob.minX, blob.minY, blob.maxX, blob.maxY, GRID_W, GRID_H
        )

        var bestChar: Char? = null
        var bestDist = Int.MAX_VALUE
        var secondDist = Int.MAX_VALUE

        for ((char, tmpl) in LETTER_TEMPLATES) {
            var d = 0
            for (i in grid.indices) if (grid[i] != tmpl[i]) d++
            if (d < bestDist) {
                secondDist = bestDist
                bestDist = d
                bestChar = char
            } else if (d < secondDist) {
                secondDist = d
            }
        }

        val maxAccept = (GRID_W * GRID_H * MAX_ACCEPT_RATIO).toInt() // 12/35 ≈ 34%
        if (bestChar == null || bestDist > maxAccept) return null
        if (secondDist - bestDist < 2) return null // 模糊，放弃

        return bestChar
    }

    /**
     * 检测 "tr" 文字右侧是否有持续的墨迹（颤音波浪线）。
     *
     * 颤音波浪线是在 "tr" 之后的水平方向上延伸的小幅上下波动曲线。
     * 检测方法：在 [startX, startX + extent] 范围内，逐列检查垂直搜索带
     * [yCenter - halfH, yCenter + halfH] 是否有黑像素。如果有足够多的列
     * （覆盖率 ≥ 45%且总跨度 ≥ 1.5 个谱线间距）有墨迹，判定为波浪线。
     */
    private fun hasWavyLine(
        image: BinaryImage,
        startX: Int,
        yCenter: Int,
        lineSpacing: Int
    ): Boolean {
        val s = lineSpacing.toDouble()
        val halfH = (WAVY_HALF_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val extent = (WAVY_SEARCH_EXTENT_FRAC * s).toInt()
        val minSpan = (WAVY_MIN_SPAN_FRAC * s).toInt()

        val xEnd = (startX + extent).coerceAtMost(image.width - 1)
        if (xEnd <= startX) return false

        var columnsWithInk = 0
        var totalColumns = 0
        var firstInkX = -1
        var lastInkX = -1

        for (x in startX..xEnd) {
            totalColumns++
            var hasInk = false
            for (y in (yCenter - halfH)..(yCenter + halfH)) {
                if (y in 0 until image.height && image.isBlack(x, y)) {
                    hasInk = true
                    break
                }
            }
            if (hasInk) {
                columnsWithInk++
                if (firstInkX < 0) firstInkX = x
                lastInkX = x
            }
        }

        if (totalColumns == 0 || columnsWithInk == 0) return false

        // 覆盖率检查
        val coverage = columnsWithInk.toDouble() / totalColumns
        if (coverage < WAVY_COVERAGE_THRESHOLD) return false

        // 跨度检查：墨迹必须至少延伸 1.5 个谱线间距
        val span = if (firstInkX >= 0 && lastInkX >= 0) lastInkX - firstInkX + 1 else 0
        return span >= minSpan
    }

    /** 把矩形区域降采样到 cols×rows 布尔网格（每格按黑像素占比 ≥ 0.4 判定）。 */
    private fun downsampleRegion(
        image: BinaryImage, minX: Int, minY: Int, maxX: Int, maxY: Int,
        cols: Int, rows: Int
    ): BooleanArray {
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val out = BooleanArray(cols * rows)
        for (r in 0 until rows) {
            val ry0 = minY + bh * r / rows
            val ry1 = (minY + bh * (r + 1) / rows).coerceAtMost(maxY + 1)
            for (c in 0 until cols) {
                val rx0 = minX + bw * c / cols
                val rx1 = (minX + bw * (c + 1) / cols).coerceAtMost(maxX + 1)
                var black = 0
                var total = 0
                for (y in ry0 until ry1) for (x in rx0 until rx1) {
                    if (image.isBlack(x, y)) black++
                    total++
                }
                out[r * cols + c] = total > 0 && black.toDouble() / total >= 0.4
            }
        }
        return out
    }

    // ---- 5×7 字母模板 -------------------------------------------------------

    private fun buildLetterTemplates(): Map<Char, BooleanArray> {
        // 模板设计：所有 7 行和 5 列都至少有 1 个填充像素。
        val glyphs = mapOf(
            // t: 顶部竖线 + 全宽横线(crossbar) + 竖笔画 + 底部微倾
            //    设计确保所有像素 8-连通（渲染后形成单个 blob），且 5 列 7 行均至少有 1 像素。
            't' to arrayOf(
                "00010",
                "11111",
                "00100",
                "00100",
                "00100",
                "00100",
                "00110"
            ),
            // r: 顶部小弯钩 + 肩膀 + 竖笔画 + 底部微倾（意大利体小写 r）
            //    设计确保所有像素 8-连通（渲染后形成单个 blob），且 5 列 7 行均至少有 1 像素。
            'r' to arrayOf(
                "01110",
                "11111",
                "11000",
                "11000",
                "11000",
                "11110",
                "00110"
            )
        )
        val out = LinkedHashMap<Char, BooleanArray>()
        for ((char, rows) in glyphs) {
            val arr = BooleanArray(GRID_W * GRID_H)
            for (r in rows.indices) {
                for (c in 0 until GRID_W) {
                    if (rows[r][c] == '1') arr[r * GRID_W + c] = true
                }
            }
            out[char] = arr
        }
        return out
    }
}
