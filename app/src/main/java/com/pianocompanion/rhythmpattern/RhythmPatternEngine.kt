package com.pianocompanion.rhythmpattern

import kotlin.random.Random

/**
 * 节奏型听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RhythmDifficulty] 生成 [RhythmPatternQuestion]：
 * 1. 从该难度的可用节奏型集合中随机选择一个类型
 * 2. 选项 = 该难度下所有可用节奏型的显示名（已打乱）
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class RhythmPatternEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param tempo 播放速度（默认慢速）
     * @param repeatCount 节奏型重复播放次数（默认 2）
     * @return 生成的题目
     */
    fun generate(
        difficulty: RhythmDifficulty,
        tempo: RhythmTempo = RhythmTempo.SLOW,
        repeatCount: Int = DEFAULT_REPEAT_COUNT
    ): RhythmPatternQuestion {
        val availableTypes = RhythmPatternType.forDifficulty(difficulty)
        val type = availableTypes.random(random)

        // 选项 = 该难度所有可用节奏型名（已打乱）
        val choices = availableTypes
            .map { it.displayName }
            .shuffled(random)

        return RhythmPatternQuestion(
            type = type,
            difficulty = difficulty,
            tempo = tempo,
            repeatCount = repeatCount,
            answerChoices = choices,
            correctAnswer = type.displayName
        )
    }

    /**
     * 计算节奏型中每个音符 onset（起始时刻，毫秒）的绝对位置。
     *
     * onset[0] = LEAD_SILENCE_MS（前导静音后第一个 click）
     * onset[i] = onset[i-1] + durations[i-1] * beatMs
     *
     * @param pattern 节奏型
     * @param tempo 播放速度
     * @param repeatCount 重复次数
     * @return 所有 onset 的毫秒时间戳列表
     */
    fun computeOnsetTimes(
        pattern: RhythmPatternType,
        tempo: RhythmTempo,
        repeatCount: Int = 1
    ): List<Double> {
        val beatMs = tempo.beatMs
        val onsets = mutableListOf<Double>()

        // 一小节的总时长
        val measureMs = pattern.totalBeats * beatMs

        for (rep in 0 until repeatCount) {
            val measureStart = LEAD_SILENCE_MS + rep * measureMs
            var time = measureStart
            for (duration in pattern.durations) {
                onsets.add(time)
                time += duration * beatMs
            }
        }
        return onsets
    }

    companion object {
        /** 前导静音（毫秒），在第一个 click 之前留出反应时间。 */
        const val LEAD_SILENCE_MS = 300.0

        /** 默认重复次数。 */
        const val DEFAULT_REPEAT_COUNT = 2

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): RhythmPatternEngine = RhythmPatternEngine(Random(seed))
    }
}
