package com.pianocompanion.modescale

import kotlin.random.Random

/**
 * 调式音阶色彩对比训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ModeScaleDifficulty] 生成 [ModeScaleQuestion]：
 *
 * 1. **随机选取目标调式**：从该难度的调式集合中随机选一个作为正确答案。
 * 2. **构建干扰项**：从剩余调式中随机选取若干作为干扰项。
 * 3. **构建选项**：正确答案 + 干扰项，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ModeScaleEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ModeScaleDifficulty): ModeScaleQuestion {
        val seed = random.nextLong()

        // 1. 随机选取目标调式
        val target = difficulty.modes.random(random)

        // 2. 从剩余调式中选取干扰项
        val distractorPool = difficulty.modes.filter { it != target }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allModes = listOf(target) + distractors
        val choices = allModes.map { it.displayName }.shuffled(random)

        return ModeScaleQuestion(
            difficulty = difficulty,
            seed = seed,
            targetMode = target,
            answerChoices = choices,
            correctAnswer = target.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ModeScaleEngine = ModeScaleEngine(Random(seed))
    }
}
