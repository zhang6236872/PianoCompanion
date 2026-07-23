package com.pianocompanion.texturerecognition

import kotlin.random.Random

/**
 * 织体类型辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MusicTextureDifficulty] 生成 [TextureCategoryQuestion]：
 *
 * 1. **随机选取目标织体**：从该难度的织体集合中随机选一个作为正确答案。
 * 2. **构建干扰项**：从该难度织体集合的剩余类型中随机选取（choiceCount - 1 个）。
 * 3. **构建选项**：正确答案 + 干扰项，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TextureCategoryEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: MusicTextureDifficulty): TextureCategoryQuestion {
        val seed = random.nextLong()

        // 1. 随机选取目标织体
        val targetTexture = difficulty.types.random(random)

        // 2. 从剩余织体中选取干扰项
        val distractorPool = difficulty.types.filter { it != targetTexture }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allTextures = listOf(targetTexture) + distractors
        val choices = allTextures.map { it.fullLabel }.shuffled(random)

        return TextureCategoryQuestion(
            difficulty = difficulty,
            seed = seed,
            targetTexture = targetTexture,
            answerChoices = choices,
            correctAnswer = targetTexture.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TextureCategoryEngine = TextureCategoryEngine(Random(seed))
    }
}
