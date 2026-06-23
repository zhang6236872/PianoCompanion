package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 八度记号(ottava / ottava bassa / quindicesima)检测器（纯 Kotlin，无 Android 依赖，
 * 完全可单元测试）。
 *
 * 八度记号是乐谱中用于指示演奏者将一段音符移高或移低一个/两个八度演奏的标记。
 * 它由两部分组成：
 *
 * 1. **文字标记**：数字 **"8"**（ottava，一个八度）或 **"15"**（quindicesima，两个八度），
 *    后面常跟小写字母缩写（如 **8va** = ottava alta、**8vb** = ottava bassa、
 *    **15ma** = quindicesima alta、**15mb** = quindicesima bassa）。
 * 2. **虚线/点线**：从文字标记向右延伸的水平虚线，标记八度移位的作用范围。虚线末端
 *    常有一个小竖钩或 \"loco\" 字样表示移位结束。
 *
 * ## 方向判定
 *
 * - 标记在谱表**上方** → 向上移位（8va = +12 半音、15ma = +24 半音）
 * - 标记在谱表**下方** → 向下移位（8vb = -12 半音、15mb = -24 半音）
 *
 * 方向完全由竖直位置决定——\"va\" 与 \"vb\" 字母只是冗余标记，不必区分。
 * 上方的 \"8\" 一定是 8va（升高），下方的 \"8\" 一定是 8vb（降低）。
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：在每个谱表系统的顶线上方（0.5~4.0 个谱线间距）和底线下方
 *    （0.5~4.0 个谱线间距）分别搜索文字类墨块。
 * 2. **数字识别**：复用 [SignatureDetector.classifyDigit] 将候选墨块降采样到 5×7 网格后
 *    与 0-9 数字模板做汉明距离匹配。先找 \"8\"；再检查其左侧是否有紧邻的 \"1\"
 *    （若 \"1\" + \"5\" + \"ma\" 中的 \"5\" 紧邻，则构成 \"15\"）。
 *    为简化检测，此处识别 \"8\" 为一个八度；若 \"8\" 的左侧有紧邻的 \"1\" 或其自身
 *    通过 \"1\"+\"5\" 序列被识别，则判定为两个八度（15ma/15mb）。
 * 3. **虚线确认**：从文字标记右边缘开始，向右扫描水平方向上的虚线/点线。
 *    八度记号的虚线是周期性出现的短水平线段（dashes），具有间断的墨迹分布。
 *    要求虚线跨度 ≥ 1.5 个谱线间距且列覆盖率在 10%~85% 之间
 *    （覆盖率 >85% 表明是实心水平线而非虚线；<10% 表明是噪声）。
 * 4. **作用范围**：虚线的起始 X 到终止 X 定义八度移位作用的音符范围。
 *
 * ## 与其他符号的区分
 *
 * - **指法数字**：指法数字标注在符头旁边（谱线间），不在谱表上方/下方 0.5 间距以外，
 *   且没有虚线延伸。
 * - **拍号数字**：位于签名区内（谱线之间），不会出现在谱表上方/下方。
 * - **力度记号字母**：在谱表下方搜索，但力度记号是字母（p/m/f 等）而非数字。
 * - **小节号**：通常在谱表上方，但小节号是数字而非 \"8\"+虚线组合，且没有虚线延伸。
 * - **跳房子序号**：位于方括号内，有序号方括号的结构，且通常较小。
 *
 * 八度记号**修改音符数据模型**——被移位范围内的音符的 MIDI 音高会被相应调整。
 */
object OctavaDetector {

    /**
     * 检测到的八度移位标记。
     *
     * @param systemIdx 所属谱表系统索引。
     * @param startX    文字标记中心的 X 坐标。
     * @param endX      虚线终止的 X 坐标（即八度移位作用的右边界）。
     * @param semitones 移位的半音数：+12（8va）、-12（8vb）、+24（15ma）、-24（15mb）。
     * @param direction ABOVE（上方，移高）或 BELOW（下方，移低）。
     * @param octaves   八度数：1（ottava）或 2（quindicesima）。
     */
    data class OctavaShift(
        val systemIdx: Int,
        val startX: Int,
        val endX: Int,
        val semitones: Int,
        val direction: OctavaDirection,
        val octaves: Int
    )

