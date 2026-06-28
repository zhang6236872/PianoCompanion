package com.pianocompanion.analytics

// ──────────────────────────────────────────────────────────────────────────
//  AchievementStore — 成就解锁状态持久化（纯 Kotlin，无 Android 依赖）
// ──────────────────────────────────────────────────────────────────────────

/**
 * 成就解锁状态持久化与差分计算。
 *
 * 解决 v2.56.0 已知限制「成就评估在统计页打开时实时计算（不持久化已解锁状态）」。
 *
 * 将已解锁成就 id 集合序列化为逗号分隔字符串存入 SharedPreferences，
 * 每次评估时与上次已解锁集合做差分，得出「本次新解锁」的成就列表。
 *
 * 用法：
 * ```
 * val previous = AchievementStore.deserialize(savedString)
 * val current = summary.unlocked.map { it.definition.id }.toSet()
 * val newly = AchievementStore.computeNewlyUnlocked(current, previous)
 * val updated = AchievementStore.merge(previous, newly)
 * val toSave = AchievementStore.serialize(updated)
 * ```
 */
object AchievementStore {

    /** 序列化分隔符。 */
    private const val SEPARATOR = ","

    // ──────────────────────────────────────────────────────────────────────
    //  序列化 / 反序列化
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 将已解锁成就 id 集合序列化为逗号分隔字符串。
     *
     * 空集合序列化为空字符串 ""。顺序按字母排序以保证确定性（便于测试和调试）。
     */
    fun serialize(ids: Set<String>): String {
        if (ids.isEmpty()) return ""
        return ids.sorted().joinToString(SEPARATOR)
    }

    /**
     * 将逗号分隔字符串反序列化为已解锁成就 id 集合。
     *
     * - null / 空白 → 空集合
     * - 自动去空白、去重
     * - 仅保留 [AchievementEngine.ALL_IDS] 中存在的有效 id（防止拼写错误或版本迁移残留）
     */
    fun deserialize(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() && it in AchievementEngine.ALL_IDS }
            .toSet()
    }

    // ──────────────────────────────────────────────────────────────────────
    //  差分计算
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 计算本次新解锁的成就 id（在 [current] 中但不在 [previous] 中）。
     *
     * @param current 本次评估得出的已解锁 id 集合
     * @param previous 上次持久化的已解锁 id 集合
     * @return 新解锁的 id 集合（仅保留有效 id）
     */
    fun computeNewlyUnlocked(current: Set<String>, previous: Set<String>): Set<String> {
        val validCurrent = current.filter { it in AchievementEngine.ALL_IDS }.toSet()
        return validCurrent - previous
    }

    /**
     * 合并已解锁集合（并集），仅保留有效 id。
     *
     * @param previous 上次持久化的已解锁 id 集合
     * @param newlyUnlocked 本次新解锁的 id 集合
     * @return 合并后的完整已解锁集合
     */
    fun merge(previous: Set<String>, newlyUnlocked: Set<String>): Set<String> {
        val validPrev = previous.filter { it in AchievementEngine.ALL_IDS }.toSet()
        val validNew = newlyUnlocked.filter { it in AchievementEngine.ALL_IDS }.toSet()
        return validPrev + validNew
    }

    // ──────────────────────────────────────────────────────────────────────
    //  高级 API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 一步完成「检测新解锁 + 更新已解锁集合」。
     *
     * @param currentUnlockedIds 本次评估得出的已解锁 id 集合
     * @param previousRaw 上次持久化的序列化字符串（来自 SharedPreferences）
     * @return [UnlockDiff] 包含新解锁列表和更新后的序列化字符串
     */
    fun evaluateDiff(
        currentUnlockedIds: Set<String>,
        previousRaw: String?
    ): UnlockDiff {
        val previous = deserialize(previousRaw)
        val newly = computeNewlyUnlocked(currentUnlockedIds, previous)
        val updated = merge(previous, newly)
        return UnlockDiff(
            newlyUnlockedIds = newly,
            updatedIds = updated,
            updatedRaw = serialize(updated)
        )
    }

    /**
     * 将新解锁的 id 列表转换为 [AchievementProgress] 列表（供 UI 展示）。
     *
     * 按 [AchievementEngine.DEFINITIONS] 中的顺序排序（分类序 + 目标值序）。
     */
    fun newlyUnlockedToProgress(
        newlyIds: Set<String>,
        summary: AchievementSummary
    ): List<AchievementProgress> {
        if (newlyIds.isEmpty()) return emptyList()
        return summary.unlocked.filter { it.definition.id in newlyIds }
    }
}

/**
 * 解锁差分结果。
 *
 * @param newlyUnlockedIds 本次新解锁的成就 id 集合（可能为空）
 * @param updatedIds 更新后的完整已解锁 id 集合
 * @param updatedRaw 更新后的序列化字符串（可直接存入 SharedPreferences）
 */
data class UnlockDiff(
    val newlyUnlockedIds: Set<String>,
    val updatedIds: Set<String>,
    val updatedRaw: String
) {
    /** 是否有新解锁的成就。 */
    val hasNewlyUnlocked: Boolean get() = newlyUnlockedIds.isNotEmpty()
}
