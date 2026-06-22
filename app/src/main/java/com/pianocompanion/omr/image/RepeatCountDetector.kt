package com.pianocompanion.omr.image

/**
 * 反复次数标注(repeat count)检测器。
 *
 * 当一个反复段需要演奏超过两遍时，乐谱会在反复结束小节线（`:‖`）上方标注
 * **"×N"**（或 "N×"），表示该反复段共演奏 N 遍。例如 "×3" 表示演奏 3 遍
 * （而非标准反复的 2 遍）。这在民歌变奏、练习曲、A-B-A 曲式的多次反复中极常见。
 *
 * 此前 OMR 管线（v2.18.0）已能检测反复记号竖线（`‖:` / `:‖`），但完全忽略
 * 上方的 "×N" 标注——无法区分标准反复（演奏 2 遍）与多次反复（演奏 3/4/… 遍）。
 *
 * ## 检测原理
 *
 * 1. 对每条 [BarlineType.REPEAT_END] 小节线，在其**上方**（谱表顶线之上的标注带）
 *    搜索文字类墨块。
 * 2. 用 5×7 点阵模板识别**乘号 "×"**（[MULTIPLIER_TEMPLATE]）与**数字 0-9**
 *    （复用 [SignatureDetector.classifyDigit]）。
 * 3. 匹配 "×" + 数字 或 数字 + "×" 的组合，解析出反复次数。
 *
 * ## 与跳房子(volta)序号的区分
 *
 * 跳房子序号（1. / 2. / 3.）也出现在顶线上方，但它们是**纯数字**（无乘号），
 * 且位于跳房子括号的左端。乘号 "×" 是反复次数标注的**标志特征**——
 * 据此将反复次数与跳房子序号、指法数字、小节号等区分开来，杜绝误判。
 *
 * 反复次数仅产生提示信息，不修改音符数据模型（与演奏法标记、力度记号一致）。
 *
 * 全程只读取 [BinaryImage] 像素，不修改任何状态。
 *
 * @see BarlineDetector 小节线/反复记号竖线检测
 * @see VoltaDetector 反复跳房子括号检测
 */
object RepeatCountDetector {

    /**
     * 检测到的反复次数标注。
     *
     * @param centerX    标注中心 X 坐标（反复结束小节线附近）
     * @param systemIdx  所属谱表系统索引
     * @param count      反复次数（应演奏多少遍）
     */
    data class RepeatCount(
        val centerX: Int,
        val systemIdx: Int,
        val count: Int
    )

    /** 已分类的字形：乘号或数字。 */
    private data class Glyph(val minX: Int, val maxX: Int, val isMultiplier: Boolean, val digit: Int)

    private const val GRID_W = SignatureDetector.GRID_W   // 5
    private const val GRID_H = SignatureDetector.GRID_H   // 7

    /**
     * 5×7 "×" 乘号点阵模板（公开，便于测试用 [renderMultiplier] 把模板按倍率画入
     * 合成图，使降采样回去能精确复原模板，验证识别链路）。
     *
     * 模板设计原则：所有 7 行和 5 列都至少有 1 个填充像素——这样渲染后的 blob 边界框
     * 恰好是 5×scale × 7×scale，降采样时每个输出格精确映射回一个模板格，保证完美
     * 往返（汉明距离 = 0）。
     */
    val MULTIPLIER_TEMPLATE: BooleanArray by lazy { buildMultiplierTemplate() }

