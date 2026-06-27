package com.pianocompanion.analytics

import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.data.model.SessionRecord

/**
 * 弱项趋势（trend）——某小节的错误随练习次数的变化方向。
 *
 * - [IMPROVING] 正在改善：近期错误率明显低于早期（下降 ≥25%）
 * - [STABLE] 保持稳定：错误率基本持平（变化在 ±25% 以内）
 * - [WORSENING] 需要警惕：近期错误率明显高于早期（上升 ≥25%）
 * - [INSUFFICIENT_DATA] 数据不足：早期/近期任一半区无会话，无法判定趋势
 */
enum class WeakSpotTrend(val label: String) {
    IMPROVING("正在改善"),
    STABLE("保持稳定"),
    WORSENING("需要警惕"),
    INSUFFICIENT_DATA("数据不足")
}

/**
 * 单个小节的弱项诊断结果。
 *
 * @param measureIndex 小节序号（从 0 开始，与 ScoreNote.measureIndex 一致）
 * @param errorCount 该小节在所有会话中的累计错误数
 * @param sessionCount 在该小节出现过 ≥1 次错误的会话数
 * @param totalSessions 分析所基于的总会话数（用于计算 [sessionRatio]）
 * @param errorTypeCounts 按错误类型细分的计数（WRONG_PITCH/MISSING_NOTE/EXTRA_NOTE/RHYTHM_ERROR）
 * @param dominantErrorType 最常见的错误类型
 * @param trend 弱项趋势（见 [WeakSpotTrend]）
 */
data class WeakSpot(
    val measureIndex: Int,
    val errorCount: Int,
    val sessionCount: Int,
    val totalSessions: Int,
    val errorTypeCounts: Map<MatchStatus, Int>,
    val dominantErrorType: MatchStatus,
    val trend: WeakSpotTrend
) {
    /** 该小节出现错误的会话占比，范围 [0,1]。 */
    val sessionRatio: Float
        get() = if (totalSessions > 0) sessionCount.toFloat() / totalSessions else 0f

    /**
     * 严重程度综合评分（用于排序）。
     *
     * 同时考虑错误**总量**（errorCount）与**持续性**（sessionRatio）：
     * 持续在多次会话中出错的小节比偶发错误更值得优先练习。
     */
    val severityScore: Float
        get() = errorCount.toFloat() + sessionRatio * 10f
}

/**
 * 推荐集中练习的连续段落。
 *
 * 将相邻的弱项小节（间隔 ≤ [WeakSpotOptions.maxPassageGap]）合并成一个练习段落，
 * 便于用户「分块慢练」。
 *
 * @param startMeasure 段落起始小节（含）
 * @param endMeasure 段落结束小节（含）
 * @param memberSpots 组成该段落的弱项小节（已按小节序号排序）
 */
data class PracticePassage(
    val startMeasure: Int,
    val endMeasure: Int,
    val memberSpots: List<WeakSpot>
) {
    /** 段落覆盖的小节数（含无弱项的间隔小节）。 */
    val measureCount: Int get() = endMeasure - startMeasure + 1

    /** 段落内累计错误总数。 */
    val totalErrors: Int get() = memberSpots.sumOf { it.errorCount }

    /** 段落中最严重的弱项小节（用于排序与展示）。 */
    val worstSpot: WeakSpot? get() = memberSpots.maxByOrNull { it.severityScore }
}

/**
 * 弱项分析完整报告。
 *
 * @param weakSpots 所有弱项小节，按严重程度从高到低排序
 * @param recommendedPassages 推荐练习段落，按累计错误数从高到低排序
 * @param totalSessions 分析所基于的总会话数
 * @param totalErrors 所有弱项小节的累计错误数
 * @param measuresAnalyzed 出现过错误的小节集合
 * @param summary 人类可读的中文摘要（供 UI / 报告展示）
 */
