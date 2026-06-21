package com.pianocompanion.omr.image

/**
 * 反复「跳房子」(volta / ending bracket)。
 *
 * 跳房子是乐谱中标记反复结尾的方括号——一条位于**五线谱顶线上方**的水平线，
 * 两端各有一个向下的短竖钩，左钩上方标注序号（1. / 2. / 3.）。演奏时第一遍走
 * 结尾 1，从反复记号跳回后走结尾 2，跳过结尾 1。
 *
 * @param startX 括号左端 X 坐标（左竖钩位置）
 * @param endX   括号右端 X 坐标（右竖钩位置）
 * @param number 结尾序号（1=第一结尾，2=第二结尾…）；未识别到数字时默认 1
 * @param y      括号水平线所在的 Y 坐标（谱表顶线上方）
 */
data class Volta(
    val startX: Int,
    val endX: Int,
    val number: Int,
    val y: Int
) {
    /** 括号覆盖的水平宽度（像素）。 */
    val width: Int get() = endX - startX + 1
}

/**
 * 纯 Kotlin 反复跳房子(volta)检测器（无 Android 依赖，完全可单元测试）。
 *
 * v2.18.0 的小节线/反复记号检测解决了竖线层面的反复结构（`‖:` / `:‖`），但完全
 * 忽略了跳房子——水平方向标记「第几遍走哪几个小节」的方括号。没有跳房子检测，
 * 含反复结尾的真实乐谱（极常见，如民歌/练习曲/A-B-A 曲式）的反复结构会被漏识。
 *
 * ## 跳房子的视觉特征
 *
 * ```
 *      1. _______________          2. _______________
 *        |               |           |               |
 *   =====|===============|====== =====|===============|======
 *   ↑topLine                ↑endX     ↑startX
 * ```
 *
 * 跳房子位于**谱表顶线上方**约 1 个谱线间距处，由三部分组成：
 * 1. **水平线段**：贯穿一个或多个小节宽度（≥ 2 个谱线间距），1~2px 粗
 * 2. **左竖钩**：从水平线左端向下延伸约 0.5~1 个谱线间距（**必有**，最可靠特征）
 * 3. **右竖钩**：从水平线右端向下延伸（闭合结尾必有；最后一组结尾可能开口）
 * 4. **序号**：左钩上方的数字 + 句点（1. / 2. / 3.）
 *
 * ## 检测原理
 *
 * 在**含谱线**的二值图上（去谱线前的 `warped` 图），在谱表顶线上方的搜索带内：
 *
 * 1. **水平线段检测**：逐行寻找最长水平黑色游程 ≥ 2 个谱线间距的行。谱线本身位于
 *    顶线**之下**（不在搜索带内），故不会被误判。
 * 2. **分组**：竖直相邻且水平重叠的长游程归为同一括号（括号线虽细，抗锯齿可能占 2 行）。
 *    排除过厚的组（> 0.6 间距 → 可能是其它水平结构）。
 * 3. **竖钩验证**：必须存在**左竖钩**（从括号线下方开始的竖直黑色游程 ≥ 0.4 间距）；
 *    此外要求**右竖钩**或括号长度 ≥ 3 间距（兼容开口跳房子），二者满足其一。
 *    ——竖钩要求是区分「真跳房子」与「恰好水平的其它线（如平缓连音线）」的关键。
 * 4. **序号识别**：左钩上方区域的黑色墨迹边界框降采样到 5×7 网格，与内置 0-9 模板
 *    做汉明距离匹配（复用 [SignatureDetector.DIGIT_TEMPLATES]）。匹配失败时默认 1。
 *
 * 全程只读取 [BinaryImage] 像素，不修改任何状态。
 *
 * @see BarlineDetector 小节线/反复记号竖线检测
 */
object VoltaDetector {

