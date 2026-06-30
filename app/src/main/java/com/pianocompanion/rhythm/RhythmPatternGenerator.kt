package com.pianocompanion.rhythm

import kotlin.random.Random

/**
 * 节奏型生成器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RhythmDifficulty] 和随机种子生成 [RhythmPattern]。
 * 使用确定性伪随机数生成器，相同种子产生相同节奏型，便于测试复现。
 *
 * 设计要点：
 * - 生成的节奏型恰好填满指定拍数（默认 4 拍 = 一个 4/4 小节）
 * - 各难度递增可用时值类型
 * - 非休止音符在舒适音域内随机选音（C4-B4 = MIDI 60-71）
 * - 附点四分音符后自动补八分音符（3.5 → 1.5 + 0.5 + ... 填满）
 *
 * @param random 底层随机数生成器，便于注入种子进行测试
 */
class RhythmPatternGenerator(
    private val random: Random = Random.Default
) {
    /** 舒适音域：C4(60) 到 B4(71)。 */
    private val minMidi = 60
    private val maxMidi = 71

    /**
     * 生成一个节奏型。
     *
     * @param difficulty 难度
     * @param targetBeats 目标拍数（默认 4.0 = 一个 4/4 小节）
     * @param tempoBpm 速度（默认 90 BPM）
     * @return 生成的节奏型
     */
    fun generate(
        difficulty: RhythmDifficulty,
        targetBeats: Double = 4.0,
        tempoBpm: Int = 90
    ): RhythmPattern {
        val pool = durationPool(difficulty)
        val events = mutableListOf<RhythmEvent>()
        var remaining = targetBeats

        while (remaining > 0.001) {
            // 从池中选择一个不超过剩余拍数的时值
            val valid = pool.filter { it.beats <= remaining + 0.001 }
            if (valid.isEmpty()) {
                // 安全兜底：用四分音符或更小的时值填充
                val filler = if (remaining >= 1.0) RhythmDuration.QUARTER
                else if (remaining >= 0.5) RhythmDuration.EIGHTH
                else RhythmDuration.SIXTEENTH
                events.add(RhythmEvent(filler, randomMidi()))
                remaining -= filler.beats
                continue
            }

            val chosen = valid.random(random)

            // 如果选择了附点四分音符(1.5拍)，后面自动跟一个八分音符(0.5拍)以填满2拍
            if (chosen == RhythmDuration.DOTTED_QUARTER && remaining >= 2.0) {
                events.add(RhythmEvent(chosen, randomMidi()))
                events.add(RhythmEvent(RhythmDuration.EIGHTH, randomMidi()))
                remaining -= 2.0
            } else {
                events.add(RhythmEvent(chosen, randomMidi()))
                remaining -= chosen.beats
            }
        }

        return RhythmPattern(
            events = events,
            tempoBpm = tempoBpm,
            beatsPerMeasure = targetBeats.toInt()
        )
    }

    /** 随机选一个 MIDI 音符。 */
    private fun randomMidi(): Int = random.nextInt(minMidi, maxMidi + 1)

    /**
     * 各难度可用的时值池。
     * - 初级：四分音符、二分音符
     * - 中级：加入八分音符、附点四分
     * - 高级：加入十六分音符、四分休止、八分休止
     */
    private fun durationPool(difficulty: RhythmDifficulty): List<RhythmDuration> = when (difficulty) {
        RhythmDifficulty.BEGINNER -> listOf(
            RhythmDuration.QUARTER,
            RhythmDuration.HALF
        )
        RhythmDifficulty.INTERMEDIATE -> listOf(
            RhythmDuration.QUARTER,
            RhythmDuration.HALF,
            RhythmDuration.EIGHTH,
            RhythmDuration.DOTTED_QUARTER
        )
        RhythmDifficulty.ADVANCED -> listOf(
            RhythmDuration.QUARTER,
            RhythmDuration.HALF,
            RhythmDuration.EIGHTH,
            RhythmDuration.DOTTED_QUARTER,
            RhythmDuration.SIXTEENTH,
            RhythmDuration.QUARTER_REST,
            RhythmDuration.EIGHTH_REST
        )
    }

    companion object {
        /** 创建带固定种子的生成器（用于测试确定性）。 */
        fun withSeed(seed: Long): RhythmPatternGenerator = RhythmPatternGenerator(Random(seed))
    }
}
