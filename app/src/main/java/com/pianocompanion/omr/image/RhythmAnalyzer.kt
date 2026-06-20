package com.pianocompanion.omr.image

/**
 * 音符时值，以四分音符为 1 个单位。
 *
 * 时值倍数用于把"一个四分音符的毫秒数"换算成该音符的实际持续时间，
 * 这样 OMR 识别出的节奏不再是清一色的四分音符。
 */
enum class NoteDuration(val quarterValue: Double, val label: String) {
    WHOLE(4.0, "全音符"),
    HALF(2.0, "二分音符"),
    QUARTER(1.0, "四分音符"),
    EIGHTH(0.5, "八分音符"),
    SIXTEENTH(0.25, "十六分音符"),
    THIRTY_SECOND(0.125, "三十二分音符");

    /**
     * 把四分音符毫秒数换算为本时值的毫秒数（至少 1ms）。
     *
     * [dotCount] 为附点个数（标准记谱法最多两个附点）：每个附点在原有时值基础上
     * 再叠加前一个时值的一半。
     *   - 0 附点 → ×1.0
     *   - 1 附点 → ×1.5
     *   - 2 附点 → ×1.75
     */
    fun toMillis(quarterMs: Long, dotCount: Int = 0): Long {
        val multiplier = if (dotCount <= 0) 1.0 else (2.0 - Math.pow(0.5, dotCount.toDouble()))
        return (quarterValue * multiplier * quarterMs).toLong().coerceAtLeast(1L)
    }
}

/**
 * 单个符头的节奏特征分析结果。
 *
 * @param filled     实心(true) / 空心(false)。
 * @param hasStem    是否检测到符干。
 * @param stemUp     符干方向：true=向上，false=向下。
 * @param stemEndX   符干远端 x 坐标（用于横梁/符尾检测）。
 * @param stemEndY   符干远端 y 坐标。
 * @param beamCount  连接该符干的横梁层数（0=无横梁）。
 * @param flagCount  符干末端的符尾层数（0=无符尾）。
 * @param dotCount   右侧附点个数（0=无附点；标准记谱最多 2 个）。
 * @param duration   综合判定的（基础）时值（不含附点倍率，附点在 [effectiveMillis] 中叠加）。
 */
data class RhythmFeatures(
    val filled: Boolean,
    val hasStem: Boolean,
    val stemUp: Boolean,
    val stemEndX: Int,
    val stemEndY: Int,
    val beamCount: Int,
    val flagCount: Int,
    val dotCount: Int = 0,
    val duration: NoteDuration
) {
    /** 横梁与符尾中较大的"尾巴"层数，决定八分/十六分等短时值。 */
    val tailCount: Int get() = maxOf(beamCount, flagCount)

    /** 是否带附点。 */
    val dotted: Boolean get() = dotCount > 0

    /**
     * 含附点倍率的实际持续毫秒数（四分音符毫秒数 [quarterMs]）。
     * 1 附点 ×1.5、2 附点 ×1.75；无附点即基础时值。
     */
    fun effectiveMillis(quarterMs: Long): Long = duration.toMillis(quarterMs, dotCount)
}

/**
 * 纯 Kotlin 节奏分析器：在去谱线后的二值图上，围绕每个已定位的符头，
 * 通过几何扫描依次判定
 *
 *   填充状态(实心/空心) → 符干 → 横梁/符尾 → 时值。
 *
 * 全程只读取 [BinaryImage] 像素，**不依赖连通块拓扑**，因此即便符干与
 * 符头融合在同一个连通块中，只要符头位置已知，仍能正确分析节奏。
 *
 * 分类规则（标准五线谱记谱法）：
 *   - 空心 + 无干        → 全音符 (4.0)
 *   - 空心 + 有干        → 二分音符 (2.0)
 *   - 实心 + 有干 + 0 尾 → 四分音符 (1.0)
 *   - 实心 + 有干 + 1 尾 → 八分音符 (0.5)
 *   - 实心 + 有干 + 2 尾 → 十六分音符 (0.25)
 *   - 实心 + 有干 + 3 尾 → 三十二分音符 (0.125)
 *   - 实心 + 无干        → 保守按四分音符 (符干漏检时不会过长)
 */
