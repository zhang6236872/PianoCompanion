package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord

// ──────────────────────────────────────────────────────────────────────────
//  Goal Period & Metric
// ──────────────────────────────────────────────────────────────────────────

/**
 * 目标周期。
 *
 * @param label UI 展示名称
 */
enum class GoalPeriod(val label: String) {
    DAILY("每日"),
    WEEKLY("每周")
}

/**
 * 目标指标类型。每个指标从 [SessionRecord] 提取一个可量化的练习维度。
 *
 * @param label 指标中文名称
 * @param icon 指标 emoji 图标
 * @param unit 目标值单位（用于 UI 展示，如"分钟"/"次"/"%"等）
 */
enum class GoalMetric(val label: String, val icon: String, val unit: String) {
    /** 练习时长（目标单位：分钟） */
    PRACTICE_TIME("练习时长", "⏱️", "分钟"),
    /** 练习次数（目标单位：次） */
    SESSION_COUNT("练习次数", "🔄", "次"),
    /** 演奏音符总数（目标单位：个） */
    NOTES_PLAYED("演奏音符", "🎵", "个"),
    /** 平均准确率（目标单位：0.0~1.0，如 0.85 = 85%） */
    ACCURACY("平均准确率", "🎯", "%"),
    /** 练习不同曲目数（目标单位：首） */
    UNIQUE_PIECES("练习曲目", "🎼", "首")
}

// ──────────────────────────────────────────────────────────────────────────
//  Goal Definition
// ──────────────────────────────────────────────────────────────────────────

/**
 * 单个练习目标的定义。
 *
 * 用户可以定义每日/每周目标，覆盖练习时长、次数、音符数、准确率、曲目数。
 *
 * @param metric 指标类型
 * @param period 周期（每日/每周）
 * @param target 目标值。单位约定：
 *   - [GoalMetric.PRACTICE_TIME]: 分钟（如 30.0 = 30 分钟）
 *   - [GoalMetric.SESSION_COUNT]: 次数（如 2.0 = 2 次）
 *   - [GoalMetric.NOTES_PLAYED]: 个数（如 500.0 = 500 个音符）
 *   - [GoalMetric.ACCURACY]: 0.0~1.0（如 0.85 = 85%）
 *   - [GoalMetric.UNIQUE_PIECES]: 首数（如 3.0 = 3 首曲目）
 */
data class GoalDefinition(
    val metric: GoalMetric,
    val period: GoalPeriod,
    val target: Double
) {
    /**
     * 唯一键，用于持久化（SharedPreferences 存储/读取）。
     */
    val key: String get() = "${period.name}_${metric.name}"

    /**
     * 格式化目标值用于 UI 展示（自动根据指标类型添加单位）。
     */
    fun formatTarget(): String = formatValue(target)

    /**
     * 格式化任意进度值用于 UI 展示。
     */
    fun formatValue(value: Double): String {
        val displayValue = if (metric == GoalMetric.ACCURACY) {
            (value * 100).toInt()
        } else {
            value.toInt()
        }
        return "$displayValue${metric.unit}"
    }
}

// ──────────────────────────────────────────────────────────────────────────
//  Goal Progress
// ──────────────────────────────────────────────────────────────────────────

/**
 * 单个目标在当前周期的进度。
 *
 * @param definition 目标定义
 * @param currentValue 当前周期内的实际值
 */
data class GoalProgress(
    val definition: GoalDefinition,
    val currentValue: Double
) {
    /** 目标值快捷访问 */
    val target: Double get() = definition.target

    /** 是否已达成 */
    val isCompleted: Boolean get() = currentValue >= target

    /** 进度比例（0.0~1.0，已达成为 1.0） */
    val progressRatio: Float get() =
        if (target > 0) (currentValue / target).toFloat().coerceIn(0f, 1f) else 0f

    /** 距离目标还差多少（≥0，已达成为 0） */
    val remaining: Double get() = (target - currentValue).coerceAtLeast(0.0)

    /** 格式化当前值 */
    fun formatCurrent(): String = definition.formatValue(currentValue)

    /** 格式化目标值 */
    fun formatTarget(): String = definition.formatTarget()

    /** 格式化剩余值 */
    fun formatRemaining(): String = definition.formatValue(remaining)

    /**
     * 状态描述。
     * - COMPLETED: 已达成
     * - ON_TRACK: 进度 ≥ 50% 未达成
     * - BEHIND: 进度 < 50%
     */
    fun status(): GoalStatus = when {
        isCompleted -> GoalStatus.COMPLETED
        progressRatio >= 0.5f -> GoalStatus.ON_TRACK
        else -> GoalStatus.BEHIND
    }
}

