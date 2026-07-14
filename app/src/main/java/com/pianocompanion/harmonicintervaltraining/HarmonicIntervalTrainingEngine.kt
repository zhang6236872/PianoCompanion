package com.pianocompanion.harmonicintervaltraining

import kotlin.random.Random

/**
 * 和声音程辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [HarmonicIntervalDifficulty] 生成 [HarmonicIntervalQuestion]：
 * 1. 从该难度的可用音程集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [HarmonicIntervalDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class HarmonicIntervalEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param lowerMidi 下方音 MIDI 编号（默认 C4=60）
     * @return 生成的题目
     */
    fun generate(
        difficulty: HarmonicIntervalDifficulty,
        lowerMidi: Int = HarmonicIntervalQuestion.DEFAULT_LOWER_MIDI
    ): HarmonicIntervalQuestion {
        val availableIntervals = HarmonicInterval.forDifficulty(difficulty)
        val interval = availableIntervals.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableIntervals.size)
        val distractors = availableIntervals.filter { it != interval }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + interval).shuffled(random)

        return HarmonicIntervalQuestion(
            interval = interval,
            difficulty = difficulty,
            lowerMidi = lowerMidi,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = interval.fullLabel
        )
    }

    /**
     * 计算两个音的绝对时间戳（毫秒）——和声音程中两音同时发声。
     *
     * @return 时间戳列表（两个相同的时间戳）
     */
    fun computeOnsetTimes(): List<Double> {
        // 和声音程：两音同时响起，所以只有一个 onset
        return listOf(LEAD_SILENCE_MS)
    }

    companion object {
        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 单次和声音持续时间（毫秒）。 */
        const val DURATION_MS = 1200.0

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): HarmonicIntervalEngine = HarmonicIntervalEngine(Random(seed))
    }
}
