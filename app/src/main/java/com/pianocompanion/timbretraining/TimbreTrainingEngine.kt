package com.pianocompanion.timbretraining

import kotlin.random.Random

/**
 * 音色辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [TimbreTrainingDifficulty] 生成 [TimbreTrainingQuestion]：
 * 1. 从该难度的可用乐器集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [TimbreTrainingDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class TimbreTrainingEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param noteDurationMs 音符持续时间（毫秒）
     * @return 生成的题目
     */
    fun generate(
        difficulty: TimbreTrainingDifficulty,
        noteDurationMs: Long = DEFAULT_NOTE_DURATION_MS
    ): TimbreTrainingQuestion {
        val availableInstruments = TimbreInstrument.forDifficulty(difficulty)
        val instrument = availableInstruments.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableInstruments.size)
        val distractors = availableInstruments.filter { it != instrument }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + instrument).shuffled(random)

        return TimbreTrainingQuestion(
            instrument = instrument,
            difficulty = difficulty,
            noteDurationMs = noteDurationMs,
            answerChoices = allChoices.map { it.shortLabel },
            correctAnswer = instrument.shortLabel
        )
    }

    companion object {
        /** 默认音符持续时间（毫秒）。 */
        const val DEFAULT_NOTE_DURATION_MS = 1500L

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): TimbreTrainingEngine = TimbreTrainingEngine(Random(seed))
    }
}