object RhythmAnalyzer {

    /**
     * @param image       去谱线后的二值图。
     * @param noteheads   已检测到的符头列表。
     * @param lineSpacing 五线谱线间距（像素），用于按比例缩放阈值。
     * @return 与 [noteheads] 顺序一一对应的节奏特征。
     */
    fun analyze(
        image: BinaryImage,
        noteheads: List<Notehead>,
        lineSpacing: Int
    ): List<RhythmFeatures> {
        if (noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.coerceAtLeast(1).toDouble()

        // 1) 填充状态 + 符干
        val base = noteheads.map { nh ->
            val filled = isFilled(image, nh)
            val stem = detectStem(image, nh, s)
            RhythmFeatures(
                filled = filled,
                hasStem = stem.found,
                stemUp = stem.up,
                stemEndX = stem.endX,
                stemEndY = stem.endY,
                beamCount = 0,
                flagCount = 0,
                duration = NoteDuration.QUARTER
            )
        }

        // 2) 横梁检测（成组符干末端之间的水平黑色连线）
        val beamCounts = detectBeamCounts(image, noteheads, base, s)

        // 3) 符尾检测（仅对无横梁的带干符头）+ 附点检测 + 综合分类
        return base.mapIndexed { idx, f ->
            val beams = beamCounts[idx]
            val flags = if (!f.hasStem || beams > 0) 0 else detectFlags(image, noteheads[idx], f, s)
            val dots = countAugmentationDots(image, noteheads[idx], s)
            f.copy(
                beamCount = beams,
                flagCount = flags,
                dotCount = dots,
                duration = classify(f.filled, f.hasStem, maxOf(beams, flags))
            )
        }
    }

    /**
     * 公开的纯分类函数，便于单元测试直接验证判定逻辑。
     */
    fun classify(filled: Boolean, hasStem: Boolean, tailCount: Int): NoteDuration = when {
        !hasStem && !filled -> NoteDuration.WHOLE
        !hasStem -> NoteDuration.QUARTER
        !filled -> NoteDuration.HALF
        tailCount >= 3 -> NoteDuration.THIRTY_SECOND
        tailCount == 2 -> NoteDuration.SIXTEENTH
        tailCount == 1 -> NoteDuration.EIGHTH
        else -> NoteDuration.QUARTER
    }

    // ---- 填充判定 -------------------------------------------------------------

    /**
     * 实心符头内部几乎全黑；空心符头(环状)中心为白。
     * 取符头中心约 50% 区域的黑像素占比判定（阈值 0.5）。
     */
    private fun isFilled(image: BinaryImage, nh: Notehead): Boolean {
        val iw = (nh.width * 0.5).toInt().coerceAtLeast(2)
        val ih = (nh.height * 0.5).toInt().coerceAtLeast(2)
        val x0 = nh.centerX - iw / 2
        val y0 = nh.centerY - ih / 2
        var black = 0
        var total = 0
        for (y in y0 until y0 + ih) {
            for (x in x0 until x0 + iw) {
                if (image.isBlack(x, y)) black++
                total++
            }
        }
        if (total == 0) return true
        return black.toDouble() / total >= 0.5
    }

    // ---- 符干判定 -------------------------------------------------------------

    private data class Stem(val found: Boolean, val up: Boolean, val endX: Int, val endY: Int)

    private val NO_STEM = Stem(found = false, up = true, endX = 0, endY = 0)

    /**
     * 在符头左右两侧向上/向下扫描垂直黑线长度。
     * 符干通常 ≥ 1.8 个谱线间距；返回最长的一根（含其方向与远端坐标）。
     */
    private fun detectStem(image: BinaryImage, nh: Notehead, s: Double): Stem {
        val minLen = (1.8 * s).toInt().coerceAtLeast(4)
        val xL = nh.centerX - nh.width / 2
        val xR = nh.centerX + nh.width / 2
        val yT = nh.centerY - nh.height / 2
        val yB = nh.centerY + nh.height / 2

        var bestLen = 0
        var bestUp = true
        var bestX = xR
        var bestEndY = yT

        // 向上扫描（右侧、左侧，各带 1px 容差）
        for (x in intArrayOf(xR, xR - 1, xL, xL + 1)) {
            val len = verticalRun(image, x, yT - 1, -1)
            if (len > bestLen) {
                bestLen = len; bestUp = true; bestX = x; bestEndY = yT - len
            }
        }
        // 向下扫描
        for (x in intArrayOf(xR, xR - 1, xL, xL + 1)) {
            val len = verticalRun(image, x, yB + 1, +1)
            if (len > bestLen) {
                bestLen = len; bestUp = false; bestX = x; bestEndY = yB + len
            }
        }

        return if (bestLen >= minLen) Stem(true, bestUp, bestX, bestEndY) else NO_STEM
    }

    /**
     * 从 [startY] 沿 [dir] 方向扫描，返回连续（允许 1 像素间断）的黑线长度。
     * 一行视为"有墨"当该列及其 ±1 邻列任一为黑。
     */
    private fun verticalRun(image: BinaryImage, x: Int, startY: Int, dir: Int): Int {
        var len = 0
        var gap = 0
        var y = startY
        while (y in 0 until image.height) {
            val ink = image.isBlack(x - 1, y) || image.isBlack(x, y) || image.isBlack(x + 1, y)
            if (ink) {
                len++; gap = 0
            } else {
                gap++
                if (gap > 1) break
            }
            y += dir
        }
        return len
    }

    // ---- 横梁判定 -------------------------------------------------------------

    /**
     * 对按 x 排序后相邻、符干同向的符头，检查符干末端之间是否存在水平黑色连线。
     * 连通的符头编为一组，整组共享相同的横梁层数。
     *
     * @return 与 [noteheads] 顺序对齐的横梁层数数组。
     */
    private fun detectBeamCounts(
        image: BinaryImage,
        noteheads: List<Notehead>,
        features: List<RhythmFeatures>,
        s: Double
    ): IntArray {
        val n = noteheads.size
        val counts = IntArray(n)
        if (n < 2) return counts

        val order = noteheads.indices.sortedBy { noteheads[it].centerX }
        val processed = BooleanArray(n)
        var i = 0
        while (i < order.size) {
            val a = order[i]
            if (!features[a].hasStem || processed[a]) {
                i++; continue
            }
            // 收集连续同向、末端相连的符头
            val group = ArrayList<Int>()
            group += a
            var j = i + 1
            while (j < order.size) {
                val b = order[j]
                val prev = group.last()
                if (!features[b].hasStem) break
                if (features[b].stemUp != features[prev].stemUp) break
                if (!beamsConnect(image, noteheads[prev], features[prev], noteheads[b], features[b], s)) break
                group += b
                j++
            }
            if (group.size >= 2) {
                val layers = countBeamLayers(image, group, noteheads, features, s)
                group.forEach { idx ->
                    counts[idx] = layers
                    processed[idx] = true
                }
                i = j
            } else {
                i++
            }
        }
        return counts
    }

    /** 两个符干末端之间、在末端 y 附近，是否存在足够长的水平黑色连线（≥60% 连通）。 */
    private fun beamsConnect(
        image: BinaryImage,
        a: Notehead, fa: RhythmFeatures,
        b: Notehead, fb: RhythmFeatures,
        s: Double
    ): Boolean {
        val xa = a.centerX
        val xb = b.centerX
        if (xb <= xa) return false
        val ya = fa.stemEndY
        val yb = fb.stemEndY
        val tol = (0.4 * s).toInt().coerceAtLeast(1)
        var connected = 0
        for (x in (xa + 1) until xb) {
            val yt = (ya + (yb - ya).toDouble() * (x - xa) / (xb - xa)).toInt()
            val ink = (yt - tol..yt + tol).any { image.isBlack(x, it) }
            if (ink) connected++
        }
        val span = (xb - xa - 1).coerceAtLeast(1)
        return connected.toDouble() / span >= 0.6
    }

    /**
     * 在一组连梁符头中，取前两个符干之间的间隙中点 x，纵向扫描堆叠的横梁层数。
     * 取间隙而非符干正上方，可避免符干本身被计入。
     */
    private fun countBeamLayers(
        image: BinaryImage,
        group: List<Int>,
        noteheads: List<Notehead>,
        features: List<RhythmFeatures>,
        s: Double
    ): Int {
        if (group.size < 2) return 0
        val xa = noteheads[group[0]].centerX
        val xb = noteheads[group[1]].centerX
        val midX = (xa + xb) / 2
        val ys = group.map { features[it].stemEndY }
        val yMin = (ys.min() - (0.6 * s).toInt()).coerceAtLeast(0)
        val yMax = (ys.max() + (0.6 * s).toInt())
        val halfWin = (0.25 * s).toInt().coerceAtLeast(1)
        return countVerticalBands(image, midX, yMin, yMax, halfWin)
    }

    // ---- 符尾判定（无横梁的带干符头）-----------------------------------------

    /**
     * 在符干末端朝远离符头的一侧扫描符尾（flag）。
     *
     * 符尾是附着在符干末端、向外(水平)卷曲的墨迹；多个符尾沿符干方向堆叠，
     * 彼此之间有间隙（八分=1 个、十六分=2 个、三十二分=3 个）。
     *
     * 与旧版\"单列纵向带计数\"不同，这里对符干末端附近的每一行计算\"从符干向外延伸的
     * 最长水平墨迹长度\"：裸符干只有约 1px 宽，不会超过阈值；而符尾会在所在行形成一条
     * 较长的水平墨迹。逐行统计后，被白行隔开的连续\"符尾行\"段即为符尾个数——这使单个
     * 卷曲的符尾即便在某一列恰好没有墨也能被正确识别为一个整体。
     *
     * 竖直扫描只覆盖符干末端到符头之间的区域（且不超过 2.2 个谱线间距），避免把符头
     * 误判为符尾；水平方向同时尝试左/右两侧并取较大值，兼容符尾向左或向右卷曲。
     */
    private fun detectFlags(image: BinaryImage, nh: Notehead, f: RhythmFeatures, s: Double): Int {
        val sx = f.stemEndX
        val sy = f.stemEndY
        val extent = (2.2 * s).toInt()
        val minFlagRun = (0.5 * s).toInt().coerceAtLeast(3)
        // 竖直扫描范围：up-stem 符尾在顶端之下；down-stem 符尾在底端之上；
        // 但都不得越过符头中心，避免把符头计入符尾区。
        val yCenter = nh.centerY
        val yStart: Int
        val yEnd: Int
        if (f.stemUp) {
            yStart = sy
            yEnd = minOf(sy + extent, yCenter - 1)
        } else {
            yEnd = sy
            yStart = maxOf(sy - extent, yCenter + 1)
        }
        return countFlagRows(image, sx, yStart, yEnd, minFlagRun)
    }

    /**
     * 逐行计算从 [stemX] 向左/右延伸的最长水平墨迹长度；连续超过 [minRun] 的行段
     * （允许 1 行间断，容忍抗锯齿噪声）计为一个符尾。
     */
    private fun countFlagRows(
        image: BinaryImage, stemX: Int, yStart: Int, yEnd: Int, minRun: Int
    ): Int {
        if (yEnd < yStart) return 0
        var flags = 0
        var inRun = false
        var gap = 0
        for (y in yStart..yEnd) {
            val reach = maxOf(
                horizontalRun(image, y, stemX, +1),
                horizontalRun(image, y, stemX, -1)
            )
            if (reach >= minRun) {
                inRun = true
                gap = 0
            } else if (inRun) {
                gap++
                if (gap > 1) {
                    flags++
                    inRun = false
                }
            }
        }
        if (inRun) flags++
        return flags.coerceIn(0, 3)
    }

    /**
     * 从 [startX] 沿 [dir] 方向扫描，返回连续水平墨迹长度。
     * 注意：此处只判定本像素 [image.isBlack]——不做纵向 ±1 容差。
     * 因为 [BinaryImage] 已是 Otsu 二值图（无抗锯齿），而符尾计数依赖\"逐行\"墨迹：
     * 若加入纵向容差，每个符尾的纵向影响区会上下各膨胀 1 行，使间距较近的堆叠符尾
     * （如十六分/三十二分）的影响区互相搭接、被误合并成单个符尾。
     */
    private fun horizontalRun(image: BinaryImage, y: Int, startX: Int, dir: Int): Int {
        var len = 0
        var x = startX
        while (x in 0 until image.width) {
            if (image.isBlack(x, y)) len++ else break
            x += dir
        }
        return len
    }

    // ---- 附点判定（augmentation dots）-----------------------------------------

    /**
     * 在符头右侧约 0.4–1.9 个谱线间距处扫描附点（小实心圆）。
     *
     * 为了把附点与右侧的下一个符头 / 符干区分开，每个连续墨列构成的墨块按下述
     * 规则归类：
     *  - **紧凑二维墨块**（水平宽度与竖直跨度都 ≤ [maxDotDim]）→ 计为一个附点；
     *  - **高而窄的墨块**（竖直跨度 > [maxDotDim]，即符干）→ 跳过，不计数也不停止；
     *  - **宽墨块**（水平宽度 > [maxDotDim]，即下一个符头）→ 停止扫描。
     *
     * 附点通常紧跟在符头右侧、在下一个符头之前，因此遇到宽墨块即意味着附点区结束。
     *
     * @return 附点个数（0–2）。
     */
    private fun countAugmentationDots(image: BinaryImage, nh: Notehead, s: Double): Int {
        val rightEdge = nh.centerX + nh.width / 2 + 1
        val xStart = (rightEdge + 0.35 * s).toInt()
        val xEnd = (rightEdge + 1.9 * s).toInt()
        if (xStart > xEnd) return 0
        val yCenter = nh.centerY
        // 附点可在符头正中(间内音符)或上下半个谱线间距处(线上音符)。
        val yHalf = (0.75 * s).toInt().coerceAtLeast(1)
        val maxDotDim = (0.8 * s).toInt().coerceAtLeast(2)

        var dots = 0
        var runStart = -1
        var runWidth = 0
        var stopped = false

        fun closeRun() {
            if (runStart < 0) return
            val w = runWidth
            if (w > maxDotDim) {
                // 宽墨块 = 下一个符头，结束扫描
                stopped = true
            } else if (w in 1..maxDotDim) {
                // 计算该墨块在竖直窗口内的跨度，排除高而窄的符干
                var yMin = Int.MAX_VALUE
                var yMax = Int.MIN_VALUE
                for (rx in runStart until runStart + w) {
                    for (dy in -yHalf..yHalf) {
                        if (image.isBlack(rx, yCenter + dy)) {
                            val yy = yCenter + dy
                            if (yy < yMin) yMin = yy
                            if (yy > yMax) yMax = yy
                        }
                    }
                }
                val vExtent = if (yMin <= yMax) yMax - yMin + 1 else 0
                if (vExtent in 1..maxDotDim) dots++
                // 否则高而窄(符干)：跳过，不计数也不停止
            }
            runStart = -1
            runWidth = 0
        }

        for (x in xStart..xEnd) {
            if (stopped) break
            val hasInk = (-yHalf..yHalf).any { dy -> image.isBlack(x, yCenter + dy) }
            if (hasInk) {
                if (runStart < 0) runStart = x
                runWidth++
            } else {
                closeRun()
            }
        }
        closeRun()
        return dots.coerceIn(0, 2)
    }

    /**
     * 在列 [x]（水平窗口半宽 [halfWin]）上，纵向扫描 [yMin..yMax]，
     * 统计被白色隔开的黑色"带"数量——对应横梁/符尾的堆叠层数。
     * 允许带内最多 1 行间断以容忍抗锯齿噪声。
     */
    private fun countVerticalBands(
        image: BinaryImage, x: Int, yMin: Int, yMax: Int, halfWin: Int
    ): Int {
        val lo = yMin.coerceIn(0, image.height - 1)
        val hi = yMax.coerceIn(0, image.height - 1)
        if (hi < lo) return 0
        var bands = 0
        var run = 0
        var gap = 0
        var inBand = false
        for (y in lo..hi) {
            val ink = (-halfWin..halfWin).any { dx -> image.isBlack(x + dx, y) }
            if (ink) {
                run++
                gap = 0
                if (!inBand) inBand = true
            } else {
                if (inBand) {
                    gap++
                    if (gap > 1) {
                        // 带结束
                        if (run >= 1) bands++
                        inBand = false
                        run = 0
                    }
                }
            }
        }
        if (inBand && run >= 1) bands++
        return bands
    }
}
