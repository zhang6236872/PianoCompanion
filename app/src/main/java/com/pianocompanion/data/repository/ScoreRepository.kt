package com.pianocompanion.data.repository

import android.content.Context
import android.net.Uri
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.parser.MidiExporter
import com.pianocompanion.data.parser.MidiParser
import com.pianocompanion.data.parser.MusicXmlExporter
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
    val source: String = "MusicXML",
    val parseFailed: Boolean = false
)

/**
 * Manages score file storage and retrieval.
 * Supports both MusicXML (.xml) and MIDI (.mid) files.
 */
class ScoreRepository(private val context: Context) {

    private val scoresDir: File by lazy {
        File(context.filesDir, "scores").apply { mkdirs() }
    }

    private val xmlParser = MusicXmlParser()
    private val midiParser = MidiParser()

    private fun isMidiFile(fileName: String) =
        fileName.endsWith(".mid", true) || fileName.endsWith(".midi", true)

    private fun parseFile(file: File): Score {
        return if (isMidiFile(file.name)) {
            file.inputStream().use { midiParser.parse(it) }
        } else {
            file.inputStream().use { xmlParser.parse(it) }
        }
    }

    /**
     * Import a MusicXML or MIDI file from a [uri] (typically returned by the
     * Storage Access Framework) into the app's internal storage.
     *
     * The file type is auto-detected from the URI's file extension.
     */
    fun importScore(uri: Uri): Result<Score> {
        return try {
            val fileName = getFileName(uri)
            val isMidi = isMidiFile(fileName)
            val ext = if (isMidi) ".mid" else ".xml"

            val score = context.contentResolver.openInputStream(uri)?.use { stream ->
                if (isMidi) midiParser.parse(stream) else xmlParser.parse(stream)
            } ?: return Result.failure(Exception("无法打开所选文件"))

            val baseName = sanitizeFileName(score.title).ifEmpty { "score" }
            val outFile = File(scoresDir, "$baseName$ext")
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
            Result.success(parseFile(file))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** List all imported score files (raw filenames, sorted). */
    fun listScores(): List<String> {
        return scoresDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * List all imported scores together with parsed metadata.
     * Supports both .xml and .mid/.midi files.
     */
    fun listImportedScores(): List<ImportedScoreInfo> {
        return scoresDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".xml") || file.name.endsWith(".mid") || file.name.endsWith(".midi"))
        }?.sortedBy { it.name }
            ?.map { file ->
                try {
                    val score = parseFile(file)
                    ImportedScoreInfo(
                        fileName = file.name,
                        title = score.title.ifBlank { file.nameWithoutExtension },
                        composer = score.composer,
                        noteCount = score.notes.size,
                        source = if (isMidiFile(file.name)) "MIDI" else "MusicXML"
                    )
                } catch (e: Exception) {
                    ImportedScoreInfo(
                        fileName = file.name,
                        title = file.nameWithoutExtension,
                        composer = "—",
                        noteCount = 0,
                        source = if (isMidiFile(file.name)) "MIDI" else "MusicXML",
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

    /**
     * 将 [score] 序列化为 MusicXML 并写入用户通过 SAF 选择的目标 [uri]。
     *
     * 用于将 OMR 识别结果或任何乐谱导出为标准 MusicXML，
     * 以便在 MuseScore / Finale / Dorico 等外部软件中打开与编辑。
     *
     * @return 成功返回 [Result.success]，失败返回 [Result.failure]。
     */
    fun exportScoreToMusicXml(score: Score, uri: Uri): Result<Unit> {
        return try {
            val xml = MusicXmlExporter().export(score)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(xml.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("无法打开目标文件"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将 [score] 序列化为标准 MIDI 文件 (.mid) 并写入用户通过 SAF 选择的目标 [uri]。
     *
     * 用于将 OMR 识别结果或任何乐谱导出为标准 MIDI，
     * 以便在 DAW / 媒体播放器 / 数码钢琴中播放，或作为练习伴奏轨。
     *
     * @return 成功返回 [Result.success]，失败返回 [Result.failure]。
     */
    fun exportScoreToMidi(score: Score, uri: Uri): Result<Unit> {
        return try {
            val bytes = MidiExporter().export(score)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: return Result.failure(Exception("无法打开目标文件"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9_\\u4e00-\\u9fa5]"), "_").take(50)
    }

    private fun getFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && it.moveToFirst()) return it.getString(nameIdx)
        }
        return uri.lastPathSegment ?: "unknown"
    }
}
