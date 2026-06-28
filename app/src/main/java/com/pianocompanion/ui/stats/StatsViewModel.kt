package com.pianocompanion.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pianocompanion.analytics.AchievementCategory
import com.pianocompanion.analytics.AchievementProgress
import com.pianocompanion.analytics.AchievementSummary
import com.pianocompanion.analytics.AchievementEngine
import com.pianocompanion.analytics.AchievementStore
import com.pianocompanion.analytics.PracticeProfile
import com.pianocompanion.analytics.PracticeProfileBuilder
import com.pianocompanion.analytics.TempoProgressRecord
import com.pianocompanion.analytics.WeakSpotAnalyzer
import com.pianocompanion.analytics.WeakSpotReport
import com.pianocompanion.analytics.GoalDefinition
import com.pianocompanion.analytics.GoalEditor
import com.pianocompanion.analytics.GoalTracker
import com.pianocompanion.analytics.GoalReport
import com.pianocompanion.analytics.GoalPeriod
import com.pianocompanion.analytics.GoalMetric
import com.pianocompanion.analytics.NoteMasteryAnalyzer
import com.pianocompanion.analytics.NoteMasteryReport
import com.pianocompanion.analytics.PracticeCalendarHeatmap
import com.pianocompanion.analytics.PracticeHeatmap
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.data.repository.StatsRepository
import kotlinx.coroutines.flow.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val GOALS_PREFS = "practice_goals"
        private const val GOALS_KEY = "goals"
        private const val ACHIEVEMENT_PREFS = "achievement_store"
        private const val UNLOCKED_KEY = "unlocked_ids"
    }

    /**
     * 单首乐谱的弱项分析结果（供统计页展示）。
     */
    data class WeakSpotSection(
        val scoreTitle: String,
        val report: WeakSpotReport
    )

    data class StatsUiState(
        val sessions: List<SessionRecord> = emptyList(),
        val totalSessions: Int = 0,
        val totalDurationMs: Long = 0L,
        val avgAccuracy: Float = 0f,
        val streak: Int = 0,
        /** 各乐谱的薄弱环节分析（仅保留存在弱项的乐谱，按累计错误数降序）。 */
        val weakSpotSections: List<WeakSpotSection> = emptyList(),
        /** 成就汇总（全部成就 + 解锁/锁定分组）。 */
        val achievementSummary: AchievementSummary? = null,
        /** 本次新解锁的成就列表（供 UI 弹出庆祝通知）。每次 computeState 后计算。 */
        val newlyUnlockedAchievements: List<AchievementProgress> = emptyList(),
        /** 练习目标追踪报告（每日/每周目标完成进度）。 */
        val goalReport: GoalReport? = null,
        /** 练习日历热力图（GitHub 风格贡献网格）。 */
        val heatmap: PracticeHeatmap? = null,
        /** 音符掌握度分析报告（跨乐谱的音高维度弱项分析）。 */
        val noteMastery: NoteMasteryReport? = null
    )

    private val repository = StatsRepository(application)
    private val gson = Gson()

    /** 目标变更刷新触发器。每次目标编辑后递增，驱动 uiState 重新计算。 */
    private val refreshTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<StatsUiState> = refreshTrigger
        .map { computeState() }
        .stateIn(
            scope = kotlinx.coroutines.MainScope(),
            started = SharingStarted.Eagerly,
            initialValue = StatsUiState()
        )

    /**
     * 根据 SharedPreferences 中的会话数据和当前目标，计算完整的 UI 状态。
     * 在 [refreshTrigger] 变化时重新调用。
     */
    private suspend fun computeState(): StatsUiState {
        val sessions = repository.getAllSessions()
        val totalDuration = sessions.sumOf { it.durationMs }
        val avgAcc = if (sessions.isNotEmpty())
            sessions.map { it.accuracy }.average().toFloat()
        else 0f

        // 按乐谱分组做弱项分析（弱项是乐谱相关的）。
        val weakSections = sessions
            .groupBy { it.scoreTitle }
            .mapNotNull { (title, scoreSessions) ->
                val report = WeakSpotAnalyzer.analyze(scoreSessions)
                if (report.hasWeakSpots) WeakSpotSection(title, report) else null
            }
            .sortedByDescending { it.report.totalErrors }

        // 成就评估 + 新解锁检测
        val tempoRecords = loadTempoRecords()
        val profile = PracticeProfileBuilder.fromSessions(sessions, tempoRecords)
        val achievementSummary = AchievementEngine.evaluate(profile)

        // 与上次持久化的已解锁集合做差分，检测本次新解锁的成就
        val previousRaw = loadUnlockedIds()
        val currentUnlockedIds = achievementSummary.unlocked.map { it.definition.id }.toSet()
        val diff = AchievementStore.evaluateDiff(currentUnlockedIds, previousRaw)
        // 持久化更新后的已解锁集合（仅在变化时写入）
        if (diff.updatedRaw != previousRaw) {
            saveUnlockedIds(diff.updatedRaw)
        }
        val newlyUnlocked = AchievementStore.newlyUnlockedToProgress(
            diff.newlyUnlockedIds, achievementSummary
        )

        // 练习目标评估
        val goals = loadGoals()
        val goalReport = if (goals.isNotEmpty()) GoalTracker.evaluate(sessions, goals) else null

        // 练习日历热力图
        val heatmap = PracticeCalendarHeatmap.build(
            sessions = sessions,
            nowEpochMs = System.currentTimeMillis()
        )

        // 音符掌握度分析（跨乐谱的音高维度弱项分析）
        val noteMastery = NoteMasteryAnalyzer.analyze(sessions)

        return StatsUiState(
            sessions = sessions,
            totalSessions = sessions.size,
            totalDurationMs = totalDuration,
            avgAccuracy = avgAcc,
            streak = calculateStreak(sessions),
            weakSpotSections = weakSections,
            achievementSummary = achievementSummary,
            newlyUnlockedAchievements = newlyUnlocked,
            goalReport = goalReport,
            heatmap = heatmap,
            noteMastery = noteMastery
        )
    }

    // ════════════════════════════════════════════════════════════════
    //  目标编辑 API（供 UI 调用）
    // ════════════════════════════════════════════════════════════════

    /**
     * 添加或更新一个练习目标。已存在相同 key 的目标会被替换。
     */
    fun addOrUpdateGoal(goal: GoalDefinition) {
        val current = loadGoals().toMutableList()
        val updated = GoalEditor.addOrUpdateGoal(current, goal)
        saveGoals(updated)
        refresh()
    }

    /**
     * 按 key 移除一个练习目标。
     */
    fun removeGoal(key: String) {
        val current = loadGoals().toMutableList()
        val updated = GoalEditor.removeGoal(current, key)
        saveGoals(updated)
        refresh()
    }

    /**
     * 切换目标的启用/禁用状态。
     */
    fun toggleGoal(goal: GoalDefinition, enabled: Boolean) {
        val current = loadGoals().toMutableList()
        val updated = GoalEditor.toggleGoal(current, goal, enabled)
        saveGoals(updated)
        refresh()
    }

    /**
     * 应用预设目标包（完全替换当前目标）。
     * @param presetName "轻松"/"适中"/"挑战"
     */
    fun applyPreset(presetName: String) {
        saveGoals(GoalEditor.applyPreset(presetName))
        refresh()
    }

    /**
     * 获取当前目标列表（供编辑 UI 初始化）。
     */
    fun getCurrentGoals(): List<GoalDefinition> = loadGoals()

    /**
     * 批量保存目标列表并刷新 UI。
     */
    fun setGoals(goals: List<GoalDefinition>) {
        saveGoals(goals)
        refresh()
    }

    /** 触发 uiState 重新计算。 */
    private fun refresh() {
        refreshTrigger.value = System.nanoTime()
    }

    // ════════════════════════════════════════════════════════════════
    //  持久化
    // ════════════════════════════════════════════════════════════════

    /**
     * 从 SharedPreferences（"tempo_progress"）加载渐速练习记录。
     * 与 PracticeViewModel 使用同一存储键，确保跨页面数据一致。
     */
    private fun loadTempoRecords(): List<TempoProgressRecord> {
        return try {
            val prefs = getApplication<Application>()
                .getSharedPreferences("tempo_progress", android.content.Context.MODE_PRIVATE)
            val json = prefs.getString("records", null) ?: return emptyList()
            val type = object : TypeToken<List<TempoProgressRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 从 SharedPreferences（"practice_goals"）加载用户自定义的练习目标。
     * 使用 [GoalEditor.deserializeGoals] 解析存储格式。
     * 无自定义目标时返回默认目标（适中预设）。
     */
    private fun loadGoals(): List<GoalDefinition> {
        return try {
            val prefs = getApplication<Application>()
                .getSharedPreferences(GOALS_PREFS, android.content.Context.MODE_PRIVATE)
            val raw = prefs.getString(GOALS_KEY, null)
            val goals = GoalEditor.deserializeGoals(raw)
            // 无自定义目标时返回默认目标
            if (goals.isNotEmpty()) goals else GoalTracker.defaultGoals()
        } catch (_: Exception) {
            GoalTracker.defaultGoals()
        }
    }

    /**
     * 保存目标列表到 SharedPreferences（"practice_goals"）。
     * 使用 [GoalEditor.serializeGoals] 序列化。
     */
    private fun saveGoals(goals: List<GoalDefinition>) {
        try {
            val prefs = getApplication<Application>()
                .getSharedPreferences(GOALS_PREFS, android.content.Context.MODE_PRIVATE)
            prefs.edit().putString(GOALS_KEY, GoalEditor.serializeGoals(goals)).apply()
        } catch (_: Exception) {
            // 持久化失败时静默忽略（内存中的目标仍可用于当前会话）
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  成就解锁状态持久化
    // ════════════════════════════════════════════════════════════════

    /**
     * 从 SharedPreferences 加载上次持久化的已解锁成就 id 字符串。
     */
    private fun loadUnlockedIds(): String? {
        return try {
            val prefs = getApplication<Application>()
                .getSharedPreferences(ACHIEVEMENT_PREFS, android.content.Context.MODE_PRIVATE)
            prefs.getString(UNLOCKED_KEY, null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 保存已解锁成就 id 字符串到 SharedPreferences。
     */
    private fun saveUnlockedIds(raw: String) {
        try {
            val prefs = getApplication<Application>()
                .getSharedPreferences(ACHIEVEMENT_PREFS, android.content.Context.MODE_PRIVATE)
            prefs.edit().putString(UNLOCKED_KEY, raw).apply()
        } catch (_: Exception) {
            // 持久化失败时静默忽略
        }
    }

    /**
     * 清除新解锁通知状态（用户关闭庆祝弹窗后调用）。
     * 触发刷新后，由于已解锁集合已持久化，重新计算时不会再产生新解锁成就。
     */
    fun clearNewlyUnlocked() {
        refresh()
    }

    private fun calculateStreak(sessions: List<SessionRecord>): Int {
        if (sessions.isEmpty()) return 0
        val today = System.currentTimeMillis() / 86400000
        val days = sessions.map { it.startTime / 86400000 }.toSet().sortedDescending()
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
