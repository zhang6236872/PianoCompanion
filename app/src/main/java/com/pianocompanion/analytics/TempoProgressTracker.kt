package com.pianocompanion.analytics

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 单次渐速练习会话的记录。
 *
 * 每当用户使用 TempoRampUp 完成一次段落循环渐速练习后，由
 * [com.pianocompanion.ui.practice.PracticeViewModel] 生成此记录并交给
 * [TempoProgressTracker] 持久化，用于跨会话跟踪速度提升趋势。
 *
 * @param scoreTitle 乐谱标题（用于区分不同曲目的同名段落）
 * @param startMeasure 练习段落起始小节（0-based，与 ScoreNote.measureIndex 一致）
 * @param endMeasure 练习段落结束小节（0-based，含）
 * @param startBpm 本次渐速练习的起始速度
 * @param peakBpm 本次练习中达到的最高速度（= 完成时 TempoRampUp.currentBpm）
 * @param targetBpm 本次渐速练习的目标速度
 * @param completed 是否达到了目标速度
 * @param durationMs 本次练习持续时长（毫秒）
 * @param timestamp 记录时间（epoch ms），默认取当前时间
 */
data class TempoProgressRecord(
    val scoreTitle: String,
    val startMeasure: Int,
    val endMeasure: Int,
    val startBpm: Int,
    val peakBpm: Int,
    val targetBpm: Int,
    val completed: Boolean,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 段落标识符——同一首乐谱的同一小节范围视为同一练习段落，
     * 用于跨会话比较速度进步。
     */
    val sectionKey: String
        get() = "${scoreTitle}::${startMeasure}-${endMeasure}"

    /** 本次练习的速度提升幅度（peakBpm − startBpm）。 */
    val bpmGain: Int
        get() = peakBpm - startBpm

    /** 本次练习完成度（0f~1f）：peakBpm 相对于 startBpm→targetBpm 的进度。 */
    val completionRatio: Float
        get() {
            val span = targetBpm - startBpm
            return if (span <= 0) 1f
            else ((peakBpm - startBpm).toFloat() / span).coerceIn(0f, 1f)
        }
}

/**
 * 速度提升趋势。
 *
 * - [IMPROVING] 正在进步：近期练习的峰值速度明显高于早期（差值 ≥ [TempoProgressOptions.trendDeltaThreshold]）
 * - [STABLE] 保持稳定：近期与早期峰值速度差异不大
 * - [STAGNANT] 停滞不前：数据充足但无进步迹象（近期 ≤ 早期）
 * - [INSUFFICIENT_DATA] 数据不足：练习次数太少无法判定趋势
 */
enum class TempoProgressTrend(val label: String) {
    IMPROVING("正在进步"),
    STABLE("保持稳定"),
    STAGNANT("停滞不前"),
    INSUFFICIENT_DATA("数据不足")
}

/**
 * 单个练习段落的速度进步汇总。
 *
 * @param sectionKey 段落标识符
 * @param scoreTitle 乐谱标题
 * @param startMeasure 段落起始小节
 * @param endMeasure 段落结束小节
 * @param recordCount 该段落的历史练习记录数
 * @param firstRecord 最早的练习记录
 * @param latestRecord 最近的练习记录
 * @param bestPeakBpm 历史最高峰值速度
 * @param trend 速度趋势
 * @param avgPeakBpm 平均峰值速度
 */
data class TempoProgressSummary(
    val sectionKey: String,
    val scoreTitle: String,
    val startMeasure: Int,
    val endMeasure: Int,
    val recordCount: Int,
    val firstRecord: TempoProgressRecord?,
    val latestRecord: TempoProgressRecord?,
    val bestPeakBpm: Int,
    val trend: TempoProgressTrend,
    val avgPeakBpm: Float
) {
    /** 首次练习与最近练习之间的速度提升量（最近 peakBpm − 首次 peakBpm）。 */
    val bpmImprovement: Int
        get() {
            val first = firstRecord?.peakBpm ?: return 0
            val latest = latestRecord?.peakBpm ?: return 0
            return latest - first
        }

    /** 是否已达到目标速度（至少一次）。 */
    val hasReachedTarget: Boolean
        get() = bestPeakBpm >= (latestRecord?.targetBpm ?: 0) && recordCount > 0

    /** 用户可读的段落范围（1-based）。 */
    val displayMeasures: String
        get() = "第 ${startMeasure + 1}–${endMeasure + 1} 小节"
}

