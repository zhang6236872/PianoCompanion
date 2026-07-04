package com.pianocompanion.rhythmreading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 节奏视读训练 UI 状态（不可变数据类）。
 */
data class RhythmReadingUiState(
    val difficulty: RhythmReadingDifficulty = RhythmReadingDifficulty.BEGINNER,
    val currentQuestion: RhythmReadingQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: RhythmReadingAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val selectedAnswer: String? = null,
    val progress: RhythmReadingProgress = RhythmReadingProgress()
)

/**
 * 节奏视读训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[RhythmReadingEngine]、[RhythmReadingSession]、
 * [RhythmReadingAudioBuilder]、[RhythmReadingProgress]）与 Android UI/音频层
 * （[RhythmReadingPlayer]）。
 */
class RhythmReadingViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: RhythmReadingSession? = null
    private val audioBuilder = RhythmReadingAudioBuilder()
    private val player = RhythmReadingPlayer()

    private val _uiState = MutableStateFlow(RhythmReadingUiState(progress = loadProgress()))
    val uiState: StateFlow<RhythmReadingUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /**
     * 选择难度（会话未进行时）。
     */
    fun selectDifficulty(difficulty: RhythmReadingDifficulty) {
        if (_uiState.value.isSessionActive) return
        update { it.copy(difficulty = difficulty) }
    }

    /**
     * 开始新的训练会话。
     */
    fun startSession(difficulty: RhythmReadingDifficulty) {
        val engine = RhythmReadingEngine()
        val newSession = RhythmReadingSession(engine, difficulty)
        newSession.start()
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
                isPlaying = false,
                audioReady = false,
                selectedAnswer = null
            )
        }
        prepareCurrentAudio()
    }

    /**
     * 播放当前题目的节奏点击音轨。
     */
    fun playAudio() {
        player.play()
        update { it.copy(isPlaying = true) }
    }

    /**
     * 停止播放。
     */
    fun stopAudio() {
        player.stop()
        update { it.copy(isPlaying = false) }
    }

    /**
     * 提交答案。
     *
     * @param answer 用户选择的选项指纹
     */
    fun submitAnswer(answer: String) {
        val s = session ?: return
        val record = s.submit(answer) ?: return

        update {
            it.copy(
                lastResult = record,
                isAnswered = true,
                selectedAnswer = answer,
                answeredCount = s.answeredCount,
                correctCount = s.correctCount,
                currentStreak = s.currentStreak,
                bestStreak = s.bestStreak
            )
        }

        // 记录进度
        val progress = loadProgress()
        progress.recordSession(
            s.difficulty(),
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
                isAnswered = false,
                selectedAnswer = null,
                isPlaying = false,
                audioReady = false
            )
        }
        prepareCurrentAudio()
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
                s.correctCount, s.answeredCount, s.bestStreak
            )
            saveProgress(progress)
        }
        session = null
        player.stop()
        update {
            it.copy(
                isSessionActive = false,
                isPlaying = false,
                currentQuestion = null,
                progress = loadProgress()
            )
        }
    }

    private fun prepareCurrentAudio() {
        val question = _uiState.value.currentQuestion ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val audio = audioBuilder.render(question.pattern)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    private inline fun update(transform: (RhythmReadingUiState) -> RhythmReadingUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): RhythmReadingProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return RhythmReadingProgress()
        return RhythmReadingProgress.fromJson(json)
    }

    private fun saveProgress(progress: RhythmReadingProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "rhythm_reading_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
