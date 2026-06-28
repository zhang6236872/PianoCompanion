package com.pianocompanion.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pianocompanion.analytics.AchievementCategory
import com.pianocompanion.analytics.AchievementProgress
import com.pianocompanion.analytics.AchievementSummary
import com.pianocompanion.analytics.AchievementEngine
import com.pianocompanion.analytics.PracticeProfile
import com.pianocompanion.analytics.PracticeProfileBuilder
import com.pianocompanion.analytics.TempoProgressRecord
import com.pianocompanion.analytics.WeakSpotAnalyzer
import com.pianocompanion.analytics.WeakSpotReport
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.data.repository.StatsRepository
import kotlinx.coroutines.flow.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {

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
        val achievementSummary: AchievementSummary? = null
    )

    private val repository = StatsRepository(application)
    private val gson = Gson()

    val uiState: StateFlow<StatsUiState> = flow {
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

        // 成就评估
        val tempoRecords = loadTempoRecords()
        val profile = PracticeProfileBuilder.fromSessions(sessions, tempoRecords)
        val achievementSummary = AchievementEngine.evaluate(profile)

        emit(StatsUiState(
            sessions = sessions,
            totalSessions = sessions.size,
            totalDurationMs = totalDuration,
            avgAccuracy = avgAcc,
            streak = calculateStreak(sessions),
            weakSpotSections = weakSections,
            achievementSummary = achievementSummary
        ))
    }.stateIn(
        scope = kotlinx.coroutines.MainScope(),
        started = SharingStarted.Eagerly,
        initialValue = StatsUiState()
    )

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
