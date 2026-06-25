package com.pianocompanion.omr.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 琶音(arpeggio / rolled chord)检测器。
 *
 * 琶音是乐谱中和弦左方的垂直波浪线（或竖线），指示演奏者将和弦中的音符
 * 从下到上依次快速滚奏，而非同时弹奏。在钢琴乐谱中极为常见——浪漫派/印象派
 * 作品中几乎每页都有琶音和弦。
 *
 * **对 score-following 的影响**：琶音和弦中的音符**不是**同时演奏的——
 * 演奏者从最低音到最高音依次快速弹奏。如果 OMR 不识别琶音，所有和弦音符
 * 会被赋予相同的 startTime，score follower 会期待它们同时出现，导致匹配
 * 失败。检测到琶音后，管线会对和弦成员应用一个小的序列延迟。
 *
 * **检测原理**：
 * 1. 将符头按系统和 X 邻近性分组，找出和弦候选（2+ 个符头在同一 X 列）
 * 2. 对竖直跨度 ≥ [MIN_CHORD_SPAN_FRAC] 个谱线间距的和弦候选，
 *    在其左方搜索窄竖线（波浪线或直线）
 * 3. 候选竖线特征：
 *    - 宽度 ≤ [MAX_WIDTH_FRAC] 个间距（窄竖线，区分于符头/小节线）
 *    - 高度 ≥ [MIN_HEIGHT_FRAC] 个间距（足够高，区分于短竖线/噪点）
 *    - 填充率 ≤ [MAX_FILL_RATE]（排除实心小节线；琶音线是细线，占包围盒面积小）
 *    - 位于和弦最左符头左方 [SEARCH_GAP_FRAC]~[SEARCH_RANGE_FRAC] 个间距
 *    - 竖直范围与和弦范围重叠 ≥ [VERTICAL_OVERLAP_FRAC] × min(和弦高度, 竖线高度)
 *
 * **与其他符号的区分**：
 * - **小节线(barline)**：远高于琶音的填充率（≈100% vs ≤65%），且贯穿全谱高
 * - **符干(stem)**：通常与符头融合为同一连通块；即使分离，符干宽度仅 1-2px，
 *   且仅从单个符头向一侧延伸，不会跨越和弦的全部竖直范围
 * - **升号(♯)**：高宽比接近 1，不会高度 >> 宽度；位于单个符头前方而非和弦前方
 * - **临时记号/拍号**：位于签名区或单个音符前方，不是垂直长线
 */
object ArpeggioDetector {

    /**
     * 检测到的琶音。
     *
     * @param noteheadIndices 该琶音覆盖的符头索引列表（位于 noteheads 列表中）
     * @param centerX 琶音竖线中心 X 坐标
     * @param topY 琶音竖线顶部 Y 坐标
     * @param bottomY 琶音竖线底部 Y 坐标
     */
    data class Arpeggio(
        val noteheadIndices: List<Int>,
        val centerX: Int,
        val topY: Int,
        val bottomY: Int
    )

    // --- 和弦候选约束 ---

    /** 和弦分组 X 容差（谱线间距倍数）：符头中心 X 差 ≤ 此值视为同一列。 */
    private const val CHORD_X_TOLERANCE_FRAC = 0.8

    /** 和弦最小竖直跨度（谱线间距倍数）：仅对跨度 ≥ 此值的和弦检测琶音。 */
    private const val MIN_CHORD_SPAN_FRAC = 1.5

    // --- 琶音竖线候选约束 ---

    /** 搜索起始间隙（谱线间距倍数）：从和弦最左边缘向左偏移多少开始搜索。 */
    private const val SEARCH_GAP_FRAC = 0.15

    /** 搜索范围（谱线间距倍数）：搜索向左延伸的总距离。 */
    private const val SEARCH_RANGE_FRAC = 1.5

    /** 候选竖线最大宽度（谱线间距倍数）。波浪线允许稍宽。 */
    private const val MAX_WIDTH_FRAC = 0.6

    /** 候选竖线最小高度（谱线间距倍数）。 */
    private const val MIN_HEIGHT_FRAC = 1.2

    /** 候选竖线最大高度（谱线间距倍数）。避免把小节线误判。 */
    private const val MAX_HEIGHT_FRAC = 8.0

    /** 候选竖线最大填充率（面积/包围盒）。排除实心小节线（填充率 ≈1.0）。 */
    private const val MAX_FILL_RATE = 0.65

    /** 竖线与和弦竖直重叠比例下限（占 min(和弦高度, 竖线高度) 的比例）。 */
    private const val VERTICAL_OVERLAP_FRAC = 0.40

    /** 候选竖线最小面积（像素）。 */
    private const val MIN_AREA = 6

