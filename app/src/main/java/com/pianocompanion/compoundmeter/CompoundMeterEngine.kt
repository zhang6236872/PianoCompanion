package com.pianocompanion.compoundmeter

import kotlin.random.Random

/**
 * 复合节拍听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [CompoundMeterDifficulty] 生成 [CompoundMeterQuestion]：
 *
 * 1. **随机选取目标拍子**：从该难度的拍子集合中随机选一个作为正确答案。
 * 2. **构建干扰项**：从剩余拍子中随机选取若干作为干扰项。
 * 3. **构建选项**：正确答案 + 干扰项，打乱顺序。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class CompoundMeterEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: CompoundMeterDifficulty): CompoundMeterQuestion {
        val seed = random.nextLong()

        // 1. 随机选取目标拍子
        val target = difficulty.meters.random(random)

        // 2. 从剩余拍子中选取干扰项
        val distractorPool = difficulty.meters.filter { it != target }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 3. 构建选项（正确 + 干扰项），打乱
        val allMeters = listOf(target) + distractors
        val choices = allMeters.map { it.displayName }.shuffled(random)

        return CompoundMeterQuestion(
            difficulty = difficulty,
            seed = seed,
            targetMeter = target,
            answerChoices = choices,
            correctAnswer = target.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): CompoundMeterEngine = CompoundMeterEngine(Random(seed))
    }
}
