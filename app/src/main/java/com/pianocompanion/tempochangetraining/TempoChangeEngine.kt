package com.pianocompanion.tempochangetraining

import kotlin.random.Random

/**
 * 速度变化方向辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TempoChangeDifficulty] 生成 [TempoChangeQuestion]：
 *
 * 1. **随机选择主音（tonic）**：旋律围绕主音变化，每次换音高增加变化，但旋律轮廓固定，
 *    使「速度变化方向」成为唯一显著特征（音高不是答题依据）。
 * 2. **从该难度候选集中选正确速度方向**。
 * 3. **构建选项**：候选集全部方向的完整标签（已打乱）。
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TempoChangeEngine(
    private val random: Random = Random.Default
) {
    /**
     * 候选主音集合（MIDI）：C4-D4-E4-F4-G4 白键，确保旋律音（主音 + 最多 +4 半音）
     * 落在舒适的钢琴中音区。
     */
    private val tonicPool: IntArray = intArrayOf(
        60, // C4
        62, // D4
        64, // E4
        65, // F4
        67  // G4
    )

    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @return 生成的题目
     */
    fun generate(difficulty: TempoChangeDifficulty): TempoChangeQuestion {
        val seed = random.nextLong()

        // 随机选主音（旋律起始音）
        val tonicMidi = tonicPool.random(random)

        // 从候选方向集合选正确答案
        val correctDirection = difficulty.directions.random(random)

        // 构建选项：候选集全部方向的完整标签（已打乱）
        // 初级 2 选项 / 中级 3 选项 / 高级 5 选项
        val choices = difficulty.directions
            .map { it.fullLabel }
            .shuffled(random)

        return TempoChangeQuestion(
            direction = correctDirection,
            difficulty = difficulty,
            seed = seed,
            tonicMidi = tonicMidi,
            answerChoices = choices,
            correctAnswer = correctDirection.fullLabel
        )
    }

    companion object {
        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TempoChangeEngine = TempoChangeEngine(Random(seed))
    }
}
