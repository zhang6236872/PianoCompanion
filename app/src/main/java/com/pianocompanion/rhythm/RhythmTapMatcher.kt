package com.pianocompanion.rhythm

/**
 * 敲击匹配器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将用户的敲击时间戳与目标节奏的起音时间比较，计算准确度。
 *
 * 匹配算法：
 * 1. 将用户敲击和目标起音分别按时间排序
 * 2. 对每个目标起音，在用户敲击中找到时间差最小的未匹配敲击
 * 3. 根据时间误差分类：Perfect（±perfectWindow）/ Good（±goodWindow）/ Miss（超出）
 * 4. 统计多余敲击（未匹配到任何目标的用户敲击）
 * 5. 综合评分
 */
class RhythmTapMatcher(
    /** Perfect 判定窗口（毫秒），默认 ±80ms。 */
    val perfectWindowMs: Long = 80L,
    /** Good 判定窗口（毫秒），默认 ±150ms。 */
    val goodWindowMs: Long = 150L
) {
    /**
     * 匹配用户敲击与目标节奏。
     *
     * @param targetOnsets 目标起音列表（非休止事件）
     * @param userTaps 用户敲击时间戳列表（毫秒，相对于敲击阶段开始）
     * @return 匹配结果
     */
    fun match(
        targetOnsets: List<OnsetTime>,
        userTaps: List<Long>
    ): TapMatchResult {
        if (targetOnsets.isEmpty()) {
            return TapMatchResult(
                score = 0.0,
                grade = TapGrade.TRY_AGAIN,
                perfectHits = 0,
                goodHits = 0,
                missedNotes = 0,
                extraTaps = userTaps.size,
                totalTargets = 0,
                timingErrors = emptyList()
            )
        }

        // 按时间排序
        val sortedTargets = targetOnsets.sortedBy { it.onsetMs }
        val sortedTaps = userTaps.sorted()

        // 贪心匹配：对每个目标找最近的未使用敲击
        val usedTaps = BooleanArray(sortedTaps.size) { false }
        val timingErrors = mutableListOf<Long>()
        var perfectHits = 0
        var goodHits = 0
        var missedNotes = 0

        for (target in sortedTargets) {
            var bestTapIdx = -1
            var bestError = Long.MAX_VALUE

            for (j in sortedTaps.indices) {
                if (usedTaps[j]) continue
                val error = kotlin.math.abs(sortedTaps[j] - target.onsetMs)
                if (error < bestError) {
                    bestError = error
                    bestTapIdx = j
                }
            }

            if (bestTapIdx >= 0 && bestError <= goodWindowMs) {
                usedTaps[bestTapIdx] = true
                timingErrors.add(sortedTaps[bestTapIdx] - target.onsetMs)
                if (bestError <= perfectWindowMs) {
                    perfectHits++
                } else {
                    goodHits++
                }
            } else {
                missedNotes++
                timingErrors.add(Long.MAX_VALUE) // 用 MAX_VALUE 表示完全错过
            }
        }

        val extraTaps = usedTaps.count { !it }
        val totalTargets = sortedTargets.size

        // 评分计算：
        // - Perfect = 1.0 分，Good = 0.6 分
        // - 基础分 = (perfect * 1.0 + good * 0.6) / totalTargets
        // - 多余敲击惩罚：每个多余敲击扣 0.15 分（最低 0 分）
        val baseScore = (perfectHits * 1.0 + goodHits * 0.6) / totalTargets
        val penalty = extraTaps * 0.15
        val score = (baseScore - penalty).coerceIn(0.0, 1.0)

        val grade = when {
            score >= 0.9 && missedNotes == 0 -> TapGrade.PERFECT
            score >= 0.75 -> TapGrade.GREAT
            score >= 0.5 -> TapGrade.GOOD
            else -> TapGrade.TRY_AGAIN
        }

        return TapMatchResult(
            score = score,
            grade = grade,
            perfectHits = perfectHits,
            goodHits = goodHits,
            missedNotes = missedNotes,
            extraTaps = extraTaps,
            totalTargets = totalTargets,
            timingErrors = timingErrors
        )
    }
}

/**
 * 敲击匹配结果。
 */
data class TapMatchResult(
    /** 综合准确度评分（0.0-1.0）。 */
    val score: Double,
    /** 评级。 */
    val grade: TapGrade,
    /** Perfect 命中数（±perfectWindow 内）。 */
    val perfectHits: Int,
    /** Good 命中数（±goodWindow 内）。 */
    val goodHits: Int,
    /** 遗漏的目标音符数（未敲击到）。 */
    val missedNotes: Int,
    /** 多余敲击数（敲了但没有对应目标）。 */
    val extraTaps: Int,
    /** 目标音符总数。 */
    val totalTargets: Int,
    /** 每个目标的定时误差（毫秒，正=偏晚，负=偏早；[Long.MAX_VALUE] 表示完全错过）。 */
    val timingErrors: List<Long>
) {
    /** 命中总数（perfect + good）。 */
    val totalHits: Int get() = perfectHits + goodHits

    /** 是否为满分（无遗漏、无多余、全部 perfect）。 */
    val isPerfect: Boolean get() = missedNotes == 0 && extraTaps == 0 && goodHits == 0

    /** 准确率百分比字符串。 */
    val scorePercent: String get() = "%.0f%%".format(score * 100)
}

/**
 * 节奏评级。
 */
enum class TapGrade(val displayName: String, val emoji: String) {
    PERFECT("完美", "🎯"),
    GREAT("很棒", "🎉"),
    GOOD("不错", "👍"),
    TRY_AGAIN("加油", "💪")
}