    /**
     * 八度记号方向。
     * - [ABOVE] 谱表上方 → 音高升高
     * - [BELOW] 谱表下方 → 音高降低
     */
    enum class OctavaDirection { ABOVE, BELOW }

    // ---- 尺寸约束（谱线间距倍数） -------------------------------------------

    /** 搜索区域起始间隙：从谱表线向外偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向外搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 4.0

    /** 候选数字墨块最大宽度。 */
    private const val MAX_BLOB_WIDTH_FRAC = 1.5

    /** 候选数字墨块最大高度。 */
    private const val MAX_BLOB_HEIGHT_FRAC = 1.5

    /** 候选数字墨块最小高度。 */
    private const val MIN_BLOB_HEIGHT_FRAC = 0.4

    /** 候选数字墨块最小宽度。 */
    private const val MIN_BLOB_WIDTH_FRAC = 0.3

    /** \"15\" 中 \"1\" 和 \"5\" 之间的最大水平间距（谱线间距倍数）。 */
    private const val TWO_DIGIT_GAP_FRAC = 0.4

    /** 虚线检测：最小跨度（谱线间距倍数）。 */
    private const val DASHED_MIN_SPAN_FRAC = 1.5

    /** 虚线检测：搜索向右延伸的最大距离（谱线间距倍数）。 */
    private const val DASHED_SEARCH_EXTENT_FRAC = 40.0

    /** 虚线检测：垂直搜索带的半高（谱线间距倍数，围绕标记 Y 中心）。 */
    private const val DASHED_HALF_HEIGHT_FRAC = 0.4

    /** 虚线检测：列覆盖率下限（低于此值表明噪声而非虚线）。 */
    private const val DASHED_MIN_COVERAGE = 0.10

    /** 虚线检测：列覆盖率上限（高于此值表明实心线而非虚线）。 */
    private const val DASHED_MAX_COVERAGE = 0.85

    /** 虚线扫描中允许的连续无墨列数（超过则认为虚线已终止）。 */
    private const val DASHED_MAX_BLANK_RUN = 3

