package com.pianocompanion.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.data.repository.SettingsRepository
import com.pianocompanion.following.DtwConfig
import kotlinx.coroutines.flow.*

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    data class SettingsUiState(
        val autoPageTurn: Boolean = true,
        val errorFeedback: Boolean = true,
        val soundOnWrong: Boolean = true,
        val hapticFeedback: Boolean = true,
        val micSensitivity: Float = 0.6f,
        val practiceMode: String = "NORMAL",
        val dtwConfig: DtwConfig = DtwConfig.DEFAULT
    )

    private val repo = SettingsRepository(application)

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadSettings() = SettingsUiState(
        autoPageTurn = repo.autoPageTurn,
        errorFeedback = repo.errorFeedback,
        soundOnWrong = repo.soundOnWrong,
        hapticFeedback = repo.hapticFeedback,
        micSensitivity = repo.micSensitivity,
        practiceMode = repo.practiceMode,
        dtwConfig = repo.getDtwConfig()
    )

    private fun persist() {
        val s = _uiState.value
        repo.autoPageTurn = s.autoPageTurn
        repo.errorFeedback = s.errorFeedback
        repo.soundOnWrong = s.soundOnWrong
        repo.hapticFeedback = s.hapticFeedback
        repo.micSensitivity = s.micSensitivity
        repo.practiceMode = s.practiceMode
        repo.saveDtwConfig(s.dtwConfig)
    }

    // === Toggle setters ===
    fun setAutoPageTurn(v: Boolean) { _uiState.update { it.copy(autoPageTurn = v) }; persist() }
    fun setErrorFeedback(v: Boolean) { _uiState.update { it.copy(errorFeedback = v) }; persist() }
    fun setSoundOnWrong(v: Boolean) { _uiState.update { it.copy(soundOnWrong = v) }; persist() }
    fun setHapticFeedback(v: Boolean) { _uiState.update { it.copy(hapticFeedback = v) }; persist() }

    fun setMicSensitivity(v: Float) { _uiState.update { it.copy(micSensitivity = v) }; persist() }
    fun setPracticeMode(mode: String) { _uiState.update { it.copy(practiceMode = mode) }; persist() }

    // === DTW Config setters ===
    fun setDtwConfig(config: DtwConfig) {
        _uiState.update { it.copy(dtwConfig = config) }
        persist()
    }

    fun updateDtwConfig(transform: (DtwConfig) -> DtwConfig) {
        val newConfig = transform(_uiState.value.dtwConfig)
        setDtwConfig(newConfig)
    }

    fun applyPreset(preset: DtwConfig) {
        setDtwConfig(preset)
    }

    /** Get current DTW config for ScoreFollower consumption */
    fun getDtwConfig(): DtwConfig = _uiState.value.dtwConfig
}
