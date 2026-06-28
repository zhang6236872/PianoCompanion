package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord

/**
 * 练习日历热力图 —— GitHub 风格的贡献热力图网格。
 *
 * 按日聚合练习会话的活动量（总时长 + 次数），生成「周(列) × 7 天(行)」的网格，
 * 直观展示用户的练习连续性与强度分布。配合现有的连续天数(streak)、成就、目标等
 * 游戏化系统，提供一目了然的练习习惯可视化。
 *
 * 本引擎为**纯 Kotlin**（无 Android 依赖），完全可单元测试。所有时间计算基于
 * UTC epoch day（`epochMs / MILLIS_PER_DAY`），避免时区/Calendar 造成的非确定性，
 * 测试只需传入固定的 `nowEpochMs` 即可复现。
 */
object PracticeCalendarHeatmap {

    /** 一天的毫秒数。 */
    const val MILLIS_PER_DAY = 86_400_000L

    /** 默认展示的周数（约 4 个月）。 */
    const val DEFAULT_WEEKS = 18

    /**
     * 强度等级下界阈值（毫秒），用于 [computeLevel]。
     *
     * 含义（钢琴练习合理档位）：
     * - Level 1 下界: ≥ 1 分钟（轻量热身）
     * - Level 2 下界: ≥ 5 分钟（短时练习）
     * - Level 3 下界: ≥ 15 分钟（正常练习）
     * - Level 4 下界: ≥ 30 分钟（集中练习）
     *
     * Level 0 = 无练习（0 毫秒）。任何 >0 的练习至少为 Level 1。
     */
    val LEVEL_THRESHOLDS_MS: LongArray = longArrayOf(
        1 * 60_000L,    // 1 分钟
        5 * 60_000L,    // 5 分钟
        15 * 60_000L,   // 15 分钟
        30 * 60_000L    // 30 分钟
    )

    /**
     * 将 epoch day 映射为 ISO 星期几（0=Monday … 6=Sunday）。
     *
     * 推导：epoch day 0（1970-01-01）是 Thursday。
     * 设 Monday=0，则 Thursday=3，故 `isoDay = (epochDay + 3) mod 7`。
     * 验证：epochDay 4 → (4+3)%7=0 → Monday（1970-01-05 确为周一）✓。
     */
    fun isoDayOfWeek(epochDay: Long): Int {
        val r = ((epochDay + 3) % 7 + 7) % 7
        return r.toInt()
    }

    /**
     * 计算单日总练习时长对应的强度等级 0–4。
     *
     * - 0: 无练习（durationMs ≤ 0）
     * - 1: 1–5 分钟
     * - 2: 5–15 分钟
     * - 3: 15–30 分钟
     * - 4: ≥30 分钟
     */
    fun computeLevel(durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        return when {
            durationMs >= LEVEL_THRESHOLDS_MS[3] -> 4
            durationMs >= LEVEL_THRESHOLDS_MS[2] -> 3
            durationMs >= LEVEL_THRESHOLDS_MS[1] -> 2
            else -> 1
        }
    }

    /**
     * 按天聚合会话，返回 epochDay → [DayActivity] 映射。
     *
     * 仅统计活动量，不限定范围（范围过滤在 [build] 中完成）。
     */
    fun bucketByDay(sessions: List<SessionRecord>): Map<Long, DayActivity> {
        return sessions
            .filter { it.durationMs > 0L }  // 忽略零时长脏数据
            .groupBy { it.startTime / MILLIS_PER_DAY }
            .mapValues { (day, sess) ->
                DayActivity(
                    epochDay = day,
                    durationMs = sess.sumOf { it.durationMs },
                    sessionCount = sess.size
                )
            }
    }

    /**
     * 计算给定范围内最长的**连续练习天数**（consecutive active days）。
     *
     * @param activeDays 有练习的 epochDay 集合（范围外的不影响——会被 [day] in 范围过滤掉，
     *   但本函数本身不限制范围，调用方应传入已过滤的集合）。
     */
    fun longestStreak(activeDays: Collection<Long>): Int {
        if (activeDays.isEmpty()) return 0
        val sorted = activeDays.sorted()
        var maxRun = 0
        var run = 0
        var prev: Long? = null
        for (day in sorted) {
            run = if (prev != null && day == prev + 1L) run + 1 else 1
            if (run > maxRun) maxRun = run
            prev = day
        }
        return maxRun
    }

