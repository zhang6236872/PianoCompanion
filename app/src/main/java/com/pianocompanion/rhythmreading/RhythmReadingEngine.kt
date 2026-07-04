package com.pianocompanion.rhythmreading

import kotlin.random.Random

/**
 * 节奏视读训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RhythmReadingDifficulty] 和随机种子生成 [RhythmReadingQuestion]。
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * 设计要点：
 * - 每道题的节奏型总时值固定为 4 拍（4/4 拍号下一个小节）。
 * - 节奏型由难度对应的时值池中随机选取的音符/休止符贪心填充，确保恰好凑满 4 拍。
 * - 提供 4 个选项（1 正确 + 3 干扰），每个选项都是不同的节奏型。用户需从视觉上
 *   辨认出与题目完全一致的节奏型。
 * - 选项之间通过 [fingerprint] 保证互不相同。
 * - 初级避免全四分音符（过于简单），保证至少包含一个八分音符。
 *
 * 时值池与最小单元：
 * - 初级池 = {四分(1.0), 八分(0.5)}，最小单元 = 0.5
 * - 中级池 = {四分(1.0), 八分(0.5), 二分(2.0), 四分休止(1.0)}，最小单元 = 0.5
 * - 高级池 = {四分(1.0), 八分(0.5), 二分(2.0), 四分休止(1.0), 十六分(0.25), 八分休止(0.5)}，最小单元 = 0.25
 *
 * 由于所有池元素均为最小单元的整数倍，且 4.0 也是最小单元的整数倍，
 * 贪心填充时只要每步选取 ≤ 剩余拍数的时值，剩余拍数始终是最小单元的非负整数倍，
 * 因此总能用最小单元凑满。这保证了算法的终止性和正确性。
 *
 * @param root 底层随机数生成器，便于注入种子进行测试
 */