    /**
     * 检测每个谱表系统上方和下方的八度记号。
     *
     * @param image       去谱线+降噪后的二值图像。
     * @param blobs       连通块列表（与 image 一致的坐标系）。
     * @param systems     谱表系统列表。
     * @param lineSpacing 平均谱线间距。
     * @return 检测到的八度移位列表（按系统索引、X 排序）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<OctavaShift> {
        if (lineSpacing < 1 || systems.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<OctavaShift>()

        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(2)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()
        val maxBlobW = (MAX_BLOB_WIDTH_FRAC * s).toInt()
        val maxBlobH = (MAX_BLOB_HEIGHT_FRAC * s).toInt()
        val minBlobW = (MIN_BLOB_WIDTH_FRAC * s).toInt().coerceAtLeast(2)
        val minBlobH = (MIN_BLOB_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val twoDigitGap = (TWO_DIGIT_GAP_FRAC * s).toInt().coerceAtLeast(2)

        systems.forEachIndexed { sysIdx, system ->
            // ---- 上方搜索（8va / 15ma） ----
            val aboveBottom = system.topLine.center - searchGap
            val aboveTop = (aboveBottom - searchRange).coerceAtLeast(0)
            if (aboveBottom > aboveTop) {
                detectOctavaInZone(
                    image, blobs, sysIdx, aboveTop, aboveBottom,
                    maxBlobW, maxBlobH, minBlobW, minBlobH,
                    twoDigitGap, lineSpacing,
                    OctavaDirection.ABOVE, s, results
                )
            }

            // ---- 下方搜索（8vb / 15mb） ----
            val belowTop = system.bottomLine.center + searchGap
            val belowBottom = (belowTop + searchRange).coerceAtMost(image.height - 1)
            if (belowBottom > belowTop) {
                detectOctavaInZone(
                    image, blobs, sysIdx, belowTop, belowBottom,
                    maxBlobW, maxBlobH, minBlobW, minBlobH,
                    twoDigitGap, lineSpacing,
                    OctavaDirection.BELOW, s, results
                )
            }
        }

        return results.sortedWith(compareBy({ it.systemIdx }, { it.startX }))
    }

    /**
     * 在一个竖直搜索区域内检测八度记号。
     */
    private fun detectOctavaInZone(
        image: BinaryImage,
        blobs: List<Blob>,
        sysIdx: Int,
        zoneTop: Int,
        zoneBottom: Int,
        maxBlobW: Int,
        maxBlobH: Int,
        minBlobW: Int,
        minBlobH: Int,
        twoDigitGap: Int,
        lineSpacing: Int,
        direction: OctavaDirection,
        s: Double,
        results: MutableList<OctavaShift>
    ) {
        // 过滤搜索区域内的候选墨块：尺寸合理的数字字形。
        val candidates = blobs.filter { blob ->
            blob.centerY in zoneTop..zoneBottom &&
                blob.minY >= zoneTop - maxBlobH / 2 &&
                blob.maxY <= zoneBottom + maxBlobH / 2 &&
                blob.width in minBlobW..maxBlobW &&
                blob.height in minBlobH..maxBlobH
        }.sortedBy { it.minX }

        if (candidates.isEmpty()) return

        for (blob in candidates) {
            // 识别这个墨块是什么数字。
            val digit = SignatureDetector.classifyDigit(image, blob) ?: continue

            if (digit == 8) {
                // 检查左侧是否有紧邻的 "1"（构成 "18"，即 "15" 的近似——实际上是 "15" 被识别时
                // "5" 被识别为 "8" 的概率很低，所以此处直接判断：如果左侧紧邻 "1"，尝试在 "1"
                // 右侧找 "5" 而非 "8"。但更简单的逻辑是：找到 "8" 就是 1 个八度。
                // 对于 "15"：需要找 "1" + "5" 序列。
                processOctavaCandidate(
                    image, blob, 1, sysIdx, direction, s, lineSpacing,
                    candidates, twoDigitGap, results
                )
            } else if (digit == 5) {
                // "5" 可能是 "15" 的一部分。检查左侧是否有紧邻的 "1"。
                val oneBlob = candidates.find { other ->
                    other !== blob &&
                        other.centerY >= blob.minY - maxBlobH &&
                        other.centerY <= blob.maxY + maxBlobH &&
                        abs(other.centerX - blob.centerX) <= maxBlobW + twoDigitGap &&
                        other.maxX < blob.minX &&
                        blob.minX - other.maxX <= twoDigitGap
                }
                if (oneBlob != null) {
                    val oneDigit = SignatureDetector.classifyDigit(image, oneBlob)
                    if (oneDigit == 1) {
                        // 确认是 "15" → 2 个八度
                        val combinedBlob = Blob(
                            label = -1, area = 0,
                            minX = minOf(oneBlob.minX, blob.minX),
                            maxX = blob.maxX,
                            minY = minOf(oneBlob.minY, blob.minY),
                            maxY = maxOf(oneBlob.maxY, blob.maxY)
                        )
                        processOctavaCandidate(
                            image, combinedBlob, 2, sysIdx, direction, s, lineSpacing,
                            candidates, twoDigitGap, results
                        )
                    }
                }
            }
        }
    }

