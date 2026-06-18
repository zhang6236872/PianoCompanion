package com.pianocompanion.data.sync

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pianocompanion.data.model.SessionRecord
import com.pianocompanion.data.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cloud sync / backup service.
 *
 * Phase 1 (current): Local JSON export/import for backup.
 * Phase 2 (future): Firebase / Google Drive integration for cross-device sync.
 */
class SyncManager(private val context: Context) {

    private val gson = Gson()
    private val statsRepository = StatsRepository(context)

    /**
     * Export all practice data as JSON string.
     */
    fun exportData(): SyncData {
        val sessions = statsRepository.getAllSessions()
        return SyncData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            deviceName = android.os.Build.MODEL,
            sessions = sessions
        )
    }

    /**
     * Export to JSON string.
     */
    fun exportToJson(): String {
        return gson.toJson(exportData())
    }

    /**
     * Save backup to file (via Uri from SAF).
     */
    suspend fun exportToFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = exportToJson()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Import data from JSON string.
     */
    fun importFromJson(json: String): SyncResult {
        return try {
            val type = object : TypeToken<SyncData>() {}.type
            val data: SyncData = gson.fromJson(json, type)

            // Merge imported sessions with existing ones
            val existing = statsRepository.getAllSessions().map { it.startTime }.toSet()
            var imported = 0
            data.sessions.forEach { session ->
                if (session.startTime !in existing) {
                    statsRepository.saveSession(session)
                    imported++
                }
            }

            SyncResult.Success(imported = imported, total = data.sessions.size)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "导入失败")
        }
    }

    /**
     * Import from file (via Uri from SAF).
     */
    suspend fun importFromFile(uri: Uri): SyncResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext SyncResult.Error("无法读取文件")

            importFromJson(json)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "导入失败")
        }
    }

    /**
     * Generate backup file name.
     */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        return "piano_backup_${dateFormat.format(Date())}.json"
    }
}

/**
 * Serializable sync data container.
 */
data class SyncData(
    val version: Int,
    val exportedAt: Long,
    val deviceName: String,
    val sessions: List<SessionRecord>
)

sealed class SyncResult {
    data class Success(val imported: Int, val total: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
