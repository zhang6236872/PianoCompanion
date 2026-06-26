package com.pianocompanion.omr.image

/**
 * 基于文本的导航/反复指令检测器。
 *
 * 在乐谱图像中识别 D.C. (Da Capo)、D.S. (Dal Segno)、al Coda、al Fine、Fine 等
 * 导航指令。这些文本指令指示演奏者非线性播放顺序（跳回开头 / 跳回 Segno / 跳至 Coda / 
 * 终止于 Fine），与 [NavigationSymbolDetector] 识别的 Segno/Coda 视觉符号配合使用。
 *
 * 检测方法：5×7 网格字母模板 + 汉明距离匹配，与 PedalMarkingDetector、TempoMarkingDetector
 * 和 DynamicMarkingDetector 的架构保持一致。先在谱表上方搜索区域中找到候选墨块，
 * 按水平间距将它们分组为文本行，然后逐一匹配字母模板，最后通过上下文解析将字母序列
 * 映射为导航指令类型。
 */
object NavigationInstructionDetector {

    // ===== 数据结构 ===========================================================

    /** 导航指令类型 */
    enum class NavigationInstructionType {
        /** D.C. (Da Capo) — 从头开始反复 */
        DA_CAPO,
        /** D.S. (Dal Segno) — 从 Segno 记号处反复 */
        DAL_SEGNO,
        /** al Coda — 反复后跳至 Coda 段落 */
        AL_CODA,
        /** al Fine — 反复后终止于 Fine */
        AL_FINE,
        /** Fine — 终止标记 */
        FINE
    }

    /** 识别到的一条导航指令 */
    data class NavigationInstruction(
        val type: NavigationInstructionType,
        /** 指令文本的中心横坐标（像素） */
        val centerX: Int,
        /** 所属谱表系统的索引 */
        val systemIdx: Int
    )

    // ===== 5×7 字母模板 =======================================================
    // 每个字母用 5 列 × 7 行的 0/1 矩阵表示。
    // 设计约束：矩阵的四条边（第 0 行、第 6 行、第 0 列、第 4 列）各自至少有一个 1，
    // 这样以整数倍缩放渲染后连通块的外接矩形恰好为 5*scale × 7*scale，
    // 下采样 5×7 时能完美还原模板（汉明距离 = 0）。
    // 所有像素 8-连通（构成单个连通块）。

    private val TEMPLATE_D = intArrayOf(
        0b11110, 0b10011, 0b10001, 0b10001, 0b10001, 0b10011, 0b11110
    )
    private val TEMPLATE_C = intArrayOf(
        0b01111, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01111
    )
    private val TEMPLATE_S = intArrayOf(
        0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110
    )
    private val TEMPLATE_F = intArrayOf(
        0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000
    )
    private val TEMPLATE_A_LOWER = intArrayOf(
        0b01110, 0b10001, 0b10001, 0b01111, 0b10001, 0b10001, 0b01110
    )
    private val TEMPLATE_L_LOWER = intArrayOf(
        0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111
    )
    private val TEMPLATE_N_LOWER = intArrayOf(
        0b00100, 0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001
    )
    private val TEMPLATE_E_LOWER = intArrayOf(
        0b01110, 0b10001, 0b10000, 0b11111, 0b10000, 0b10001, 0b01110
    )
    private val TEMPLATE_O_LOWER = intArrayOf(
        0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110
    )
    private val TEMPLATE_D_LOWER = intArrayOf(
        0b00001, 0b00001, 0b00001, 0b01111, 0b10001, 0b10001, 0b01111
    )

    /** 字母 → 模板的映射表 */
    private val LETTER_TEMPLATES: Map<Char, IntArray> = mapOf(
        'D' to TEMPLATE_D,
        'C' to TEMPLATE_C,
        'S' to TEMPLATE_S,
        'F' to TEMPLATE_F,
        'a' to TEMPLATE_A_LOWER,
        'l' to TEMPLATE_L_LOWER,
        'n' to TEMPLATE_N_LOWER,
        'e' to TEMPLATE_E_LOWER,
        'o' to TEMPLATE_O_LOWER,
        'd' to TEMPLATE_D_LOWER
    )

    // ===== 检测参数（以谱线间距倍数或像素表示）================================

