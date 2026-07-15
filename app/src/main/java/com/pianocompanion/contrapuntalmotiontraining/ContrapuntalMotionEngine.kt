package com.pianocompanion.contrapuntalmotiontraining

import kotlin.random.Random

/**
 * 声部运动辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ContrapuntalMotionDifficulty] 生成 [ContrapuntalMotionQuestion]：
 * 1. 从该难度的可用运动类型集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [ContrapuntalMotionDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ContrapuntalMotionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ContrapuntalMotionDifficulty): ContrapuntalMotionQuestion {
        val availableTypes = ContrapuntalMotionType.forDifficulty(difficulty)
        val motion = availableTypes.random(random)
        val seed = random.nextLong()

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableTypes.size)
        val distractors = availableTypes.filter { it != motion }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + motion).shuffled(random)

        return ContrapuntalMotionQuestion(
            motion = motion,
            difficulty = difficulty,
            seed = seed,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = motion.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ContrapuntalMotionEngine = ContrapuntalMotionEngine(Random(seed))
    }
}
