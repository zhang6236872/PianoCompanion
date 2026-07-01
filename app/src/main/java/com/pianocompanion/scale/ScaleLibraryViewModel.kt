package com.pianocompanion.scale

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
 * 音阶词典 UI 状态（不可变数据类）。
 */
data class ScaleLibraryUiState(
    val selectedRoot: ScaleRoot = ScaleRoot.C,
    val selectedType: ScaleType = ScaleType.MAJOR,
    val currentScale: ScaleInfo? = null,
    val degreeNames: List<String> = emptyList(),
    val fingering: List<Int> = emptyList(),
    val intervalSteps: List<Int> = emptyList(),
    val isPlaying: Boolean = false,
    val playDirection: PlayDirection = PlayDirection.ASCENDING_DESCENDING,
    val audioReady: Boolean = false,
    val categories: Map<ScaleCategory, List<ScaleType>> = emptyMap(),
    val relativeKeyName: String = ""
)

/**
 * 音阶词典 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[ScaleEngine]、[ScaleAudioBuilder]）与
 * Android 音频层（[ScalePlayer]）。
 */
class ScaleLibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val audioBuilder = ScaleAudioBuilder()
    private val player = ScalePlayer()

    private val _uiState = MutableStateFlow(
        ScaleLibraryUiState(
            categories = ScaleEngine.allScalesByCategory()
        )
    )
    val uiState: StateFlow<ScaleLibraryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
        selectScale(ScaleRoot.C, ScaleType.MAJOR)
    }

    fun selectRoot(root: ScaleRoot) {
        val state = _uiState.value
        selectScale(root, state.selectedType)
    }

    fun selectType(type: ScaleType) {
        val state = _uiState.value
        selectScale(state.selectedRoot, type)
    }

    fun selectDirection(direction: PlayDirection) {
        update { it.copy(playDirection = direction) }
        prepareAudio()
    }

    private fun selectScale(root: ScaleRoot, type: ScaleType) {
        val scale = ScaleEngine.build(root, type)
        val degrees = ScaleEngine.degreeNames(type)
        val fingers = ScaleEngine.suggestedFingering(root, type)
        val steps = ScaleEngine.intervalSteps(type)
        val relativeKey = computeRelativeKey(root, type)

        update {
            it.copy(
                selectedRoot = root,
                selectedType = type,
                currentScale = scale,
                degreeNames = degrees,
                fingering = fingers,
                intervalSteps = steps,
                relativeKeyName = relativeKey,
                audioReady = false
            )
        }
        prepareAudio()
    }

    private fun computeRelativeKey(root: ScaleRoot, type: ScaleType): String {
        return when (type.category) {
            ScaleCategory.MAJOR -> {
                val relMinor = ScaleEngine.relativeMinor(root)
                val preferFlats = ScaleEngine.preferFlatsKey(relMinor)
                "关系小调：${relMinor.name(preferFlats)}自然小调"
            }
            ScaleCategory.MINOR -> {
                val relMajor = ScaleEngine.relativeMajor(root)
                val preferFlats = ScaleEngine.preferFlatsKey(relMajor)
                "关系大调：${relMajor.name(preferFlats)}自然大调"
            }
            else -> ""
        }
    }

    private fun prepareAudio() {
        val scale = _uiState.value.currentScale ?: return
        val direction = _uiState.value.playDirection

        player.stop()
        update { it.copy(isPlaying = false, audioReady = false) }

        viewModelScope.launch(Dispatchers.Default) {
            val audio = when (direction) {
                PlayDirection.ASCENDING -> audioBuilder.renderAscending(scale)
                PlayDirection.DESCENDING -> audioBuilder.renderDescending(scale)
                PlayDirection.ASCENDING_DESCENDING -> audioBuilder.renderAscendingDescending(scale)
            }
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    fun playScale() {
        if (!_uiState.value.audioReady) return
        player.play()
        update { it.copy(isPlaying = true) }
    }

    fun stopPlayback() {
        player.stop()
        update { it.copy(isPlaying = false) }
    }

    private inline fun update(transform: (ScaleLibraryUiState) -> ScaleLibraryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
