package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord

// ──────────────────────────────────────────────────────────────────────────
//  Achievement Categories
// ──────────────────────────────────────────────────────────────────────────

/**
 * 成就分类。
 *
 * 每个分类对应一个维度的练习目标，用于在 UI 中分组展示。
 */
enum class AchievementCategory(val label: String, val icon: String) {
    STREAK("坚持练习", "🔥"),
    VOLUME("练习量", "📚"),
    ACCURACY("准确度", "🎯"),
    NOTES("音符演奏", "🎵"),
    REPERTOIRE("曲目探索", "🎼"),
    TIME("练习时长", "⏱️"),
    TEMPO("速度突破", "🚀")
}

// ──────────────────────────────────────────────────────────────────────────
//  Achievement Definition
// ──────────────────────────────────────────────────────────────────────────

/**
 * 单个成就的定义。
 *
 * @param id 全局唯一标识符（用于持久化已解锁集合）
 * @param title 成就名称（中文）
 * @param description 成就描述（中文，展示目标）
 * @param category 所属分类
 * @param target 达成目标值（[metric] 函数返回值 ≥ [target] 即解锁）
 * @param metric 从 [PracticeProfile] 提取当前进度的函数
 */
data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val target: Double,
    val metric: (PracticeProfile) -> Double
) {
    /**
     * 格式化目标值用于 UI 展示（时间类自动转分钟/小时，准确率类转百分比）。
     */
    fun formatTarget(): String = formatMetricValue(target)

    /**
     * 格式化任意进度值用于 UI 展示。
     */
    fun formatMetricValue(value: Double): String = when (category) {
        AchievementCategory.TIME -> {
            val minutes = (value / 60_000.0)
            if (minutes >= 60) "${(minutes / 60).toInt()}小时" else "${minutes.toInt()}分钟"
        }
        AchievementCategory.ACCURACY -> {
            if (id == "PITCH_PERFECT") "${(value * 100).toInt()}%"
            else value.toInt().toString()
        }
        else -> value.toInt().toString()
    }
}

// ──────────────────────────────────────────────────────────────────────────
//  Practice Profile (aggregated metrics from raw session data)
// ──────────────────────────────────────────────────────────────────────────

/**
 * 从原始练习会话聚合出的统计画像，供 [AchievementEngine] 评估成就进度。
 *
 * 所有字段都已预先聚合好，成就引擎只需做简单的比较即可——这使得
 * 成就定义与数据聚合解耦，易于测试和扩展。
 *
 * @param totalSessions 总练习次数
 * @param bestStreakDays 历史最长连续练习天数（成就解锁用 bestStreak，已解锁不会丢失）
 * @param currentStreakDays 当前连续练习天数（仅用于展示）
 * @param totalPracticeMs 总练习时长（毫秒）
 * @param totalNotesPlayed 总演奏音符数
 * @param bestAccuracy 历史最高单次准确率（0~1）
 * @param highAccuracyCount90 准确率 ≥ 90% 的会话数
 * @param highAccuracyCount95 准确率 ≥ 95% 的会话数（对应 PERFECT 评级）
 * @param distinctScores 练习过的不同曲目数
 * @param tempoSessionsCompleted 达到目标速度的渐速练习次数（TempoProgressRecord.completed=true）
 */
data class PracticeProfile(
    val totalSessions: Int = 0,
    val bestStreakDays: Int = 0,
    val currentStreakDays: Int = 0,
    val totalPracticeMs: Long = 0L,
    val totalNotesPlayed: Int = 0,
    val bestAccuracy: Float = 0f,
    val highAccuracyCount90: Int = 0,
    val highAccuracyCount95: Int = 0,
    val distinctScores: Int = 0,
    val tempoSessionsCompleted: Int = 0
)

/**
 * 从原始 [SessionRecord] 列表（+ 可选的 [TempoProgressRecord]）构建 [PracticeProfile]。
 *
 * 包含连续天数（streak）计算逻辑：
 * - **当前连续天数**：从今天往回数，连续有练习记录的天数
 * - **历史最长连续天数**：所有练习天中，最长的连续天数段
 */
object PracticeProfileBuilder {

    /** 一天的毫秒数。 */
    private const val DAY_MS = 86_400_000L

