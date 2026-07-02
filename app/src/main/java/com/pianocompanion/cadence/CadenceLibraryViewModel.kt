package com.pianocompanion.cadence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.chord.ChordRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 终止式参考库 UI 状态（不可变数据类）。
 */
data class CadenceLibraryUiState(
    val selectedKey: ChordRoot = ChordRoot.C,
    val selectedMode: CadenceMode = CadenceMode.MAJOR,
    val selectedCadence: CadenceType = CadenceType.PERFECT_AUTHENTIC,
    val currentInstance: CadenceInstance? = null,
    val availableCadences: List<CadenceType> = emptyList(),
    val isPlaying: Boolean = false,
    val audioReady: Boolean = false,
    val categories: Map<CadenceCategory, List<CadenceType>> = emptyMap()
)

/**
 * 终止式参考库 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[CadenceEngine]、[CadenceAudioBuilder]）与
 * Android 音频层（[CadencePlayer]）。
 */
class CadenceLibraryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val audioBuilder = CadenceAudioBuilder()
    private val player = CadencePlayer()

    private val _uiState = MutableStateFlow(
        CadenceLibraryUiState(
            categories = CadenceEngine.allCadencesByCategory()
        )
    )
    val uiState: StateFlow<CadenceLibraryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
        refreshAvailableCadences()
        selectCadence(CadenceType.PERFECT_AUTHENTIC)
    }

    /**
     * 选择调性主音。
     */
    fun selectKey(key: ChordRoot) {
        update { it.copy(selectedKey = key) }
        rebuildInstance()
    }

    /**
     * 切换调式（大调 / 和声小调）。
     * 如果当前终止式不支持所选调式，自动切换到第一个支持的终止式。
     */
    fun selectMode(mode: CadenceMode) {
        val state = _uiState.value
        val currentCadence = state.selectedCadence
        val modes = CadenceEngine.supportedModes(currentCadence)
        val actualMode = if (mode in modes) mode else modes.first()

        update { it.copy(selectedMode = actualMode) }
        rebuildInstance()
    }

    /**
     * 选择终止式类型。
     */
    fun selectCadence(type: CadenceType) {
        val state = _uiState.value
        // 确保当前调式被该终止式支持
        val modes = CadenceEngine.supportedModes(type)
        val mode = if (state.selectedMode in modes) state.selectedMode else modes.first()

        update {
            it.copy(
                selectedCadence = type,
                selectedMode = mode,
                availableCadences = CadenceType.entries.filter { ct ->
                    state.selectedMode in CadenceEngine.supportedModes(ct)
                }
            )
        }
        rebuildInstance()
    }

    /**
     * 重建终止式实例并预渲染音频。
     */
    private fun rebuildInstance() {
        val state = _uiState.value
        val instance = CadenceEngine.instantiate(
            keyRoot = state.selectedKey,
            cadenceType = state.selectedCadence,
            mode = state.selectedMode
        )

        update {
            it.copy(
                currentInstance = instance,
                audioReady = false
            )
        }
        prepareAudio()
    }

    /**
     * 根据当前调式刷新可用终止式列表。
     */
    private fun refreshAvailableCadences() {
        val state = _uiState.value
        update {
            it.copy(
                availableCadences = CadenceType.entries.filter { ct ->
                    state.selectedMode in CadenceEngine.supportedModes(ct)
                }
            )
        }
    }

    /**
     * 后台预渲染终止式音频。
     */
    private fun prepareAudio() {
        val instance = _uiState.value.currentInstance ?: return

        player.stop()
        update { it.copy(isPlaying = false, audioReady = false) }

        viewModelScope.launch(Dispatchers.Default) {
            val audio = audioBuilder.render(instance)
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    /**
     * 播放当前终止式。
     */
    fun playCadence() {
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

    private inline fun update(transform: (CadenceLibraryUiState) -> CadenceLibraryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