data class WeakSpotReport(
    val weakSpots: List<WeakSpot>,
    val recommendedPassages: List<PracticePassage>,
    val totalSessions: Int,
    val totalErrors: Int,
    val measuresAnalyzed: Set<Int>,
    val summary: String
) {
    /** 是否存在弱项（无弱项时 UI 可展示鼓励信息）。 */
    val hasWeakSpots: Boolean get() = weakSpots.isNotEmpty()
}

/**
 * 弱项分析参数。
 *
 * @param minSessionRatio 「出错会话占比」阈值。某小节出错会话数 / 总会话数 ≥ 此值即判为弱项。
 *        默认 0.5（一半以上会话在此小节出错）。
 * @param minAbsoluteErrors 「绝对错误数」阈值。满足 [minSessionRatio] **或** 累计错误数 ≥
 *        此值即判为弱项。默认 2（避免单次偶发错误被判定为弱项）。
 * @param maxPassageGap 段落合并的最大小节间隔。相邻弱项小节间隔 ≤ 此值时合并为同一练习段落。
 *        默认 1（允许间隔 1 个无弱项小节）。
 * @param trendImprovementRatio 判定「正在改善」的下降比例阈值（近期错误率 ≤ 早期 × 此值）。
 *        默认 0.75。
 * @param trendWorseningRatio 判定「需要警惕」的上升比例阈值（近期错误率 ≥ 早期 × 此值）。
 *        默认 1.25。
 */
data class WeakSpotOptions(
    val minSessionRatio: Float = 0.5f,
    val minAbsoluteErrors: Int = 2,
    val maxPassageGap: Int = 1,
    val trendImprovementRatio: Float = 0.75f,
    val trendWorseningRatio: Float = 1.25f
)

/**
 * 薄弱环节分析引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 聚合多次练习会话（[SessionRecord]）中的逐小节错误（[com.pianocompanion.data.model.ErrorPosition]），
 * 找出用户反复出错的「弱项小节」，分析错误趋势（改善/稳定/恶化），并将相邻弱项合并为
 * 推荐集中练习的段落。
 *
 * 调用方应先按乐谱过滤会话（弱项是乐谱相关的——「欢乐颂」第 5 小节与「小星星」第 5 小节
 * 无关）。本分析器不关心会话属于哪首乐谱，只负责对传入的会话集合做聚合。
 *
 * 典型用法：
 * ```
 * val sessions = statsRepository.getAllSessions().filter { it.scoreTitle == "欢乐颂" }
 * val report = WeakSpotAnalyzer.analyze(sessions)
 * if (report.hasWeakSpots) {
 *     println(report.summary)
 * }
 * ```
 */
object WeakSpotAnalyzer {

