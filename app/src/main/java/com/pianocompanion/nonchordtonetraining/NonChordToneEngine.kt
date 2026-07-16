package com.pianocompanion.nonchordtonetraining

import kotlin.random.Random

/**
 * 和弦外音辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [NonChordToneDifficulty] 生成 [NonChordToneQuestion]：
 *
 * - **初级**：从 {经过音, 倚音} 中随机选类型，2 选项。
 * - **中级**：从 {经过音, 辅助音, 倚音} 中随机选类型，3 选项。
 * - **高级**：从全部 4 种类型中随机选类型，4 选项。
 *
 * 选定类型后，再从该类型的多个旋律模板中随机选一个（提供旋律变化）。
 * 选项为该难度下所有类型的完整标签，已打乱。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class NonChordToneEngine(
    private val random: Random = Random.Default
) {
    /** 根音 MIDI 音高（C4 = 60）。 */
    private val rootMidi: Int = 60

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: NonChordToneDifficulty): NonChordToneQuestion {
        val seed = random.nextLong()
        val types = difficulty.types
        val type = types.random(random)
        val template = type.templates.random(random)

        // 选项：该难度下所有类型的完整标签，打乱
        val choices = types.map { it.fullLabel }.shuffled(random)

        return NonChordToneQuestion(
            type = type,
            template = template,
            difficulty = difficulty,
            seed = seed,
            rootMidi = rootMidi,
            answerChoices = choices,
            correctAnswer = type.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): NonChordToneEngine = NonChordToneEngine(Random(seed))
    }
}