/**
 * 渐速进度追踪选项。
 *
 * @param trendDeltaThreshold 判定「正在进步」的速度差阈值（BPM），近期平均峰值 − 早期平均峰值 ≥ 此值即为进步。默认 5。
 * @param minRecordsForTrend 判定趋势所需的最少记录数。低于此数返回 [TempoProgressTrend.INSUFFICIENT_DATA]。默认 3。
 * @param recentRatio 判定趋势时「近期」记录所占比例（最后 N% 的记录为近期）。默认 0.4。
 */
data class TempoProgressOptions(
    val trendDeltaThreshold: Int = 5,
    val minRecordsForTrend: Int = 3,
    val recentRatio: Float = 0.4f
)

/**
 * 渐速进度追踪器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 管理多次渐速练习会话的 [TempoProgressRecord]，提供按段落分组的历史查询、
 * 最佳速度统计、进步趋势分析和中文可读摘要。
 *
 * 持久化由调用方负责（PracticeViewModel 通过 SharedPreferences + Gson 序列化
 * records 列表）；本类专注于数据分析逻辑。
 *
 * 典型用法：
 * ```kotlin
 * val tracker = TempoProgressTracker()
 * tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 80, 120, false))
 * tracker.record(TempoProgressRecord("欢乐颂", 4, 7, 60, 100, 120, false))
 *
 * val summary = tracker.getSectionSummary("欢乐颂", 4, 7)
 * println(summary?.trend)  // IMPROVING
 * ```
 */
class TempoProgressTracker {

    private val _records = mutableListOf<TempoProgressRecord>()

    /** 所有练习记录，按时间戳升序排列。 */
    val records: List<TempoProgressRecord>
        get() = _records.toList()

    /**
     * 用预置记录初始化（用于从持久化存储恢复或测试）。
     * 记录会自动按时间戳排序。
     */
    fun loadRecords(loaded: List<TempoProgressRecord>) {
        _records.clear()
        _records.addAll(loaded.sortedBy { it.timestamp })
    }

    /**
     * 记录一次渐速练习会话结果。
     *
     * @return 新增后的该段落记录总数
     */
    fun record(r: TempoProgressRecord): Int {
        _records.add(r)
        _records.sortBy { it.timestamp }
        return getSectionRecords(r.scoreTitle, r.startMeasure, r.endMeasure).size
    }

    /**
     * 获取指定段落的所有练习记录（按时间升序）。
     */
    fun getSectionRecords(
        scoreTitle: String,
        startMeasure: Int,
        endMeasure: Int
    ): List<TempoProgressRecord> {
        return _records.filter { it.sectionKey == sectionKey(scoreTitle, startMeasure, endMeasure) }
    }

    /**
     * 获取指定段落的历史最高峰值速度。
     * 无记录时返回 null。
     */
    fun getBestPeakBpm(scoreTitle: String, startMeasure: Int, endMeasure: Int): Int? {
        val sectionRecords = getSectionRecords(scoreTitle, startMeasure, endMeasure)
        return sectionRecords.maxOfOrNull { it.peakBpm }
    }

    /**
     * 获取指定段落的速度进步趋势。
     */
    fun getTrend(
        scoreTitle: String,
        startMeasure: Int,
        endMeasure: Int,
        options: TempoProgressOptions = TempoProgressOptions()
    ): TempoProgressTrend {
        val sectionRecords = getSectionRecords(scoreTitle, startMeasure, endMeasure)
        return computeTrend(sectionRecords, options)
    }

