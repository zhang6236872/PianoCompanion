package com.pianocompanion.subdivisionrecognition

import kotlin.random.Random

/**
 * 节奏细分听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [SubdivisionDifficulty] 生成 [SubdivisionQuestion]：
 *
 * 1. **随机选细分类型**：从该难度的 [SubdivisionDifficulty.subdivisionOptions] 中随机一种。
 * 2. **构建选项**：按细分密度（notesPerBeat）升序生成显示名（2 → 3 → 4），保持自然顺序，
 *    便于用户从「少」到「多」对应感知到的密度。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class SubdivisionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: SubdivisionDifficulty): SubdivisionQuestion {
        // 随机选细分类型（从该难度候选集合）
        val subdivision = difficulty.subdivisionOptions.random(random)

        // 选项：按细分密度升序排列（2 → 3 → 4），便于用户从「疏」到「密」对应
        val choices = difficulty.subdivisionOptions
            .sortedBy { it.notesPerBeat }
            .map { it.displayName }

        return SubdivisionQuestion(
            difficulty = difficulty,
            subdivision = subdivision,
            beatMs = difficulty.beatMs,
            beatsPerMeasure = difficulty.beatsPerMeasure,
            measureRepeat = difficulty.measureRepeat,
            staccato = difficulty.staccato,
            answerChoices = choices,
            correctAnswer = subdivision.displayName
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): SubdivisionEngine = SubdivisionEngine(Random(seed))
    }
}
