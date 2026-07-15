package com.pianocompanion.polyrhythmtraining

import kotlin.random.Random

/**
 * 复合节奏辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [PolyrhythmDifficulty] 生成 [PolyrhythmQuestion]：
 * 1. 从该难度的可用复合节奏集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [PolyrhythmDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class PolyrhythmTrainingEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param cycleCount 播放的周期数（默认 2，即重复两遍复合节奏）
     * @return 生成的题目
     */
    fun generate(
        difficulty: PolyrhythmDifficulty,
        cycleCount: Int = DEFAULT_CYCLE_COUNT
    ): PolyrhythmQuestion {
        val availablePolyrhythms = PolyrhythmType.forDifficulty(difficulty)
        val polyrhythm = availablePolyrhythms.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availablePolyrhythms.size)
        val distractors = availablePolyrhythms.filter { it != polyrhythm }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + polyrhythm).shuffled(random)

        return PolyrhythmQuestion(
            polyrhythm = polyrhythm,
            difficulty = difficulty,
            cycleCount = cycleCount,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = polyrhythm.fullLabel
        )
    }

    /**
     * 计算复合节奏题目的所有点击 onset 时间（毫秒）。
     *
     * 高音声部：在每周期内等分 [highCount] 份， onset[i] = cycleStart + i * cycleDuration / highCount
     * 低音声部：在每周期内等分 [lowCount] 份， onset[j] = cycleStart + j * cycleDuration / lowCount
     * 两声部在每个周期起始时刻对齐。
     *
     * @param polyrhythm 复合节奏类型
     * @param cycleCount 周期数
     * @return 高音 onset 列表和低音 onset 列表（每周期累加）
     */
    fun computeOnsetTimes(
        polyrhythm: PolyrhythmType,
        cycleCount: Int = DEFAULT_CYCLE_COUNT
    ): Pair<List<Double>, List<Double>> {
        val highOnsets = mutableListOf<Double>()
        val lowOnsets = mutableListOf<Double>()
        val cycleStepMs = CYCLE_DURATION_MS / polyrhythm.highCount
        val lowStepMs = CYCLE_DURATION_MS / polyrhythm.lowCount

        for (c in 0 until cycleCount) {
            val cycleStart = LEAD_SILENCE_MS + c * CYCLE_DURATION_MS
            for (i in 0 until polyrhythm.highCount) {
                highOnsets.add(cycleStart + i * cycleStepMs)
            }
            for (j in 0 until polyrhythm.lowCount) {
                lowOnsets.add(cycleStart + j * lowStepMs)
            }
        }
        return highOnsets to lowOnsets
    }

    companion object {
        /** 前导静音（毫秒），在第一组音符之前留出反应时间。 */
        const val LEAD_SILENCE_MS = 500.0

        /** 单个复合节奏周期的持续时长（毫秒）。 */
        const val CYCLE_DURATION_MS = 2400.0

        /** 默认周期数（重复播放的次数）。 */
        const val DEFAULT_CYCLE_COUNT = 2

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): PolyrhythmTrainingEngine =
            PolyrhythmTrainingEngine(Random(seed))
    }
}
