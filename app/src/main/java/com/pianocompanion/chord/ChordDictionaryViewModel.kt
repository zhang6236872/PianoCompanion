package com.pianocompanion.chord

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
 * 和弦词典 UI 状态（不可变数据类）。
 */
data class ChordDictionaryUiState(
    val selectedRoot: ChordRoot = ChordRoot.C,
    val selectedType: ChordType = ChordType.MAJOR,
    val selectedInversion: ChordInversion = ChordInversion.ROOT_POSITION,
    val currentVoicing: ChordVoicing? = null,
    val intervalNames: List<String> = emptyList(),
    val fingering: List<Int> = emptyList(),
    val isPlaying: Boolean = false,
    val isArpeggioMode: Boolean = false,
    val audioReady: Boolean = false,
    val categories: Map<ChordCategory, List<ChordType>> = emptyMap(),
    val availableInversions: List<ChordInversion> = emptyList()
)

/**
 * 和弦词典 ViewModel。
 *
 * 连接纯 Kotlin 领域层（[ChordEngine]、[ChordAudioBuilder]）与
 * Android 音频层（[ChordPlayer]）。
 *
 * UI 状态通过 [uiState]（StateFlow）暴露，Compose 直接 collectAsState 观察。
 */
class ChordDictionaryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val audioBuilder = ChordAudioBuilder()
    private val player = ChordPlayer()

    private val _uiState = MutableStateFlow(
        ChordDictionaryUiState(
            categories = ChordEngine.allChordsByCategory()
        )
    )
    val uiState: StateFlow<ChordDictionaryUiState> = _uiState.asStateFlow()

    init {
        player.onComplete = {
            update { it.copy(isPlaying = false) }
        }
        // 初始化默认和弦
        selectChord(ChordRoot.C, ChordType.MAJOR, ChordInversion.ROOT_POSITION)
    }

    /**
     * 选择根音。
     */
    fun selectRoot(root: ChordRoot) {
        val state = _uiState.value
        selectChord(root, state.selectedType, state.selectedInversion)
    }

    /**
     * 选择和弦类型。
     */
    fun selectType(type: ChordType) {
        val state = _uiState.value
        // 如果当前转位对新和弦类型不可用，回退到原位
        val inversions = ChordEngine.availableInversions(type)
        val inversion = if (state.selectedInversion in inversions) state.selectedInversion
                        else ChordInversion.ROOT_POSITION
        selectChord(state.selectedRoot, type, inversion)
    }

    /**
     * 选择转位。
     */
    fun selectInversion(inversion: ChordInversion) {
        val state = _uiState.value
        selectChord(state.selectedRoot, state.selectedType, inversion)
    }

    /**
     * 切换琶音/柱式模式。
     */
    fun toggleArpeggioMode() {
        update { it.copy(isArpeggioMode = !it.isArpeggioMode) }
        prepareAudio()
    }

    /**
     * 统一和弦选择入口——重建 voicing 并预渲染音频。
     */
    private fun selectChord(
        root: ChordRoot,
        type: ChordType,
        inversion: ChordInversion
    ) {
        val voicing = ChordEngine.build(root, type, inversion)
        val intervals = ChordEngine.intervalNames(type)
        val fingers = ChordEngine.suggestedFingering(type, inversion)
        val inversions = ChordEngine.availableInversions(type)

        update {
            it.copy(
                selectedRoot = root,
                selectedType = type,
                selectedInversion = inversion,
                currentVoicing = voicing,
                intervalNames = intervals,
                fingering = fingers,
                availableInversions = inversions,
                audioReady = false
            )
        }
        prepareAudio()
    }

    /**
     * 后台预渲染和弦音频。
     */
    private fun prepareAudio() {
        val voicing = _uiState.value.currentVoicing ?: return
        val arpeggiated = _uiState.value.isArpeggioMode

        player.stop()
        update { it.copy(isPlaying = false, audioReady = false) }

        viewModelScope.launch(Dispatchers.Default) {
            val audio = if (arpeggiated) {
                audioBuilder.renderArpeggiated(voicing)
            } else {
                audioBuilder.renderBlocked(voicing)
            }
            withContext(Dispatchers.Main) {
                player.prepare(audio)
                update { it.copy(audioReady = true) }
            }
        }
    }

    /**
     * 播放当前和弦。
     */
    fun playChord() {
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

    private inline fun update(transform: (ChordDictionaryUiState) -> ChordDictionaryUiState) {
        _uiState.value = transform(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
