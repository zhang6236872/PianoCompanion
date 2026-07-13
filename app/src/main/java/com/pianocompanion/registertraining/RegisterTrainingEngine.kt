package com.pianocompanion.registertraining

import kotlin.random.Random

/**
 * 音区辨识训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [RegisterTrainingDifficulty] 生成 [RegisterTrainingQuestion]：
 * 1. 从该难度的可用音区集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [RegisterTrainingDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class RegisterTrainingEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param noteCount 播放的音符数量（默认 4：C-E-G-C 琶音）
     * @return 生成的题目
     */
    fun generate(
        difficulty: RegisterTrainingDifficulty,
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): RegisterTrainingQuestion {
        val availableRegisters = MusicRegister.forDifficulty(difficulty)
        val register = availableRegisters.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableRegisters.size)
        val distractors = availableRegisters.filter { it != register }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + register).shuffled(random)

        return RegisterTrainingQuestion(
            register = register,
            difficulty = difficulty,
            noteCount = noteCount,
            answerChoices = allChoices.map { it.fullLabel },
            correctAnswer = register.fullLabel
        )
    }

    /**
     * 计算每个音符的绝对时间戳（毫秒）。
     *
     * @param noteCount 音符数量
     * @return 时间戳列表
     */
    fun computeOnsetTimes(
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in 0 until noteCount) {
            onsets.add(LEAD_SILENCE_MS + i * NOTE_DURATION_MS)
        }
        return onsets
    }

    companion object {
        /** 默认音符数量（C-E-G-C 琶音）。 */
        const val DEFAULT_NOTE_COUNT = 4

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 400.0

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): RegisterTrainingEngine = RegisterTrainingEngine(Random(seed))
    }
}
