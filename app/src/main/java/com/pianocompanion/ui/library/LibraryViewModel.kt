package com.pianocompanion.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianocompanion.analytics.DifficultyLevel
import com.pianocompanion.data.FavoriteStore
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.repository.ScoreRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryUiState(
    val importedScores: List<ScoreItem> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val favorites: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false
)

/**
 * A single row in the library. [fileName] is the on-disk filename for imported
 * scores (empty for built-in demo scores, which have no backing file).
 *
 * [difficultyScore] / [difficultyLevel] 由 [DifficultyEstimator] 在仓库解析时
 * 计算，使导入乐谱卡片与内置乐谱卡片展示一致的难度信息。
 */
data class ScoreItem(
    val title: String,
    val composer: String,
    val noteCount: Int,
    val fileName: String = "",
    val isImported: Boolean = fileName.isNotEmpty(),
    val parseFailed: Boolean = false,
    val source: String = "MusicXML",
    val difficultyScore: Int = 0,
    val difficultyLevel: DifficultyLevel = DifficultyLevel.BEGINNER
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScoreRepository(application)

    private val favoriteStore = FavoriteStore().apply {
        // 从 SharedPreferences 恢复收藏数据
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fromJson(prefs.getString(FAVORITES_KEY, null))
    }

    private val _uiState = MutableStateFlow(LibraryUiState(favorites = favoriteStore.list().toSet()))
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
                source = info.source,
                difficultyScore = info.difficultyScore,
                difficultyLevel = info.difficultyLevel
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
            // 同步移除收藏标记（防止遗留无效收藏）
            onImportedScoreDeleted(fileName)
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

    // ───────────────────────── 收藏管理 ─────────────────────────

    /** 切换内置乐谱的收藏状态。 */
    fun toggleBuiltInFavorite(scoreId: String) {
        toggleFavorite(FavoriteStore.keyForBuiltIn(scoreId))
    }

    /** 切换导入乐谱的收藏状态。 */
    fun toggleImportedFavorite(fileName: String) {
        toggleFavorite(FavoriteStore.keyForImported(fileName))
    }

    private fun toggleFavorite(key: String) {
        favoriteStore.toggle(key)
        persistFavorites()
        _uiState.update { it.copy(favorites = favoriteStore.list().toSet()) }
    }

    /**
     * 删除导入乐谱时，同步移除其收藏标记（防止遗留无效收藏）。
     */
    fun onImportedScoreDeleted(fileName: String) {
        val key = FavoriteStore.keyForImported(fileName)
        if (favoriteStore.isFavorite(key)) {
            favoriteStore.remove(key)
            persistFavorites()
            _uiState.update { it.copy(favorites = favoriteStore.list().toSet()) }
        }
    }

    /** 切换「只看收藏」筛选模式。 */
    fun toggleFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    private fun persistFavorites() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(FAVORITES_KEY, favoriteStore.toJson()).apply()
    }

    companion object {
        private const val PREFS_NAME = "score_favorites"
        private const val FAVORITES_KEY = "favorites_json"
    }
}
