package com.pianocompanion.omr.image

/**
 * 力度记号(dynamic marking)检测器。
 *
 * 在五线谱下方（偶尔上方）检测文字形式的力度标记：pp / p / mp / mf / f / ff 等。
 * 力度记号指示演奏音量（从极弱 pp 到极强 ff），是表情记号而非音符属性——
 * 不改变音符时值或 onset，仅提供演奏指导。
 *
 * **检测原理**：
 * 1. 在每个谱表系统的下方（底线之下 0.5~4 个谱线间距）搜索文字类墨块
 * 2. 将水平方向上相邻的墨块分组为字母序列
 * 3. 对每个墨块的边界框做 5×7 降采样，与字母模板
 *    'p'/'m'/'f'/'s'/'z'/'r'/'c'/'e'/'d' 做汉明距离匹配
 * 4. 将匹配到的字母按从左到右的顺序拼接，判断是否为已知力度记号
 * 5. 缩写类标记（如 cresc. / decresc.）末尾的句点被单独检测并附加到文本中
 *
 * **与符头/休止符/符干的区分**：
 * - 符干是细长竖线（1-2px 宽），降采样后与任何字母模板的汉明距离都很大
 * - 休止符（锯齿形/旗形/块状）形状与字母截然不同
 * - 附点/断奏点是紧凑实心圆，与字母不匹配
 * - 谱号曲线通常不在搜索区域（搜索区在底线之下）
 *
 * 力度记号仅产生提示信息，不修改音符数据模型。
 */
object DynamicMarkingDetector {

    /**
     * 检测到的力度记号。
     *
     * @param text 力度文本，如 "p"、"mf"、"pp"
     * @param centerX 标记中心的 X 坐标（用于与音符关联）
     * @param systemIdx 所属谱表系统索引
     */
    data class DynamicMarking(
        val text: String,
        val centerX: Int,
        val systemIdx: Int
    )

    /** 已知的标准力度记号集合。不在列表中的字母组合会被忽略。 */
    private val KNOWN_DYNAMICS = setOf(
        "ppp", "pp", "p", "mp", "mf", "f", "ff", "fff",  // 基本强弱梯度
        "sfz", "sf", "sfp", "fp",  // 突强类(sforzando)
        "rf", "rfz",  // rinforzando(突强)
        "cresc", "cresc.",  // crescendo(渐强)
        "decresc", "decresc."  // decrescendo(渐弱)
    )

    /** 以句点结尾的缩写标记。当末尾检测到句点时会尝试附加 "." 形式匹配。 */
    private val ABBREVIATION_ROOTS = setOf("cresc", "decresc")

    private const val GRID_W = 5
    private const val GRID_H = 7

    /**
     * 内置 'p'、'm'、'f' 的 5×7 点阵模板。测试可用 [renderLetter] 把模板按倍率画入
     * 合成图，这样降采样回去能精确复原模板，验证识别链路。
     *
     * 模板设计基于**音乐排版中斜体小写字母**的典型形态：
     * - **p**：上方圆碗 + 左侧下降笔画(descender)
     * - **m**：三条竖笔画由顶部弧线连接，整体宽矮
     * - **f**：顶部钩 + 中部横线(crossbar) + 竖笔画 + 底部小钩
     */
    val LETTER_TEMPLATES: Map<Char, BooleanArray> by lazy { buildLetterTemplates() }

