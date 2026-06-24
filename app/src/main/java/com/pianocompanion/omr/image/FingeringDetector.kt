package com.pianocompanion.omr.image

import kotlin.math.abs

/**
 * 指法数字(fingering number)检测器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 指法数字是写在音符**上方或下方**的小数字（1–5），指示演奏者用哪根手指弹奏该音符。
 * 在钢琴音乐中极为常见，尤其在教学乐谱和练习曲中——正确的指法是流畅演奏的基础。
 *
 * - **1** = 拇指(thumb)
 * - **2** = 食指(index)
 * - **3** = 中指(middle)
 * - **4** = 无名指(ring)
 * - **5** = 小指(pinky)
 *
 * ## 检测原理
 *
 * 1. **搜索区域**：对每个符头，在其上方和下方各搜索一个竖直区域（间距 0.4~3.0
 *    个谱线间距）。指法数字通常放在符头上方（右手 stem-up 时）或下方
 *    （左手 stem-down 时），搜索两侧以兼容两种情况。
 *
 * 2. **尺寸约束**：指法数字是小型字形，高度约 0.4~1.0 个谱线间距，宽度约 0.3~0.8
 *    个间距——显著小于符头（约 1 个间距）但大于噪声（>2px）。这排除了大型字形
 *    （如和弦符号、歌词文字）和噪声碎片。
 *
 * 3. **数字识别**：复用 [SignatureDetector.classifyDigit] 做 5×7 点阵模板匹配
 *    （与拍号、连音数字、八度数字使用同一套模板），保证一致性。
 *
 * 4. **范围过滤**：仅接受数字 1–5（有效手指编号）。0、6–9 不是手指编号，被拒绝。
 *
 * 5. **水平对齐**：指法数字中心 X 与符头中心 X 的偏差 ≤ 0.5 个谱线间距，
 *    确保数字属于该音符而非邻近音符。
 *
 * ## 与其他数字标注的区分
 *
 * - **拍号数字**：位于谱表左侧签名区内（signatureEndX 已排除），不在音符上方
 * - **跳房子序号**：位于顶线上方方括号上方，由 VoltaDetector 单独处理
 * - **连音数字**：位于多个音符上方中央（跨音符），由 TupletDetector 单独处理
 * - **八度数字**：带虚线（8va/15ma），由 OctavaDetector 单独处理
 * - **小节号**：位于谱表左边缘外，不在音符正上方
 * - 指法数字是**单个音符正上方/下方的小型孤立数字**，与其他数字标注在位置和
 *   尺度上天然不同
 */
object FingeringDetector {

    /** 候选最小高度（谱线间距倍数）。 */
    private const val MIN_HEIGHT_FRAC = 0.4

    /** 候选最大高度（谱线间距倍数）。 */
    private const val MAX_HEIGHT_FRAC = 1.2

    /** 候选最大宽度（谱线间距倍数）。 */
    private const val MAX_WIDTH_FRAC = 0.9

    /** 符头中心与数字中心 X 偏差最大值（谱线间距倍数）。 */
    private const val CENTER_X_TOLERANCE_FRAC = 0.5

    /** 搜索区域起始间隙（谱线间距倍数）：从符头边缘向外偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.3

    /** 搜索区域范围（谱线间距倍数）。 */
    private const val SEARCH_RANGE_FRAC = 3.0

    /** 连通块最小面积（排除噪声碎片）。 */
    private const val MIN_AREA = 3

    /**
     * 指法检测结果。
     *
     * @param noteheadIdx 对应的符头在 noteheads 列表中的索引
     * @param finger      手指编号（1–5）
     * @param above       true = 数字在符头上方，false = 在下方
     * @param centerX     数字中心 X 坐标
     * @param centerY     数字中心 Y 坐标
     */
    data class Fingering(
        val noteheadIdx: Int,
        val finger: Int,
        val above: Boolean,
        val centerX: Int,
        val centerY: Int
    )

