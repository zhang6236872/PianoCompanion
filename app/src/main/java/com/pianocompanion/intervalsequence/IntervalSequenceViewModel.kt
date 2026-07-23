package com.pianocompanion.intervalsequence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音程序列记忆训练 ViewModel。
 *
 * 管理 UI 状态：难度选择、题目生成、音频播放、答题反馈、统计。
 */
class IntervalSequenceViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val player = IntervalSequencePlayer()
    private val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: IntervalSequenceSession? = null
    private val progress = IntervalSequenceProgress()

    private val _uiState = MutableStateFlow(IntervalSequenceUiState())
    val uiState: StateFlow<IntervalSequenceUiState> = _uiState.asStateFlow()

    init {
        loadProgress()
    }

    /**
     * 选择难度并开始训练。
     */
    fun selectDifficulty(difficulty: IntervalSequenceDifficulty) {
        val engine = IntervalSequenceEngine(difficulty, kotlin.random.Random.Default)
        session = IntervalSequenceSession(engine, difficulty)
        session!!.start()
        _uiState.value = IntervalSequenceUiState(
            difficulty = difficulty,
            currentQuestion = session!!.currentQuestion,
            hasAnswered = false,
            feedback = null
        )
    }

    /**
     * 播放当前题目音频。
     */
    fun playAudio() {
        val question = session?.currentQuestion ?: return
        viewModelScope.launch(Dispatchers.IO) {
            player.play(question)
        }
    }

    /**
     * 提交答案。
     */
    fun submitAnswer(answer: String) {
        val sess = session ?: return
        if (sess.currentQuestion == null) return
        if (_uiState.value.hasAnswered) return

        val record = sess.submit(answer)
        val isCorrect = record.isCorrect
        val target = record.question.targetSequence

        _uiState.value = _uiState.value.copy(
            hasAnswered = true,
            feedback = IntervalSequenceFeedback(
                isCorrect = isCorrect,
                correctAnswer = record.question.correctAnswer,
                targetDisplay = target.entries.joinToString(" → ") { it.fullDescription },
                userAnswer = answer
            ),
            streak = sess.streak,
            bestStreak = sess.bestStreak,
            answeredCount = sess.answeredCount,
            accuracy = sess.accuracy
        )
    }

    /**
     * 进入下一题。
     */
    fun nextQuestion() {
        val sess = session ?: return
        sess.next()
        _uiState.value = _uiState.value.copy(
            currentQuestion = sess.currentQuestion,
            hasAnswered = false,
            feedback = null
        )
    }

    /**
     * 加载历史进度。
     */
    private fun loadProgress() {
        val json = prefs.getString(PREFS_KEY, null)
        if (json != null) {
            val loaded = IntervalSequenceProgress.fromJson(json)
            // 将统计写入当前实例
            IntervalSequenceDifficulty.values().forEach { diff ->
                val stats = loaded.getProgress(diff)
                if (stats.totalAnswered > 0) {
                    progress.recordSession(
                        diff,
                        stats.totalCorrect,
                        stats.totalAnswered,
                        stats.bestStreak
                    )
                }
            }
        }
    }

    /**
     * 保存进度（在 onCleared 时调用）。
     */
    private fun saveProgress() {
        val sess = session ?: return
        if (sess.answeredCount > 0) {
            progress.recordSession(
                sess.difficulty,
                sess.correctCount,
                sess.answeredCount,
                sess.bestStreak
            )
        }
        prefs.edit().putString(PREFS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }

    companion object {
        private const val PREFS_NAME = "interval_sequence_progress"
        private const val PREFS_KEY = "progress_json"
    }
}

/**
 * UI 状态。
 */
data class IntervalSequenceUiState(
    val difficulty: IntervalSequenceDifficulty? = null,
    val currentQuestion: IntervalSequenceQuestion? = null,
    val hasAnswered: Boolean = false,
    val feedback: IntervalSequenceFeedback? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val answeredCount: Int = 0,
    val accuracy: Double = 0.0
)

/**
 * 答题反馈。
 */
data class IntervalSequenceFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val targetDisplay: String,
    val userAnswer: String
)
