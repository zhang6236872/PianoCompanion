package com.pianocompanion.texturerecognitiontraining

import kotlin.random.Random

/**
 * 织体辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TextureDifficulty] 生成 [TextureQuestion]：
 * 1. 从该难度的可用织体集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [TextureDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TextureEngine(
    private val random: Random = Random.Default
) {
    /** 内部计数器，为每道题分配唯一种子（音频渲染用）。 */
    private var seedCounter: Long = 0L

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: TextureDifficulty): TextureQuestion {
        val availableTypes = TextureType.forDifficulty(difficulty)
        val texture = availableTypes.random(random)
        val seed = random.nextLong()

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableTypes.size)
        val distractors = availableTypes.filter { it != texture }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + texture).shuffled(random)

        return TextureQuestion(
            texture = texture,
            difficulty = difficulty,
            seed = seed,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = texture.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TextureEngine = TextureEngine(Random(seed))
    }
}