    /**
     * 对一个已确认的八度记号文字标记进行虚线检测并创建 [OctavaShift]。
     *
     * 必须检测到虚线/点线延伸才确认是八度记号（虚线是八度记号的标志性特征，
     * 区别于指法数字等其他数字标注）。
     */
    private fun processOctavaCandidate(
        image: BinaryImage,
        digitBlob: Blob,
        octaves: Int,
        sysIdx: Int,
        direction: OctavaDirection,
        s: Double,
        lineSpacing: Int,
        allCandidates: List<Blob>,
        twoDigitGap: Int,
        results: MutableList<OctavaShift>
    ) {
        val markerCenterX = digitBlob.centerX
        val markerCenterY = digitBlob.centerY
        val markerRightX = digitBlob.maxX

        // 跳过已经处理过的标记（避免 "15" 被重复处理为 "1" + "5" 各一次）。
        if (results.any { it.systemIdx == sysIdx && abs(it.startX - markerCenterX) <= lineSpacing }) {
            return
        }

        // 检测虚线延伸
        val dashedEnd = findDashedLineExtent(
            image, markerRightX + 1, markerCenterY, lineSpacing, s
        )

        if (dashedEnd == null) {
            // 没有检测到虚线——不是八度记号（可能只是指法数字或其他数字）
            return
        }

        val semitones = if (direction == OctavaDirection.ABOVE) {
            12 * octaves
        } else {
            -12 * octaves
        }

        results += OctavaShift(
            systemIdx = sysIdx,
            startX = markerCenterX,
            endX = dashedEnd,
            semitones = semitones,
            direction = direction,
            octaves = octaves
        )
    }

    /**
     * 从指定 X 位置向右扫描虚线/点线，返回虚线终止的 X 坐标。
     *
     * 八度记号的虚线是周期性的短水平线段，在像素层面表现为：
     * - 某些列有黑像素（dashes）
     * - 某些列全白（gaps）
     * - 整体覆盖一端水平距离
     *
     * 检测方法：在 [startX, startX + extent] 范围内，逐列检查垂直搜索带
     * [yCenter - halfH, yCenter + halfH] 是否有黑像素。统计有墨列的比例和跨度。
     * 当连续无墨列数超过 [DASHED_MAX_BLANK_RUN] 时认为虚线终止。
     *
     * @return 虚线最后一个有墨列的 X 坐标；如果没有有效虚线则返回 null。
     */
    private fun findDashedLineExtent(
        image: BinaryImage,
        startX: Int,
        yCenter: Int,
        lineSpacing: Int,
        s: Double
    ): Int? {
        val halfH = (DASHED_HALF_HEIGHT_FRAC * s).toInt().coerceAtLeast(2)
        val extent = (DASHED_SEARCH_EXTENT_FRAC * s).toInt()
        val minSpan = (DASHED_MIN_SPAN_FRAC * s).toInt()

        val xEnd = (startX + extent).coerceAtMost(image.width - 1)
        if (xEnd <= startX) return null

        var columnsWithInk = 0
        var firstInkX = -1
        var lastInkX = -1
        var blankRun = 0

        for (x in startX..xEnd) {
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
                blankRun = 0
            } else {
                blankRun++
                // 连续无墨列超过阈值 → 虚线终止
                if (firstInkX >= 0 && blankRun > DASHED_MAX_BLANK_RUN) {
                    break
                }
            }
        }

        if (columnsWithInk == 0 || firstInkX < 0) return null

        // 跨度检查：虚线必须至少延伸 minSpan 像素
        val span = lastInkX - firstInkX + 1
        if (span < minSpan) return null

        // 覆盖率检查：虚线的列覆盖率应在合理范围内
        val totalColumnsScanned = lastInkX - startX + 1
        if (totalColumnsScanned <= 0) return null
        val coverage = columnsWithInk.toDouble() / totalColumnsScanned
        if (coverage < DASHED_MIN_COVERAGE || coverage > DASHED_MAX_COVERAGE) return null

        return lastInkX
    }

    /**
     * 给定一个音符头 X 坐标和系统索引，返回该音符应应用的八度移位半音数。
     * 如果音符不在任何八度记号范围内，返回 0。
     *
     * @param shifts   已检测到的八度移位列表。
     * @param systemIdx 音符头所属的系统索引。
     * @param noteX    音符头的 X 坐标。
     * @return 半音移位量（+12, -12, +24, -24, 或 0）。
     */
    fun semitoneShiftForNote(
        shifts: List<OctavaShift>,
        systemIdx: Int,
        noteX: Int
    ): Int {
        return shifts
            .filter { it.systemIdx == systemIdx && noteX in it.startX..it.endX }
            .sumOf { it.semitones }
    }
}
