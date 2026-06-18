package com.pianocompanion.data.repository

import android.content.Context
import android.net.Uri
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.parser.MusicXmlParser
import java.io.File

/**
 * Manages score file storage and retrieval.
 * Scores are stored in app's internal storage as MusicXML files.
 */
class ScoreRepository(private val context: Context) {

    private val scoresDir: File by lazy {
        File(context.filesDir, "scores").apply { mkdirs() }
    }

    private val parser = MusicXmlParser()

    /** Import a MusicXML file from a Uri into local storage. */
    fun importScore(uri: Uri): Result<Score> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            inputStream.use { stream ->
                val score = parser.parse(stream)
                // Save to local storage
                val fileName = "\${sanitizeFileName(score.title)}.xml"
                val outFile = File(scoresDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                Result.success(score)
            }
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

    /** List all imported score files. */
    fun listScores(): List<String> {
        return scoresDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }

    /** Delete a score file. */
    fun deleteScore(fileName: String): Boolean {
        return File(scoresDir, fileName).delete()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9_\u4e00-\u9fa5]"), "_").take(50)
    }
}