class RhythmReadingEngine(
    private val root: Random = Random.Default
) {

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: RhythmReadingDifficulty): RhythmReadingQuestion {
        val correctPattern = generatePattern(difficulty)
        val correctFingerprint = fingerprint(correctPattern)

        // 生成 3 个互不相同且与正确答案不同的干扰项
        val distractorPatterns = mutableListOf<List<RhythmItem>>()
        val seenFingerprints = mutableSetOf(correctFingerprint)
        var attempts = 0
        while (distractorPatterns.size < NUM_DISTRACTORS && attempts < MAX_DISTRACTOR_ATTEMPTS) {
            val candidate = generatePattern(difficulty)
            val fp = fingerprint(candidate)
            if (fp !in seenFingerprints) {
                seenFingerprints.add(fp)
                distractorPatterns.add(candidate)
            }
            attempts++
        }

        // 兜底：若随机未能产生足够干扰项，用确定性变体补充
        var fallbackIndex = 0
        while (distractorPatterns.size < NUM_DISTRACTORS) {
            val variant = makeFallbackDistractor(correctPattern, fallbackIndex)
            val fp = fingerprint(variant)
            if (fp !in seenFingerprints) {
                seenFingerprints.add(fp)
                distractorPatterns.add(variant)
            }
            fallbackIndex++
            if (fallbackIndex > 50) {
                // 极端兜底：即使重复也要凑满
                distractorPatterns.add(variant)
            }
        }

        val correctOption = makeOption(correctPattern)
        val distractorOptions = distractorPatterns.map { makeOption(it) }
        val allOptions = (listOf(correctOption) + distractorOptions).shuffled(root)

        return RhythmReadingQuestion(
            difficulty = difficulty,
            pattern = correctPattern,
            answerOptions = allOptions,
            correctAnswer = correctFingerprint
        )
    }

    // ── 节奏型生成 ────────────────────────────────────────

    /**
     * 生成一个总时值为 4 拍的节奏型。
     *
     * 贪心填充：每步从时值池中随机选取一个 ≤ 剩余拍数的时值，
     * 直到剩余拍数为 0。初级避免全四分音符。
     *
     * @param difficulty 难度
     * @return 节奏序列
     */
    fun generatePattern(difficulty: RhythmReadingDifficulty): List<RhythmItem> {
        val pool = poolFor(difficulty)
        repeat(PATTERN_GENERATION_ATTEMPTS) {
            val pattern = mutableListOf<RhythmItem>()
            var remaining = TOTAL_BEATS
            while (remaining > BEAT_EPSILON) {
                val valid = pool.filter { it.beats <= remaining + BEAT_EPSILON }
                val chosen = if (valid.isNotEmpty()) valid.random(root) else pool.minBy { it.beats }
                pattern.add(RhythmItem(chosen))
                remaining -= chosen.beats
            }
            if (isValidPattern(pattern, difficulty, pool)) {
                return pattern
            }
        }
        // 兜底：四分×4
        return List(4) { RhythmItem(RhythmDuration.QUARTER) }
    }

    /**
     * 判断生成的节奏型是否足够有趣（避免过于简单的退化模式）。
     *
     * 规则：
     * - 初级：不能全是四分音符（必须至少有一个八分音符），否则太简单
     * - 所有难度：元素数 ≥ 2（避免单个音符占满 4 拍）
     */
    private fun isValidPattern(
        pattern: List<RhythmItem>,
        difficulty: RhythmReadingDifficulty,
        pool: List<RhythmDuration>
    ): Boolean {
        if (pattern.size < 2) return false
        if (difficulty == RhythmReadingDifficulty.BEGINNER) {
            // 初级池只有四分和八分，避免全四分
            if (pattern.all { it.duration == RhythmDuration.QUARTER }) return false
        }
        return true
    }

    /**
     * 返回难度对应的时值池。
     */
    fun poolFor(difficulty: RhythmReadingDifficulty): List<RhythmDuration> {
        return when (difficulty) {
            RhythmReadingDifficulty.BEGINNER -> listOf(
                RhythmDuration.QUARTER,
                RhythmDuration.EIGHTH
            )
            RhythmReadingDifficulty.INTERMEDIATE -> listOf(
                RhythmDuration.QUARTER,
                RhythmDuration.EIGHTH,
                RhythmDuration.HALF,
                RhythmDuration.QUARTER_REST
            )
            RhythmReadingDifficulty.ADVANCED -> listOf(
                RhythmDuration.QUARTER,
                RhythmDuration.EIGHTH,
                RhythmDuration.HALF,
                RhythmDuration.QUARTER_REST,
                RhythmDuration.SIXTEENTH,
                RhythmDuration.EIGHTH_REST
            )
        }
    }

    // ── 指纹与选项 ──────────────────────────────────────────

    /**
     * 计算节奏序列的指纹（时值名按顺序拼接，用 "|" 分隔）。
     * 指纹相同的两个节奏型视为「同一答案」。
     */
    fun fingerprint(items: List<RhythmItem>): String =
        items.joinToString("|") { it.duration.name }

    /**
     * 生成节奏序列的显示标签（简称用空格拼接，如 "四 四 八 八"）。
     */
    fun patternLabel(items: List<RhythmItem>): String =
        items.joinToString(" ") { it.duration.shortLabel }

    /**
     * 将节奏序列构造为选项。
     */
    private fun makeOption(items: List<RhythmItem>): RhythmPatternOption =
        RhythmPatternOption(
            items = items,
            label = patternLabel(items),
            fingerprint = fingerprint(items)
        )

    /**
     * 确定性兜底干扰项：基于正确节奏型做小改动。
     *
     * 策略：交换相邻元素、或将第一个元素替换为两个更小的等值元素。
     * 确保即使随机生成失败也能产生多样化的干扰项。
     */
    private fun makeFallbackDistractor(correct: List<RhythmItem>, index: Int): List<RhythmItem> {
        val result = correct.toMutableList()
        when (index % 3) {
            0 -> {
                // 交换前两个元素
                if (result.size >= 2) {
                    val tmp = result[0]
                    result[0] = result[1]
                    result[1] = tmp
                }
            }
            1 -> {
                // 交换后两个元素
                if (result.size >= 2) {
                    val n = result.size
                    val tmp = result[n - 1]
                    result[n - 1] = result[n - 2]
                    result[n - 2] = tmp
                }
            }
            2 -> {
                // 将第一个四分替换为两个八分（若存在四分）
                val qi = result.indexOfFirst { it.duration == RhythmDuration.QUARTER }
                if (qi >= 0) {
                    result[qi] = RhythmItem(RhythmDuration.EIGHTH)
                    result.add(qi + 1, RhythmItem(RhythmDuration.EIGHTH))
                }
            }
        }
        return result
    }

    companion object {
        /** 节奏型目标总拍数（4/4 拍号 = 4 拍）。 */
        const val TOTAL_BEATS = 4.0

        /** 拍数浮点容差。 */
        const val BEAT_EPSILON = 1e-9

        /** 干扰项数量。 */
        const val NUM_DISTRACTORS = 3

        /** 干扰项生成最大尝试次数。 */
        const val MAX_DISTRACTOR_ATTEMPTS = 80

        /** 节奏型生成最大尝试次数（用于重试避免退化模式）。 */
        const val PATTERN_GENERATION_ATTEMPTS = 30

        /**
         * 创建带固定种子的引擎实例（用于测试确定性）。
         */
        fun withSeed(seed: Long): RhythmReadingEngine = RhythmReadingEngine(Random(seed))
    }
}
