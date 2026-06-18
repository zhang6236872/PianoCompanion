package com.pianocompanion.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.repository.ScoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryUiState(
    val scores: List<ScoreItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

data class ScoreItem(
    val fileName: String,
    val title: String,
    val isSelected: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScoreRepository(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refreshScores()
    }

    fun refreshScores() {
        val files = repository.listScores()
        val items = files.map { fileName ->
            ScoreItem(
                fileName = fileName,
                title = fileName.removeSuffix(".xml").removeSuffix(".mid"),
                isSelected = false
            )
        }
        _uiState.update { it.copy(scores = items) }
    }

    fun importScore(uri: android.net.Uri) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = repository.importScore(uri)
            if (result.isSuccess) {
                val score = result.getOrThrow()!!
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "导入成功: ${score.title}"
                    )
                }
                refreshScores()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "导入失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun loadScore(fileName: String): Result<Score> {
        return repository.loadScore(fileName)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
