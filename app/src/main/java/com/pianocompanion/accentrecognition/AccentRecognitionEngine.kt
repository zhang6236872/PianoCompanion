package com.pianocompanion.accentrecognition

import kotlin.random.Random

/**
 * 强拍 / 重音辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [AccentDifficulty] 生成 [AccentQuestion]：
 *
 * 1. **随机选小节拍数**：从该难度的 [AccentDifficulty.beatsPerMeasureOptions] 中随机一种。
 * 2. **随机选强拍位置**：在 1..N 中随机选一拍作为强拍。
 * 3. **构建选项**：按顺序生成「第 1 拍 … 第 N 拍」（位置型选项保持自然顺序，便于用户对应）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class AccentEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: AccentDifficulty): AccentQuestion {
        // 随机选小节拍数（从该难度候选集合）
        val beatsPerMeasure = difficulty.beatsPerMeasureOptions.random(random)

        // 随机选强拍位置（1..beatsPerMeasure）
        val accentPosition = random.nextInt(1, beatsPerMeasure + 1)

        // 选项：按自然顺序生成「第 1 拍 … 第 N 拍」
        val choices = (1..beatsPerMeasure).map { positionLabel(it) }

        return AccentQuestion(
            difficulty = difficulty,
            beatsPerMeasure = beatsPerMeasure,
            accentPosition = accentPosition,
            strength = difficulty.strength,
            beatIntervalMs = difficulty.tempoIntervalMs,
            measureRepeat = difficulty.measureRepeat,
            answerChoices = choices,
            correctAnswer = positionLabel(accentPosition)
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): AccentEngine = AccentEngine(Random(seed))

        /** 拍位序号文本（如「第 3 拍」）。与题目使用的格式保持一致。 */
        fun positionLabel(position: Int): String = "第 $position 拍"
    }
}
