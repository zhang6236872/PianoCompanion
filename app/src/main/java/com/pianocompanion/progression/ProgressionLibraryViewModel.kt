package com.pianocompanion.progression

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
 * 和弦进行词典 UI 状态（不可变数据类）。
 */
data class ProgressionLibraryUiState(
    val templates: List<ProgressionTemplate> = emptyList(),
    val templatesByGenre: Map<ProgressionGenre, List<ProgressionTemplate>> = emptyMap(),
    val selectedTemplate: ProgressionTemplate? = null,
    val selectedKey: com.pianocompanion.chord.ChordRoot = com.pianocompanion.chord.ChordRoot.C,
    val currentInstance: ProgressionInstance? = null,
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val audioDurationMs: Long = 0L
)

/**
 * 和弦进行词典 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[ProgressionEngine]、[ProgressionAudioBuilder]）与
 * Android 音频层（[ProgressionPlayer]）。
 */
class ProgressionLibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val audioBuilder = ProgressionAudioBuilder()
    private val player = ProgressionPlayer()

    private val _uiState = MutableStateFlow(
        ProgressionLibraryUiState(
            templates = ProgressionEngine.allTemplates(),
            templatesByGenre = ProgressionEngine.templatesByGenre()
        )
    )
    val uiState: StateFlow<ProgressionLibraryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
        // 初始化默认进行
        ProgressionEngine.findTemplate("pop_axis")?.let { selectTemplate(it) }
    }

    /**
     * 选择进行模板。
     */
    fun selectTemplate(template: ProgressionTemplate) {
        val instance = ProgressionEngine.instantiate(template, _uiState.value.selectedKey)
        update {
            it.copy(
                selectedTemplate = template,
                currentInstance = instance,
                audioReady = false
            )
        }
        prepareAudio()
    }

    /**
     * 选择调性根音。
     */
    fun selectKey(key: com.pianocompanion.chord.ChordRoot) {
        val template = _uiState.value.selectedTemplate ?: return
        val instance = ProgressionEngine.instantiate(template, key)
        update {
            it.copy(
                selectedKey = key,
                currentInstance = instance,
                audioReady = false
            )
        }
        prepareAudio()
    }

    /**
     * 后台预渲染进行音频。
     */
    private fun prepareAudio() {
        val instance = _uiState.value.currentInstance ?: return

        player.stop()
        update { it.copy(isPlaying = false, audioReady = false) }

        viewModelScope.launch(Dispatchers.Default) {
            val durationMs = audioBuilder.estimateDurationMs(instance)
            val audio = audioBuilder.render(instance)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update {
                    it.copy(
                        audioReady = true,
                        audioDurationMs = durationMs
                    )
                }
            }
        }
    }

    /**
     * 播放当前进行。
     */
    fun playProgression() {
        if (!_uiState.value.audioReady) return
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

    private inline fun update(transform: (ProgressionLibraryUiState) -> ProgressionLibraryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
