package com.pianocompanion.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.repository.ScoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryUiState(
    val importedScores: List<ScoreItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

/**
 * A single row in the library. [fileName] is the on-disk filename for imported
 * scores (empty for built-in demo scores, which have no backing file).
 */
data class ScoreItem(
    val title: String,
    val composer: String,
    val noteCount: Int,
    val fileName: String = "",
    val isImported: Boolean = fileName.isNotEmpty(),
    val parseFailed: Boolean = false,
    val source: String = "MusicXML"
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScoreRepository(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refreshScores()
    }

    fun refreshScores() {
        val items = repository.listImportedScores().map { info ->
            ScoreItem(
                title = info.title,
                composer = info.composer,
                noteCount = info.noteCount,
                fileName = info.fileName,
                isImported = true,
                parseFailed = info.parseFailed,
                source = info.source
            )
        }
        _uiState.update { it.copy(importedScores = items) }
    }

    /** Import a MusicXML file selected via the Storage Access Framework. */
    fun importScore(uri: Uri) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = repository.importScore(uri)
            if (result.isSuccess) {
                val score = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "导入成功: ${score.title}（${score.notes.size} 个音符）"
                    )
                }
                refreshScores()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "导入失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    /** Delete a previously imported score file. */
    fun deleteScore(fileName: String) {
        viewModelScope.launch {
            val deleted = repository.deleteScore(fileName)
            refreshScores()
            _uiState.update {
                it.copy(message = if (deleted) "已删除乐谱" else "删除失败")
            }
        }
    }

    /** Load an imported [Score] by its stored filename (used when starting practice). */
    fun loadScore(fileName: String): Result<Score> {
        return repository.loadScore(fileName)
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