    /**
     * 从会话记录构建练习画像。
     *
     * @param sessions 全部练习会话记录
     * @param tempoRecords 渐速练习记录（可选，用于速度成就）
     */
    fun fromSessions(
        sessions: List<SessionRecord>,
        tempoRecords: List<TempoProgressRecord> = emptyList()
    ): PracticeProfile {
        if (sessions.isEmpty() && tempoRecords.isEmpty()) {
            return PracticeProfile()
        }

        return PracticeProfile(
            totalSessions = sessions.size,
            bestStreakDays = computeBestStreak(sessions),
            currentStreakDays = computeCurrentStreak(sessions),
            totalPracticeMs = sessions.sumOf { it.durationMs },
            totalNotesPlayed = sessions.sumOf { it.totalNotes },
            bestAccuracy = sessions.maxOfOrNull { it.accuracy } ?: 0f,
            highAccuracyCount90 = sessions.count { it.accuracy >= 0.90f },
            highAccuracyCount95 = sessions.count { it.accuracy >= 0.95f },
            distinctScores = sessions.map { it.scoreTitle }.distinct().size,
            tempoSessionsCompleted = tempoRecords.count { it.completed }
        )
    }

    /**
     * 计算历史最长连续练习天数。
     *
     * 将所有练习日期去重排序，找出最长的连续日期段。
     */
    fun computeBestStreak(sessions: List<SessionRecord>): Int {
        if (sessions.isEmpty()) return 0
        val days = sessions.map { it.startTime / DAY_MS }.toSet().sorted()
        var best = 1
        var current = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) {
                current++
                best = maxOf(best, current)
            } else {
                current = 1
            }
        }
        return best
    }

    /**
     * 计算当前连续练习天数（从今天往回数）。
     *
     * 与 [com.pianocompanion.ui.stats.StatsViewModel] 中的逻辑一致。
     */
    fun computeCurrentStreak(sessions: List<SessionRecord>): Int {
        if (sessions.isEmpty()) return 0
        val today = System.currentTimeMillis() / DAY_MS
        val days = sessions.map { it.startTime / DAY_MS }.toSet().sortedDescending()
        var streak = 0
        var expected = today
        for (day in days) {
            if (day == expected) {
                streak++
                expected--
            } else break
        }
        return streak
    }
}

// ──────────────────────────────────────────────────────────────────────────
//  Achievement Progress & Summary
// ──────────────────────────────────────────────────────────────────────────

/**
 * 单个成就的评估结果（进度）。
 *
 * @param definition 成就定义
 * @param currentValue 当前已达到的值（来自 [PracticeProfile]）
 */
data class AchievementProgress(
    val definition: AchievementDefinition,
    val currentValue: Double
) {
    /** 是否已解锁（当前值 ≥ 目标值）。 */
    val isUnlocked: Boolean get() = currentValue >= definition.target

    /** 目标值。 */
    val target: Double get() = definition.target

    /** 进度比例 [0, 1]，已解锁时为 1。 */
    val progressRatio: Float
        get() = if (definition.target <= 0) 1f
        else (currentValue / definition.target).toFloat().coerceIn(0f, 1f)

    /** 当前值的格式化字符串。 */
    fun formatCurrentValue(): String = definition.formatMetricValue(currentValue)
}

/**
 * 全部成就的汇总结果。
 *
 * @param all 全部成就进度列表（按分类排序）
 * @param unlocked 已解锁的成就进度列表
 * @param locked 未解锁的成就进度列表（含进度条）
 */
data class AchievementSummary(
    val all: List<AchievementProgress>,
    val unlocked: List<AchievementProgress>,
    val locked: List<AchievementProgress>
) {
    /** 成就总数。 */
    val totalCount: Int get() = all.size

    /** 已解锁数量。 */
    val unlockedCount: Int get() = unlocked.size

    /** 完成比例 [0, 1]。 */
    val completionRatio: Float
        get() = if (totalCount == 0) 0f else unlockedCount.toFloat() / totalCount

    /** 按分类分组的成就进度。 */
    fun byCategory(): Map<AchievementCategory, List<AchievementProgress>> =
        all.groupBy { it.definition.category }

    /**
     * 比较两个快照，返回新解锁的成就（[previousIds] 中没有但现在已解锁的）。
     *
     * @param previousIds 上一次已解锁的成就 id 集合
     */
    fun newlyUnlocked(previousIds: Set<String>): List<AchievementProgress> =
        unlocked.filter { it.definition.id !in previousIds }
}

