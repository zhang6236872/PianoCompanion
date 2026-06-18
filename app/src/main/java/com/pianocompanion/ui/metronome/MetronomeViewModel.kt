package com.pianocompanion.ui.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pianocompanion.audio.Metronome
import kotlinx.coroutines.flow.*

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    data class MetronomeUiState(
        val isPlaying: Boolean = false,
        val bpm: Int = 120,
        val beatsPerMeasure: Int = 4,
        val currentBeat: Int = -1
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

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
