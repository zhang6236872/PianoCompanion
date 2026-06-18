package com.pianocompanion.data.repository

import android.content.Context
import android.net.Uri
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.parser.MusicXmlParser
import java.io.File

/**
 * Lightweight metadata describing an imported score, used for listing
 * without forcing the caller to fully load every [Score].
 */
data class ImportedScoreInfo(
    val fileName: String,
    val title: String,
    val composer: String,
    val noteCount: Int,
    val parseFailed: Boolean = false
)

/**
 * Manages score file storage and retrieval.
 * Scores are stored in app's internal storage as MusicXML files.
 */
class ScoreRepository(private val context: Context) {

    private val scoresDir: File by lazy {
        File(context.filesDir, "scores").apply { mkdirs() }
    }

    private val parser = MusicXmlParser()

    /**
     * Import a MusicXML file from a [uri] (typically returned by the Storage
     * Access Framework) into the app's internal storage.
     *
     * The file is first parsed to validate it and extract its metadata, then
     * copied to [scoresDir] under a sanitized filename derived from the score
     * title. If a file with the same name already exists it is overwritten.
     *
     * @return the parsed [Score] on success, or a failure wrapping the cause.
     */
    fun importScore(uri: Uri): Result<Score> {
        return try {
            val score = context.contentResolver.openInputStream(uri)?.use { stream ->
                parser.parse(stream)
            } ?: return Result.failure(Exception("无法打开所选文件"))

            // Sanitize a stable filename from the score title. Re-importing the
            // same score overwrites the previous copy instead of duplicating it.
            val baseName = sanitizeFileName(score.title).ifEmpty { "score" }
            val outFile = File(scoresDir, "$baseName.xml")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            Result.success(score)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Load a previously imported score by filename. */
    fun loadScore(fileName: String): Result<Score> {
        return try {
            val file = File(scoresDir, fileName)
            if (!file.exists()) return Result.failure(Exception("Score not found: $fileName"))
            file.inputStream().use { stream ->
                Result.success(parser.parse(stream))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** List all imported score files (raw filenames, sorted). */
    fun listScores(): List<String> {
        return scoresDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * List all imported scores together with parsed metadata (title, composer,
     * note count). Files that fail to parse are still returned but flagged with
     * [ImportedScoreInfo.parseFailed] so the UI can surface them gracefully.
     */
    fun listImportedScores(): List<ImportedScoreInfo> {
        return scoresDir.listFiles { file -> file.isFile && file.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            ?.map { file ->
                try {
                    val score = file.inputStream().use { stream -> parser.parse(stream) }
                    ImportedScoreInfo(
                        fileName = file.name,
                        title = score.title.ifBlank { file.nameWithoutExtension },
                        composer = score.composer,
                        noteCount = score.notes.size
                    )
                } catch (e: Exception) {
                    ImportedScoreInfo(
                        fileName = file.name,
                        title = file.nameWithoutExtension,
                        composer = "—",
                        noteCount = 0,
                        parseFailed = true
                    )
                }
            }
            ?: emptyList()
    }

    /** Delete a score file. */
    fun deleteScore(fileName: String): Boolean {
        return File(scoresDir, fileName).delete()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9_\u4e00-\u9fa5]"), "_").take(50)
    }
}