    /**
     * 检测每个符头上方/下方的指法数字。
     *
     * @param image         去谱线+降噪后的二值图像
     * @param blobs         连通块列表（与 image 一致的坐标系）
     * @param noteheads     符头列表
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）
     * @param systems       谱表系统列表
     * @param lineSpacing   平均谱线间距
     * @return 检测到的指法列表（按符头索引排序）；每个符头最多一个指法
     */
    fun detect(
        image: BinaryImage,
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        systems: List<StaffSystem>,
        lineSpacing: Int
    ): List<Fingering> {
        if (lineSpacing < 1 || noteheads.isEmpty()) return emptyList()
        val s = lineSpacing.toDouble()
        val results = ArrayList<Fingering>()

        val minHeight = (MIN_HEIGHT_FRAC * s).toInt().coerceAtLeast(3)
        val maxHeight = (MAX_HEIGHT_FRAC * s).toInt()
        val maxWidth = (MAX_WIDTH_FRAC * s).toInt().coerceAtLeast(4)
        val centerXTol = (CENTER_X_TOLERANCE_FRAC * s).toInt().coerceAtLeast(2)
        val searchGap = (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(1)
        val searchRange = (SEARCH_RANGE_FRAC * s).toInt()

        noteheads.forEachIndexed { idx, nh ->
            val sysIdx = systemIndices.getOrElse(idx) { -1 }
            if (sysIdx < 0 || sysIdx >= systems.size) return@forEachIndexed

            val system = systems[sysIdx]
            val topLineY = system.topLine.center
            val bottomLineY = system.bottomLine.center

            // --- 上方搜索 ---
            val nhTopEdge = nh.centerY - nh.height / 2
            val aboveBottom = minOf(nhTopEdge, topLineY) - searchGap
            val aboveTop = (aboveBottom - searchRange).coerceAtLeast(0)
            if (aboveBottom > aboveTop) {
                val match = findFingeringBlob(
                    image, blobs, nh.centerX,
                    aboveTop, aboveBottom,
                    minHeight, maxHeight, maxWidth,
                    centerXTol, s
                )
                if (match != null) {
                    results += Fingering(idx, match.first, above = true,
                        nh.centerX, match.second.centerY)
                    return@forEachIndexed
                }
            }

            // --- 下方搜索 ---
            val nhBotEdge = nh.centerY + nh.height / 2
            val belowTop = maxOf(nhBotEdge, bottomLineY) + searchGap
            val belowBottom = (belowTop + searchRange).coerceAtMost(image.height - 1)
            if (belowBottom > belowTop) {
                val match = findFingeringBlob(
                    image, blobs, nh.centerX,
                    belowTop, belowBottom,
                    minHeight, maxHeight, maxWidth,
                    centerXTol, s
                )
                if (match != null) {
                    results += Fingering(idx, match.first, above = false,
                        nh.centerX, match.second.centerY)
                }
            }
        }

        return results
    }

    /**
     * 在指定区域内搜索符合指法数字形状的墨块。
     *
     * @return (手指编号, 匹配的 Blob) 或 null
     */
    private fun findFingeringBlob(
        image: BinaryImage,
        blobs: List<Blob>,
        targetCenterX: Int,
        yStart: Int,
        yEnd: Int,
        minHeight: Int,
        maxHeight: Int,
        maxWidth: Int,
        centerXTol: Int,
        lineSpacing: Double
    ): Pair<Int, Blob>? {
        val candidates = blobs.filter { blob ->
            blob.centerY in yStart..yEnd &&
                blob.minY >= yStart - maxHeight / 2 &&
                blob.maxY <= yEnd + maxHeight / 2 &&
                blob.height in minHeight..maxHeight &&
                blob.width <= maxWidth &&
                blob.area >= MIN_AREA &&
                abs(blob.centerX - targetCenterX) <= centerXTol
        }.sortedBy { abs(it.centerY - (yStart + yEnd) / 2) }  // 最近优先

        for (blob in candidates) {
            val digit = SignatureDetector.classifyDigit(image, blob)
            if (digit != null && digit in 1..5) {
                return digit to blob
            }
        }
        return null
    }
}
