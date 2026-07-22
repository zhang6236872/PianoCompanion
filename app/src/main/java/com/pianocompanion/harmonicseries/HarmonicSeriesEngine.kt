package com.pianocompanion.harmonicseries

import kotlin.random.Random

/**
 * 泛音列辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [HarmonicDifficulty] 生成 [HarmonicSeriesQuestion]：
 *
 * 1. **随机选取目标泛音**：从该难度的泛音集合中随机选一个作为正确答案。
 * 2. **构建干扰项**：从剩余泛音中随机选取若干作为干扰项。
 * 3. **构建选项**：正确答案 + 干扰项，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class HarmonicSeriesEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: HarmonicDifficulty): HarmonicSeriesQuestion {
        val seed = random.nextLong()

        // 1. 随机选取目标泛音
        val target = difficulty.harmonics.random(random)

        // 2. 从剩余泛音中选取干扰项
        val distractorPool = difficulty.harmonics.filter { it != target }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allHarmonics = (listOf(target) + distractors)
        val choices = allHarmonics.map { it.displayName }.shuffled(random)

        return HarmonicSeriesQuestion(
            difficulty = difficulty,
            seed = seed,
            targetHarmonic = target,
            answerChoices = choices,
            correctAnswer = target.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): HarmonicSeriesEngine = HarmonicSeriesEngine(Random(seed))
    }
}
