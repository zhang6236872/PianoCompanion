package com.pianocompanion.rhythm

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
 * 节奏训练阶段（UI 状态流转）。
 */
enum class RhythmPhase {
    /** 选择阶段：选择难度。 */
    SETUP,
    /** 播放参考音频阶段。 */
    LISTENING,
    /** 用户敲击阶段。 */
    TAPPING,
    /** 结果展示阶段。 */
    RESULT
}

/**
 * 节奏训练 UI 状态（不可变数据类）。
 */
data class RhythmUiState(
    val difficulty: RhythmDifficulty = RhythmDifficulty.BEGINNER,
    val phase: RhythmPhase = RhythmPhase.SETUP,
    val currentPattern: RhythmPattern? = null,
    val answeredCount: Int = 0,
    val passedCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val averageScore: Double = 0.0,
    val lastResult: TapMatchResult? = null,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val progress: RhythmProgress = RhythmProgress(),
    /** 用户敲击时间戳列表（TAPPING 阶段收集）。 */
    val userTaps: List<Long> = emptyList(),
    /** 敲击阶段开始的时间戳（用于计算相对时间）。 */
    val tappingStartTimeMs: Long = 0L
)

/**
 * 节奏训练 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[RhythmPatternGenerator]、[RhythmSession]、[RhythmAudioBuilder]、
 * [RhythmProgress]）与 Android UI/音频层（[RhythmPlayer]）。
 *
 * 工作流：
 * 1. startSession() → 生成节奏型，预渲染音频
 * 2. playReference() → 播放参考音频（含预备拍）
 * 3. startTapping() → 进入敲击阶段，开始记录时间
 * 4. recordTap() → 记录用户一次敲击
 * 5. submitTaps() → 匹配判定，进入结果阶段
 * 6. nextQuestion() → 下一题
 */
class RhythmViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private var session: RhythmSession? = null
    private val audioBuilder = RhythmAudioBuilder()
    private val player = RhythmPlayer()

    private val _uiState = MutableStateFlow(RhythmUiState(progress = loadProgress()))
    val uiState: StateFlow<RhythmUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
    }

    /**
     * 设置难度。
     */
    fun setDifficulty(difficulty: RhythmDifficulty) {
        update { it.copy(difficulty = difficulty) }
    }

    /**
     * 开始新的训练会话。
     */
    fun startSession() {
        val difficulty = _uiState.value.difficulty
        val generator = RhythmPatternGenerator()
        val newSession = RhythmSession(generator, difficulty)
        newSession.start()
        session = newSession

        update {
            it.copy(
                currentPattern = newSession.currentPattern,
                answeredCount = 0,
                passedCount = 0,
                currentStreak = 0,
                bestStreak = 0,
                averageScore = 0.0,
                lastResult = null,
                phase = RhythmPhase.LISTENING,
                isPlaying = false,
                audioReady = false,
                userTaps = emptyList()
            )
        }
        prepareCurrentAudio()
    }

    /**
     * 播放当前题目的参考音频。
     */
    fun playReference() {
        player.play()
        update { it.copy(isPlaying = true) }
    }

    /**
     * 停止播放。
     */
    fun stopPlayback() {
        player.stop()
        update { it.copy(isPlaying = false) }
    }

    /**
     * 进入敲击阶段。
     */
    fun startTapping() {
        player.stop()
        update {
            it.copy(
                phase = RhythmPhase.TAPPING,
                isPlaying = false,
                userTaps = emptyList(),
                tappingStartTimeMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * 记录一次用户敲击。
     */
    fun recordTap() {
        val state = _uiState.value
        if (state.phase != RhythmPhase.TAPPING) return
        val tapTime = System.currentTimeMillis() - state.tappingStartTimeMs
        update { it.copy(userTaps = it.userTaps + tapTime) }
    }

    /**
     * 提交敲击，判定结果。
     */
    fun submitTaps() {
        val s = session ?: return
        val taps = _uiState.value.userTaps
        val result = s.submitTaps(taps) ?: return

        // 记录进度
        val progress = loadProgress()
        progress.recordSession(
            s.difficulty(),
            if (result.score >= RhythmSession.PASS_THRESHOLD) 1 else 0,
            1, result.score, s.bestStreak
        )
        saveProgress(progress)

        update {
            it.copy(
                lastResult = result,
                phase = RhythmPhase.RESULT,
                answeredCount = s.answeredCount,
                passedCount = s.passedCount,
                currentStreak = s.currentStreak,
                bestStreak = s.bestStreak,
                averageScore = s.averageScore,
                progress = progress
            )
        }
    }

    /**
     * 进入下一题。
     */
    fun nextQuestion() {
        val s = session ?: return
        s.next()
        update {
            it.copy(
                currentPattern = s.currentPattern,
                lastResult = null,
                phase = RhythmPhase.LISTENING,
                isPlaying = false,
                audioReady = false,
                userTaps = emptyList()
            )
        }
        prepareCurrentAudio()
    }

    /**
     * 重听参考音频（从结果或敲击阶段返回）。
     */
    fun replayReference() {
        update {
            it.copy(
                phase = RhythmPhase.LISTENING,
                userTaps = emptyList(),
                lastResult = null
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
                s.passedCount, s.answeredCount,
                s.averageScore, s.bestStreak
            )
            saveProgress(progress)
        }
        session = null
        player.stop()
        update {
            it.copy(
                phase = RhythmPhase.SETUP,
                isPlaying = false,
                currentPattern = null,
                progress = loadProgress()
            )
        }
    }

    private fun prepareCurrentAudio() {
        val pattern = _uiState.value.currentPattern ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val audio = audioBuilder.render(pattern)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    private inline fun update(transform: (RhythmUiState) -> RhythmUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun loadProgress(): RhythmProgress {
        val json = prefs.getString(PROGRESS_KEY, null) ?: return RhythmProgress()
        return RhythmProgress.fromJson(json)
    }

    private fun saveProgress(progress: RhythmProgress) {
        prefs.edit().putString(PROGRESS_KEY, progress.toJson()).apply()
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        private const val PREFS_NAME = "rhythm_training"
        private const val PROGRESS_KEY = "progress_json"
    }
}
