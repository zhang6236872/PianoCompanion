package com.pianocompanion.intervaltraining

import kotlin.random.Random

/**
 * 音程听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [IntervalDifficulty] 生成 [IntervalQuestion]：
 * 1. 从难度的音程集合中随机选择一个正确的音程类型
 * 2. 随机选择一个根音（较低音，在舒适中音区 C4-B4 范围内）
 * 3. 计算较高音（= 根音 + 半音数），钳制到钢琴范围 [21, 108]
 * 4. 根据播放方向确定播放顺序
 * 5. 选项 = 该难度的全部音程集合（已打乱，含正确答案）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param root 底层随机数生成器
 */
class IntervalTrainingEngine(
    private val root: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param playDirection 播放方向（默认上行旋律）
     * @return 生成的题目
     */
    fun generate(
        difficulty: IntervalDifficulty,
        playDirection: PlayDirection = PlayDirection.ASCENDING
    ): IntervalQuestion {
        // 1. 选择正确的音程类型
        val interval = difficulty.intervals.random(root)

        // 2. 选择根音（C4-E5，确保 +12 半音仍在钢琴范围内）
        val lowerMidi = root.nextInt(START_MIN, START_MAX + 1)

        // 3. 计算较高音并钳制
        val upperMidi = (lowerMidi + interval.semitones).coerceIn(MIN_MIDI, MAX_MIDI)

        // 4. 确定播放顺序
        val playOrder = when (playDirection) {
            PlayDirection.ASCENDING -> listOf(lowerMidi, upperMidi)
            PlayDirection.DESCENDING -> listOf(upperMidi, lowerMidi)
            PlayDirection.HARMONIC -> listOf(lowerMidi, upperMidi)
        }

        // 5. 选项 = 难度的全部音程集合（打乱）
        val options = difficulty.intervals.shuffled(root)

        return IntervalQuestion(
            interval = interval,
            playDirection = playDirection,
            lowerMidi = lowerMidi,
            upperMidi = upperMidi,
            playOrder = playOrder,
            options = options
        )
    }

    companion object {
        /** 根音最低 C4。 */
        const val START_MIN = 60

        /** 根音最高 E5（确保即使 +12 半音也舒适）。 */
        const val START_MAX = 76

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): IntervalTrainingEngine = IntervalTrainingEngine(Random(seed))
    }
}
