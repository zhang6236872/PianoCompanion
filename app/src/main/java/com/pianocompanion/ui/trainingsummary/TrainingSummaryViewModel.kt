package com.pianocompanion.ui.trainingsummary

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.chordreading.ChordReadingProgress
import com.pianocompanion.interval.IntervalProgress
import com.pianocompanion.keysig.KeySigProgress
import com.pianocompanion.notation.NoteReadingProgress
import com.pianocompanion.rhythm.RhythmProgress
import com.pianocompanion.rhythmreading.RhythmReadingProgress
import com.pianocompanion.trainingsummary.TrainerSummary
import com.pianocompanion.trainingsummary.TrainerType
import com.pianocompanion.trainingsummary.TrainingSummaryEngine
import com.pianocompanion.trainingsummary.TrainingSummaryReport
import com.pianocompanion.training.EarTrainingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 训练汇总统计页的 UI 状态。
 */
data class TrainingSummaryUiState(
    val isLoading: Boolean = true,
    val report: TrainingSummaryReport = TrainingSummaryEngine.summarize(emptyList())
)

/**
 * 训练汇总统计 ViewModel。
 *
 * 从各训练模块各自的 SharedPreferences 文件中读取 JSON 进度数据，
 * 反序列化后提取统一的 [TrainerSummary]，再交给 [TrainingSummaryEngine] 聚合。
 *
 * 读取发生在后台线程（IO dispatcher），避免阻塞 UI。
 */
class TrainingSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TrainingSummaryUiState())
    val uiState: StateFlow<TrainingSummaryUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    /**
     * 重新从 SharedPreferences 加载所有模块进度并刷新报告。
     * 用户从子训练页返回时可调用以获取最新数据。
     */
    fun loadSummary() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val trainers = collectAllSummaries(getApplication())
            val report = TrainingSummaryEngine.summarize(trainers)
            _uiState.value = TrainingSummaryUiState(isLoading = false, report = report)
        }
    }

    companion object {
        /**
         * 从各训练模块的 SharedPreferences 中读取进度，汇总为 [TrainerSummary] 列表。
         *
         * 提取为 companion object 的纯函数（仅依赖 [Context]），便于在不实例化
         * ViewModel 的情况下进行逻辑验证（如在 Paparazzi 截图测试中）。
         */
        fun collectAllSummaries(context: Context): List<TrainerSummary> {
            val summaries = mutableListOf<TrainerSummary>()

            // === 识谱训练 ===
            runCatching {
                val json = context.getSharedPreferences("note_reading", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) NoteReadingProgress.fromJson(json) else NoteReadingProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.NOTE_READING,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 音程识别 ===
            runCatching {
                val json = context.getSharedPreferences("interval_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) IntervalProgress.fromJson(json) else IntervalProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.INTERVAL,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 和弦识别 ===
            runCatching {
                val json = context.getSharedPreferences("chord_reading_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) ChordReadingProgress.fromJson(json) else ChordReadingProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.CHORD_READING,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 调号识别 ===
            runCatching {
                val json = context.getSharedPreferences("key_sig_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) KeySigProgress.fromJson(json) else KeySigProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.KEY_SIGNATURE,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 节奏视读 ===
            runCatching {
                val json = context.getSharedPreferences("rhythm_reading_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) RhythmReadingProgress.fromJson(json) else RhythmReadingProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.RHYTHM_READING,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 听音训练（听觉）===
            runCatching {
                val json = context.getSharedPreferences("ear_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) EarTrainingProgress.fromJson(json) else EarTrainingProgress()
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.EAR_TRAINING,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalAnswered,
                        totalCorrect = p.totalCorrect,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAccuracy } ?: 0.0
                    )
                )
            }

            // === 节奏训练（听觉）===
            runCatching {
                val json = context.getSharedPreferences("rhythm_training", Context.MODE_PRIVATE)
                    .getString("progress_json", null)
                val p = if (json != null) RhythmProgress.fromJson(json) else RhythmProgress()
                // RhythmProgress 使用 totalQuestions/totalPassed/overallPassRate
                summaries.add(
                    TrainerSummary(
                        type = TrainerType.RHYTHM,
                        totalSessions = p.totalSessions,
                        totalAnswered = p.totalQuestions,
                        totalCorrect = p.totalPassed,
                        bestStreak = p.stats.values.maxOfOrNull { it.bestStreak } ?: 0,
                        bestAccuracy = p.stats.values.maxOfOrNull { it.bestAvgScore } ?: 0.0
                    )
                )
            }

            return summaries
        }
    }
}
