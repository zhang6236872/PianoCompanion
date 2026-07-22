package com.pianocompanion.chordinversion

import kotlin.random.Random

/**
 * 和弦转位听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ChordInversionDifficulty] 生成 [ChordInversionQuestion]：
 *
 * 1. **随机选取目标和弦类型**：从该难度的和弦集合中随机选一个。
 * 2. **随机选取有效转位**：从该和弦可用的转位 ∩ 难度选项集合中随机选一个作为正确答案。
 * 3. **构建干扰项**：从难度选项集合的剩余转位中选取作为干扰项。
 * 4. **构建选项**：正确答案 + 干扰项，打乱顺序。
 * 5. **随机选取根音**：从该难度的根音范围内随机选一个。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ChordInversionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ChordInversionDifficulty): ChordInversionQuestion {
        val seed = random.nextLong()

        // 1. 随机选取目标和弦类型
        val chordType = difficulty.chords.random(random)

        // 2. 选取该和弦可用的转位（与难度选项的交集）
        val validInversions = difficulty.inversionOptions.filter {
            it.order <= chordType.maxInversionOrder
        }
        val targetInversion = validInversions.random(random)

        // 3. 从剩余选项中选取干扰项
        val distractorPool = difficulty.inversionOptions.filter { it != targetInversion }
        val distractors = distractorPool.shuffled(random).take(difficulty.choiceCount - 1)

        // 4. 构建选项（正确 + 干扰项），打乱
        val allInversions = listOf(targetInversion) + distractors
        val choices = allInversions.map { it.choiceLabel }.shuffled(random)

        // 5. 随机选取根音
        val rootMidi = (difficulty.rootMidiMin..difficulty.rootMidiMax).random(random)

        return ChordInversionQuestion(
            difficulty = difficulty,
            seed = seed,
            rootMidi = rootMidi,
            chordType = chordType,
            targetInversion = targetInversion,
            answerChoices = choices,
            correctAnswer = targetInversion.choiceLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ChordInversionEngine = ChordInversionEngine(Random(seed))
    }
}
