package com.pianocompanion.meterrecognition

import kotlin.random.Random

/**
 * 拍号听辨训练出题引擎（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 根据 [MeterRecognitionDifficulty] 生成 [MeterRecognitionQuestion]：
 * 1. 从该难度的可用拍号集合中随机选择一个正确类型
 * 2. 从可用集合中选取干扰项（共 [MeterRecognitionDifficulty.choiceCount] 个选项），打乱顺序
 *
 * 使用确定性随机数生成器，相同种子产生相同题目，便于测试复现。
 *
 * @param random 底层随机数生成器
 */
class MeterRecognitionEngine(
    private val random: Random = Random.Default
) {
    /**
     * 生成一道题目。
     *
     * @param difficulty 难度
     * @param tempo 播放速度（默认慢速）
     * @param measureRepeat 小节重复播放次数（默认 4）
     * @return 生成的题目
     */
    fun generate(
        difficulty: MeterRecognitionDifficulty,
        tempo: MeterRecognitionTempo = MeterRecognitionTempo.SLOW,
        measureRepeat: Int = DEFAULT_MEASURE_REPEAT
    ): MeterRecognitionQuestion {
        val availableMeters = MeterType.forDifficulty(difficulty)
        val meter = availableMeters.random(random)

        // 构建选项：正确答案 + 从其余类型中选出的干扰项
        val choiceCount = minOf(difficulty.choiceCount, availableMeters.size)
        val distractors = availableMeters.filter { it != meter }
            .shuffled(random)
            .take(choiceCount - 1)
        val allChoices = (distractors + meter).shuffled(random)

        return MeterRecognitionQuestion(
            meter = meter,
            difficulty = difficulty,
            tempo = tempo,
            measureRepeat = measureRepeat,
            answerChoices = allChoices.map { "${it.symbol}  ${it.displayName}" },
            correctAnswer = "${meter.symbol}  ${meter.displayName}"
        )
    }

    /**
     * 计算拍号题目中每个 click 的 onset（起始时刻，毫秒）。
     *
     * onset[0] = LEAD_SILENCE_MS
     * onset[i] = onset[i-1] + clickIntervalMs
     *
     * @param meter 拍号类型
     * @param tempo 播放速度
     * @param measureRepeat 小节重复次数
     * @return 所有 click 的毫秒时间戳列表
     */
    fun computeOnsetTimes(
        meter: MeterType,
        tempo: MeterRecognitionTempo,
        measureRepeat: Int = 1
    ): List<Double> {
        val intervalMs = tempo.clickIntervalMs
        val onsets = mutableListOf<Double>()

        for (rep in 0 until measureRepeat) {
            val measureStart = LEAD_SILENCE_MS + rep * meter.beatsPerMeasure * intervalMs
            for (beat in 0 until meter.beatsPerMeasure) {
                onsets.add(measureStart + beat * intervalMs)
            }
        }
        return onsets
    }

    /**
     * 计算拍号题目中每个 click 的重音级别。
     *
     * @param meter 拍号类型
     * @param measureRepeat 小节重复次数
     * @return 所有 click 的重音级别列表
     */
    fun computeAccentPattern(
        meter: MeterType,
        measureRepeat: Int = 1
    ): List<AccentLevel> {
        return List(measureRepeat * meter.beatsPerMeasure) { idx ->
            meter.accentPattern[idx % meter.beatsPerMeasure]
        }
    }

    companion object {
        /** 前导静音（毫秒），在第一个 click 之前留出反应时间。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 默认小节重复次数。 */
        const val DEFAULT_MEASURE_REPEAT = 4

        /** 创建带固定种子的引擎实例（用于测试确定性）。 */
        fun withSeed(seed: Long): MeterRecognitionEngine = MeterRecognitionEngine(Random(seed))
    }
}