    /** 搜索区域上边界：距谱表顶线上方 0.5 个谱线间距开始 */
    private const val SEARCH_GAP_FRAC = 0.5
    /** 搜索区域下边界：距谱表顶线上方 5.0 个谱线间距 */
    private const val SEARCH_RANGE_FRAC = 5.0
    /** 候选墨块最大宽度（谱线间距倍数） */
    private const val MAX_BLOB_WIDTH_FRAC = 3.0
    /** 候选墨块最大高度（谱线间距倍数） */
    private const val MAX_BLOB_HEIGHT_FRAC = 2.0
    /** 候选墨块最小面积（像素） */
    private const val MIN_BLOB_AREA = 4

    /** 水平分组的间距阈值：相邻墨块间距 ≤ 此倍数的谱线间距时归为同一文本行 */
    private const val GROUP_GAP_FRAC = 3.0
    /** 词内间距阈值：组内相邻字母间距 ≤ 此倍数时为同一单词，更大则为词间分隔 */
    private const val WORD_GAP_FRAC = 1.0

    /** 网格宽度 */
    private const val GRID_W = 5
    /** 网格高度 */
    private const val GRID_H = 7
    /** 模板总像素数 = 35 */
    private const val TEMPLATE_PIXELS = GRID_W * GRID_H
    /** 汉明距离接受阈值：≤ TEMPLATE_PIXELS * 0.34 ≈ 12 */
    private const val MAX_ACCEPT_DIST = 12
    /** 最近匹配与次近匹配之间的距离差必须 ≥ 此值，避免歧义 */
    private const val MIN_SECOND_GAP = 2

    // ===== 匹配辅助 ===========================================================