    /**
     * 分析给定会话列表，生成弱项报告。
     *
     * @param sessions 已按乐谱过滤的会话列表（通常为同一首乐谱的全部历史会话）
     * @param options 分析参数
     */
    fun analyze(
        sessions: List<SessionRecord>,
        options: WeakSpotOptions = WeakSpotOptions()
    ): WeakSpotReport {
        val totalSessions = sessions.size

        // 边界：无会话或无任何错误数据
        if (totalSessions == 0) {
            return emptyReport(0, "暂无练习数据，无法分析薄弱环节。")
        }

        // 按会话 startTime 升序，便于后续趋势分析（早→晚）
        val ordered = sessions.sortedBy { it.startTime }

        val hasAnyError = ordered.any { it.errorPositions.isNotEmpty() }
        if (!hasAnyError) {
            return emptyReport(totalSessions, "未发现错误记录，表现优异！继续保持。")
        }

        // --- 1. 逐小节聚合 ---
        // measureIndex -> (errorTypeCounts, touchedSessionCount, totalErrors)
        val errorTypeCountsByMeasure = mutableMapOf<Int, MutableMap<MatchStatus, Int>>()
        val touchedSessionsByMeasure = mutableMapOf<Int, MutableSet<Int /* session id */>>()
        val totalErrorsByMeasure = mutableMapOf<Int, Int>()

        ordered.forEachIndexed { idx, session ->
            session.errorPositions.forEach { err ->
                val m = err.measureIndex
                totalErrorsByMeasure[m] = (totalErrorsByMeasure[m] ?: 0) + 1
                errorTypeCountsByMeasure
                    .getOrPut(m) { mutableMapOf() }
                    .merge(err.errorType, 1) { a, b -> a + b }
                touchedSessionsByMeasure
                    .getOrPut(m) { mutableSetOf() }
                    .add(idx)
            }
        }

        // --- 2. 早/晚分区用于趋势分析 ---
        val half = totalSessions / 2
        val olderRange = 0 until half                       // 早期会话下标区间
        val recentRange = half until totalSessions          // 近期会话下标区间（奇数时多取一个）
        val olderSessionCount = olderRange.count()
        val recentSessionCount = recentRange.count()

        // --- 3. 判定弱项小节 ---
        val weakSpots = totalErrorsByMeasure.mapNotNull { (measure, totalErrors) ->
            val sessionCount = touchedSessionsByMeasure[measure]?.size ?: 0
            val ratio = sessionCount.toFloat() / totalSessions
            val isWeak = ratio >= options.minSessionRatio || totalErrors >= options.minAbsoluteErrors
            if (!isWeak) return@mapNotNull null

            val typeCounts = errorTypeCountsByMeasure[measure] ?: emptyMap()
            val dominant = typeCounts.maxByOrNull { it.value }?.key
                ?: MatchStatus.WRONG_PITCH
            val trend = computeTrend(
                measure, ordered, olderRange, recentRange,
                olderSessionCount, recentSessionCount, options
            )

            WeakSpot(
                measureIndex = measure,
                errorCount = totalErrors,
                sessionCount = sessionCount,
                totalSessions = totalSessions,
                errorTypeCounts = typeCounts,
                dominantErrorType = dominant,
                trend = trend
            )
        }.sortedByDescending { it.severityScore }

        // --- 4. 合并相邻弱项为练习段落 ---
        val passages = buildPassages(weakSpots, options.maxPassageGap)

        // --- 5. 汇总 ---
        val totalWeakErrors = weakSpots.sumOf { it.errorCount }
        val measuresAnalyzed = totalErrorsByMeasure.keys
        val summary = buildSummary(weakSpots, passages, totalSessions, totalWeakErrors, options)

        return WeakSpotReport(
            weakSpots = weakSpots,
            recommendedPassages = passages,
            totalSessions = totalSessions,
            totalErrors = totalWeakErrors,
            measuresAnalyzed = measuresAnalyzed,
            summary = summary
        )
    }

    /**
     * 计算某小节的错误趋势。
     *
     * 比较该小节在「早期会话」与「近期会话」中的**平均每会话错误数**（错误率），
     * 用以消除两区会话数不等的偏差。
     */
    private fun computeTrend(
        measure: Int,
        ordered: List<SessionRecord>,
        olderRange: IntRange,
        recentRange: IntRange,
        olderSessionCount: Int,
        recentSessionCount: Int,
        options: WeakSpotOptions
    ): WeakSpotTrend {
        if (olderSessionCount == 0 || recentSessionCount == 0) {
            return WeakSpotTrend.INSUFFICIENT_DATA
        }
        val olderErrors = olderRange.sumOf { idx ->
            ordered[idx].errorPositions.count { it.measureIndex == measure }
        }
        val recentErrors = recentRange.sumOf { idx ->
            ordered[idx].errorPositions.count { it.measureIndex == measure }
        }
        val olderRate = olderErrors.toFloat() / olderSessionCount
        val recentRate = recentErrors.toFloat() / recentSessionCount

        // 早期从未出错 → 近期也无错（不应出现在弱项中），近期有错 → 恶化
        return when {
            olderRate == 0f && recentRate == 0f -> WeakSpotTrend.STABLE
            olderRate == 0f -> WeakSpotTrend.WORSENING
            recentRate == 0f -> WeakSpotTrend.IMPROVING
            recentRate <= olderRate * options.trendImprovementRatio -> WeakSpotTrend.IMPROVING
            recentRate >= olderRate * options.trendWorseningRatio -> WeakSpotTrend.WORSENING
            else -> WeakSpotTrend.STABLE
        }
    }

