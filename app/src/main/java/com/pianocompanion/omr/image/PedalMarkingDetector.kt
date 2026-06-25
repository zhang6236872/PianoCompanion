package com.pianocompanion.omr.image

/**
 * 踏板记号(pedal marking)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 在钢琴音乐中，延音踏板(sustain pedal)是最重要的踏板控制，其使用指示是乐谱中
 * 极为常见的标记。踏板记号有两种基本形式：
 *
 * 1. **踏板踩下标记 (Ped.)**：谱表下方（通常是低音谱表）的文字 "Ped."，
 *    指示演奏者踩下延音踏板。通常用斜体书写，首字母大写。
 * 2. **踏板释放标记 (∗/✱)**：一个小星号/花形标记，指示演奏者松开踏板。
 *    有时也用方括号(bracket)连接 Ped. 和释放位置，但纯文字+星号形式最常见。
 *
 * 此前 OMR 管线完全忽略踏板记号，无法从乐谱图像中识别踏板控制信息——
 * 对一个名为 "Piano Companion" 的应用来说，踏板是钢琴演奏的核心组成部分。
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：在每个谱表系统的底线之下 0.5~4 个谱线间距内搜索
 *    （踏板记号通常紧贴谱表下方书写）。
 * 2. **"Ped." 检测**：
 *    - 将搜索区域内的连通块按水平邻近度分组为「文本行」
 *    - 对每个墨块降采样到 5×7 网格，与字母模板 'P'/'e'/'d' 做汉明距离匹配
 *    - 字母序列匹配 "Ped" + 末尾句点(period) → 判定为踏板踩下标记
 * 3. **释放标记检测**：
 *    - 在搜索区域内查找星形/花形连通块
 *    - 通过与 RELEASE_TEMPLATE (星形点阵) 的汉明距离匹配判定
 *    - 释放标记是小型紧凑墨块，与字母/数字形状不同
 *
 * ## 与其他符号的区分
 *
 * - **力度记号(pp/mf 等)**：DynamicMarkingDetector 也搜索谱表下方，但力度记号
 *   使用不同字母组合(p/m/f 等)，不会匹配 "Ped"。两者搜索区域重叠但匹配条件不同。
 * - **符干/符头**：符干是细长竖线（1-2px 宽），降采样后与字母模板汉明距离很大；
 *   符头是椭圆形，也不会匹配字母模板。
 * - **休止符**：休止符形状（锯齿/旗形/块状）与字母截然不同。
 * - **hairpin(渐强/渐弱)**：hairpin 是两条斜线，不是文字。
 *
 * 踏板记号仅产生提示信息，不修改音符数据模型（与力度记号、hairpin 等一致）。
 */
object PedalMarkingDetector {

    /**
     * 检测到的踏板记号。
     *
     * @param type     踏板类型（PRESS=踩下, RELEASE=释放）。
     * @param centerX  标记中心的 X 坐标（用于与音符关联）。
     * @param systemIdx 所属谱表系统索引。
     */
    data class PedalMarking(
        val type: PedalType,
        val centerX: Int,
        val systemIdx: Int
    )

    /**
     * 踏板记号类型。
     *
     * - [PRESS] 踩下踏板（"Ped." 文字标记）
     * - [RELEASE] 释放踏板（星号/花形标记）
     */
    enum class PedalType { PRESS, RELEASE }

    // ---- 搜索区域参数（谱线间距倍数） ---------------------------------------

    /** 搜索区域起始间隙：从谱表底线向下偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向下搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 4.0

    // ---- 候选墨块尺寸约束（谱线间距倍数） -----------------------------------

    /** 候选墨块最大宽度。 */
    private const val MAX_BLOB_WIDTH_FRAC = 3.0

    /** 候选墨块最大高度。 */
    private const val MAX_BLOB_HEIGHT_FRAC = 2.0

    /** 候选墨块最小面积（像素）。 */
    private const val MIN_BLOB_AREA = 4

    // ---- 水平分组参数 -------------------------------------------------------

    /** 组内元素间最大水平间距（谱线间距倍数）。 */
    private const val GROUP_GAP_FRAC = 0.8

    // ---- 模板网格尺寸 -------------------------------------------------------

    private const val GRID_W = 5
    private const val GRID_H = 7

    // ---- 字母匹配阈值 -------------------------------------------------------

    /** 最大可接受汉明距离（占 5×7=35 格的比例）。 */
    private const val MAX_ACCEPT_DIST = 12  // 12/35 ≈ 34%

    /** 最近与次近距离差（消歧：差值不够大说明模糊，放弃匹配）。 */
    private const val MIN_SECOND_GAP = 2

