package com.pianocompanion.ui.practice

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.audio.AudioRecorder
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
        val sessionSaved: Boolean = false
    )

    enum class FeedbackType { NONE, CORRECT, WRONG_PITCH, EXTRA_NOTE, MISSING_NOTE }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // Available scores for selection
    val availableScores = DemoScores.getAll()

    private var audioRecorder: AudioRecorder? = null
    private var scoreFollower: ScoreFollower? = null
    private val statsRepository = StatsRepository(application)
    private var practiceStartTime: Long = 0

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
        }
        _uiState.update { it.copy(score = score, sessionSaved = false) }
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
                accuracy = accuracy
            )
            statsRepository.saveSession(record)
            _uiState.update { it.copy(sessionSaved = true) }
        }

        _uiState.update { it.copy(isPracticing = false) }
    }

    private fun handleMatchResult(result: MatchResult) {
        when (result.status) {
            MatchStatus.CORRECT -> {
                _uiState.update {
                    it.copy(
                        correctCount = it.correctCount + 1,
                        lastFeedback = FeedbackType.CORRECT,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount + 1, it.wrongCount)
                    )
                }
            }
            MatchStatus.WRONG_PITCH -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = FeedbackType.WRONG_PITCH,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
            }
            MatchStatus.EXTRA_NOTE -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = FeedbackType.EXTRA_NOTE,
                        lastDetectedNote = result.detectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
            }
            MatchStatus.MISSING_NOTE -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = FeedbackType.MISSING_NOTE,
                        lastExpectedNote = result.expectedNote?.noteName ?: "",
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
            }
            MatchStatus.RHYTHM_ERROR -> {
                _uiState.update {
                    it.copy(
                        wrongCount = it.wrongCount + 1,
                        lastFeedback = FeedbackType.WRONG_PITCH,
                        accuracy = calcAccuracy(it.correctCount, it.wrongCount + 1)
                    )
                }
            }
        }
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