/**
 * 目标完成状态。
 */
enum class GoalStatus(val label: String, val emoji: String) {
    COMPLETED("已达成", "✅"),
    ON_TRACK("进行中", "💪"),
    BEHIND("需加油", "📌")
}

// ──────────────────────────────────────────────────────────────────────────
//  Goal Report
// ──────────────────────────────────────────────────────────────────────────

/**
 * 一次目标评估的报告，包含所有目标在各自当前周期的进度。
 *
 * @param progresses 所有目标的进度列表（已完成在前，未完成按进度降序）
 * @param dailyGoals 当日目标进度子集（便于 UI 分组）
 * @param weeklyGoals 本周目标进度子集
 * @param dayStartMs 当日 00:00 的 epoch 毫秒
 * @param weekStartMs 本周一 00:00 的 epoch 毫秒
 * @param nowMs 评估时刻的 epoch 毫秒
 */
data class GoalReport(
    val progresses: List<GoalProgress>,
    val dailyGoals: List<GoalProgress>,
    val weeklyGoals: List<GoalProgress>,
    val dayStartMs: Long,
    val weekStartMs: Long,
    val nowMs: Long
) {
    /** 已完成目标数 */
    val completedCount: Int get() = progresses.count { it.isCompleted }

    /** 总目标数 */
    val totalCount: Int get() = progresses.size

    /** 完成比例（0.0~1.0） */
    val completionRatio: Float get() =
        if (progresses.isNotEmpty()) completedCount.toFloat() / progresses.size else 0f

    /** 是否所有目标都已达成 */
    val allCompleted: Boolean get() = progresses.isNotEmpty() && progresses.all { it.isCompleted }

    /** 当日目标完成数 */
    val dailyCompletedCount: Int get() = dailyGoals.count { it.isCompleted }

    /** 本周目标完成数 */
    val weeklyCompletedCount: Int get() = weeklyGoals.count { it.isCompleted }

    /**
     * 最接近完成的未达成目标（进度最高），用于"继续努力"提示。
     * 如果全部已完成则返回 null。
     */
    val nextGoal: GoalProgress? get() =
        progresses.filterNot { it.isCompleted }.maxByOrNull { it.progressRatio }
}

// ──────────────────────────────────────────────────────────────────────────
//  Goal Streak
// ──────────────────────────────────────────────────────────────────────────

/**
 * 单个目标的连续完成天数/周数。
 *
 * @param goal 目标定义
 * @param streak 连续完成的周期数
 * @param bestStreak 历史最长连续周期数
 */
data class GoalStreak(
    val goal: GoalDefinition,
    val streak: Int,
    val bestStreak: Int
) {
    /** 是否有连续完成记录 */
    val hasStreak: Boolean get() = streak > 0
}

// ──────────────────────────────────────────────────────────────────────────
//  Goal Tracker Engine
// ──────────────────────────────────────────────────────────────────────────

/**
 * 练习目标追踪引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 核心职责：
 * 1. **目标评估** [evaluate]：将原始 SessionRecord 列表与目标定义对照，计算每个目标
 *    在当前周期内的进度。
 * 2. **连续完成统计** [computeDailyStreak] / [computeWeeklyStreak]：计算单个目标连续
 *    达成的天数/周数。当前周期未结束时，未达标不中断连续记录。
 * 3. **目标预设** [presets] / [defaultGoals]：提供轻松/适中/挑战三个难度预设包。
 *
 * 周期划分基于 epoch 天数（`DAY_MS = 86_400_000`）的整数除法，与时区无关。
 * 周起始日固定为周一（ISO 8601 约定）。
 */
object GoalTracker {