    /**
     * 内置 'P'（大写）、'e'（小写）、'd'（小写）的 5×7 点阵模板。
     *
     * 模板设计原则（与 DynamicMarkingDetector / TrillDetector 一致）：
     * 所有 7 行和 5 列都必须至少有一个填充像素——这样渲染后的 blob 边界框恰好是
     * 5×scale × 7×scale，降采样时每个输出格精确映射回一个模板格，保证完美往返
     * （hamming距离 = 0）。
     *
     * 模板设计基于**音乐排版中 Ped. 的典型形态**：
     * - **P**（大写）：左侧完整竖笔画 + 右上方圆碗（bowl），底部无碗
     * - **e**（小写斜体）：圆弧 + 中央水平线
     * - **d**（小写斜体）：右侧上升笔画(ascender) + 左下圆碗
     */
    val LETTER_TEMPLATES: Map<Char, BooleanArray> by lazy { buildLetterTemplates() }

    /**
     * 释放标记(星形/花形)的 5×7 点阵模板。
     *
     * 钢琴乐谱中的踏板释放标记是一个小型星号/花形符号。在 5×7 网格中表示为
     * 从中心向外辐射的星形——中心列满高、交叉斜线从四角向中心收拢。
     */
    val RELEASE_TEMPLATE: BooleanArray by lazy { buildReleaseTemplate() }

    /**
     * 在每个谱表系统下方检测踏板记号。
     *
     * @param image       去谱线+降噪后的二值图像。
     * @param blobs       连通块列表（与 image 一致的坐标系）。
     * @param systems     谱表系统列表。
     * @param lineSpacing 平均谱线间距。
     * @return 检测到的踏板记号列表（按系统、X 排序）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<PedalMarking> {
        if (lineSpacing < 1) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<PedalMarking>()

        systems.forEachIndexed { sysIdx, system ->
            val bottomY = system.bottomLine.center

            // 搜索区：底线之下 0.5~4 个谱线间距
            val searchTop = bottomY + (SEARCH_GAP_FRAC * s).toInt()
            val searchBottom = (bottomY + SEARCH_RANGE_FRAC * s).toInt()
                .coerceAtMost(image.height - 1)

            if (searchTop >= searchBottom) return@forEachIndexed

            val maxBlobW = (MAX_BLOB_WIDTH_FRAC * s).toInt()
            val maxBlobH = (MAX_BLOB_HEIGHT_FRAC * s).toInt()

            // 过滤搜索区域内的候选墨块
            val candidates = blobs.filter { blob ->
                blob.centerY in searchTop..searchBottom &&
                    blob.minY >= searchTop - lineSpacing / 2 &&
                    blob.area >= MIN_BLOB_AREA &&
                    blob.width >= 2 &&
                    blob.height >= 3 &&
                    blob.width <= maxBlobW &&
                    blob.height <= maxBlobH
            }.sortedBy { it.minX }

            if (candidates.isEmpty()) return@forEachIndexed

            // ---- 1. 释放标记检测 ----
            // 在搜索区域内查找星形释放标记（独立检测，不依赖水平分组）
            for (blob in candidates) {
                if (isReleaseMark(image, blob, lineSpacing)) {
                    results += PedalMarking(PedalType.RELEASE, blob.centerX, sysIdx)
                }
            }

            // ---- 2. "Ped." 文字检测 ----
            // 将水平方向上相邻的墨块分组为字母序列
            val maxGap = (GROUP_GAP_FRAC * s).toInt().coerceAtLeast(3)
            val groups = ArrayList<MutableList<Blob>>()
            for (blob in candidates) {
                if (groups.isNotEmpty()) {
                    val lastBlob = groups.last().last()
                    if (blob.minX - lastBlob.maxX <= maxGap) {
                        groups.last().add(blob)
                        continue
                    }
                }
                groups.add(ArrayList(listOf(blob)))
            }

            // 对每组墨块做字母匹配
            for (group in groups) {
                if (group.isEmpty()) continue

                val matched = group.map { blob -> blob to matchLetter(image, blob) }
                val letters = matched.mapNotNull { it.second }

                if (letters.size >= 3) {
                    val text = letters.take(3).joinToString("")
                    // 检查是否有末尾句点
                    val hasPeriod = checkTrailingPeriod(group, matched)
                    if (text == "Ped") {
                        val centerX = (group.first().minX + group.last().maxX) / 2
                        results += PedalMarking(PedalType.PRESS, centerX, sysIdx)
                    }
                    // 即使没有句点也接受 "Ped"（有些乐谱省略句点）
                }
            }
        }

        // 按 X 排序，方便用户阅读时间轴
        return results.sortedBy { it.centerX }
    }

    /**
     * 将单个墨块降采样到 5×7 网格后与字母模板做汉明距离匹配。
     * 要求最近距离足够小、且与次近拉开差距（避免模糊误识）。
     */
    private fun matchLetter(image: BinaryImage, blob: Blob): Char? {
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

        if (bestChar == null || bestDist > MAX_ACCEPT_DIST) return null
        if (secondDist - bestDist < MIN_SECOND_GAP) return null

        return bestChar
    }

