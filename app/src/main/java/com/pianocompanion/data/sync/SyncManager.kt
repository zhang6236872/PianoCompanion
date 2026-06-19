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
 * 数据同步 / 备份服务（Android 传输层）。
 *
 * 合并语义（去重 / LWW 冲突解决 / schema 迁移 / 完整性校验）全部委托给纯 Kotlin 的
 * [SyncEngine]；本类只负责 I/O（SharedPreferences、SAF 文件、JSON 序列化）。
 *
 * - Phase 1（当前）: 本地 JSON 导出 / 导入，跨设备通过文件搬运即可双向同步。
 * - Phase 2（未来）: 接入 Firebase / Google Drive 作为传输层，合并逻辑不变。
 */
class SyncManager(private val context: Context) {

    private val gson = Gson()
    private val statsRepository = StatsRepository(context)
    private val engine = SyncEngine()

    /**
     * 导出本机全部练习数据（含 schema 版本与完整性校验和）。
     */
    fun exportData(): SyncData {
        val sessions = statsRepository.getAllSessions()
        return SyncData(
            version = CURRENT_SYNC_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            deviceName = android.os.Build.MODEL,
            sessions = sessions,
            checksum = engine.checksum(sessions)
        )
    }

    /** 导出为 JSON 字符串。 */
    fun exportToJson(): String = gson.toJson(exportData())

    /** 经 SAF 保存备份到 [uri]。 */
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
     * 从 JSON 字符串导入并合并到本机数据。
     *
     * 流程：解析 → 校验 → 迁移到当前 schema → 与本机数据 LWW 合并 → 持久化。
     * 任一步硬性失败都返回 [SyncResult.Error] 且不修改本机数据。
     */
    fun importFromJson(json: String): SyncResult {
        return try {
            val type = object : TypeToken<SyncData>() {}.type
            val raw: SyncData = gson.fromJson(json, type)
                ?: return SyncResult.Error("导入失败: 空或无效的 JSON")

            val migrated = engine.migrate(raw)
            val validation = engine.validate(migrated)
            if (!validation.ok) {
                return SyncResult.Error("导入失败: ${validation.errors.joinToString("; ")}")
            }

            val local = SyncData(
                version = CURRENT_SYNC_SCHEMA_VERSION,
                exportedAt = 0,
                deviceName = android.os.Build.MODEL,
                sessions = statsRepository.getAllSessions(),
                checksum = null
            )

            val result = engine.merge(local, migrated)

            // 仅当确有变更时重写存储（保持 newest-first，并裁剪到上限）
            if (result.changed > 0) {
                val capped = result.merged.take(SyncEngine.MAX_SESSIONS)
                replaceAllSessions(capped)
            }

            SyncResult.Success(
                imported = result.added,
                updated = result.updated,
                skipped = result.skipped,
                total = migrated.sessions.size
            )
        } catch (e: Exception) {
            SyncResult.Error("导入失败: ${e.message ?: "未知错误"}")
        }
    }

    /** 经 SAF 从 [uri] 读取并导入。 */
    suspend fun importFromFile(uri: Uri): SyncResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return@withContext SyncResult.Error("无法读取文件")

            importFromJson(json)
        } catch (e: Exception) {
            SyncResult.Error("导入失败: ${e.message ?: "未知错误"}")
        }
    }

    /** 生成备份文件名。 */
    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        return "piano_backup_${dateFormat.format(Date())}.json"
    }

    /**
     * 用合并后的完整列表整体覆盖本机会话存储（保持 newest-first）。
     * 仅在导入产生变更时由 [importFromJson] 调用。
     */
    private fun replaceAllSessions(sessions: List<SessionRecord>) {
        val prefs = context.getSharedPreferences("piano_companion_stats", Context.MODE_PRIVATE)
        // 复用 repository 的 KEY_SESSIONS 约定，倒序逐条 save 会重复读改写且重算连胜，
        // 这里直接整体写回 sessions 键以保持 O(1) 写入与稳定顺序。
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }
}

/**
 * 可序列化的同步载荷。
 */
data class SyncData(
    val version: Int,
    val exportedAt: Long,
    val deviceName: String,
    val sessions: List<SessionRecord>,
    /** 可选的 SHA-1 完整性校验和（由 [SyncEngine.checksum] 产生）。 */
    val checksum: String? = null
)

sealed class SyncResult {
    /**
     * @param imported 新增条数（本机原先没有的）
     * @param updated  覆盖条数（远端较新，LWW 胜出）
     * @param skipped  跳过条数（本机较新或完全相同）
     * @param total    导入载荷中的总会话数
     */
    data class Success(
        val imported: Int,
        val updated: Int,
        val skipped: Int,
        val total: Int
    ) : SyncResult() {
        /** 向后兼容：旧调用方读取的"导入数"。 */
        @Deprecated("使用 imported/updated 细分", ReplaceWith("imported"))
        val count: Int get() = imported
    }

    data class Error(val message: String) : SyncResult()
}
