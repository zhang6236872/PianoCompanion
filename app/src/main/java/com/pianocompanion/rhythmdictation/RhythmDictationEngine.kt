package com.pianocompanion.rhythmdictation

import kotlin.random.Random

/**
 * 节奏听写训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RhythmDictationDifficulty] 生成 [RhythmDictationQuestion]：
 * 1. 从该难度的可用节奏单元集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [RhythmDictationDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class RhythmDictationEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param tempo 播放速度（默认慢速）
     * @param repeatCount 节奏单元重复播放次数（默认 2）
     * @return 生成的题目
     */
    fun generate(
        difficulty: RhythmDictationDifficulty,
        tempo: RhythmDictationTempo = RhythmDictationTempo.SLOW,
        repeatCount: Int = DEFAULT_REPEAT_COUNT
    ): RhythmDictationQuestion {
        val availableCells = RhythmCellType.forDifficulty(difficulty)
        val cell = availableCells.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableCells.size)
        val distractors = availableCells.filter { it != cell }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + cell).shuffled(random)

        return RhythmDictationQuestion(
            cell = cell,
            difficulty = difficulty,
            tempo = tempo,
            repeatCount = repeatCount,
            answerChoices = allChoices.map { "${it.symbol}  ${it.displayName}" },
            correctAnswer = "${cell.symbol}  ${cell.displayName}"
        )
    }

    /**
     * 计算节奏单元中每个音符 onset（起始时刻，毫秒）的绝对位置。
     *
     * onset[0] = LEAD_SILENCE_MS（前导静音后第一个 click）
     * onset[i] = onset[i-1] + durations[i-1] * beatMs
     *
     * @param cell 节奏单元
     * @param tempo 播放速度
     * @param repeatCount 重复次数
     * @return 所有 onset 的毫秒时间戳列表
     */
    fun computeOnsetTimes(
        cell: RhythmCellType,
        tempo: RhythmDictationTempo,
        repeatCount: Int = 1
    ): List<Double> {
        val beatMs = tempo.beatMs
        val onsets = mutableListOf<Double>()
        val cellMs = cell.totalBeats * beatMs

        for (rep in 0 until repeatCount) {
            val cellStart = LEAD_SILENCE_MS + rep * cellMs
            var time = cellStart
            for (duration in cell.durations) {
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
        fun withSeed(seed: Long): RhythmDictationEngine = RhythmDictationEngine(Random(seed))
    }
}
