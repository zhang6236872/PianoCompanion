package com.pianocompanion.circle

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
 * 五度圈 UI 状态（不可变数据类）。
 */
data class CircleOfFifthsUiState(
    val selectedMode: CircleMode = CircleMode.MAJOR,
    val selectedKey: CircleKey = CircleKey(0, CircleMode.MAJOR),
    val currentKeyInfo: KeyInfo? = null,
    val diatonicChords: List<DiatonicChord> = emptyList(),
    val majorKeys: List<CircleKey> = emptyList(),
    val minorKeys: List<CircleKey> = emptyList(),
    val closelyRelatedKeys: List<CircleKey> = emptyList(),
    val playMode: CirclePlayMode = CirclePlayMode.DIATONIC_CHORDS,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false
)

/**
 * 五度圈 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[CircleOfFifthsEngine]、[CircleOfFifthsAudioBuilder]）
 * 与 Android 音频层（[CircleOfFifthsPlayer]）。
 */
class CircleOfFifthsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val audioBuilder = CircleOfFifthsAudioBuilder()
    private val player = CircleOfFifthsPlayer()

    private val _uiState = MutableStateFlow(
        CircleOfFifthsUiState(
            majorKeys = CircleOfFifthsEngine.allKeys(CircleMode.MAJOR),
            minorKeys = CircleOfFifthsEngine.allKeys(CircleMode.MINOR)
        )
    )
    val uiState: StateFlow<CircleOfFifthsUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
        selectKey(CircleKey(0, CircleMode.MAJOR))
    }

    /**
     * 选择圆环上的某个调性。
     */
    fun selectKey(key: CircleKey) {
        val info = CircleOfFifthsEngine.keyInfo(key)
        val chords = CircleOfFifthsEngine.diatonicChords(key)
        val related = CircleOfFifthsEngine.closelyRelatedKeys(key)
        update {
            it.copy(
                selectedMode = key.mode,
                selectedKey = key,
                currentKeyInfo = info,
                diatonicChords = chords,
                closelyRelatedKeys = related,
                audioReady = false
            )
        }
        prepareAudio()
    }

    /**
     * 切换调式（大调↔小调），保持主音不变。
     */
    fun toggleMode() {
        val current = _uiState.value.selectedKey
        val newMode = if (current.isMajor) CircleMode.MINOR else CircleMode.MAJOR
        selectKey(CircleKey(current.tonicPc, newMode))
    }

    /**
     * 切换播放模式（顺阶和弦↔音阶）。
     */
    fun selectPlayMode(mode: CirclePlayMode) {
        update { it.copy(playMode = mode) }
        prepareAudio()
    }

    private fun prepareAudio() {
        val key = _uiState.value.selectedKey
        val mode = _uiState.value.playMode

        player.stop()
        update { it.copy(isPlaying = false, audioReady = false) }

        viewModelScope.launch(Dispatchers.Default) {
            val audio = when (mode) {
                CirclePlayMode.DIATONIC_CHORDS -> audioBuilder.renderDiatonicChords(key)
                CirclePlayMode.SCALE -> audioBuilder.renderScale(key)
            }
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    fun play() {
        if (!_uiState.value.audioReady) return
        player.play()
        update { it.copy(isPlaying = true) }
    }

    fun stopPlayback() {
        player.stop()
        update { it.copy(isPlaying = false) }
    }

    private inline fun update(transform: (CircleOfFifthsUiState) -> CircleOfFifthsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
