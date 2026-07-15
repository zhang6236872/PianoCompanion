package com.pianocompanion.modulationrecognition

import kotlin.random.Random

/**
 * 转调辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ModulationDifficulty] 生成 [ModulationQuestion]：
 * 1. 从该难度的可用转调类型集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [ModulationDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ModulationEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ModulationDifficulty): ModulationQuestion {
        val availableTypes = ModulationType.forDifficulty(difficulty)
        val modulation = availableTypes.random(random)
        val seed = random.nextLong()

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableTypes.size)
        val distractors = availableTypes.filter { it != modulation }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + modulation).shuffled(random)

        return ModulationQuestion(
            modulation = modulation,
            difficulty = difficulty,
            seed = seed,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = modulation.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ModulationEngine = ModulationEngine(Random(seed))
    }
}