    /**
     * 获取指定段落的完整进度汇总。
     * 无记录时返回 null。
     */
    fun getSectionSummary(
        scoreTitle: String,
        startMeasure: Int,
        endMeasure: Int,
        options: TempoProgressOptions = TempoProgressOptions()
    ): TempoProgressSummary? {
        val sectionRecords = getSectionRecords(scoreTitle, startMeasure, endMeasure)
        if (sectionRecords.isEmpty()) return null

        return TempoProgressSummary(
            sectionKey = sectionKey(scoreTitle, startMeasure, endMeasure),
            scoreTitle = scoreTitle,
            startMeasure = startMeasure,
            endMeasure = endMeasure,
            recordCount = sectionRecords.size,
            firstRecord = sectionRecords.first(),
            latestRecord = sectionRecords.last(),
            bestPeakBpm = sectionRecords.maxOf { it.peakBpm },
            trend = computeTrend(sectionRecords, options),
            avgPeakBpm = sectionRecords.map { it.peakBpm.toFloat() }.average().toFloat()
        )
    }

    /**
     * 获取所有练习过的段落汇总，按最近练习时间降序排列。
     */
    fun getAllSectionSummaries(
        options: TempoProgressOptions = TempoProgressOptions()
    ): List<TempoProgressSummary> {
        val grouped = _records.groupBy { it.sectionKey }
        return grouped.map { (_, sectionRecords) ->
            TempoProgressSummary(
                sectionKey = sectionRecords.first().sectionKey,
                scoreTitle = sectionRecords.first().scoreTitle,
                startMeasure = sectionRecords.first().startMeasure,
                endMeasure = sectionRecords.first().endMeasure,
                recordCount = sectionRecords.size,
                firstRecord = sectionRecords.minByOrNull { it.timestamp },
                latestRecord = sectionRecords.maxByOrNull { it.timestamp },
                bestPeakBpm = sectionRecords.maxOf { it.peakBpm },
                trend = computeTrend(sectionRecords, options),
                avgPeakBpm = sectionRecords.map { it.peakBpm.toFloat() }.average().toFloat()
            )
        }.sortedByDescending { it.latestRecord?.timestamp ?: 0L }
    }

    /**
     * 估算还需多少次练习才能达到目标速度。
     *
     * 基于近期记录的平均每次峰值速度增量来估算。如果最近趋势为进步
     * 且增量 > 0，返回估算次数；否则返回 null（无法估算或已达目标）。
     */
    fun estimateSessionsToTarget(
        scoreTitle: String,
        startMeasure: Int,
        endMeasure: Int,
        options: TempoProgressOptions = TempoProgressOptions()
    ): Int? {
        val sectionRecords = getSectionRecords(scoreTitle, startMeasure, endMeasure)
        if (sectionRecords.size < 2) return null

        val latest = sectionRecords.last()
        if (latest.completed) return 0

        // 计算每次练习的平均峰值速度增量
        val recentWindow = maxOf(
            options.minRecordsForTrend,
            (sectionRecords.size * options.recentRatio).roundToInt().coerceAtLeast(1)
        ).coerceAtMost(sectionRecords.size)

        val recentRecords = sectionRecords.takeLast(recentWindow)
        val increments = recentRecords.zipWithNext { a, b -> b.peakBpm - a.peakBpm }
            .filter { it > 0 }

        if (increments.isEmpty()) return null

        val avgIncrement = increments.average()
        if (avgIncrement < 1) return null

        val remaining = latest.targetBpm - latest.peakBpm
        if (remaining <= 0) return 0

        return (remaining / avgIncrement).roundToInt().coerceAtLeast(1)
    }

