package com.pianocompanion.training

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
 * 听音训练 UI 状态（不可变数据类）。
 */
data class EarTrainingUiState(
    val exerciseType: ExerciseType = ExerciseType.INTERVAL,
    val difficulty: Difficulty = Difficulty.BEGINNER,
    val currentQuestion: EarTrainingQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val lastResult: AnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: EarTrainingProgress = EarTrainingProgress()
)

/**
 * 听音训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[EarTrainingEngine]、[EarTrainingSession]、[EarTrainingAudioBuilder]、
 * [EarTrainingProgress]）与 Android UI/音频层（[EarTrainingPlayer]）。
 *
 * UI 状态通过 [uiState]（StateFlow）暴露，Compose 直接 collectAsState 观察。
 */
class EarTrainingViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: EarTrainingSession? = null
    private val audioBuilder = EarTrainingAudioBuilder()
    private val player = EarTrainingPlayer()

    private val _uiState = MutableStateFlow(EarTrainingUiState(progress = loadProgress()))
    val uiState: StateFlow<EarTrainingUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /**
     * 开始新的训练会话。
     */
    fun startSession(exerciseType: ExerciseType, difficulty: Difficulty) {
        val engine = EarTrainingEngine()
        val newSession = EarTrainingSession(engine, exerciseType, difficulty)
        newSession.start()
        session = newSession

        update {
            it.copy(
                exerciseType = exerciseType,
                difficulty = difficulty,
                currentQuestion = newSession.currentQuestion,
                answeredCount = 0,
                correctCount = 0,
                currentStreak = 0,
                lastResult = null,
                isAnswered = false,
                isSessionActive = true,
                isPlaying = false,
                audioReady = false
            )
        }
        prepareCurrentAudio()
    }

    /**
     * 播放当前题目的音频。
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
                currentStreak = s.currentStreak
            )
        }

        // 记录进度
        val progress = loadProgress()
        progress.recordSession(
            s.exerciseType(), s.difficulty(),
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
                s.exerciseType(), s.difficulty(),
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
            val audio = audioBuilder.render(question)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    private inline fun update(transform: (EarTrainingUiState) -> EarTrainingUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): EarTrainingProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return EarTrainingProgress()
        return EarTrainingProgress.fromJson(json)
    }

    private fun saveProgress(progress: EarTrainingProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "ear_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