    /**
     * 在含谱线的二值图上检测反复跳房子括号。
     *
     * @param image        含谱线的二值图（去谱线前的原图）。
     * @param system       当前谱表系统。
     * @param upperLimit   搜索区域的上界 Y 坐标。跳房子只在此 Y 与 `system.topLine` 之间
     *                     寻找。多系统页面应传入上一个系统的底线（避免把上方谱表的
     *                     内容/谱线误判）；第一个系统传 0（图像顶部）。默认 0。
     * @param signatureEndX 签名区（谱号/调号/拍号）右边缘 X。起始位置在此左侧的长水平
     *                     游程被跳过（排除签名区内的内容）。默认 0（不跳过）。
     * @return 跳房子列表，按 startX 排序。
     */
    fun detect(
        image: BinaryImage,
        system: StaffSystem,
        upperLimit: Int = 0,
        signatureEndX: Int = 0
    ): List<Volta> {
        val s = system.lineSpacing
        if (s <= 0) return emptyList()

        val topLineY = system.topLine.center
        // 搜索带：顶线上方约 2 个谱线间距，且不低于 upperLimit。
        val yBot = topLineY - 1
        val yTop = maxOf(upperLimit, topLineY - 2 * s)
        if (yBot < yTop) return emptyList()

        val minBracketW = (2 * s).coerceAtLeast(6)
        val fromX = signatureEndX.coerceIn(0, image.width)

        // ---- 1. 在搜索带内收集长水平游程 ----
        // 每条记录：(y, startX, endX)
        data class Run(val y: Int, val startX: Int, val endX: Int) {
            val width: Int get() = endX - startX + 1
        }
        val runs = ArrayList<Run>()
        for (y in yTop..yBot) {
            var x = fromX
            while (x < image.width) {
                if (!image.isBlack(x, y)) { x++; continue }
                val runStart = x
                while (x < image.width && image.isBlack(x, y)) x++
                val runEnd = x - 1
                if (runEnd - runStart + 1 >= minBracketW) {
                    runs += Run(y, runStart, runEnd)
                }
            }
        }
        if (runs.isEmpty()) return emptyList()

        // ---- 2. 竖直相邻 + 水平重叠的游程归为同一括号 ----
        val sorted = runs.sortedWith(compareBy({ it.y }, { it.startX }))
        val groups = ArrayList<MutableList<Run>>()
        for (run in sorted) {
            val merged = groups.lastOrNull()?.let { g ->
                val lastY = g.maxOf { it.y }
                val gMinX = g.minOf { it.startX }
                val gMaxX = g.maxOf { it.endX }
                // 与组中任一游程竖直相邻（≤2 行间隔）且水平重叠
                run.y - lastY <= 2 && run.startX <= gMaxX + 1 && run.endX >= gMinX - 1
            } ?: false
            if (merged) {
                groups.last() += run
            } else {
                groups += mutableListOf(run)
            }
        }

        // ---- 3. 对每个括号候选：验证竖钩 + 识别序号 ----
        val maxGroupHeight = (0.6 * s).toInt().coerceAtLeast(2)
        val hookMin = (0.4 * s).toInt().coerceAtLeast(2)
        val longBracketMin = (3 * s).coerceAtLeast(8)
        val result = ArrayList<Volta>()

        for (g in groups) {
            val bracketY = g.first().y  // 游程都竖直相邻，取首行作为括号线 Y
            val groupHeight = g.maxOf { it.y } - g.minOf { it.y } + 1
            if (groupHeight > maxGroupHeight) continue  // 排除过厚的水平结构

            val startX = g.minOf { it.startX }
            val endX = g.maxOf { it.endX }
            val bracketLen = endX - startX + 1

            val leftHook = verticalRunDown(image, startX, bracketY, topLineY)
            val rightHook = verticalRunDown(image, endX, bracketY, topLineY)
            val hasLeftHook = leftHook >= hookMin
            val hasRightHook = rightHook >= hookMin

            // 必须有左竖钩；且（有右竖钩 或 括号足够长以兼容开口跳房子）
            if (!hasLeftHook) continue
            if (!hasRightHook && bracketLen < longBracketMin) continue

            val number = detectNumber(image, startX, bracketY, s)
            result += Volta(startX, endX, number, bracketY)
        }

        return result.sortedBy { it.startX }
    }