    /** 一天的毫秒数 */
    const val DAY_MS = 86_400_000L

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 评估所有目标在各自当前周期的进度。
     *
     * @param sessions 全部历史练习会话
     * @param goals 目标定义列表
     * @param now 当前时刻 epoch 毫秒（测试时可注入；默认取系统时间）
     * @return [GoalReport]
     */
    fun evaluate(
        sessions: List<SessionRecord>,
        goals: List<GoalDefinition>,
        now: Long = System.currentTimeMillis()
    ): GoalReport {
        val dayStart = startOfDay(now)
        val weekStart = startOfWeek(now)

        val progresses = goals.map { goal ->
            val (start, end) = when (goal.period) {
                GoalPeriod.DAILY -> dayStart to dayStart + DAY_MS
                GoalPeriod.WEEKLY -> weekStart to weekStart + DAY_MS * 7
            }
            val value = computeMetricValue(sessions, goal.metric, start, end)
            GoalProgress(goal, value)
        }.sortedWith(
            compareByDescending<GoalProgress> { it.isCompleted }
                .thenByDescending { it.progressRatio }
        )

        return GoalReport(
            progresses = progresses,
            dailyGoals = progresses.filter { it.definition.period == GoalPeriod.DAILY },
            weeklyGoals = progresses.filter { it.definition.period == GoalPeriod.WEEKLY },
            dayStartMs = dayStart,
            weekStartMs = weekStart,
            nowMs = now
        )
    }

    /**
     * 计算每日目标的连续达成天数。
     *
     * 规则：
     * - 从今天开始往回数。如果今天已达标，计入连续天数。
     * - 如果今天尚未达标（当天还在进行中），跳过今天，从昨天开始计数。
     *   这样当天未完成不会中断已有的连续记录。
     * - 一旦某天未达标，停止计数。
     *
     * @param sessions 全部历史练习会话
     * @param goal 目标定义（仅评估 [GoalPeriod.DAILY] 目标）
     * @param now 当前时刻 epoch 毫秒
     * @return 连续达成天数
     */
    fun computeDailyStreak(
        sessions: List<SessionRecord>,
        goal: GoalDefinition,
        now: Long = System.currentTimeMillis()
    ): Int {
        if (goal.period != GoalPeriod.DAILY) return 0
        val todayDay = now / DAY_MS

        var streak = 0

        // 检查今天
        val todayValue = computeMetricValue(sessions, goal.metric, todayDay * DAY_MS, (todayDay + 1) * DAY_MS)
        if (todayValue >= goal.target) {
            streak++
        }
        // 无论今天是否达标，都从昨天开始往回数
        // （今天未达标不中断已有连续记录，因为当天还在进行中）
        var checkDay = todayDay - 1

        // 往回数连续达标的天数
        while (checkDay >= 0) {
            val value = computeMetricValue(sessions, goal.metric, checkDay * DAY_MS, (checkDay + 1) * DAY_MS)
            if (value >= goal.target) {
                streak++
                checkDay--
            } else {
                break
            }
        }
        return streak
    }

    /**
     * 计算每周目标的连续达成周数。
     *
     * 规则同 [computeDailyStreak]，但以周为单位。本周未达标不中断连续记录。
     *
     * @param sessions 全部历史练习会话
     * @param goal 目标定义（仅评估 [GoalPeriod.WEEKLY] 目标）
     * @param now 当前时刻 epoch 毫秒
     * @return 连续达成周数
     */
    fun computeWeeklyStreak(
        sessions: List<SessionRecord>,
        goal: GoalDefinition,
        now: Long = System.currentTimeMillis()
    ): Int {
        if (goal.period != GoalPeriod.WEEKLY) return 0
        val thisWeekStartDay = startOfWeek(now) / DAY_MS

        var streak = 0
        var checkWeekStartDay = thisWeekStartDay

        // 检查本周
        val weekValue = computeMetricValue(
            sessions, goal.metric,
            checkWeekStartDay * DAY_MS,
            (checkWeekStartDay + 7) * DAY_MS
        )
        if (weekValue >= goal.target) {
            streak++
        }
        // 无论本周是否达标，都从上周开始往回数
        checkWeekStartDay -= 7

        while (checkWeekStartDay >= 0) {
            val value = computeMetricValue(
                sessions, goal.metric,
                checkWeekStartDay * DAY_MS,
                (checkWeekStartDay + 7) * DAY_MS
            )
            if (value >= goal.target) {
                streak++
                checkWeekStartDay -= 7
            } else {
                break
            }
        }
        return streak
    }

