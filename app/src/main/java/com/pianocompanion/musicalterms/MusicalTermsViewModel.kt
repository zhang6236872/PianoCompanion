package com.pianocompanion.musicalterms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音乐术语训练 UI 状态（不可变数据类）。
 */
data class MusicalTermsUiState(
    val difficulty: TermDifficulty = TermDifficulty.BEGINNER,
    val category: TermCategory? = null,
    val direction: QuizDirection? = null,
    val currentQuestion: TermQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: TermAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val progress: MusicalTermsProgress = MusicalTermsProgress()
)

/**
 * 音乐术语训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[MusicalTermsEngine]、[MusicalTermsSession]、
 * [MusicalTermsProgress]）与 Android UI 层。
 * 使用 SharedPreferences 持久化进度数据。
 */
class MusicalTermsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: MusicalTermsSession? = null

    private val _uiState = MutableStateFlow(MusicalTermsUiState(progress = loadProgress()))
    val uiState: StateFlow<MusicalTermsUiState> = _uiState.asStateFlow()

    /**
     * 开始新的训练会话。
     */
    fun startSession(
        difficulty: TermDifficulty,
        category: TermCategory?,
        direction: QuizDirection?
    ) {
        val engine = MusicalTermsEngine()
        val newSession = MusicalTermsSession(engine, difficulty, category, direction)
        newSession.start()
        session = newSession

        update {
            it.copy(
                difficulty = difficulty,
                category = category,
                direction = direction,
                currentQuestion = newSession.currentQuestion,
                answeredCount = 0,
                correctCount = 0,
                currentStreak = 0,
                bestStreak = 0,
                lastResult = null,
                isAnswered = false,
                isSessionActive = true
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
                bestStreak = s.bestStreak
            )
        }

        // 记录进度（逐题记录）
        val progress = loadProgress()
        progress.recordSession(
            s.difficulty(), s.category(),
            if (record.isCorrect) 1 else 0,
            1, s.bestStreak
        )
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
                s.difficulty(), s.category(),
                s.correctCount, s.answeredCount, s.bestStreak
            )
            saveProgress(progress)
        }
        session = null
        update {
            it.copy(
                isSessionActive = false,
                currentQuestion = null,
                progress = loadProgress()
            )
        }
    }

    private inline fun update(transform: (MusicalTermsUiState) -> MusicalTermsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): MusicalTermsProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return MusicalTermsProgress()
        return MusicalTermsProgress.fromJson(json)
    }

    private fun saveProgress(progress: MusicalTermsProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    companion object {
        private const val PREFS_NAME = "musical_terms_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
