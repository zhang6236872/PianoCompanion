package com.pianocompanion.voiceentryorder

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
 * 声部进入顺序辨识训练 UI 状态（不可变数据类）。
 */
data class VoiceEntryUiState(
    val difficulty: EntryDifficulty = EntryDifficulty.BEGINNER,
    val currentQuestion: EntryOrderQuestion? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastResult: EntryAnswerRecord? = null,
    val isAnswered: Boolean = false,
    val isSessionActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: VoiceEntryProgress = VoiceEntryProgress()
)

/**
 * 声部进入顺序辨识训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[VoiceEntryEngine]、[VoiceEntrySession]、
 * [VoiceEntryAudioBuilder]、[VoiceEntryProgress]）与 Android UI/音频层（[VoiceEntryPlayer]）。
 */
class VoiceEntryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: VoiceEntrySession? = null
    private val audioBuilder = VoiceEntryAudioBuilder()
    private val player = VoiceEntryPlayer()

    private val _uiState = MutableStateFlow(VoiceEntryUiState(progress = loadProgress()))
    val uiState: StateFlow<VoiceEntryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /** 开始新的训练会话。 */
    fun startSession(difficulty: EntryDifficulty) {
        val engine = VoiceEntryEngine()
        val newSession = VoiceEntrySession(engine, difficulty)
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

    private inline fun update(transform: (VoiceEntryUiState) -> VoiceEntryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): VoiceEntryProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return VoiceEntryProgress()
        return VoiceEntryProgress.fromJson(json)
    }

    private fun saveProgress(progress: VoiceEntryProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "voice_entry_order_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
