package com.pianocompanion.omr.image

/**
 * 渐变速度（临时速度变化）文字检测器。
 *
 * 在乐谱图像中识别 rit. (ritardando, 渐慢)、rall. (rallentando, 渐慢)、
 * ritardando 的近义缩写 riten. (ritenuto, 突慢)、accel. (accelerando, 渐快)，
 * 以及 "a tempo"（回原速）等基于文本的速度变化指令。
 *
 * 这些文字标记与 [TempoMarkingDetector] 识别的绝对速度（如 ♩=120）互补：
 * - TempoMarkingDetector 识别的是**确定**的节拍速度（固定值）；
 * - 本检测器识别的是速度的**变化趋势**（渐慢 / 渐快 / 回原速），属于表现性指令，
 *   仅产生信息性提示，不修改音符数据模型。
 *
 * 检测方法：5×7 网格字母模板 + 汉明距离匹配，与 NavigationInstructionDetector、
 * PedalMarkingDetector、DynamicMarkingDetector 的架构保持一致。先在谱表上方搜索区域中
 * 找到候选墨块，按水平间距将它们分组为文本行，然后逐一匹配字母模板，最后通过上下文
 * 解析将字母序列映射为速度变化类型。
 *
 * 字母近似说明：小写字母 "i" 在印刷体中由一个点 + 一条竖线组成，两者不连通，无法用单个
 * 5×7 模板表示。本检测器将 "i" 的竖线用 "l"（小写 L）模板近似——两者均为细长竖直笔画，
 * 在分类阶段把 "l" 同时视作 i/l 处理。
 */
object TempoChangeDetector {

    // ===== 数据结构 ===========================================================

    /** 速度变化的方向 */
    enum class TempoDirection {
        /** 渐慢 / 突慢 */
        SLOW_DOWN,
        /** 渐快 */
        SPEED_UP,
        /** 回到原速 */
        RETURN
    }

    /** 速度变化指令类型 */
    enum class TempoChangeType(
        val direction: TempoDirection,
        /** 中文显示标签 */
        val chineseLabel: String
    ) {
        /** rit. (ritardando) — 渐慢 */
        RITARDANDO(TempoDirection.SLOW_DOWN, "渐慢"),
        /** rall. (rallentando) — 渐慢 */
        RALLENTANDO(TempoDirection.SLOW_DOWN, "渐慢"),
        /** riten. (ritenuto) — 突慢 */
        RITENUTO(TempoDirection.SLOW_DOWN, "突慢"),
        /** accel. (accelerando) — 渐快 */
        ACCELERANDO(TempoDirection.SPEED_UP, "渐快"),
        /** a tempo — 回原速 */
        A_TEMPO(TempoDirection.RETURN, "回原速")
    }

    /** 识别到的一条速度变化指令 */
    data class TempoChange(
        val type: TempoChangeType,
        /** 文本的中心横坐标（像素） */
        val centerX: Int,
        /** 所属谱表系统的索引 */
        val systemIdx: Int
    )

    // ===== 5×7 字母模板 =======================================================
    // 每个字母用 5 列 × 7 行的 0/1 矩阵表示（第 0 列 = 最高位）。
    // 设计约束：矩阵的四条边（第 0 行、第 6 行、第 0 列、第 4 列）各自至少有一个 1，
    // 这样以整数倍缩放渲染后连通块的外接矩形恰好为 5*scale × 7*scale，
    // 下采样 5×7 时能完美还原模板（汉明距离 = 0）。
    // 所有像素 8-连通（构成单个连通块）。

    /** t：顶部十字横梁 + 居中竖干 */
    private val TEMPLATE_T = intArrayOf(
        0b00010, 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b01100
    )
    /** l（兼作 i 的竖干）：斜体细长竖线带底部右衬线 */
    private val TEMPLATE_L = intArrayOf(
        0b10000, 0b11000, 0b01000, 0b01000, 0b00100, 0b00100, 0b00111
    )
    /** r：顶部小拱 + 居中竖干 */
    private val TEMPLATE_R = intArrayOf(
        0b01110, 0b10001, 0b01110, 0b00100, 0b00100, 0b00100, 0b00100
    )
    /** n：顶部拱 + 两条外竖干 */
    private val TEMPLATE_N = intArrayOf(
        0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001
    )
    /** a：开口圆肚 + 右竖干 */
    private val TEMPLATE_A = intArrayOf(
        0b01110, 0b10001, 0b00001, 0b01111, 0b10001, 0b10001, 0b01110
    )
    /** o：闭合圆肚 */
    private val TEMPLATE_O = intArrayOf(
        0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110
    )
    /** c：右侧开口弧 */
    private val TEMPLATE_C = intArrayOf(
        0b01111, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b01111
    )
    /** e：右侧开口弧 + 中横 */
    private val TEMPLATE_E = intArrayOf(
        0b01111, 0b10000, 0b10000, 0b11111, 0b10000, 0b10000, 0b01111
    )
    /** m：三条竖干 + 顶部连接横 */
    private val TEMPLATE_M = intArrayOf(
        0b10101, 0b11111, 0b10101, 0b10101, 0b10101, 0b10101, 0b10101
    )
    /** p：圆肚 + 左下延伸（降部） */
    private val TEMPLATE_P = intArrayOf(
        0b01110, 0b10001, 0b10001, 0b10001, 0b01110, 0b10000, 0b10000
    )