    /**
     * 检查一组字母匹配后是否有末尾句点(period)。
     * 句点是最后匹配字母之后的小型紧凑墨块。
     */
    private fun checkTrailingPeriod(
        group: List<Blob>,
        matched: List<Pair<Blob, Char?>>
    ): Boolean {
        val lastMatchedIdx = matched.indexOfLast { it.second != null }
        if (lastMatchedIdx < 0 || lastMatchedIdx >= group.size - 1) return false
        return group.subList(lastMatchedIdx + 1, group.size).any { isPeriod(it) }
    }

    /**
     * 判断一个墨块是否为句点(period)。
     * 句点是小型紧凑实心墨块——宽高均不超过 0.5 个谱线间距，且面积 ≥ 2 像素。
     */
    private fun isPeriod(blob: Blob): Boolean {
        val maxDim = 5  // 句点尺寸很小
        return blob.width in 2..maxDim && blob.height in 2..maxDim
    }

    /**
     * 判断一个连通块是否为踏板释放标记（星形/花形）。
     *
     * 释放标记是一个小型紧凑的星形符号。通过与 RELEASE_TEMPLATE 做汉明距离匹配判定。
     * 同时检查几何特征：大致方形、中等填充率、尺寸在合理范围内。
     */
    private fun isReleaseMark(image: BinaryImage, blob: Blob, lineSpacing: Int): Boolean {
        val s = lineSpacing.toDouble()

        // 尺寸约束：释放标记是小型符号
        val minWidth = 3
        val minHeight = 3
        val maxWidth = (1.5 * s).toInt()
        val maxHeight = (1.5 * s).toInt()
        if (blob.width < minWidth || blob.height < minHeight) return false
        if (blob.width > maxWidth || blob.height > maxHeight) return false

        // 大致方形（星形各向同性）
        val ratio = blob.width.toDouble() / blob.height.coerceAtLeast(1)
        if (ratio < 0.4 || ratio > 2.5) return false

        // 模板匹配
        val grid = downsampleRegion(
            image, blob.minX, blob.minY, blob.maxX, blob.maxY, GRID_W, GRID_H
        )

        var d = 0
        for (i in grid.indices) if (grid[i] != RELEASE_TEMPLATE[i]) d++

        return d <= MAX_ACCEPT_DIST
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
        val glyphs = mapOf(
            // P（大写）：左侧完整竖笔画 + 右上方圆碗（bowl）
            // 竖笔画贯穿全高，圆碗在行 0-3，行 4-6 仅竖笔画
            'P' to arrayOf(
                "11110",   // 顶部横笔画 + 左竖笔画
                "10001",   // 碗右壁
                "10001",   // 碗右壁
                "11110",   // 碗底 + 左竖笔画
                "10000",   // 竖笔画
                "10000",   // 竖笔画
                "10001"    // 竖笔画 + 右下小脚（保证第5列有像素）
            ),
            // e（小写斜体）：圆弧 + 中央水平线
            'e' to arrayOf(
                "01110",
                "10001",
                "10000",
                "11111",
                "10000",
                "10001",
                "01110"
            ),
            // d（小写斜体）：右侧上升笔画(ascender) + 左下圆碗
            'd' to arrayOf(
                "00001",
                "00001",
                "00001",
                "01111",
                "10001",
                "10001",
                "01111"
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

    /**
     * 释放标记（星形/花形）的 5×7 点阵模板。
     *
     * 钢琴乐谱中的踏板释放星号通常是一个从中心向外辐射的小型花形符号。
     * 在 5×7 网格中表示为：中心列满高 + 交叉斜线从四角向中心收拢。
     */
    private fun buildReleaseTemplate(): BooleanArray {
        val glyph = arrayOf(
            "00100",   // 顶点
            "00100",   // 竖线段
            "10101",   // 左右翼展开
            "01110",   // 最宽处
            "10101",   // 左右翼展开
            "00100",   // 竖线段
            "00100"    // 底点
        )
        val arr = BooleanArray(GRID_W * GRID_H)
        for (r in glyph.indices) {
            for (c in 0 until GRID_W) {
                if (glyph[r][c] == '1') arr[r * GRID_W + c] = true
            }
        }
        return arr
    }
}
