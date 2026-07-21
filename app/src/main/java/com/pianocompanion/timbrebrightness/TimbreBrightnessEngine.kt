package com.pianocompanion.timbrebrightness

import kotlin.random.Random

/**
 * 音色亮度辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TimbreBrightnessDifficulty] 生成 [TimbreBrightnessQuestion]：
 *
 * 1. **随机选择基频（fundamental）**：在中音区随机变化，避免每次都是同一音高。
 *    音高不影响亮度判断（亮度由泛音数量决定，与绝对音高无关）。
 * 2. **从该难度候选集中选正确亮度等级**。
 * 3. **构建选项**：候选集全部亮度等级的完整标签（已打乱）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TimbreBrightnessEngine(
    private val random: Random = Random.Default
) {
    /**
     * 候选基频集合（MIDI）：C4-D4-E4-F4-G4-A4-B4 白键，确保基频在清晰的中音区，
     * 泛音层次分明且不会因过高/过低而影响亮度感知。
     */
    private val fundamentalPool: IntArray = intArrayOf(
        60, // C4
        62, // D4
        64, // E4
        65, // F4
        67, // G4
        69, // A4
        71  // B4
    )

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: TimbreBrightnessDifficulty): TimbreBrightnessQuestion {
        val seed = random.nextLong()

        // 随机选基频
        val fundamentalMidi = fundamentalPool.random(random)

        // 从候选亮度集合选正确答案
        val correctBrightness = difficulty.levels.random(random)

        // 构建选项：候选集全部亮度的完整标签（已打乱）
        val choices = difficulty.levels
            .map { it.fullLabel }
            .shuffled(random)

        return TimbreBrightnessQuestion(
            brightness = correctBrightness,
            difficulty = difficulty,
            seed = seed,
            fundamentalMidi = fundamentalMidi,
            answerChoices = choices,
            correctAnswer = correctBrightness.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TimbreBrightnessEngine = TimbreBrightnessEngine(Random(seed))
    }
}