    /**
     * 构建完整的练习热力图。
     *
     * @param sessions 全部练习会话（引擎内部按天聚合 + 范围过滤）。
     * @param nowEpochMs 参考当前时间（epoch ms）。网格右边界 = 当天。传入固定值可在测试中复现。
     * @param weeks 展示的周数（列数），默认 [DEFAULT_WEEKS]。必须 > 0。
     * @param weekStartIsoDay 每周的起始日（ISO: 0=Monday … 6=Sunday），默认周一。
     * @return [PracticeHeatmap]，包含网格列与汇总统计。
     */
    fun build(
        sessions: List<SessionRecord>,
        nowEpochMs: Long,
        weeks: Int = DEFAULT_WEEKS,
        weekStartIsoDay: Int = 0
    ): PracticeHeatmap {
        require(weeks > 0) { "weeks must be > 0 (got $weeks)" }
        require(weekStartIsoDay in 0..6) { "weekStartIsoDay must be 0..6 (got $weekStartIsoDay)" }

        val todayEpochDay = nowEpochMs / MILLIS_PER_DAY
        val todayIsoDay = isoDayOfWeek(todayEpochDay)

        // 当前周首日（epoch day）
        val currentWeekStart = todayEpochDay - ((todayIsoDay - weekStartIsoDay + 7) % 7)
        // 网格首日（最早一周的起始日）
        val firstDay = currentWeekStart - (weeks - 1) * 7L
        // 网格最后一天（不含未来）
        val lastDay = todayEpochDay

        // 按天聚合，仅保留范围内的活动
        val allActivities = bucketByDay(sessions)
        val dayActivity = allActivities
            .filterKeys { it in firstDay..lastDay }

        // 构建 weeks × 7 网格
        val columns = (0 until weeks).map { w ->
            val weekStart = firstDay + w * 7L
            val cells = (0 until 7).map { d ->
                val day = weekStart + d
                val act = dayActivity[day]
                if (act != null && day <= lastDay) {
                    HeatmapCell(
                        epochDay = day,
                        durationMs = act.durationMs,
                        sessionCount = act.sessionCount,
                        level = computeLevel(act.durationMs)
                    )
                } else {
                    // 无练习 或 未来日期
                    HeatmapCell(epochDay = day, durationMs = 0L, sessionCount = 0, level = 0)
                }
            }
            HeatmapColumn(weekStartEpochDay = weekStart, cells = cells)
        }

        // 汇总统计
        val activeCells = dayActivity.values.sortedByDescending { it.durationMs }
        val activeDays = dayActivity.size
        val totalDurationMs = dayActivity.values.sumOf { it.durationMs }
        val longest = longestStreak(dayActivity.keys)
        val best = activeCells.firstOrNull()
        val bestCell = best?.let {
            HeatmapCell(it.epochDay, it.durationMs, it.sessionCount, computeLevel(it.durationMs))
        }
        val maxDayDurationMs = activeCells.firstOrNull()?.durationMs ?: 0L

        return PracticeHeatmap(
            columns = columns,
            weeks = weeks,
            firstEpochDay = firstDay,
            lastEpochDay = lastDay,
            activeDays = activeDays,
            totalDurationMs = totalDurationMs,
            longestStreak = longest,
            bestDay = bestCell,
            maxDayDurationMs = maxDayDurationMs
        )
    }
}

/**
 * 单日的聚合活动量。
 *
 * @param epochDay 该天对应的 epoch day（`startTime / MILLIS_PER_DAY`）。
 * @param durationMs 当天所有会话的总时长（毫秒）。
 * @param sessionCount 当天的会话数。
 */
data class DayActivity(
    val epochDay: Long,
    val durationMs: Long,
    val sessionCount: Int
)

/**
 * 热力图网格中的单个格子（一天）。
 *
 * @param epochDay 该天对应的 epoch day。
 * @param durationMs 当天总练习时长（毫秒）；0 表示无练习。
 * @param sessionCount 当天会话数。
 * @param level 强度等级 0–4（见 [PracticeCalendarHeatmap.computeLevel]）。
 */
data class HeatmapCell(
    val epochDay: Long,
    val durationMs: Long,
    val sessionCount: Int,
    val level: Int
) {
    /** 该天是否有练习。 */
    val isActive: Boolean get() = durationMs > 0L
}

/**
 * 热力图的一列（一周）。
 *
 * @param weekStartEpochDay 该周起始日的 epoch day。
 * @param cells 该周 7 天的格子，按星期顺序排列（首个元素 = [weekStartEpochDay] 对应的星期）。
 */
data class HeatmapColumn(
    val weekStartEpochDay: Long,
    val cells: List<HeatmapCell>
)

/**
 * 完整的练习热力图结果。
 *
 * @param columns 网格列（按时间从左到右排列，最早 → 最近）。
 * @param weeks 网格列数。
 * @param firstEpochDay 网格首日（最早一天的 epoch day）。
 * @param lastEpochDay 网格最后一天（= 当天）。
 * @param activeDays 范围内有练习的天数。
 * @param totalDurationMs 范围内总练习时长（毫秒）。
 * @param longestStreak 范围内最长连续练习天数。
 * @param bestDay 范围内练习时长最长的一天（无练习则为 null）。
 * @param maxDayDurationMs 范围内单日最大练习时长（用于 UI 阈值参考）。
 */
data class PracticeHeatmap(
    val columns: List<HeatmapColumn>,
    val weeks: Int,
    val firstEpochDay: Long,
    val lastEpochDay: Long,
    val activeDays: Int,
    val totalDurationMs: Long,
    val longestStreak: Int,
    val bestDay: HeatmapCell?,
    val maxDayDurationMs: Long
) {
    /** 总格数 = 周数 × 7。 */
    val totalCells: Int get() = weeks * 7

    /** 活跃率 = 活跃天数 / 范围总天数（含今天），范围 [0,1]。 */
    val activeRate: Float
        get() {
            val span = (lastEpochDay - firstEpochDay + 1).coerceAtLeast(1)
            return activeDays.toFloat() / span
        }

    /**
     * 生成一句话摘要，供 UI 卡片展示。
     * 例：「过去 18 周练习了 42 天，累计 12 小时 30 分，最长连续 7 天」
     */
    fun summary(): String {
        val days = activeDays
        val dur = formatDuration(totalDurationMs)
        return "过去 $weeks 周练习了 $days 天，累计 $dur，最长连续 $longestStreak 天"
    }

    companion object {
        /** 将毫秒时长格式化为「X小时Y分钟」或「Y分钟」（向下取整）。 */
        fun formatDuration(ms: Long): String {
            val totalMin = ms / 60_000L
            val hours = totalMin / 60
            val minutes = totalMin % 60
            return if (hours > 0) "$hours 小时 $minutes 分" else "$minutes 分"
        }
    }
}
