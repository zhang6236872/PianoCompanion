package com.pianocompanion.consonancetraining

import kotlin.random.Random

/**
 * 协和度辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ConsonanceDifficulty] 生成 [ConsonanceQuestion]：
 *
 * - **初级**：二选一「协和 vs 不协和」。正确答案若落在完全协和或不完全协和，
 *   则归为「协和」；若为不协和则归为「不协和」。从全部 12 个音程中随机选音程。
 * - **中级**：三选一「完全协和 vs 不完全协和 vs 不协和」，和声方式呈现。
 * - **高级**：三选一，和声方式，随机八度偏移（−1/0/+1），使音程出现在不同音区。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ConsonanceEngine(
    private val random: Random = Random.Default
) {
    /** 基准 MIDI 音高（C4 = 60）。 */
    private val baseMidi: Int = 60

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ConsonanceDifficulty): ConsonanceQuestion {
        val seed = random.nextLong()

        // 高级难度随机八度偏移（-1, 0, +1）
        val octaveOffset = if (difficulty == ConsonanceDifficulty.ADVANCED) {
            random.nextInt(-1, 2) // -1, 0, 1
        } else {
            0
        }

        val presentation = if (difficulty.harmonic) Presentation.HARMONIC else Presentation.MELODIC

        if (difficulty == ConsonanceDifficulty.BEGINNER) {
            return generateBeginner(seed, octaveOffset, presentation, difficulty)
        }
        return generateThreeWay(seed, octaveOffset, presentation, difficulty)
    }

    /**
     * 初级：协和 vs 不协和（二选一）。
     *
     * 先随机决定答案是「协和」还是「不协和」，再从对应音程池选一个具体音程。
     */
    private fun generateBeginner(
        seed: Long,
        octaveOffset: Int,
        presentation: Presentation,
        difficulty: ConsonanceDifficulty
    ): ConsonanceQuestion {
        // 随机决定答案是协和还是不协和（各 50%）
        val isConsonant = random.nextBoolean()
        val pool = if (isConsonant) {
            MusicInterval.forCategory(ConsonanceCategory.PERFECT_CONSONANCE) +
                MusicInterval.forCategory(ConsonanceCategory.IMPERFECT_CONSONANCE)
        } else {
            MusicInterval.forCategory(ConsonanceCategory.DISSONANCE)
        }
        val interval = pool.random(random)

        // 初级标签：协和 / 不协和
        val correctLabel = if (isConsonant) BEGINNER_CONSONANT_LABEL else BEGINNER_DISSONANT_LABEL
        val otherLabel = if (isConsonant) BEGINNER_DISSONANT_LABEL else BEGINNER_CONSONANT_LABEL

        val choices = mutableListOf(correctLabel, otherLabel)
        choices.shuffle(random)

        return ConsonanceQuestion(
            interval = interval,
            category = interval.category,
            difficulty = difficulty,
            seed = seed,
            baseMidi = baseMidi,
            octaveOffset = octaveOffset,
            presentation = presentation,
            answerChoices = choices,
            correctAnswer = correctLabel
        )
    }

    /**
     * 中级/高级：完全协和 vs 不完全协和 vs 不协和（三选一）。
     */
    private fun generateThreeWay(
        seed: Long,
        octaveOffset: Int,
        presentation: Presentation,
        difficulty: ConsonanceDifficulty
    ): ConsonanceQuestion {
        // 从三大类中随机选正确类别
        val correctCategory = ConsonanceCategory.ALL.random(random)
        // 从该类别中选一个具体音程
        val interval = MusicInterval.forCategory(correctCategory).random(random)

        // 构建选项：正确类别 + 其余两类作为干扰
        val distractors = ConsonanceCategory.ALL.filter { it != correctCategory }
        val allChoices = (distractors + correctCategory).shuffled(random)

        return ConsonanceQuestion(
            interval = interval,
            category = correctCategory,
            difficulty = difficulty,
            seed = seed,
            baseMidi = baseMidi,
            octaveOffset = octaveOffset,
            presentation = presentation,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = correctCategory.fullLabel
        )
    }

    companion object {
        /** 初级「协和」标签。 */
        const val BEGINNER_CONSONANT_LABEL = "协和 (Consonant)"

        /** 初级「不协和」标签。 */
        const val BEGINNER_DISSONANT_LABEL = "不协和 (Dissonant)"

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ConsonanceEngine = ConsonanceEngine(Random(seed))
    }
}