    /**
     * 从 `(x, fromY+1)` 向下扫描竖直黑色游程长度（带 ±1 列容差，应对 1~2px 粗的钩）。
     * 到达 [limitY]（含）即止。返回最长连续黑色段的竖直跨度。
     */
    private fun verticalRunDown(image: BinaryImage, x: Int, fromY: Int, limitY: Int): Int {
        if (x < 0 || x >= image.width) return 0
        var best = 0
        for (dx in -1..1) {
            val cx = x + dx
            if (cx < 0 || cx >= image.width) continue
            var run = 0
            var y = fromY + 1
            while (y <= limitY && image.isBlack(cx, y)) { run++; y++ }
            if (run > best) best = run
        }
        return best
    }

    /**
     * 在左钩上方区域识别跳房子序号。
     *
     * 搜索区：`x ∈ [startX - 0.5s, startX + 2.5s]`，`y ∈ [bracketY - 2.5s, bracketY - 2]`
     * （位于括号线上方、左钩右侧，天然排除括号线本身与向下竖钩）。
     *
     * 区内可能有数字字形和句点「.」（标准记谱 1. / 2.）两个分离的连通块。句点会
     * 拉宽数字边界框、打乱 5×7 降采样网格对齐。因此先在区内做连通块标记，取**最大**
     * 连通块（=数字字形，句点远小于数字）的边界框，降采样到 5×7 网格后与
     * [SignatureDetector.DIGIT_TEMPLATES] 做汉明距离匹配。匹配置信度不足时返回 [DEFAULT_NUMBER]。
     */
    private fun detectNumber(image: BinaryImage, startX: Int, bracketY: Int, s: Int): Int {
        val x0 = (startX - 0.5 * s).toInt().coerceAtLeast(0)
        val x1 = (startX + 2.5 * s).toInt().coerceAtMost(image.width - 1)
        // 区域底界留在括号线上方 2px（排除括号线本身与向下竖钩，即使括号线为 2px 粗）
        val y0 = (bracketY - 2.5 * s).toInt().coerceAtLeast(0)
        val y1 = (bracketY - 2).coerceIn(y0, bracketY - 1)
        if (x1 < x0 || y1 < y0) return DEFAULT_NUMBER

        // 裁剪子图并做连通块标记，取最大连通块为数字字形（排除句点等小墨块）。
        val subW = x1 - x0 + 1
        val subH = y1 - y0 + 1
        val sub = BinaryImage(subW, subH, BooleanArray(subW * subH))
        for (yy in 0 until subH) for (xx in 0 until subW) {
            sub.pixels[yy * subW + xx] = image.isBlack(x0 + xx, y0 + yy)
        }
        val blobs = ConnectedComponents.label(sub, minPixels = 3)
        val digit = blobs.maxByOrNull { it.area } ?: return DEFAULT_NUMBER
        if (digit.width < 2 || digit.height < 3) return DEFAULT_NUMBER

        val grid = downsampleRegion(
            sub, digit.minX, digit.minY, digit.maxX, digit.maxY, GRID_W, GRID_H
        )
        return matchDigit(grid) ?: DEFAULT_NUMBER
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

    /**
     * 用汉明距离把 5×7 网格匹配到最近的数字模板。要求最近距离足够小、且与次近拉开
     * 差距（避免模糊误识）；不满足时返回 null。
     */
    private fun matchDigit(grid: BooleanArray): Int? {
        var bestDigit = -1
        var bestDist = Int.MAX_VALUE
        var secondDist = Int.MAX_VALUE
        for ((digit, tmpl) in SignatureDetector.DIGIT_TEMPLATES) {
            var d = 0
            for (i in grid.indices) if (grid[i] != tmpl[i]) d++
            if (d < bestDist) {
                secondDist = bestDist
                bestDist = d
                bestDigit = digit
            } else if (d < secondDist) {
                secondDist = d
            }
        }
        val maxAccept = (GRID_W * GRID_H * 0.30).toInt()
        if (bestDigit < 0 || bestDist > maxAccept) return null
        if (secondDist - bestDist < 2) return null  // 模糊，放弃
        return bestDigit
    }

    private const val GRID_W = 5
    private const val GRID_H = 7

    /** 未识别到序号时的默认结尾号（最常见的「第一结尾」）。 */
    private const val DEFAULT_NUMBER = 1
}