    /**
     * 检测每个系统中和弦左方的琶音竖线。
     *
     * @param blobs 去谱线+降噪后的连通块列表
     * @param noteheads 全部符头列表
     * @param systemIndices 每个符头所属的谱表系统索引（与 noteheads 等长）
     * @param lineSpacing 平均谱线间距
     * @return 检测到的琶音列表
     */
    fun detect(
        blobs: List<Blob>,
        noteheads: List<Notehead>,
        systemIndices: List<Int>,
        lineSpacing: Int
    ): List<Arpeggio> {
        if (lineSpacing <= 0 || noteheads.isEmpty()) return emptyList()

        val s = lineSpacing.toDouble()
        val xTol = (CHORD_X_TOLERANCE_FRAC * s).toInt().coerceAtLeast(2)
        val minSpan = (MIN_CHORD_SPAN_FRAC * s).toInt().coerceAtLeast(2)

        // 按系统分组符头
        val bySystem = HashMap<Int, MutableList<Int>>()
        for (i in noteheads.indices) {
            val sys = systemIndices.getOrElse(i) { 0 }
            bySystem.getOrPut(sys) { mutableListOf() }.add(i)
        }

        val results = ArrayList<Arpeggio>()
        for ((_, indices) in bySystem) {
            // 按符头中心 X 排序，然后按 X 邻近性分组为和弦候选
            val sorted = indices.sortedBy { noteheads[it].centerX }
            val chordGroups = groupByX(sorted, noteheads, xTol)
            for (group in chordGroups) {
                if (group.size < 2) continue
                // 计算和弦的竖直范围
                val chordMinY = group.minOf { noteheads[it].centerY }
                val chordMaxY = group.maxOf { noteheads[it].centerY }
                if (chordMaxY - chordMinY < minSpan) continue
                // 和弦最左边缘 X（最左符头中心 - 半宽）
                val chordLeftEdge = group.minOf { noteheads[it].centerX - noteheads[it].width / 2 }
                // 搜索琶音竖线
                val arpeggio = findArpeggioLine(blobs, chordLeftEdge, chordMinY, chordMaxY, s)
                if (arpeggio != null) {
                    results += Arpeggio(group, arpeggio.first, arpeggio.second, arpeggio.third)
                }
            }
        }
        return results
    }

    /**
     * 按 X 邻近性将排序后的符头索引分组。
     * 相邻符头中心 X 差 ≤ [xTol] 的归为同一组。
     */
    private fun groupByX(
        sorted: List<Int>,
        noteheads: List<Notehead>,
        xTol: Int
    ): List<List<Int>> {
        if (sorted.isEmpty()) return emptyList()
        val groups = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var prevX = noteheads[sorted[0]].centerX
        current.add(sorted[0])
        for (k in 1 until sorted.size) {
            val idx = sorted[k]
            val x = noteheads[idx].centerX
            if (x - prevX <= xTol) {
                current.add(idx)
            } else {
                groups.add(current)
                current = ArrayList()
                current.add(idx)
            }
            prevX = x
        }
        groups.add(current)
        return groups
    }

    /**
     * 在和弦左方搜索琶音竖线。
     *
     * @param blobs 连通块列表
     * @param chordLeftEdge 和弦最左边缘 X
     * @param chordMinY 和弦顶部 Y
     * @param chordMaxY 和弦底部 Y
     * @param s 谱线间距（Double）
     * @return Triple(centerX, topY, bottomY) 或 null
     */
    private fun findArpeggioLine(
        blobs: List<Blob>,
        chordLeftEdge: Int,
        chordMinY: Int,
        chordMaxY: Int,
        s: Double
    ): Triple<Int, Int, Int>? {
        val searchLeft = chordLeftEdge - (SEARCH_RANGE_FRAC * s).toInt()
        val searchRight = chordLeftEdge - (SEARCH_GAP_FRAC * s).toInt().coerceAtLeast(1)
        val maxWidth = (MAX_WIDTH_FRAC * s).toInt().coerceAtLeast(3)
        val minHeight = (MIN_HEIGHT_FRAC * s).toInt().coerceAtLeast(4)
        val maxHeight = (MAX_HEIGHT_FRAC * s).toInt()

        val chordHeight = chordMaxY - chordMinY
        val minOverlap = (VERTICAL_OVERLAP_FRAC * min(chordHeight, minHeight)).toInt()

        var best: Triple<Int, Int, Int>? = null
        var bestScore = -1

        for (blob in blobs) {
            // 位置约束：竖线的右边缘在和弦左方的搜索区域内
            if (blob.maxX < searchLeft || blob.maxX > searchRight) continue
            // 尺寸约束
            if (blob.width > maxWidth) continue
            if (blob.height < minHeight || blob.height > maxHeight) continue
            if (blob.area < MIN_AREA) continue
            // 填充率约束（排除实心小节线）—— 仅对宽度 ≥ 3px 的块检查填充率。
            // 1-2px 宽的直线（琶音直线）填充率天然为 1.0，但显然不是小节线（小节线宽 ≥ 3px）。
            if (blob.width >= 3) {
                val fillRate = blob.area.toDouble() / (blob.width * blob.height).coerceAtLeast(1)
                if (fillRate > MAX_FILL_RATE) continue
            }
            // 竖直重叠约束
            val overlapTop = max(blob.minY, chordMinY)
            val overlapBot = min(blob.maxY, chordMaxY)
            val overlap = overlapBot - overlapTop
            if (overlap < minOverlap) continue

            // 选择竖直重叠最大的候选（如果有多个匹配）
            if (overlap > bestScore) {
                bestScore = overlap
                best = Triple(blob.centerX, blob.minY, blob.maxY)
            }
        }
        return best
    }
}