    /**
     * 计算目标的历史最长连续达成周期数。
     *
     * @param sessions 全部历史练习会话
     * @param goal 目标定义
     * @param now 当前时刻 epoch 毫秒
     * @return 历史最长连续达成周期数
     */
    fun computeBestStreak(
        sessions: List<SessionRecord>,
        goal: GoalDefinition,
        now: Long = System.currentTimeMillis()
    ): Int {
        val periodDays = when (goal.period) {
            GoalPeriod.DAILY -> 1
            GoalPeriod.WEEKLY -> 7
        }
        val currentDay = now / DAY_MS
        // 找到最早有练习记录的天数
        val earliestDay = sessions.minOfOrNull { it.startTime / DAY_MS } ?: return 0
        if (earliestDay > currentDay) return 0

        var best = 0
        var current = 0
        var day = earliestDay
        // 对齐到周期起点（周目标对齐到周一）
        if (goal.period == GoalPeriod.WEEKLY) {
            day -= daysSinceMonday(day)
        }

        while (day <= currentDay) {
            val periodEnd = day + periodDays
            val value = computeMetricValue(sessions, goal.metric, day * DAY_MS, periodEnd * DAY_MS)
            if (value >= goal.target) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
            day += periodDays
        }
        return best
    }

    /**
     * 一次性计算目标在当前和历史中的连续完成数据。
     */
    fun computeStreak(
        sessions: List<SessionRecord>,
        goal: GoalDefinition,
        now: Long = System.currentTimeMillis()
    ): GoalStreak {
        val current = when (goal.period) {
            GoalPeriod.DAILY -> computeDailyStreak(sessions, goal, now)
            GoalPeriod.WEEKLY -> computeWeeklyStreak(sessions, goal, now)
        }
        val best = computeBestStreak(sessions, goal, now)
        return GoalStreak(goal, current, best)
    }

    // ── 目标预设 ────────────────────────────────────────────────────

    /**
     * 三个难度预设包：轻松 / 适中 / 挑战。
     */
    fun presets(): Map<String, List<GoalDefinition>> = mapOf(
        "轻松" to listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 15.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 1.0)
        ),
        "适中" to listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0),
            GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85),
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0)
        ),
        "挑战" to listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 60.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 3.0),
            GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.90),
            GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.WEEKLY, 5.0),
            GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.WEEKLY, 2000.0)
        )
    )

    /**
     * 默认目标（适中预设）。用户未自定义时使用。
     */
    fun defaultGoals(): List<GoalDefinition> = presets()["适中"]!!

    // ── 内部计算工具 ────────────────────────────────────────────────

    /**
     * 计算指定时间范围内某个指标的值。
     */
    private fun computeMetricValue(
        sessions: List<SessionRecord>,
        metric: GoalMetric,
        startMs: Long,
        endMs: Long
    ): Double {
        val periodSessions = sessions.filter { it.startTime >= startMs && it.startTime < endMs }
        return when (metric) {
            GoalMetric.PRACTICE_TIME ->
                periodSessions.sumOf { it.durationMs }.toDouble() / 60_000.0
            GoalMetric.SESSION_COUNT ->
                periodSessions.size.toDouble()
            GoalMetric.NOTES_PLAYED ->
                periodSessions.sumOf { it.totalNotes }.toDouble()
            GoalMetric.ACCURACY ->
                if (periodSessions.isEmpty()) 0.0
                else periodSessions.map { it.accuracy }.average()
            GoalMetric.UNIQUE_PIECES ->
                periodSessions.map { it.scoreTitle }.distinct().size.toDouble()
        }
    }

    /**
     * 返回某天 00:00 的 epoch 毫秒。
     */
    private fun startOfDay(now: Long): Long {
        return (now / DAY_MS) * DAY_MS
    }

    /**
     * 返回当前周周一 00:00 的 epoch 毫秒。
     *
     * 基于 epoch 天数计算星期几。1970-01-01（epoch day 0）是星期四，
     * 所以 `(dayNumber + 3) % 7` 得到 0=星期一的 dayOfWeek。
     */
    private fun startOfWeek(now: Long): Long {
        val dayNumber = now / DAY_MS
        val mondayOffset = daysSinceMonday(dayNumber)
        return (dayNumber - mondayOffset) * DAY_MS
    }

    /**
     * 给定 epoch 天数，返回距离最近周一的天数偏移（0=周一，1=周二，...，6=周日）。
     */
    private fun daysSinceMonday(dayNumber: Long): Long {
        return (((dayNumber + 3) % 7) + 7) % 7
    }
}