// ──────────────────────────────────────────────────────────────────────────
//  Achievement Engine
// ──────────────────────────────────────────────────────────────────────────

/**
 * 成就评估引擎。
 *
 * 纯 Kotlin，无 Android 依赖，完全可单元测试。
 *
 * 用法：
 * ```
 * val profile = PracticeProfileBuilder.fromSessions(sessions, tempoRecords)
 * val summary = AchievementEngine.evaluate(profile)
 * ```
 */
object AchievementEngine {

    /**
     * 全部成就定义（22 个，覆盖 7 个维度）。
     */
    val DEFINITIONS: List<AchievementDefinition> = buildList {

        // ── 坚持练习（streak） ──
        addAll(listOf(
            AchievementDefinition(
                id = "FIRST_STEPS",
                title = "迈出第一步",
                description = "完成第一次练习",
                category = AchievementCategory.STREAK,
                target = 1.0,
                metric = { it.totalSessions.toDouble() }
            ),
            AchievementDefinition(
                id = "DAILY_HABIT",
                title = "日常习惯",
                description = "连续练习 7 天",
                category = AchievementCategory.STREAK,
                target = 7.0,
                metric = { it.bestStreakDays.toDouble() }
            ),
            AchievementDefinition(
                id = "FORTNIGHT",
                title = "坚持两周",
                description = "连续练习 14 天",
                category = AchievementCategory.STREAK,
                target = 14.0,
                metric = { it.bestStreakDays.toDouble() }
            ),
            AchievementDefinition(
                id = "MONTHLY_DEVOTION",
                title = "月度坚持",
                description = "连续练习 30 天",
                category = AchievementCategory.STREAK,
                target = 30.0,
                metric = { it.bestStreakDays.toDouble() }
            ),
            AchievementDefinition(
                id = "IRON_WILL",
                title = "钢铁意志",
                description = "连续练习 100 天",
                category = AchievementCategory.STREAK,
                target = 100.0,
                metric = { it.bestStreakDays.toDouble() }
            )
        ))

        // ── 练习量（volume） ──
        addAll(listOf(
            AchievementDefinition(
                id = "GETTING_WARM",
                title = "渐入佳境",
                description = "完成 10 次练习",
                category = AchievementCategory.VOLUME,
                target = 10.0,
                metric = { it.totalSessions.toDouble() }
            ),
            AchievementDefinition(
                id = "CENTURY",
                title = "百场练习",
                description = "完成 100 次练习",
                category = AchievementCategory.VOLUME,
                target = 100.0,
                metric = { it.totalSessions.toDouble() }
            ),
            AchievementDefinition(
                id = "FIVE_HUNDRED",
                title = "五百场成就",
                description = "完成 500 次练习",
                category = AchievementCategory.VOLUME,
                target = 500.0,
                metric = { it.totalSessions.toDouble() }
            )
        ))

        // ── 准确度（accuracy） ──
        addAll(listOf(
            AchievementDefinition(
                id = "PITCH_PERFECT",
                title = "完美音准",
                description = "在一次练习中达到 100% 准确率",
                category = AchievementCategory.ACCURACY,
                target = 1.0,
                metric = { it.bestAccuracy.toDouble() }
            ),
            AchievementDefinition(
                id = "CONSISTENT",
                title = "稳定发挥",
                description = "在 10 次练习中达到 90%+ 准确率",
                category = AchievementCategory.ACCURACY,
                target = 10.0,
                metric = { it.highAccuracyCount90.toDouble() }
            ),
            AchievementDefinition(
                id = "VIRTUOSO_ACCURACY",
                title = "大师级精准",
                description = "在 20 次练习中达到 95%+ 准确率",
                category = AchievementCategory.ACCURACY,
                target = 20.0,
                metric = { it.highAccuracyCount95.toDouble() }
            )
        ))

        // ── 音符演奏（notes） ──
        addAll(listOf(
            AchievementDefinition(
                id = "CENTURY_NOTES",
                title = "百音齐发",
                description = "累计演奏 100 个音符",
                category = AchievementCategory.NOTES,
                target = 100.0,
                metric = { it.totalNotesPlayed.toDouble() }
            ),
            AchievementDefinition(
                id = "THOUSAND_NOTES",
                title = "千音奏响",
                description = "累计演奏 1000 个音符",
                category = AchievementCategory.NOTES,
                target = 1000.0,
                metric = { it.totalNotesPlayed.toDouble() }
            ),
            AchievementDefinition(
                id = "TEN_THOUSAND_NOTES",
                title = "万音大师",
                description = "累计演奏 10000 个音符",
                category = AchievementCategory.NOTES,
                target = 10000.0,
                metric = { it.totalNotesPlayed.toDouble() }
            )
        ))

        // ── 曲目探索（repertoire） ──
        addAll(listOf(
            AchievementDefinition(
                id = "EXPLORER",
                title = "初探曲目",
                description = "练习 5 首不同的曲目",
                category = AchievementCategory.REPERTOIRE,
                target = 5.0,
                metric = { it.distinctScores.toDouble() }
            ),
            AchievementDefinition(
                id = "COLLECTOR",
                title = "曲目收藏家",
                description = "练习 10 首不同的曲目",
                category = AchievementCategory.REPERTOIRE,
                target = 10.0,
                metric = { it.distinctScores.toDouble() }
            ),
            AchievementDefinition(
                id = "REPERTOIRE_MASTER",
                title = "曲目大师",
                description = "练习 20 首不同的曲目",
                category = AchievementCategory.REPERTOIRE,
                target = 20.0,
                metric = { it.distinctScores.toDouble() }
            )
        ))

        // ── 练习时长（time） ──
        addAll(listOf(
            AchievementDefinition(
                id = "HALF_HOUR",
                title = "半小时投入",
                description = "累计练习 30 分钟",
                category = AchievementCategory.TIME,
                target = 30 * 60_000.0, // 1,800,000 ms
                metric = { it.totalPracticeMs.toDouble() }
            ),
            AchievementDefinition(
                id = "MARATHON",
                title = "马拉松练习",
                description = "累计练习 5 小时",
                category = AchievementCategory.TIME,
                target = 5 * 3_600_000.0, // 18,000,000 ms
                metric = { it.totalPracticeMs.toDouble() }
            ),
            AchievementDefinition(
                id = "DEDICATED",
                title = "专注投入",
                description = "累计练习 24 小时",
                category = AchievementCategory.TIME,
                target = 24 * 3_600_000.0, // 86,400,000 ms
                metric = { it.totalPracticeMs.toDouble() }
            )
        ))

        // ── 速度突破（tempo） ──
        addAll(listOf(
            AchievementDefinition(
                id = "SPEED_DEMON",
                title = "速度突破",
                description = "在一次渐速练习中达到目标速度",
                category = AchievementCategory.TEMPO,
                target = 1.0,
                metric = { it.tempoSessionsCompleted.toDouble() }
            ),
            AchievementDefinition(
                id = "TEMPO_MASTER",
                title = "速度大师",
                description = "完成 10 次渐速练习并达到目标速度",
                category = AchievementCategory.TEMPO,
                target = 10.0,
                metric = { it.tempoSessionsCompleted.toDouble() }
            )
        ))
    }

