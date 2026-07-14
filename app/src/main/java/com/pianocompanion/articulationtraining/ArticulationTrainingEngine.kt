package com.pianocompanion.articulationtraining

import kotlin.random.Random

/**
 * 演奏法辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [ArticulationTrainingDifficulty] 生成 [ArticulationTrainingQuestion]：
 * 1. 从该难度的可用演奏法集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [ArticulationTrainingDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class ArticulationTrainingEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param noteCount 播放的音符数量（默认 5）
     * @return 生成的题目
     */
    fun generate(
        difficulty: ArticulationTrainingDifficulty,
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): ArticulationTrainingQuestion {
        val availableArticulations = ArticulationType.forDifficulty(difficulty)
        val articulation = availableArticulations.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableArticulations.size)
        val distractors = availableArticulations.filter { it != articulation }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + articulation).shuffled(random)

        return ArticulationTrainingQuestion(
            articulation = articulation,
            difficulty = difficulty,
            noteCount = noteCount,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = articulation.fullLabel
        )
    }

    /**
     * 计算演奏法题目中每个音符的 onset（起始时刻，毫秒）。
     *
     * onset[0] = LEAD_SILENCE_MS
     * onset[i] = onset[i-1] + NOTE_SPACING_MS
     *
     * @param noteCount 音符数量
     * @return 所有音符的毫秒时间戳列表
     */
    fun computeOnsetTimes(
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in 0 until noteCount) {
            onsets.add(LEAD_SILENCE_MS + i * NOTE_SPACING_MS)
        }
        return onsets
    }

    companion object {
        /** 前导静音（毫秒），在第一个音符之前留出反应时间。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 节拍间距（毫秒）——音符之间的时间间隔。 */
        const val NOTE_SPACING_MS = 380.0

        /** 默认音符数量（C 大调五声音阶 C-D-E-G-A）。 */
        const val DEFAULT_NOTE_COUNT = 5

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): ArticulationTrainingEngine =
            ArticulationTrainingEngine(Random(seed))
    }
}
