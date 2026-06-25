package com.pianocompanion.omr.image

/**
 * 速度记号(tempo marking)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 速度记号是乐谱上方标注的节拍速度，通常以 "♪ = 120" 或 "♩ = 90" 的形式出现
 * （一个音符符号 + 等号 + BPM 数字）。它指示演奏者每分钟演奏多少个该类型的拍子。
 *
 * 此前 OMR 管线完全使用外部传入的默认 tempo（120 BPM），无法从图像中识别实际
 * 速度——若乐谱标注 ♩ = 60 但管线按 120 BPM 计算，所有音符的时长都偏短一半，
 * 严重影响 score-following 的时间对齐和练习体验。
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：在第一个谱表系统的顶线上方 0.5~5.0 个谱线间距的区域内搜索
 *    （速度记号通常写在乐谱开头、第一行谱表的上方）。
 * 2. **水平分组**：将搜索区域内的连通块按水平邻近度分组为「文本行」。
 * 3. **数字识别**：将每个墨块降采样到 5×7 网格，复用 [SignatureDetector.DIGIT_TEMPLATES]
 *    做汉明距离匹配，识别 0-9 数字。
 * 4. **等号检测**：在数字序列的左侧搜索 "=" 符号——由两条平行水平线段（各自宽>高）
 *    组成。等号是速度记号的标志性特征，可与小节号、指法数字等纯数字区分。
 * 5. **BPM 组合**：将数字序列从左到右拼接为整数，验证范围 20~400。
 *
 * ## 与其他数字标注的区分
 *
 * - **拍号数字**：拍号在签名区（谱线之间），速度记号在谱线上方，搜索区域不重叠。
 * - **跳房子序号**：跳房子序号 1/2 在方括号上方，且没有等号前缀。
 * - **连音数字**：连音数字在音符组上方居中，没有等号前缀。
 * - **指法数字**：指法数字 1-5 紧贴符头，没有等号前缀。
 * - **小节号**：小节号在谱表左侧，没有等号前缀。
 * - **反复次数**：反复次数有乘号(×)前缀，没有等号。
 *
 * 等号("=")是唯一可靠的区分特征：只有速度记号会用 "等号 = 数字" 的格式。
 *
 * 检测到的速度会应用到 [com.pianocompanion.data.model.Score.tempo] 字段，
 * 影响所有音符时长计算。
 */
object TempoMarkingDetector {

    /**
     * 检测到的速度记号。
     *
     * @param bpm     每分钟拍数（Beats Per Minute）。
     * @param centerX 速度记号中心 X 坐标（用于信息追溯）。
     */
    data class TempoDetection(
        val bpm: Int,
        val centerX: Int
    )

    // ---- 搜索区域参数（谱线间距倍数） ---------------------------------------

    /** 搜索区域起始间隙：从谱表顶线向上偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.5

    /** 搜索区域范围（向上搜索多远）。 */
    private const val SEARCH_RANGE_FRAC = 5.0

    // ---- 候选墨块尺寸约束（谱线间距倍数） -----------------------------------

    /** 候选墨块最大宽度。 */
    private const val MAX_BLOB_WIDTH_FRAC = 3.0

    /** 候选墨块最大高度。 */
    private const val MAX_BLOB_HEIGHT_FRAC = 2.0

    // ---- 水平分组参数 -------------------------------------------------------

    /** 组内元素间最大水平间距（谱线间距倍数）。 */
    private const val GROUP_GAP_FRAC = 1.2

    // ---- 等号("=")检测参数 --------------------------------------------------

    /** 等号每根水平线段的最小宽高比（线段必须明显宽于高）。 */
    private const val EQUALS_MIN_ASPECT = 1.5

    /** 两根线段的 X 范围重叠量占较短线段宽度的比例（0~1）。 */
    private const val EQUALS_X_OVERLAP_RATIO = 0.4

    /** 等号整体最大高度（谱线间距倍数）。 */
    private const val EQUALS_MAX_HEIGHT_FRAC = 1.5

    /** 每根线段的最小宽度（谱线间距倍数）。 */
    private const val EQUALS_MIN_WIDTH_FRAC = 0.25

    /** 两根线段之间的最小垂直间隙（像素）。 */
    private const val EQUALS_MIN_GAP = 1

    // ---- BPM 范围约束 -------------------------------------------------------

    /** 合理 BPM 下限（极慢板 Larghissimo ≈ 24）。 */
    private const val MIN_BPM = 20

    /** 合理 BPM 上限（极快板 Prestissimo ≈ 200，留余量）。 */
    private const val MAX_BPM = 400

    /** 最少数字位数（BPM 总是 ≥ 2 位数字）。 */
    private const val MIN_DIGITS = 2

