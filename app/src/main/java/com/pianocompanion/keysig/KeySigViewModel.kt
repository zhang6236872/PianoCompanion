package com.pianocompanion.keysig

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
 * 调号识别训练 UI 状态（不可变数据类）。
 */
data class KeySigUiState(
    val clef: KeySigClef = KeySigClef.TREBLE,
    val difficulty: KeySigDifficulty = KeySigDifficulty.BEGINNER,
    val currentQuestion: KeySigQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: KeySigAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: KeySigProgress = KeySigProgress()
)

/**
 * 调号识别训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[KeySigEngine]、[KeySigSession]、
 * [KeySigAudioBuilder]、[KeySigProgress]）与 Android UI/音频层（[KeySigPlayer]）。
 */
class KeySigViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: KeySigSession? = null
    private val audioBuilder = KeySigAudioBuilder()
    private val player = KeySigPlayer()

    private val _uiState = MutableStateFlow(KeySigUiState(progress = loadProgress()))
    val uiState: StateFlow<KeySigUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /**
     * 开始新的训练会话。
     */
    fun startSession(clef: KeySigClef, difficulty: KeySigDifficulty) {
        val engine = KeySigEngine()
        val newSession = KeySigSession(engine, clef, difficulty)
        newSession.start()
        session = newSession

        update {
            it.copy(
                clef = clef,
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

    /**
     * 播放当前题目的音阶音频。
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
                currentStreak = s.currentStreak,
                bestStreak = s.bestStreak
            )
        }

        // 记录进度
        val progress = loadProgress()
        progress.recordSession(
            s.clef(), s.difficulty(),
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
                s.clef(), s.difficulty(),
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

    private inline fun update(transform: (KeySigUiState) -> KeySigUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): KeySigProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return KeySigProgress()
        return KeySigProgress.fromJson(json)
    }

    private fun saveProgress(progress: KeySigProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "key_sig_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
