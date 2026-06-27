package com.pianocompanion.following

import com.pianocompanion.data.model.Score

/**
 * 段落循环练习控制器 (Section Loop Controller)
 *
 * 支持用户选择乐谱中的一个小节范围 `[startMeasure, endMeasure]`，在练习时
 * 当演奏到达段落末尾后自动将 score-follower 的位置重置回段落开头，实现
 * 指定段落的反复练习。常用于攻克薄弱环节（与 [com.pianocompanion.analytics.WeakSpotAnalyzer]
 * 识别出的薄弱段落配合使用）。
 *
 * 纯 Kotlin，无 Android 依赖，完全可单元测试。
 *
 * 典型集成流程：
 * 1. [ScoreFollower] 持有一个 [SectionLooper] 实例；
 * 2. 用户在 UI 中选择段落范围并启用循环 → 设置 [enabled]/[startMeasure]/[endMeasure]；
 * 3. 每次检测到音符、score 位置更新后调用 [shouldLoop] 判定是否到达段落末尾；
 * 4. 返回 true 时调用 [OnlineDTW.seekTo] 跳回 [startIndex]，并调用 [recordLoop] 计数。
 *
 * @param score 关联的乐谱
 */
class SectionLooper(private val score: Score) {

    /** 循环练习是否启用 */
    var enabled: Boolean = false

    /** 段落起始小节（含），默认 0 */
    var startMeasure: Int = 0

    /**
     * 段落结束小节（含），默认为乐谱最后一小节。
     */
    var endMeasure: Int = maxMeasure(score)

    /** 已完成的循环次数（每从末尾跳回开头 +1） */
    var loopCount: Int = 0
        private set

    /**
     * 段落起始对应的第一个音符索引（`score.notes` 中 `measureIndex >= startMeasure`
     * 的最小索引）。若段落内无音符，返回 [score] 的音符总数。
     */
    fun startIndex(): Int {
        val idx = score.notes.indexOfFirst { it.measureIndex >= startMeasure }
        return if (idx < 0) score.notes.size else idx
    }

    /**
     * 段落结束的**排他上界**索引（= 段落最后一个音符索引 + 1）。
     * 当 score 位置 ≥ 该值时，表示已演奏完整个段落，应触发循环。
     * 若段落内无音符，返回 0。
     */
    fun endExclusiveIndex(): Int {
        val idx = score.notes.indexOfLast { it.measureIndex <= endMeasure }
        return if (idx < 0) 0 else idx + 1
    }

    /** 段落内包含的音符数（[endExclusiveIndex] - [startIndex]，下限 0）。 */
    fun sectionNoteCount(): Int = (endExclusiveIndex() - startIndex()).coerceAtLeast(0)

    /**
     * 判定当前 score 位置是否已到达/越过段落末尾，应触发循环。
     *
     * 仅在 [enabled] 为 true、且段落非空（[sectionNoteCount] > 0）时返回 true。
     * 这保证单音符段落或空段落不会陷入无限循环。
     */
    fun shouldLoop(currentScorePosition: Int): Boolean {
        if (!enabled) return false
        if (sectionNoteCount() <= 0) return false
        return currentScorePosition >= endExclusiveIndex()
    }

    /**
     * 验证段落配置是否有效：`startMeasure <= endMeasure` 且段落内至少有 1 个音符。
     */
    fun isValid(): Boolean {
        return startMeasure <= endMeasure && sectionNoteCount() > 0
    }

    /** 记录一次循环（[loopCount] +1）。 */
    fun recordLoop() {
        loopCount++
    }

    /** 重置循环计数归零。 */
    fun resetLoopCount() {
        loopCount = 0
    }

    companion object {
        /**
         * 获取乐谱的最大小节号（音符为空时返回 0）。
         * 作为 [SectionLooper] 的工具函数，也供 ViewModel 计算 UI 边界。
         */
        fun maxMeasure(score: Score): Int =
            if (score.notes.isEmpty()) 0 else score.notes.maxOf { it.measureIndex }
    }
}