    /**
     * 将相邻弱项小节合并为练习段落。
     *
     * 按小节序号排序后，相邻两项间隔（后一项 measureIndex − 前一项 measureIndex）≤ [maxGap]
     * 即归入同一段落；间隔超过 [maxGap] 则开启新段落。
     */
    private fun buildPassages(
        weakSpots: List<WeakSpot>,
        maxGap: Int
    ): List<PracticePassage> {
        if (weakSpots.isEmpty()) return emptyList()
        val sorted = weakSpots.sortedBy { it.measureIndex }
        val passages = mutableListOf<PracticePassage>()
        val current = mutableListOf<WeakSpot>()

        for (spot in sorted) {
            if (current.isEmpty()) {
                current.add(spot)
            } else {
                val prevMeasure = current.last().measureIndex
                if (spot.measureIndex - prevMeasure <= maxGap) {
                    current.add(spot)
                } else {
                    passages += flushPassage(current)
                    current.clear()
                    current.add(spot)
                }
            }
        }
        if (current.isNotEmpty()) {
            passages += flushPassage(current)
        }
        return passages.sortedByDescending { it.totalErrors }
    }

    private fun flushPassage(spots: List<WeakSpot>): PracticePassage {
        val first = spots.first().measureIndex
        val last = spots.last().measureIndex
        return PracticePassage(
            startMeasure = first,
            endMeasure = last,
            memberSpots = spots.toList()
        )
    }

    private fun buildSummary(
        weakSpots: List<WeakSpot>,
        passages: List<PracticePassage>,
        totalSessions: Int,
        totalWeakErrors: Int,
        options: WeakSpotOptions
    ): String {
        if (weakSpots.isEmpty()) {
            return "经过 $totalSessions 次练习分析，未发现明显弱项，表现稳定。"
        }
        val sb = StringBuilder()
        sb.append("发现 ${weakSpots.size} 个薄弱小节（共 $totalWeakErrors 次错误），")
        val improving = weakSpots.count { it.trend == WeakSpotTrend.IMPROVING }
        val worsening = weakSpots.count { it.trend == WeakSpotTrend.WORSENING }
        when {
            improving > 0 && worsening == 0 ->
                sb.append("其中 $improving 处正在改善，练习卓有成效。")
            worsening > 0 && improving == 0 ->
                sb.append("其中 $worsening 处需重点关注，建议放慢速度重点练习。")
            improving > 0 && worsening > 0 ->
                sb.append("其中 $improving 处正在改善、$worsening 处需重点关注。")
            else ->
                sb.append("整体趋势稳定，建议针对薄弱小节反复慢练。")
        }
        if (passages.isNotEmpty()) {
            val topPassage = passages.first()
            sb.append("建议优先练习第 ${displayMeasure(topPassage.startMeasure)}–" +
                    "${displayMeasure(topPassage.endMeasure)} 小节。")
        }
        return sb.toString()
    }

    /** 将内部 0 基小节序号转换为用户友好的 1 基显示。 */
    private fun displayMeasure(measureIndex: Int): Int = measureIndex + 1

    private fun emptyReport(totalSessions: Int, summary: String): WeakSpotReport =
        WeakSpotReport(
            weakSpots = emptyList(),
            recommendedPassages = emptyList(),
            totalSessions = totalSessions,
            totalErrors = 0,
            measuresAnalyzed = emptySet(),
            summary = summary
        )
}
