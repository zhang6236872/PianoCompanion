package com.pianocompanion.mixedpractice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 综合练习 UI 状态（不可变数据类）。
 */
data class MixedPracticeUiState(
    val difficulty: MixedDifficulty = MixedDifficulty.BEGINNER,
    val currentQuestion: MixedQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: MixedAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    /** 各题型的答题次数。 */
    val typeAttempts: Map<MixedQuestionType, Int> = emptyMap(),
    /** 各题型的答对次数。 */
    val typeCorrect: Map<MixedQuestionType, Int> = emptyMap(),
    val progress: MixedPracticeProgress = MixedPracticeProgress()
)

/**
 * 综合练习 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[MixedPracticeEngine]、[MixedPracticeSession]、
 * [MixedPracticeProgress]）与 Android UI 层。
 */
class MixedPracticeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: MixedPracticeSession? = null
    private var engine: MixedPracticeEngine? = null

    private val _uiState = MutableStateFlow(MixedPracticeUiState(progress = loadProgress()))
    val uiState: StateFlow<MixedPracticeUiState> = _uiState.asStateFlow()

    /**
     * 开始新的综合练习会话。
     */
    fun startSession(difficulty: MixedDifficulty) {
        val newEngine = MixedPracticeEngine()
        val newSession = MixedPracticeSession(newEngine, difficulty)
        newSession.start()
        engine = newEngine
        session = newSession

        update {
            it.copy(
                difficulty = difficulty,
                currentQuestion = newSession.currentQuestion,
                answeredCount = 0,
                correctCount = 0,
                currentStreak = 0,
                bestStreak = 0,
                lastResult = null,
                isAnswered = false,
                isSessionActive = true,
                typeAttempts = newSession.typeAttempts,
                typeCorrect = newSession.typeCorrect
            )
        }
    }

    /**
     * 提交答案。
     */
    fun submitAnswer(answer: String) {
        val s = session ?: return
        val record = s.submit(answer) ?: return

        update {
            it.copy(
                lastResult = record,
                isAnswered = true,
                answeredCount = s.answeredCount,
                correctCount = s.correctCount,
                currentStreak = s.currentStreak,
                bestStreak = s.bestStreak,
                typeAttempts = s.typeAttempts,
                typeCorrect = s.typeCorrect
            )
        }

        // 记录单题进度（按题型）
        val progress = loadProgress()
        progress.recordQuestion(record.type, record.isCorrect)
        saveProgress(progress)
        update { it.copy(progress = progress) }
    }

    /**
     * 进入下一题。
     */
    fun nextQuestion() {
        val s = session ?: return
        s.next()
        update {
            it.copy(
                currentQuestion = s.currentQuestion,
                lastResult = null,
                isAnswered = false
            )
        }
    }

    /**
     * 结束会话。
     */
    fun endSession() {
        val s = session
        if (s != null && s.answeredCount > 0) {
            val progress = loadProgress()
            progress.recordSession(
                s.difficulty(),
                s.correctCount,
                s.answeredCount,
                s.bestStreak
            )
            saveProgress(progress)
        }
        session = null
        engine = null
        update {
            it.copy(
                isSessionActive = false,
                currentQuestion = null,
                progress = loadProgress()
            )
        }
    }

    private inline fun update(transform: (MixedPracticeUiState) -> MixedPracticeUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): MixedPracticeProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return MixedPracticeProgress()
        return MixedPracticeProgress.fromJson(json)
    }

    private fun saveProgress(progress: MixedPracticeProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    companion object {
        private const val PREFS_NAME = "mixed_practice"
        private const val PROGRESS_KEY = "progress_json"
    }
}
