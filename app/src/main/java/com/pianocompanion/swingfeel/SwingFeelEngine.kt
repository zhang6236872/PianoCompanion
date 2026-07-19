package com.pianocompanion.swingfeel

import kotlin.random.Random

/**
 * 摇摆感辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [SwingDifficulty] 生成 [SwingQuestion]：
 *
 * 1. **随机选拖摆感**：从该难度的 [SwingDifficulty.candidateRatios] 中随机一种。
 * 2. **构建选项**：按摇摆程度（等分 → 轻摇摆 → 摇摆）排序生成显示名，保证选项顺序固定、易读。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class SwingFeelEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: SwingDifficulty): SwingQuestion {
        val ratio = difficulty.candidateRatios.random(random)
        val choices = difficulty.candidateRatios
            .sortedBy { swingAmount(it) }
            .map { it.displayName }

        return SwingQuestion(
            difficulty = difficulty,
            ratio = ratio,
            swingFraction = ratio.fraction,
            tempoBpm = difficulty.tempoBpm,
            beatsPerQuestion = difficulty.beatsPerQuestion,
            answerChoices = choices,
            correctAnswer = ratio.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): SwingFeelEngine = SwingFeelEngine(Random(seed))

        /** 摇摆程度排序权重（用于选项排序：等分 → 轻摇摆 → 摇摆）。 */
        internal fun swingAmount(r: SwingRatio): Int = when (r) {
            SwingRatio.STRAIGHT -> 0
            SwingRatio.LIGHT_SWING -> 1
            SwingRatio.SWING -> 2
        }
    }
}
