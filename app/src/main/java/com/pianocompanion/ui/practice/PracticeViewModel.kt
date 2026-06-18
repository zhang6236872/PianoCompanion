package com.pianocompanion.ui.practice

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.audio.AudioRecorder
import com.pianocompanion.data.model.MatchResult
import com.pianocompanion.data.model.MatchStatus
import com.pianocompanion.data.model.Score
import com.pianocompanion.following.ScoreFollower
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Practice screen.
 *
 * Bridges the AudioRecorder, ScoreFollower engine, and the Compose UI.
 * Manages practice state, permissions, and real-time note feedback.
 */
class PracticeViewModel(
    application: Application
) : AndroidViewModel(application) {

    // === UI State ===
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
        val errorMessage: String? = null
    )

    enum class FeedbackType { NONE, CORRECT, WRONG_PITCH, EXTRA_NOTE, MISSING_NOTE }

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    // === Engine ===
    private var audioRecorder: AudioRecorder? = null
    private var scoreFollower: ScoreFollower? = null

    /**
     * Check if the app has microphone permission.
     */
    fun checkMicPermission(): Boolean {
        val granted = getApplication<Application>().checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasMicPermission = granted) }
        return granted
    }

    /**
     * Set the current score to practice.
     */
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
        _uiState.update { it.copy(score = score) }
    }

    /**
     * Start practicing — begins audio capture and score following.
     */
    fun startPractice() {
        if (!checkMicPermission()) {
            _uiState.update { it.copy(errorMessage = "需要麦克风权限") }
            return
        }

        val follower = scoreFollower ?: run {
            _uiState.update { it.copy(errorMessage = "请先选择乐谱") }
            return
        }

        follower.start()

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
                    errorMessage = null
                )
            }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(errorMessage = "无法访问麦克风: ${e.message}") }
        }
    }

    /**
     * Stop practicing.
     */
    fun stopPractice() {
        audioRecorder?.stop()
        audioRecorder = null
        scoreFollower?.stop()
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