    /**
     * 将字母模板渲染到二值图像中（供测试使用）。
     * 每个 5×7 网格单元绘制为 scale×scale 的黑色像素块。
     */
    internal fun renderLetter(img: BinaryImage, char: Char, x: Int, y: Int, scale: Int) {
        val tmpl = LETTER_TEMPLATES[char] ?: return
        for (r in 0 until GRID_H) {
            for (c in 0 until GRID_W) {
                if ((tmpl[r] shr (GRID_W - 1 - c)) and 1 == 1) {
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
     * 将 [image] 中外接矩形 (minX,minY)-(maxX,maxY) 区域下采样为 GRID_W × GRID_H 的 0/1 矩阵。
     */
    private fun downsampleRegion(
        image: BinaryImage, minX: Int, minY: Int, maxX: Int, maxY: Int
    ): IntArray {
        val grid = IntArray(GRID_H)
        for (gy in 0 until GRID_H) {
            var row = 0
            for (gx in 0 until GRID_W) {
                // 采样窗口中心
                val sx0 = minX + (gx * (maxX - minX + 1)) / GRID_W
                val sx1 = minX + ((gx + 1) * (maxX - minX + 1)) / GRID_W - 1
                val sy0 = minY + (gy * (maxY - minY + 1)) / GRID_H
                val sy1 = minY + ((gy + 1) * (maxY - minY + 1)) / GRID_H - 1
                var black = 0
                var total = 0
                for (y in maxOf(sy0, 0)..minOf(sy1, image.height - 1)) {
                    for (x in maxOf(sx0, 0)..minOf(sx1, image.width - 1)) {
                        total++
                        if (image.isBlack(x, y)) black++
                    }
                }
                // 过半数像素为黑色即为 1
                if (total > 0 && black * 2 >= total) {
                    row = row or (1 shl (GRID_W - 1 - gx))
                }
            }
            grid[gy] = row
        }
        return grid
    }

    /**
     * 计算两个模板的汉明距离（不同像素数）。
     */
    private fun hammingDistance(a: IntArray, b: IntArray): Int {
        var dist = 0
        for (i in 0 until GRID_H) {
            // XOR 后统计 1 的个数
            var diff = a[i] xor b[i]
            while (diff != 0) {
                dist += diff and 1
                diff = diff ushr 1
            }
        }
        return dist
    }

    /**
     * 将一个候选墨块与所有字母模板匹配，返回最佳匹配字母。
     * 要求：最近距离 ≤ [MAX_ACCEPT_DIST] 且与次近距离之差 ≥ [MIN_SECOND_GAP]。
     * @return 匹配的字符，或 null（无可靠匹配）
     */
    private fun matchLetter(image: BinaryImage, blob: Blob): Char? {
        val grid = downsampleRegion(image, blob.minX, blob.minY, blob.maxX, blob.maxY)
        var bestChar: Char? = null
        var bestDist = TEMPLATE_PIXELS
        var secondDist = TEMPLATE_PIXELS
        for ((ch, template) in LETTER_TEMPLATES) {
            val d = hammingDistance(grid, template)
            if (d < bestDist) {
                secondDist = bestDist
                bestDist = d
                bestChar = ch
            } else if (d < secondDist) {
                secondDist = d
            }
        }
        if (bestDist <= MAX_ACCEPT_DIST && (secondDist - bestDist) >= MIN_SECOND_GAP) {
            return bestChar
        }
        return null
    }

    // ===== 候选墨块筛选 =======================================================

    /**
     * 从 [blobs] 中筛选出位于谱表上方搜索区域、尺寸合理的候选墨块。
     */
    private fun filterCandidates(
        blobs: List<Blob>,
        searchTop: Int,
        searchBottom: Int,
        lineSpacing: Double
    ): List<Blob> {
        val maxWidth = (lineSpacing * MAX_BLOB_WIDTH_FRAC).toInt()
        val maxHeight = (lineSpacing * MAX_BLOB_HEIGHT_FRAC).toInt()
        return blobs.filter { b ->
            b.area >= MIN_BLOB_AREA &&
                b.width <= maxWidth &&
                b.height <= maxHeight &&
                b.centerY in searchTop..searchBottom
        }
    }

    // ===== 水平分组 ===========================================================

    /** 一个已匹配字母的条目：包含字母、原始墨块、中心坐标 */
    private data class MatchedChar(
        val ch: Char,
        val blob: Blob
    )

    /**
     * 将候选墨块按水平间距分组为文本行。
     * 先按 centerX 排序，相邻墨块间距 ≤ [GROUP_GAP_FRAC] * lineSpacing 时归为一组。
     */
    private fun groupHorizontal(
        candidates: List<Blob>,
        lineSpacing: Double
    ): List<List<Blob>> {
        if (candidates.isEmpty()) return emptyList()
        val sorted = candidates.sortedBy { it.centerX }
        val maxGap = (lineSpacing * GROUP_GAP_FRAC).toInt()
        val groups = mutableListOf<MutableList<Blob>>()
        var current = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val gap = curr.minX - prev.maxX
            if (gap <= maxGap) {
                current.add(curr)
            } else {
                groups.add(current)
                current = mutableListOf(curr)
            }
        }
        groups.add(current)
        return groups
    }

    // ===== 指令模式解析 =======================================================

    /**
     * 将一个单词的字母列表解析为导航指令类型（可能为 null）。
     * 使用上下文模式匹配：大写字母（D/C/S/F）作为锚点，小写字母提供长度和特征确认。
     *
     * 支持的模式：
     * - [D, C] → DA_CAPO （可含句点，句点不匹配任何字母模板故被忽略）
     * - [D, S] → DAL_SEGNO
     * - [F] + 2~3 个小写字母 → FINE
     * - [C] + 2~3 个小写字母（含 o/d） → CODA
     * - [a, l] → AL （连接词）
     */
    private fun classifyWord(chars: List<Char>): NavigationInstructionType? {
        if (chars.isEmpty()) return null

        // 只取已匹配的字母（跳过句点等非字母 blob）
        val letters = chars
        val first = letters[0]
        val n = letters.size

        // D.C. → DA_CAPO
        if (first == 'D' && n in 2..3 && letters.contains('C')) {
            return NavigationInstructionType.DA_CAPO
        }
        // D.S. → DAL_SEGNO
        if (first == 'D' && n in 2..3 && letters.contains('S')) {
            return NavigationInstructionType.DAL_SEGNO
        }
        // Fine → FINE （大写 F + 小写序列）
        if (first == 'F' && n in 3..5) {
            return NavigationInstructionType.FINE
        }
        // Coda → CODA （大写 C + 小写序列含 o/d）
        if (first == 'C' && n in 3..5 && (letters.contains('o') || letters.contains('d'))) {
            return NavigationInstructionType.AL_CODA
        }
        // al → AL 连接词
        if (first == 'a' && n in 2..3 && letters.contains('l')) {
            // AL 连接词本身不产生独立指令类型，需与后续 Fine/Coda 组合
            // 返回 null，由调用方组合处理
            return null
        }
        return null
    }

    /**
     * 解析一个文本行（多个单词）中包含的导航指令。
     * 先逐词分类，再处理 "al Coda" / "al Fine" 组合。
     * 当 "al" 连接词后跟 "Coda" 或 "Fine" 时，跳过该后续词避免重复计数。
     */
    private fun parseInstructions(
        words: List<List<MatchedChar>>
    ): List<NavigationInstructionType> {
        val results = mutableListOf<NavigationInstructionType>()
        var i = 0
        while (i < words.size) {
            val wordChars = words[i].map { it.ch }
            // 检测 "al" 连接词
            val isAlConnector = wordChars.size in 2..3 &&
                wordChars[0] == 'a' && wordChars.contains('l')

            if (isAlConnector && i + 1 < words.size) {
                // al + 下一个词 → AL_CODA 或 AL_FINE
                val nextChars = words[i + 1].map { it.ch }
                val nextFirst = nextChars.firstOrNull()
                when {
                    nextFirst == 'F' -> {
                        results.add(NavigationInstructionType.AL_FINE)
                        i += 2  // 跳过 "al" 和 "Fine"，避免 Fine 被独立分类
                        continue
                    }
                    nextFirst == 'C' && (nextChars.contains('o') || nextChars.contains('d')) -> {
                        results.add(NavigationInstructionType.AL_CODA)
                        i += 2  // 跳过 "al" 和 "Coda"，避免重复计数
                        continue
                    }
                }
            }

            classifyWord(wordChars)?.let { results.add(it) }
            i++
        }

        return results
    }

    // ===== 主入口 =============================================================

    /**
     * 在谱表上方检测导航指令文本。
     *
     * @param image 已二值化的乐谱图像（去谱线+降噪后）
     * @param blobs 全部连通块（与 image 一致的坐标系）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距（像素）
     * @return 检测到的导航指令列表
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<NavigationInstruction> {
        if (lineSpacing < 1 || systems.isEmpty()) return emptyList()
        val instructions = mutableListOf<NavigationInstruction>()
        val s = lineSpacing.toDouble()

        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()

        for ((sysIdx, system) in systems.withIndex()) {
            val topLineY = system.topLine.center

            // 搜索区域：顶线上方 searchGap ~ searchGap+searchRange
            val searchBottom = topLineY - searchGap
            val searchTop = (searchBottom - searchRange).coerceAtLeast(0)

            // 多系统页面：搜索上界不超过上一个系统的底线 + 1 间距
            val upperLimit = if (sysIdx > 0) {
                systems[sysIdx - 1].bottomLine.center + lineSpacing
            } else {
                0
            }
            val effectiveTop = maxOf(searchTop, upperLimit)
            if (effectiveTop >= searchBottom) continue

            // 1. 筛选搜索区域内的候选墨块
            val candidates = filterCandidates(blobs, effectiveTop, searchBottom, s)
            if (candidates.isEmpty()) continue

            // 2. 水平分组为文本行
            val lines = groupHorizontal(candidates, s)

            // 3. 逐行匹配字母并解析指令
            for (line in lines) {
                if (line.size < 2) continue  // 单个墨块不构成有意义的文本

                // 匹配每个墨块的字母
                val matched = line.mapNotNull { blob ->
                    val ch = matchLetter(image, blob)
                    if (ch != null) MatchedChar(ch, blob) else null
                }
                if (matched.size < 2) continue  // 至少匹配到 2 个字母

                // 拆分单词并解析指令
                val matchedSorted = matched.sortedBy { it.blob.centerX }
                val words = splitMatchedWords(matchedSorted, s)
                val types = parseInstructions(words)

                if (types.isNotEmpty()) {
                    val centerX = matchedSorted.map { it.blob.centerX }.average().toInt()
                    for (type in types) {
                        instructions.add(NavigationInstruction(type, centerX, sysIdx))
                    }
                }
            }
        }

        return instructions
    }

    /**
     * 将已匹配的字母列表按词间距拆分为单词。
     */
    private fun splitMatchedWords(
        matched: List<MatchedChar>,
        lineSpacing: Double
    ): List<List<MatchedChar>> {
        if (matched.isEmpty()) return emptyList()
        val maxGap = (lineSpacing * WORD_GAP_FRAC).toInt()
        val words = mutableListOf<MutableList<MatchedChar>>()
        var current = mutableListOf(matched[0])
        for (i in 1 until matched.size) {
            val gap = matched[i].blob.minX - matched[i - 1].blob.maxX
            if (gap <= maxGap) {
                current.add(matched[i])
            } else {
                words.add(current)
                current = mutableListOf(matched[i])
            }
        }
        words.add(current)
        return words
    }
}