    /**
     * 在第一个谱表系统上方检测速度记号。
     *
     * @param image       去谱线+降噪后的二值图像。
     * @param blobs       连通块列表（与 image 一致的坐标系）。
     * @param systems     谱表系统列表（至少 1 个）。
     * @param lineSpacing 平均谱线间距。
     * @return 检测到的速度，或 null（未检测到）。
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): TempoDetection? {
        if (lineSpacing < 1 || systems.isEmpty()) return null
        val s = lineSpacing.toDouble()

        val firstSystem = systems.first()
        val topY = firstSystem.topLine.center
        val searchBottom = topY - (SEARCH_GAP_FRAC * s).toInt()
        val searchTop = (topY - SEARCH_RANGE_FRAC * s).toInt().coerceAtLeast(0)
        if (searchBottom <= searchTop) return null

        // 过滤搜索区域内的候选墨块。
        val maxBlobW = (MAX_BLOB_WIDTH_FRAC * s).toInt()
        val maxBlobH = (MAX_BLOB_HEIGHT_FRAC * s).toInt()
        val candidates = blobs.filter { blob ->
            blob.centerY in searchTop..searchBottom &&
                blob.minY >= searchTop &&
                blob.maxY <= searchBottom + maxBlobH / 2 &&
                blob.area >= 4 &&
                blob.width >= 2 &&
                blob.height >= 2 &&
                blob.width <= maxBlobW &&
                blob.height <= maxBlobH
        }.sortedBy { it.minX }

        if (candidates.isEmpty()) return null

        // 将水平方向上相邻的墨块分组为「文本行」。
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

        // 对每组查找「等号 + 连续数字」模式。
        for (group in groups) {
            val result = tryParseTempo(image, group, lineSpacing)
            if (result != null) return result
        }

        return null
    }

    /**
     * 尝试将一组墨块解析为速度记号。
     *
     * 要求：组内最右侧存在 ≥2 位的连续数字序列，其左侧有 "=" 符号。
     */
    private fun tryParseTempo(
        image: BinaryImage,
        group: List<Blob>,
        lineSpacing: Int
    ): TempoDetection? {
        // 逐个墨块做数字识别。
        val classified = group.map { blob ->
            blob to SignatureDetector.classifyDigit(image, blob)
        }

        // 找到最右侧的连续数字序列（run length ≥ MIN_DIGITS）。
        val digitRun = findRightmostDigitRun(classified) ?: return null
        if (digitRun.size < MIN_DIGITS) return null

        // 将数字拼接为 BPM 值。
        val bpmStr = digitRun.mapNotNull { it.second }.joinToString("")
        val bpm = bpmStr.toIntOrNull() ?: return null
        if (bpm !in MIN_BPM..MAX_BPM) return null

        // 在数字序列左侧查找 "=" 符号。
        val digitStartX = digitRun.first().first.minX
        val leftBlobs = group.filter { it.maxX < digitStartX }
        if (!hasEqualsSign(leftBlobs, lineSpacing)) return null

        val centerX = (group.first().minX + group.last().maxX) / 2
        return TempoDetection(bpm, centerX)
    }

    /**
     * 在分类后的墨块列表中找到最右侧的连续数字序列。
     *
     * 如果有多段等长的连续数字，取最右的一段。
     * 返回该段（blob→digit?）的子列表，或 null（无 ≥2 位的数字序列）。
     */
    private fun findRightmostDigitRun(
        classified: List<Pair<Blob, Int?>>
    ): List<Pair<Blob, Int?>>? {
        var bestStart = -1
        var bestLen = 0

        var i = 0
        while (i < classified.size) {
            if (classified[i].second != null) {
                var j = i
                while (j < classified.size && classified[j].second != null) j++
                val len = j - i
                // >= 而非 > ：等长时取更靠右的段。
                if (len >= bestLen) {
                    bestLen = len
                    bestStart = i
                }
                i = j
            } else {
                i++
            }
        }

        return if (bestStart >= 0) {
            classified.subList(bestStart, bestStart + bestLen)
        } else {
            null
        }
    }

    /**
     * 在给定的墨块列表中查找 "=" 符号。
     *
     * "=" 由两条平行的水平线段组成：
     * - 每根线段明显宽于高（宽高比 ≥ [EQUALS_MIN_ASPECT]）
     * - 两根线段的 X 范围充分重叠
     * - 两根线段之间有垂直间隙
     * - 整体高度不超过 [EQUALS_MAX_HEIGHT_FRAC] 个谱线间距
     *
     * 这是区分速度记号与其他数字标注（小节号、指法等）的关键特征。
     */
    private fun hasEqualsSign(blobs: List<Blob>, lineSpacing: Int): Boolean {
        if (blobs.size < 2) return false
        val s = lineSpacing.toDouble()
        val maxHeight = (EQUALS_MAX_HEIGHT_FRAC * s).toInt()
        val minWidth = (EQUALS_MIN_WIDTH_FRAC * s).toInt().coerceAtLeast(2)

        for (i in blobs.indices) {
            val b1 = blobs[i]
            if (b1.aspectRatio < EQUALS_MIN_ASPECT) continue
            if (b1.width < minWidth) continue
            for (j in i + 1 until blobs.size) {
                val b2 = blobs[j]
                if (b2.aspectRatio < EQUALS_MIN_ASPECT) continue
                if (b2.width < minWidth) continue
                // X 范围重叠量要求：至少为较短线段宽度的 40%。
                val minOverlap = (minOf(b1.width, b2.width) * EQUALS_X_OVERLAP_RATIO)
                    .toInt().coerceAtLeast(2)
                val overlap = minOf(b1.maxX, b2.maxX) - maxOf(b1.minX, b2.minX)
                if (overlap < minOverlap) continue
                // 确定上下关系
                val upper = if (b1.minY <= b2.minY) b1 else b2
                val lower = if (upper === b1) b2 else b1
                // 垂直间隙
                val gap = lower.minY - upper.maxY
                if (gap < EQUALS_MIN_GAP) continue
                // 整体高度合理
                val combinedHeight = lower.maxY - upper.minY + 1
                if (combinedHeight > maxHeight) continue
                return true
            }
        }
        return false
    }
}
