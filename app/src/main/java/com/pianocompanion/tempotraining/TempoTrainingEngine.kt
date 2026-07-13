package com.pianocompanion.tempotraining

import kotlin.random.Random

/**
 * 速度辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TempoTrainingDifficulty] 生成 [TempoTrainingQuestion]：
 * 1. 从该难度的可用速度集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [TempoTrainingDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TempoTrainingEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param clickCount 播放的 click 总次数（默认 8）
     * @return 生成的题目
     */
    fun generate(
        difficulty: TempoTrainingDifficulty,
        clickCount: Int = DEFAULT_CLICK_COUNT
    ): TempoTrainingQuestion {
        val availableTempos = TempoCategory.forDifficulty(difficulty)
        val tempo = availableTempos.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableTempos.size)
        val distractors = availableTempos.filter { it != tempo }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + tempo).shuffled(random)

        return TempoTrainingQuestion(
            tempo = tempo,
            difficulty = difficulty,
            clickCount = clickCount,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = tempo.fullLabel
        )
    }

    /**
     * 计算速度题目中每个 click 的 onset（起始时刻，毫秒）。
     *
     * onset[0] = LEAD_SILENCE_MS
     * onset[i] = onset[i-1] + intervalMs
     *
     * @param tempo 速度类型
     * @param clickCount click 总次数
     * @return 所有 click 的毫秒时间戳列表
     */
    fun computeOnsetTimes(
        tempo: TempoCategory,
        clickCount: Int = DEFAULT_CLICK_COUNT
    ): List<Double> {
        val intervalMs = tempo.intervalMs
        val onsets = mutableListOf<Double>()

        for (i in 0 until clickCount) {
            onsets.add(LEAD_SILENCE_MS + i * intervalMs)
        }
        return onsets
    }

    companion object {
        /** 前导静音（毫秒），在第一个 click 之前留出反应时间。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 默认 click 次数。 */
        const val DEFAULT_CLICK_COUNT = 8

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TempoTrainingEngine = TempoTrainingEngine(Random(seed))
    }
}
