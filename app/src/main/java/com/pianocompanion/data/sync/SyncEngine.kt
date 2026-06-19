package com.pianocompanion.data.sync

import com.pianocompanion.data.model.SessionRecord
import java.security.MessageDigest

/**
 * 当前同步数据格式的 schema 版本。
 *
 * - v1: 初始版本（仅 startTime 去重，无冲突解决 / 无校验和）。
 * - v2: 引入 [SessionRecord.updatedAt]（LWW 冲突解决）+ 完整性校验和。
 *
 * 旧的 v1 备份文件仍可被 [SyncManager] 读取：[migrate] 会将其升级到当前版本。
 */
const val CURRENT_SYNC_SCHEMA_VERSION: Int = 2

/**
 * 纯 Kotlin 的离线优先同步 / 合并引擎。
 *
 * 设计目标：
 * 1. **无 Android 依赖** —— 仅依赖 [SessionRecord] 与 JDK，可在 JVM 上完整单测。
 * 2. **设备无关的身份** —— 用确定性 fingerprint（startTime/曲名/时长/音数）识别"同一条"
 *    练习记录，即便不同设备生成的 `id`（= currentTimeMillis）不同也能正确去重合并。
 * 3. **Last-Write-Wins 冲突解决** —— 当本地与远端出现同一 fingerprint 但内容不同时，
 *    以 [SessionRecord.updatedAt]（缺失时回退到 startTime）较新者为准。
 * 4. **幂等** —— 同一份导入数据重复导入不会产生重复记录。
 * 5. **可校验** —— SHA-1 完整性校验和 + schema 版本 + 字段健全性检查。
 *
 * 真实的云端（Firebase / Google Drive）只需替换 [SyncManager] 的传输层，
 * 合并语义完全复用本引擎。
 */
class SyncEngine {

    /**
     * 将 [incoming]（远端 / 备份文件）合并到 [local]（本机现有数据）。
     *
     * 不修改入参列表；合并后的完整结果通过 [MergeResult.merged] 返回，
     * 调用方负责持久化。结果按 startTime 倒序排列（与本机存储约定一致）。
     */
    fun merge(local: SyncData, incoming: SyncData): MergeResult {
        // 以 fingerprint 为键建立本地索引
        val byFingerprint: MutableMap<String, SessionRecord> =
            local.sessions.associateBy { fingerprint(it) }.toMutableMap()

        var added = 0
        var updated = 0
        var skipped = 0

        for (inc in incoming.sessions) {
            val key = fingerprint(inc)
            val existing = byFingerprint[key]
            if (existing == null) {
                // 本机不存在 → 新增
                byFingerprint[key] = inc
                added++
            } else {
                val incTs = lastModified(inc)
                val locTs = lastModified(existing)
                if (incTs > locTs) {
                    // 远端更新（冲突，LWW：远端胜）→ 覆盖
                    byFingerprint[key] = inc
                    updated++
                } else {
                    // 本机更新或完全相同 → 跳过
                    skipped++
                }
            }
        }

        val merged = byFingerprint.values.sortedByDescending { it.startTime }
        return MergeResult(
            merged = merged,
            added = added,
            updated = updated,
            skipped = skipped,
            totalBefore = local.sessions.size,
            totalAfter = merged.size
        )
    }

    /**
     * 校验一份 [SyncData] 的完整性与一致性。
     *
     * @return [ValidationResult]；[ValidationResult.ok] 为 true 仅当不存在硬性错误。
     *         版本过旧、数值越界等问题以 warning 形式上报（可经 [migrate] 修复）。
     */
    fun validate(data: SyncData): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (data.version !in 1..CURRENT_SYNC_SCHEMA_VERSION) {
            errors += "不支持的 schema 版本: ${data.version}"
        } else if (data.version < CURRENT_SYNC_SCHEMA_VERSION) {
            warnings += "旧版数据 (v${data.version})，将自动迁移到 v$CURRENT_SYNC_SCHEMA_VERSION"
        }

        if (data.sessions.isEmpty() && data.version >= 1) {
            // 空载荷不一定是错误（全新设备首次同步），仅提示
            warnings += "导入数据为空"
        }

