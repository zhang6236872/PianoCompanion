package com.pianocompanion.texturerecognitiontraining

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
 * 织体辨识训练 UI 状态（不可变数据类）。
 */
data class TextureUiState(
    val difficulty: TextureDifficulty = TextureDifficulty.BEGINNER,
    val currentQuestion: TextureQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: TextureAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: TextureProgress = TextureProgress()
)

/**
 * 织体辨识训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[TextureEngine]、[TextureSession]、
 * [TextureAudioBuilder]、[TextureProgress]）与 Android UI/音频层
 * （[TexturePlayer]）。
 */
class TextureViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: TextureSession? = null
    private val audioBuilder = TextureAudioBuilder()
    private val player = TexturePlayer()

    private val _uiState = MutableStateFlow(TextureUiState(progress = loadProgress()))
    val uiState: StateFlow<TextureUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /** 开始新的训练会话。 */
    fun startSession(difficulty: TextureDifficulty) {
        val engine = TextureEngine()
        val newSession = TextureSession(engine, difficulty)
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
                audioReady = false
            )
        }
        prepareCurrentAudio()
    }

    /** 播放当前题目的音频。 */
    fun playAudio() {
        player.play()
        update { it.copy(isPlaying = true) }
    }

    /** 停止播放。 */
    fun stopAudio() {
        player.stop()
        update { it.copy(isPlaying = false) }
    }

    /** 提交答案。 */
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

    /** 进入下一题。 */
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

    /** 结束会话。 */
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
            val audio = audioBuilder.render(question)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    private inline fun update(transform: (TextureUiState) -> TextureUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): TextureProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return TextureProgress()
        return TextureProgress.fromJson(json)
    }

    private fun saveProgress(progress: TextureProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "texture_recognition_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
