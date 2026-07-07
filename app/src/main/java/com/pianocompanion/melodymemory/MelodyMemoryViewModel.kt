package com.pianocompanion.melodymemory

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
 * 旋律记忆训练 UI 状态（不可变数据类）。
 */
data class MelodyMemoryUiState(
    val difficulty: MelodyDifficulty = MelodyDifficulty.BEGINNER,
    val tempo: MelodyTempo = MelodyTempo.SLOW,
    val currentQuestion: MelodyQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: MelodyAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: MelodyMemoryProgress = MelodyMemoryProgress()
)

/**
 * 旋律记忆训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[MelodyMemoryEngine]、[MelodyMemorySession]、
 * [MelodyMemoryAudioBuilder]、[MelodyMemoryProgress]）与 Android UI/音频层
 * （[MelodyMemoryPlayer]）。
 */
class MelodyMemoryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: MelodyMemorySession? = null
    private val audioBuilder = MelodyMemoryAudioBuilder()
    private val player = MelodyMemoryPlayer()

    private val _uiState = MutableStateFlow(MelodyMemoryUiState(progress = loadProgress()))
    val uiState: StateFlow<MelodyMemoryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /** 开始新的训练会话。 */
    fun startSession(difficulty: MelodyDifficulty, tempo: MelodyTempo) {
        val engine = MelodyMemoryEngine()
        val newSession = MelodyMemorySession(engine, difficulty, tempo)
        newSession.start()
        session = newSession

        update {
            it.copy(
                difficulty = difficulty,
                tempo = tempo,
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
            s.difficulty(), s.tempo(),
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
                s.difficulty(), s.tempo(),
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

    private inline fun update(transform: (MelodyMemoryUiState) -> MelodyMemoryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): MelodyMemoryProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return MelodyMemoryProgress()
        return MelodyMemoryProgress.fromJson(json)
    }

    private fun saveProgress(progress: MelodyMemoryProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "melody_memory_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
