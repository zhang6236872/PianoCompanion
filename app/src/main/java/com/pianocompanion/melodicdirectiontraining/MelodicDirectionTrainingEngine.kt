package com.pianocompanion.melodicdirectiontraining

import kotlin.random.Random

/**
 * 旋律方向辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MelodicDirectionDifficulty] 生成 [MelodicDirectionQuestion]：
 * 1. 从该难度的可用方向集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [MelodicDirectionDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class MelodicDirectionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(
        difficulty: MelodicDirectionDifficulty
    ): MelodicDirectionQuestion {
        val availableDirections = MelodicDirection.forDifficulty(difficulty)
        val direction = availableDirections.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableDirections.size)
        val distractors = availableDirections.filter { it != direction }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + direction).shuffled(random)

        return MelodicDirectionQuestion(
            direction = direction,
            difficulty = difficulty,
            noteCount = DEFAULT_NOTE_COUNT,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = direction.fullLabel
        )
    }

    companion object {
        /** 默认音符数量。 */
        const val DEFAULT_NOTE_COUNT = 4

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): MelodicDirectionEngine = MelodicDirectionEngine(Random(seed))
    }
}