    /** 字母 → 模板的映射表 */
    private val LETTER_TEMPLATES: Map<Char, IntArray> = mapOf(
        't' to TEMPLATE_T,
        'l' to TEMPLATE_L,
        'r' to TEMPLATE_R,
        'n' to TEMPLATE_N,
        'a' to TEMPLATE_A,
        'o' to TEMPLATE_O,
        'c' to TEMPLATE_C,
        'e' to TEMPLATE_E,
        'm' to TEMPLATE_M,
        'p' to TEMPLATE_P
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

    /** 一个已匹配字母的条目：包含字母、原始墨块 */
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

    // ===== 词分类 =============================================================

    /**
     * 判断归一化后的字母集合中是否包含指定字母。
     * （i 已被归一化为 l，故此处只需检查 l 即可覆盖 i/l 两种情况。）
     */
    private fun List<Char>.has(c: Char): Boolean = this.contains(c)

    /**
     * 将一个单词的字母列表解析为速度变化类型（可能为 null）。
     *
     * 约定：小写 "i" 用 "l" 模板近似，故所有 i/l 统一按 l 处理。
     *
     * 支持的缩写（句点不匹配任何字母模板，自然被忽略）：
     * - {r,l,t} → RITARDANDO   （rit.）
     * - {r,a,l} 且不含 t → RALLENTANDO   （rall.）
     * - {r,l,t,e,n} → RITENUTO   （riten.）
     * - {a,c,e,l} 且不含 t → ACCELERANDO   （accel.，两个 c 可能合并为一个墨块）
     *
     * 检查顺序经精心设计以避免歧义：
     * 1. ACCELERANDO（含 a+c+e，不含 t）→ accel 的特征是双 c + e
     * 2. RALLENTANDO（r 开头 + a + l，不含 t）→ rall 不含 t，与 rit/riten 区分
     * 3. RITENUTO（r 开头 + t + e + n）→ riten 的特征是 e+n
     * 4. RITARDANDO（r 开头 + t）→ rit 的兜底匹配
     */
    private fun classifyWord(letters: List<Char>): TempoChangeType? {
        if (letters.isEmpty()) return null
        // i → l 归一化
        val norm = letters.map { if (it == 'i') 'l' else it }
        val startsWithR = norm[0] == 'r'
        val hasT = norm.has('t')
        val hasA = norm.has('a')
        val hasL = norm.has('l')
        val hasC = norm.has('c')
        val hasE = norm.has('e')
        val hasN = norm.has('n')

        // 1. accel. → 渐快（双 c 的特征字母 a,c,e,l；不含 t）
        if (hasA && hasC && hasE && hasL && !hasT) {
            return TempoChangeType.ACCELERANDO
        }
        // 2. rall. → 渐慢（r 开头，含 a,l，不含 t）
        if (startsWithR && hasA && hasL && !hasT) {
            return TempoChangeType.RALLENTANDO
        }
        // 3. riten. → 突慢（r 开头，含 t,e,n）
        if (startsWithR && hasT && hasE && hasN) {
            return TempoChangeType.RITENUTO
        }
        // 4. rit. → 渐慢（r 开头，含 t）兜底
        if (startsWithR && hasT) {
            return TempoChangeType.RITARDANDO
        }
        return null
    }

    /**
     * 判断一个单词是否为 "tempo"（t,e,m,p,o）。
     * 容忍 OCR 偶发漏字，至少匹配其中 4 个特征字母即认定为 tempo。
     */
    private fun isTempoWord(letters: List<Char>): Boolean {
        if (letters.isEmpty()) return false
        val norm = letters.map { if (it == 'i') 'l' else it }
        var hits = 0
        if (norm.has('t')) hits++
        if (norm.has('e')) hits++
        if (norm.has('m')) hits++
        if (norm.has('p')) hits++
        if (norm.has('o')) hits++
        return hits >= 4
    }

    /**
     * 判断一个单词是否为单字母连接词 "a"。
     */
    private fun isAConnector(letters: List<Char>): Boolean =
        letters.size == 1 && letters[0] == 'a'

    // ===== 指令模式解析 =======================================================

    /**
     * 解析一个文本行（多个单词）中包含的速度变化指令。
     * 先逐词分类，再处理 "a tempo" 组合（a + tempo → A_TEMPO）。
     */
    private fun parseInstructions(
        words: List<List<MatchedChar>>
    ): List<TempoChangeType> {
        val results = mutableListOf<TempoChangeType>()
        var i = 0
        while (i < words.size) {
            val wordChars = words[i].map { it.ch }

            // 检测 "a tempo" 连接词：a 后面跟 tempo
            if (isAConnector(wordChars) && i + 1 < words.size && isTempoWord(words[i + 1].map { it.ch })) {
                results.add(TempoChangeType.A_TEMPO)
                i += 2  // 跳过 "a" 和 "tempo"
                continue
            }

            classifyWord(wordChars)?.let { results.add(it) }
            i++
        }
        return results
    }

    // ===== 主入口 =============================================================

    /**
     * 在谱表上方检测速度变化文字。
     *
     * @param image 已二值化的乐谱图像（去谱线+降噪后）
     * @param blobs 全部连通块（与 image 一致的坐标系）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距（像素）
     * @return 检测到的速度变化指令列表
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<TempoChange> {
        if (lineSpacing < 1 || systems.isEmpty()) return emptyList()
        val changes = mutableListOf<TempoChange>()
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
                        changes.add(TempoChange(type, centerX, sysIdx))
                    }
                }
            }
        }

        return changes
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