    /** 成就 id 集合（用于校验）。 */
    val ALL_IDS: Set<String> = DEFINITIONS.map { it.id }.toSet()

    /**
     * 评估全部成就，返回汇总结果。
     */
    fun evaluate(profile: PracticeProfile): AchievementSummary {
        val all = DEFINITIONS
            .map { def ->
                AchievementProgress(
                    definition = def,
                    currentValue = def.metric(profile)
                )
            }
            .sortedWith(
                compareBy<AchievementProgress> { !it.isUnlocked } // 已解锁在前
                    .thenBy { it.definition.category.ordinal }     // 按分类排序
                    .thenBy { it.definition.target }               // 同分类按目标值升序
            )
        return AchievementSummary(
            all = all,
            unlocked = all.filter { it.isUnlocked },
            locked = all.filter { !it.isUnlocked }
        )
    }

    /**
     * 仅评估单个成就。
     */
    fun evaluateOne(definition: AchievementDefinition, profile: PracticeProfile): AchievementProgress =
        AchievementProgress(definition, definition.metric(profile))

    /**
     * 评估并返回新解锁的成就（与上次已解锁的 id 集合比较）。
     *
     * 用于练习结束后弹出成就解锁通知。
     */
    fun evaluateNewlyUnlocked(
        profile: PracticeProfile,
        previousUnlockedIds: Set<String>
    ): List<AchievementProgress> =
        evaluate(profile).newlyUnlocked(previousUnlockedIds)
}