        data.sessions.forEachIndexed { idx, s ->
            val where = "session[$idx]"
            if (s.durationMs < 0) errors += "$where 时长为负 (${s.durationMs})"
            if (s.totalNotes < 0) errors += "$where 总音数为负 (${s.totalNotes})"
            if (s.correctNotes < 0) errors += "$where 正确音数为负 (${s.correctNotes})"
            if (s.totalNotes > 0 && s.correctNotes > s.totalNotes) {
                errors += "$where 正确音数(${s.correctNotes}) > 总音数(${s.totalNotes})"
            }
            if (s.accuracy.isNaN() || s.accuracy < 0f || s.accuracy > 1f) {
                errors += "$where 准确率越界 (${s.accuracy})"
            }
            if (s.scoreTitle.isBlank()) warnings += "$where 曲名为空"
        }

        // 完整性校验和（仅当载荷自带 checksum 时才核对）
        data.checksum?.let { expected ->
            val actual = checksum(data.sessions)
            if (!expected.equals(actual, ignoreCase = true)) {
                errors += "校验和不匹配（数据可能损坏）"
            }
        }

        return ValidationResult(ok = errors.isEmpty(), errors = errors, warnings = warnings)
    }

    /**
     * 将旧版 [SyncData] 迁移到当前 schema。
     *
     * - 提升 [SyncData.version] 到 [CURRENT_SYNC_SCHEMA_VERSION]。
     * - 规范化异常数值（NaN / Infinity 的 accuracy → 0）。
     * - 清除 checksum（迁移后内容已变，需重新计算）。
     *
     * 已是当前版本的载荷为幂等 no-op。
     */
    fun migrate(data: SyncData): SyncData {
        if (data.version >= CURRENT_SYNC_SCHEMA_VERSION && data.checksum != null) {
            return data
        }
        val normalized = data.sessions.map { s ->
            if (s.accuracy.isNaN() || s.accuracy.isInfinite()) {
                s.copy(accuracy = 0f)
            } else {
                s
            }
        }
        return data.copy(
            version = CURRENT_SYNC_SCHEMA_VERSION,
            sessions = normalized,
            checksum = null
        )
    }

    /**
     * 对一份会话列表计算确定性的 SHA-1 完整性校验和。
     * 排序后规范化，保证相同内容（不同顺序）产生相同校验和。
     */
    fun checksum(sessions: List<SessionRecord>): String {
        val md = MessageDigest.getInstance("SHA-1")
        sessions.sortedBy { it.startTime }.forEach { s ->
            val canonical = buildString {
                append(s.startTime).append(':')
                append(s.scoreTitle).append(':')
                append(s.durationMs).append(':')
                append(s.totalNotes).append(':')
                append(s.correctNotes).append(':')
                append(s.accuracy).append('|')
            }
            md.update(canonical.toByteArray(Charsets.UTF_8))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 设备无关的会话身份指纹。同一首曲子在同一毫秒开始、相同时长与音数统计
     * 视为同一条记录（跨设备 id 不同也能合并）。
     */
    fun fingerprint(s: SessionRecord): String =
        "${s.startTime}|${s.scoreTitle}|${s.durationMs}|${s.totalNotes}|${s.correctNotes}"

    /** LWW 排序键：优先 [SessionRecord.updatedAt]，缺失(0)时回退到 startTime。 */
    private fun lastModified(s: SessionRecord): Long =
        if (s.updatedAt > 0) s.updatedAt else s.startTime

    companion object {
        /** 本机存储与会话合并后保留的最大条数（与 StatsRepository 上限一致）。 */
        const val MAX_SESSIONS: Int = 200
    }
}

/**
 * 合并结果。携带可直接展示给用户的统计信息。
 */
data class MergeResult(
    /** 合并后的完整会话列表（按 startTime 倒序），调用方据此持久化。 */
    val merged: List<SessionRecord>,
    /** 本机不存在、来自远端的新增条数。 */
    val added: Int,
    /** 远端较新、覆盖了本机的条数（即被 LWW 解决的冲突数）。 */
    val updated: Int,
    /** 本机较新或内容完全相同而跳过的条数。 */
    val skipped: Int,
    /** 合并前本机条数。 */
    val totalBefore: Int,
    /** 合并后总条数。 */
    val totalAfter: Int
) {
    /** 本次实际写入（新增 + 覆盖）的条数。 */
    val changed: Int get() = added + updated
}

/**
 * 数据校验结果。
 */
data class ValidationResult(
    val ok: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
