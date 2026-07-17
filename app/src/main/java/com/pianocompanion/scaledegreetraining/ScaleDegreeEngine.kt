package com.pianocompanion.scaledegreetraining

import kotlin.random.Random

/**
 * 调内音级辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ScaleDegreeDifficulty] 生成 [ScaleDegreeQuestion]：
 *
 * 1. **随机选择主音（tonic）**：从候选主音集合中随机选取一个 MIDI 音高。每次换调
 *    强迫用户依赖相对音高（调内功能感），而非记住某个绝对音高对应某个音级——
 *    这是相对音高训练与绝对音高训练的本质区别。
 * 2. **从该难度候选集中选目标音级**。
 * 3. **构建选项**：正确答案 + 同候选集中的干扰项（已打乱）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ScaleDegreeEngine(
    private val random: Random = Random.Default
) {
    /**
     * 候选主音集合（MIDI）：C3/G3 区间的白键起始音，确保目标音（最高 +11 半音）
     * 不超过 MIDI 84（C6）。涵盖 C/D/E/F/G/A 大调，避免单一调性记忆。
     */
    private val tonicPool: IntArray = intArrayOf(
        48, // C3
        50, // D3
        52, // E3
        53, // F3
        55, // G3
        57, // A3
        60, // C4
        62, // D4
        64, // E4
        65, // F4
        67, // G4
        69  // A4
    )

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: ScaleDegreeDifficulty): ScaleDegreeQuestion {
        val seed = random.nextLong()

        // 随机选主音（换调，强迫相对音高）
        val tonicMidi = tonicPool.random(random)

        // 从候选音级集合选正确答案
        val correctDegree = difficulty.degrees.random(random)

        // 构建选项：候选集全部音级的完整标签（已打乱）
        // 初级 2 选项 / 中级 4 选项 / 高级 7 选项
        val choices = difficulty.degrees
            .map { it.fullLabel }
            .shuffled(random)

        return ScaleDegreeQuestion(
            degree = correctDegree,
            difficulty = difficulty,
            seed = seed,
            tonicMidi = tonicMidi,
            answerChoices = choices,
            correctAnswer = correctDegree.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ScaleDegreeEngine = ScaleDegreeEngine(Random(seed))
    }
}
