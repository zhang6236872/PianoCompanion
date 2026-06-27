package com.pianocompanion.ui.practice

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.audio.AudioRecorder
import com.pianocompanion.audio.HapticFeedback
import com.pianocompanion.audio.Metronome
import com.pianocompanion.data.DemoScores
import com.pianocompanion.data.model.*
import com.pianocompanion.data.repository.StatsRepository
import com.pianocompanion.following.ScoreFollower
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Practice screen.
 * Bridges the AudioRecorder, ScoreFollower engine, and the Compose UI.
 * Saves practice sessions to StatsRepository.
 */
class PracticeViewModel(
    application: Application
) : AndroidViewModel(application) {

    data class PracticeUiState(
        val isPracticing: Boolean = false,
        val hasMicPermission: Boolean = false,
        val currentPage: Int = 0,
        val totalPages: Int = 1,
        val currentMeasure: Int = 0,
        val currentNoteIndex: Int = 0,
        val correctCount: Int = 0,
        val wrongCount: Int = 0,
        val accuracy: Float = 0f,
        val lastFeedback: FeedbackType = FeedbackType.NONE,
        val lastExpectedNote: String = "",
        val lastDetectedNote: String = "",
        val score: Score? = null,
        val errorMessage: String? = null,
        val sessionSaved: Boolean = false,
        val practiceMode: PracticeMode = PracticeMode.NORMAL,
        val metronomeEnabled: Boolean = false,
        val metronomeBpm: Int = 120,
        val metronomeBeat: Int = -1,
        val rightHandAccuracy: Float = 0f,
        val leftHandAccuracy: Float = 0f,
        val rightHandCorrect: Int = 0,
        val leftHandCorrect: Int = 0,
        // === Section loop practice ===
        val loopEnabled: Boolean = false,
        val loopStartMeasure: Int = 0,
        val loopEndMeasure: Int = 0,
        val maxMeasure: Int = 0,
        val loopCount: Int = 0
    )

    enum class FeedbackType { NONE, CORRECT, WRONG_PITCH, EXTRA_NOTE, MISSING_NOTE }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // Available scores for selection
    val availableScores = DemoScores.getAll()

    private var audioRecorder: AudioRecorder? = null
    private var scoreFollower: ScoreFollower? = null
    private val statsRepository = StatsRepository(application)
    private val haptic = HapticFeedback(application)
    private val settingsRepo = com.pianocompanion.data.repository.SettingsRepository(application)
    private var practiceStartTime: Long = 0

    // === Metronome ===
    private val metronome = Metronome()

    init {
        metronome.onBeat = { beat ->
            _uiState.update { it.copy(metronomeBeat = beat) }
        }
    }

    fun toggleMetronome() {
        val enabled = !_uiState.value.metronomeEnabled
        if (enabled) {
            metronome.start()
        } else {
            metronome.stop()
            _uiState.update { it.copy(metronomeBeat = -1) }
        }
        _uiState.update { it.copy(metronomeEnabled = enabled) }
    }

    fun setMetronomeBpm(bpm: Int) {
        metronome.setBpm(bpm)
        _uiState.update { it.copy(metronomeBpm = metronome.getBpm()) }
    }

    fun checkMicPermission(): Boolean {
        val granted = getApplication<Application>().checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasMicPermission = granted) }
        return granted
    }

    fun setScore(score: Score) {
        scoreFollower = ScoreFollower(score)
        scoreFollower?.let { follower ->
            follower.onPositionUpdate = { noteIdx, measure, page ->
                _uiState.update {
                    it.copy(
                        currentNoteIndex = noteIdx,
                        currentMeasure = measure,
                        currentPage = page,
                        totalPages = maxOf(1, score.notes.size / 32 + 1)
                    )
                }
            }
            follower.onPageTurn = { newPage ->
                _uiState.update { it.copy(currentPage = newPage) }
            }
            follower.onNoteMatch = { result ->
                handleMatchResult(result)
            }
            // 段落循环回调：更新循环计数
            follower.onSectionLoop = { count ->
                _uiState.update { it.copy(loopCount = count) }
            }
        }
        val maxM = com.pianocompanion.following.SectionLooper.maxMeasure(score)
        _uiState.update {
            it.copy(
                score = score,
                sessionSaved = false,
                maxMeasure = maxM,
                loopStartMeasure = 0,
                loopEndMeasure = maxM,
                loopEnabled = false,
                loopCount = 0
            )
        }
    }

    fun startPractice() {
        if (!checkMicPermission()) {
            _uiState.update { it.copy(errorMessage = "需要麦克风权限") }
            return
        }

        val follower = scoreFollower ?: run {
            // Auto-select first demo score if none selected
            val score = availableScores.first()
            setScore(score)
            scoreFollower ?: return
        }

        follower.start()
        practiceStartTime = System.currentTimeMillis()

        audioRecorder = AudioRecorder { samples ->
            follower.processAudio(samples)
        }

        try {
            audioRecorder?.start()
            _uiState.update {
                it.copy(
                    isPracticing = true,
                    correctCount = 0,
                    wrongCount = 0,
                    accuracy = 0f,
                    lastFeedback = FeedbackType.NONE,
                    errorMessage = null,
                    sessionSaved = false
                )
            }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(errorMessage = "无法访问麦克风: ${e.message}") }
        }
    }

    fun stopPractice() {
        audioRecorder?.stop()
        audioRecorder = null
        scoreFollower?.stop()
        metronome.stop()
        _uiState.update { it.copy(metronomeBeat = -1) }

        // Haptic feedback for completion
        haptic.enabled = settingsRepo.hapticFeedback
        haptic.practiceComplete()

        // Save session to stats
        val duration = System.currentTimeMillis() - practiceStartTime
        val score = _uiState.value.score
        if (score != null && _uiState.value.correctCount + _uiState.value.wrongCount > 0) {
            val stats = scoreFollower?.getStats()
            val total = _uiState.value.correctCount + _uiState.value.wrongCount
            val accuracy = if (total > 0) _uiState.value.correctCount.toFloat() / total else 0f

            val record = SessionRecord(
                scoreTitle = score.title,
                startTime = practiceStartTime,
                durationMs = duration,
                totalNotes = total,
                correctNotes = _uiState.value.correctCount,
                wrongNotes = _uiState.value.wrongCount,
                missedNotes = stats?.missedNotes ?: 0,
                extraNotes = stats?.extraNotes ?: 0,
                accuracy = accuracy,
                updatedAt = System.currentTimeMillis()
            )
            statsRepository.saveSession(record)
            _uiState.update { it.copy(sessionSaved = true) }
        }

        _uiState.update { it.copy(isPracticing = false) }
    }

    private fun handleMatchResult(result: MatchResult) {
        // FOLLOW mode: only track position, no error feedback
        if (_uiState.value.practiceMode == PracticeMode.FOLLOW) return

        // EXAM mode: accumulate stats but no real-time feedback
        val showFeedback = _uiState.value.practiceMode == PracticeMode.NORMAL
        haptic.enabled = settingsRepo.hapticFeedback

        // Update hand tracker stats
        val follower = scoreFollower
        val handStats = follower?.handTracker?.stats

        when (result.status) {
            MatchStatus.CORRECT -> {
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastFeedback = if (showFeedback) FeedbackType.CORRECT else FeedbackType.NONE,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount + 1, it.wrongCount)
                    )
                }
                if (showFeedback) haptic.correctNote()
            }
            MatchStatus.WRONG_PITCH -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = if (showFeedback) FeedbackType.WRONG_PITCH else FeedbackType.NONE,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
                if (showFeedback) haptic.wrongPitch()
            }
            MatchStatus.EXTRA_NOTE -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = if (showFeedback) FeedbackType.EXTRA_NOTE else FeedbackType.NONE,
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
                if (showFeedback) haptic.extraNote()
            }
            MatchStatus.MISSING_NOTE -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = if (showFeedback) FeedbackType.MISSING_NOTE else FeedbackType.NONE,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
                if (showFeedback) haptic.missingNote()
            }
            MatchStatus.RHYTHM_ERROR -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = if (showFeedback) FeedbackType.WRONG_PITCH else FeedbackType.NONE,
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
                if (showFeedback) haptic.wrongPitch()
            }
        }

        // Update hand separation stats
        if (handStats != null) {
            _uiState.update {
                it.copy(
                    rightHandAccuracy = handStats.rightAccuracy,
                    leftHandAccuracy = handStats.leftAccuracy,
                    rightHandCorrect = handStats.rightCorrect,
                    leftHandCorrect = handStats.leftCorrect
                )
            }
        }
    }

    fun setPracticeMode(mode: PracticeMode) {
        _uiState.update { it.copy(practiceMode = mode) }
    }

    // === Section loop practice ===

    /** 启用/禁用段落循环练习。 */
    fun setLoopEnabled(enabled: Boolean) {
        scoreFollower?.sectionLooper?.let { looper ->
            looper.enabled = enabled
            if (enabled) {
                looper.startMeasure = _uiState.value.loopStartMeasure
                looper.endMeasure = _uiState.value.loopEndMeasure
                looper.resetLoopCount()
            }
        }
        _uiState.update { it.copy(loopEnabled = enabled, loopCount = 0) }
    }

    /**
     * 设置循环段落范围 [start, end]（会自动保证 start <= end 并 clamp 到合法范围）。
     */
    fun setLoopRange(start: Int, end: Int) {
        val maxM = _uiState.value.maxMeasure
        val s = start.coerceIn(0, maxM)
        val e = end.coerceIn(0, maxM)
        val (lo, hi) = if (s <= e) s to e else e to s
        scoreFollower?.sectionLooper?.let { looper ->
            looper.startMeasure = lo
            looper.endMeasure = hi
            looper.resetLoopCount()
        }
        _uiState.update { it.copy(loopStartMeasure = lo, loopEndMeasure = hi, loopCount = 0) }
    }

    private fun calcAccuracy(correct: Int, wrong: Int): Float {
        val total = correct + wrong
        return if (total > 0) correct.toFloat() / total else 0f
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPractice()
    }
}