    /**
     * 在每个谱表系统下方检测力度记号。
     *
     * @param image 去谱线+降噪后的二值图像
     * @param blobs 连通块列表（与 image 一致的坐标系）
     * @param systems 谱表系统列表
     * @param lineSpacing 平均谱线间距
     * @return 检测到的力度记号列表（按系统、X 排序）
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<DynamicMarking> {
        val results = ArrayList<DynamicMarking>()

        systems.forEachIndexed { sysIdx, system ->
            val bottomY = system.bottomLine.center

            // 搜索区：底线之下 0.5~4 个谱线间距（力度记号通常紧贴谱表下方）
            val searchTop = bottomY + (lineSpacing * 0.5).toInt()
            val searchBottom = (bottomY + lineSpacing * 4).coerceAtMost(image.height - 1)

            if (searchTop >= searchBottom) return@forEachIndexed

            // 过滤搜索区域内的候选墨块
            val candidates = blobs.filter { blob ->
                blob.centerY in searchTop..searchBottom &&
                    blob.minY >= searchTop - lineSpacing / 2 &&
                    blob.height >= 3 &&
                    blob.width >= 2 &&
                    blob.height <= lineSpacing * 2 &&
                    blob.width <= lineSpacing * 3
            }.sortedBy { it.minX }

            if (candidates.isEmpty()) return@forEachIndexed

            // 将水平方向上相邻的墨块分组为字母序列
            val maxGap = (lineSpacing * 0.8).toInt().coerceAtLeast(3)
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

                // 逐个匹配字母，记录哪些 blob 匹配成功
                val matched = group.map { blob -> blob to matchLetter(image, blob, lineSpacing) }
                val letters = matched.mapNotNull { it.second }
                if (letters.isEmpty()) continue

                var text = letters.joinToString("")

                // 缩写标记末尾的句点检测：最后一个匹配的字母之后，若有未匹配的小型
                // 紧凑墨块（句点），且当前文本是缩写词根，则附加 "."。
                // 句点在标准音乐排版中是标记缩写的句点，不是字母。
                if (text in ABBREVIATION_ROOTS) {
                    val lastMatchedIdx = matched.indexOfLast { it.second != null }
                    if (lastMatchedIdx >= 0 && lastMatchedIdx < group.size - 1) {
                        val hasTrailingPeriod = group.subList(lastMatchedIdx + 1, group.size)
                            .any { isPeriod(it, lineSpacing) }
                        if (hasTrailingPeriod) {
                            text += "."
                        }
                    }
                }

                if (text in KNOWN_DYNAMICS) {
                    val centerX = (group.first().minX + group.last().maxX) / 2
                    results += DynamicMarking(text, centerX, sysIdx)
                }
            }
        }

        return results
    }

    /**
     * 将单个墨块降采样到 5×7 网格后与字母模板做汉明距离匹配。
     * 要求最近距离足够小、且与次近拉开差距（避免模糊误识）。
     */
    private fun matchLetter(image: BinaryImage, blob: Blob, lineSpacing: Int): Char? {
        // 过大或过小的墨块不是字母
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

        val maxAccept = (GRID_W * GRID_H * 0.35).toInt() // 12/35 ≈ 34%
        if (bestChar == null || bestDist > maxAccept) return null
        if (secondDist - bestDist < 2) return null // 模糊，放弃

        return bestChar
    }

    /**
     * 判断一个墨块是否为缩写末尾的句点（period）。
     * 句点是小型紧凑实心墨块——宽高均不超过 0.5 个谱线间距，且面积 ≥ 2 像素。
     */
    private fun isPeriod(blob: Blob, lineSpacing: Int): Boolean {
        val maxDim = (lineSpacing * 0.5).toInt().coerceAtLeast(3)
        return blob.width in 2..maxDim && blob.height in 2..maxDim
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

    // ---- 5×7 字母模板 -----------------------------------------------------

    private fun buildLetterTemplates(): Map<Char, BooleanArray> {
        // 模板设计原则：所有 7 行和 5 列都必须至少有一个填充像素。
        // 这样渲染后的 blob 边界框恰好是 5×scale × 7×scale，
        // 降采样时每个输出格精确映射回一个模板格，保证完美往返（hamming距离=0）。
        val glyphs = mapOf(
            // p: 圆碗（上5行）+ 左侧下降笔画(descender)
            'p' to arrayOf(
                "01110",
                "10001",
                "10001",
                "10001",
                "01110",
                "10000",
                "10000"
            ),
            // m: 三条竖笔画 + 顶部连接弧线
            'm' to arrayOf(
                "10101",
                "11111",
                "10101",
                "10101",
                "10101",
                "10101",
                "10101"
            ),
            // f: 顶部钩 + 全宽横线(crossbar) + 竖笔画 + 底部钩
            'f' to arrayOf(
                "00100",
                "00010",
                "11111",
                "00010",
                "00010",
                "00010",
                "00100"
            ),
            // s: S 形曲线（上弧 → 中间过渡 → 下弧）
            's' to arrayOf(
                "01110",
                "10001",
                "10000",
                "01110",
                "00001",
                "10001",
                "01110"
            ),
            // z: 顶部水平线 + 对角线 + 底部水平线
            'z' to arrayOf(
                "11111",
                "00001",
                "00010",
                "00100",
                "01000",
                "10000",
                "11111"
            ),
            // r: 顶部小弯钩 + 竖笔画（意大利体小写 r）
            'r' to arrayOf(
                "00010",
                "00110",
                "01000",
                "11100",
                "01000",
                "01000",
                "01001"
            ),
            // c: 左侧开放的圆弧
            'c' to arrayOf(
                "01110",
                "10001",
                "10000",
                "10000",
                "10000",
                "10001",
                "01110"
            ),
            // e: 圆弧 + 中央水平线（与 c 的区别仅在中间行全填充）
            'e' to arrayOf(
                "01110",
                "10001",
                "10000",
                "11111",
                "10000",
                "10001",
                "01110"
            ),
            // d: 右侧上升笔画(ascender) + 左下圆碗
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
}