    /**
     * 检测反复次数标注。
     *
     * @param image            去谱线+降噪后的二值图像（标注带在顶线上方，不受去谱线影响；
     *                         坐标系与 [barlinesBySystem] 一致）。
     * @param blobs            连通块列表（与 image 一致的坐标系）。
     * @param barlinesBySystem 每个谱表系统的小节线列表（来自 [BarlineDetector]）。
     * @param systems          谱表系统列表。
     * @param lineSpacing      平均谱线间距。
     * @return 检测到的反复次数列表（按系统、X 排序）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        barlinesBySystem: List<List<Barline>>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<RepeatCount> {
        val s = lineSpacing
        if (s <= 0) return emptyList()

        val results = ArrayList<RepeatCount>()

        barlinesBySystem.forEachIndexed { sysIdx, barlines ->
            if (sysIdx >= systems.size) return@forEachIndexed
            val system = systems[sysIdx]
            val topLineY = system.topLine.center

            // 标注带：顶线正上方 0.3~2.5 个谱线间距（反复次数标注通常紧贴顶线上方）。
            val bandBot = topLineY - 1
            var bandTop = topLineY - (s * 2.5).toInt()
            // 多系统安全：不越过上一个系统的底线 + 1 间距（避免把上方谱表内容误判）。
            if (sysIdx > 0) {
                val limit = systems[sysIdx - 1].bottomLine.center + s
                if (limit > bandTop) bandTop = limit
            }
            if (bandBot < bandTop) return@forEachIndexed

            // 文字墨块的尺寸约束（乘号/数字均为小字形）。
            val maxTextW = s
            val maxTextH = s + s / 2

            // 仅对反复结束小节线（:‖）检测反复次数标注。
            for (barline in barlines) {
                if (barline.type != BarlineType.REPEAT_END) continue
                val cx = barline.centerX
                // 水平搜索窗：反复结束小节线 ±1.5 个谱线间距。
                val xLo = (cx - s * 1.5).toInt().coerceAtLeast(0)
                val xHi = (cx + s * 1.5).toInt().coerceAtMost(image.width - 1)
                if (xLo > xHi) continue

                // 收集标注带 + 水平窗内的候选文字墨块（水平区间与窗口有重叠即可）。
                val candidates = blobs.filter { blob ->
                    blob.centerY in bandTop..bandBot &&
                        blob.maxY < topLineY &&                 // 完全位于顶线之上
                        blob.maxX >= xLo && blob.minX <= xHi &&  // 水平与窗口重叠
                        blob.width in 2..maxTextW &&
                        blob.height in 3..maxTextH
                }

                if (candidates.isEmpty()) continue

                // 把每个候选墨块分类为乘号或数字。
                val glyphs = candidates.mapNotNull { blob ->
                    when (val d = SignatureDetector.classifyDigit(image, blob)) {
                        null -> if (classifyMultiplier(image, blob, s))
                            Glyph(blob.minX, blob.maxX, true, -1) else null
                        else -> Glyph(blob.minX, blob.maxX, false, d)
                    }
                }.sortedBy { it.minX }

                if (glyphs.isEmpty()) continue

                // 查找 "×" + 数字 或 数字 + "×" 的组合。
                val count = resolveCount(glyphs, s)
                if (count != null && count >= 2) {
                    results += RepeatCount(cx, sysIdx, count)
                }
            }
        }

        return results.sortedWith(compareBy({ it.systemIdx }, { it.centerX }))
    }

    /**
     * 从已分类的字形序列中解析反复次数。
     *
     * 找到乘号 "×" 后，取其紧邻一侧（右侧优先，即 "×N" 形式）的连续数字
     * 解析为整数；若右侧无数字则取左侧（"N×" 形式）。
     *
     * @return 解析出的次数；若无乘号或乘号旁无数字则返回 null。
     */
    private fun resolveCount(glyphs: List<Glyph>, s: Int): Int? {
        val mul = glyphs.firstOrNull { it.isMultiplier } ?: return null

        // 相邻字形之间的最大空隙（基于实际字形间距，而非 minX 差值）。
        // 这样可以正确处理多位数字（如 ×12），即使每个字形宽度接近一整个间距。
        val maxGap = (s * 0.8).toInt().coerceAtLeast(3)

        // 右侧连续数字（×N 形式，更常见）。
        val rightDigits = ArrayList<Glyph>()
        var prevMaxX = mul.maxX
        for (g in glyphs) {
            if (g.isMultiplier) continue
            if (g.minX <= mul.maxX) continue        // 必须在 × 右侧
            if (g.minX - prevMaxX > maxGap) break    // 间距过大，序列中断
            rightDigits.add(g)
            prevMaxX = g.maxX
        }
        if (rightDigits.isNotEmpty()) {
            return rightDigits.joinToString("") { it.digit.toString() }.toIntOrNull()
        }

        // 左侧连续数字（N× 形式）。
        val leftDigits = ArrayList<Glyph>()
        var prevMinX = mul.minX
        for (g in glyphs.asReversed()) {
            if (g.isMultiplier) continue
            if (g.maxX >= mul.minX) continue         // 必须在 × 左侧
            if (prevMinX - g.maxX > maxGap) break     // 间距过大，序列中断
            leftDigits.add(g)
            prevMinX = g.minX
        }
        if (leftDigits.isNotEmpty()) {
            // leftDigits 是从右到左收集的，需要反转得到从左到右的数字序列。
            return leftDigits.asReversed().joinToString("") { it.digit.toString() }.toIntOrNull()
        }
        return null
    }

    /**
     * 将单个墨块降采样到 5×7 网格后与乘号模板做汉明距离匹配。
     */
    private fun classifyMultiplier(image: BinaryImage, blob: Blob, lineSpacing: Int): Boolean {
        if (blob.width > lineSpacing || blob.height > lineSpacing + lineSpacing / 2) return false
        if (blob.width < 2 || blob.height < 3) return false

        val grid = downsampleRegion(
            image, blob.minX, blob.minY, blob.maxX, blob.maxY, GRID_W, GRID_H
        )
        var d = 0
        for (i in grid.indices) if (grid[i] != MULTIPLIER_TEMPLATE[i]) d++
        val maxAccept = (GRID_W * GRID_H * 0.30).toInt() // 10/35 ≈ 29%
        return d <= maxAccept
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

    // ---- 5×7 乘号模板 -------------------------------------------------------

    private fun buildMultiplierTemplate(): BooleanArray {
        // ×：两条对角线交叉。每行每列至少 1 像素 → 完美降采样往返。
        val rows = arrayOf(
            "10001",
            "10001",
            "01010",
            "00100",
            "01010",
            "10001",
            "10001"
        )
        val arr = BooleanArray(GRID_W * GRID_H)
        for (r in rows.indices) {
            for (c in 0 until GRID_W) {
                if (rows[r][c] == '1') arr[r * GRID_W + c] = true
            }
        }
        return arr
    }
}
