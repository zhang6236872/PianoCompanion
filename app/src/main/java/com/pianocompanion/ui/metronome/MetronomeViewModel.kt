package com.pianocompanion.ui.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pianocompanion.audio.Metronome
import com.pianocompanion.audio.Subdivision
import kotlinx.coroutines.flow.*

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    data class MetronomeUiState(
        val isPlaying: Boolean = false,
        val bpm: Int = 120,
        val beatsPerMeasure: Int = 4,
        val currentBeat: Int = -1,
        val subdivision: Subdivision = Subdivision.QUARTER,
        /** 当前子拍点索引（用于细分可视化），未播放时为 -1。 */
        val currentSubClick: Int = -1
    )

    private val _uiState = MutableStateFlow(MetronomeUiState())
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private var metronome: Metronome? = null

    init {
        metronome = Metronome()
        metronome?.onBeat = { beat ->
            _uiState.update { it.copy(currentBeat = beat) }
        }
    }

    fun start() {
        metronome?.let { m ->
            m.setBpm(_uiState.value.bpm)
            m.setBeatsPerMeasure(_uiState.value.beatsPerMeasure)
            m.setSubdivision(_uiState.value.subdivision)
            m.start()
            _uiState.update { it.copy(isPlaying = true, currentBeat = -1) }
        }
    }

    fun stop() {
        metronome?.stop()
        _uiState.update { it.copy(isPlaying = false, currentBeat = -1) }
    }

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(40, 240)
        metronome?.setBpm(clamped)
        _uiState.update { it.copy(bpm = clamped) }
    }

    fun increaseBpm() = setBpm(_uiState.value.bpm + 1)
    fun decreaseBpm() = setBpm(_uiState.value.bpm - 1)

    fun setBeatsPerMeasure(beats: Int) {
        metronome?.setBeatsPerMeasure(beats)
        _uiState.update { it.copy(beatsPerMeasure = beats, currentBeat = -1) }
    }

    /**
     * 设置细分模式。播放中切换时由 [Metronome] 在下一个子拍点生效，
     * UI 立即更新以反映新选择。
     */
    fun setSubdivision(sub: Subdivision) {
        metronome?.setSubdivision(sub)
        _uiState.update { it.copy(subdivision = sub, currentBeat = -1) }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