    /**
     * 生成指定段落的中文可读进度摘要。
     * 无记录时返回提示信息。
     */
    fun buildReadableSummary(
        scoreTitle: String,
        startMeasure: Int,
        endMeasure: Int,
        options: TempoProgressOptions = TempoProgressOptions()
    ): String {
        val summary = getSectionSummary(scoreTitle, startMeasure, endMeasure, options)
            ?: return "「$scoreTitle」第 ${startMeasure + 1}–${endMeasure + 1} 小节暂无渐速练习记录。"

        val sb = StringBuilder()
        sb.append("「$scoreTitle」${summary.displayMeasures}：")

        when (summary.trend) {
            TempoProgressTrend.IMPROVING -> {
                sb.append("速度正在进步，")
                sb.append("从首次 ${summary.firstRecord?.peakBpm} BPM 提升到 ${summary.latestRecord?.peakBpm} BPM")
                if (summary.bpmImprovement > 0) {
                    sb.append("（+${summary.bpmImprovement} BPM）")
                }
                sb.append("。")
            }
            TempoProgressTrend.STABLE -> {
                sb.append("速度保持稳定在 ${summary.avgPeakBpm.roundToInt()} BPM 附近。")
            }
            TempoProgressTrend.STAGNANT -> {
                sb.append("速度未见明显提升，建议尝试更小的提速增量或增加每步循环遍数。")
            }
            TempoProgressTrend.INSUFFICIENT_DATA -> {
                sb.append("已练习 ${summary.recordCount} 次，继续练习以获得趋势分析。")
            }
        }

        if (summary.hasReachedTarget) {
            sb.append(" 🎉 已达到目标速度 ${summary.latestRecord?.targetBpm} BPM！")
        } else {
            val latest = summary.latestRecord
            if (latest != null) {
                val gap = latest.targetBpm - latest.peakBpm
                if (gap > 0) {
                    sb.append("距目标 ${latest.targetBpm} BPM 还差 $gap BPM。")
                }
            }
        }

        val estimate = estimateSessionsToTarget(scoreTitle, startMeasure, endMeasure, options)
        if (estimate != null && estimate > 0) {
            sb.append("预计还需约 $estimate 次练习。")
        }

        return sb.toString()
    }

    /**
     * 清除所有记录。
     */
    fun clear() {
        _records.clear()
    }

    // ── 内部辅助 ──

    private fun sectionKey(scoreTitle: String, startMeasure: Int, endMeasure: Int): String =
        "$scoreTitle::$startMeasure-$endMeasure"

    /**
     * 计算趋势。
     *
     * 将记录分为早期和近期两半，比较平均峰值速度。
     */
    private fun computeTrend(
        sectionRecords: List<TempoProgressRecord>,
        options: TempoProgressOptions
    ): TempoProgressTrend {
        if (sectionRecords.size < options.minRecordsForTrend) {
            return TempoProgressTrend.INSUFFICIENT_DATA
        }

        val ordered = sectionRecords.sortedBy { it.timestamp }
        val recentCount = maxOf(
            1,
            (ordered.size * options.recentRatio).roundToInt()
        ).coerceAtMost(ordered.size)
        val earlyCount = ordered.size - recentCount

        if (earlyCount == 0) {
            // 所有记录都在「近期」窗口内，尝试与第一项比较
            val firstPeak = ordered.first().peakBpm.toFloat()
            val recentAvg = ordered.drop(1).map { it.peakBpm.toFloat() }.average()
            val delta = recentAvg - firstPeak
            return classifyTrend(delta, options.trendDeltaThreshold)
        }

        val earlyAvg = ordered.take(earlyCount).map { it.peakBpm.toFloat() }.average()
        val recentAvg = ordered.takeLast(recentCount).map { it.peakBpm.toFloat() }.average()
        val delta = recentAvg - earlyAvg

        return classifyTrend(delta, options.trendDeltaThreshold)
    }

    private fun classifyTrend(delta: Double, threshold: Int): TempoProgressTrend {
        return when {
            delta >= threshold -> TempoProgressTrend.IMPROVING
            delta <= -threshold -> TempoProgressTrend.STAGNANT
            else -> TempoProgressTrend.STABLE
        }
    }
}
