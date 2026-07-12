package com.pianocompanion.meterrecognition

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 拍号听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [MeterRecognitionQuestion] 的重音节拍模式渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 与节奏听写不同，拍号听辨的核心是**重音分组**而非时间间距。因此：
 * - 所有 click 的**时间间距相同**（等间隔），唯一区分依据是重音级别（音高+音量）
 * - 强拍用更高音高 + 更大音量，弱拍用更低音高 + 更小音量
 * - 小节可重复播放多次，帮助用户多听几遍
 *
 * 渲染流程：
 * 1. 用 [computeOnsetTimes] / [computeAccentPattern] 计算 click 时间戳和重音级别
 * 2. 将时间戳转换为采样偏移
 * 3. 在每个偏移处叠加对应重音级别的 click 波形
 * 4. 软限幅防止叠加时削波
 *
 * @param sampleRate 采样率
 */
class MeterRecognitionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: MeterRecognitionQuestion): FloatArray {
        return renderMeter(
            meter = question.meter,
            tempo = question.tempo,
            measureRepeat = question.measureRepeat
        )
    }

    /**
     * 将拍号重音模式渲染为连续 PCM 采样。
     *
     * @param meter 拍号类型
     * @param tempo 播放速度
     * @param measureRepeat 小节重复次数
     * @return PCM Float 缓冲区
     */
    fun renderMeter(
        meter: MeterType,
        tempo: MeterRecognitionTempo,
        measureRepeat: Int = 1
    ): FloatArray {
        val onsets = computeOnsetTimes(meter, tempo, measureRepeat)
        val accents = computeAccentPattern(meter, measureRepeat)
        if (onsets.isEmpty()) return FloatArray(0)

        val clickSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + clickSamples + tailSamples

        val output = FloatArray(totalLength)

        // 预生成各重音级别的 click 波形
        val strongClick = generateClick(clickSamples, AccentLevel.STRONG)
        val mediumClick = generateClick(clickSamples, AccentLevel.MEDIUM)
        val weakClick = generateClick(clickSamples, AccentLevel.WEAK)

        for (i in onsets.indices) {
            val offset = (onsets[i] * sampleRate / 1000.0).toInt()
            val clickWave = when (accents[i]) {
                AccentLevel.STRONG -> strongClick
                AccentLevel.MEDIUM -> mediumClick
                AccentLevel.WEAK -> weakClick
            }
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += clickWave[j]
                }
            }
        }

        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 计算每个 click 的绝对时间戳（毫秒）。
     *
     * 与 [MeterRecognitionEngine.computeOnsetTimes] 逻辑一致，独立实现以保持自包含性。
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
     * 计算每个 click 的重音级别。
     *
     * 与 [MeterRecognitionEngine.computeAccentPattern] 逻辑一致。
     */
    fun computeAccentPattern(
        meter: MeterType,
        measureRepeat: Int = 1
    ): List<AccentLevel> {
        return List(measureRepeat * meter.beatsPerMeasure) { idx ->
            meter.accentPattern[idx % meter.beatsPerMeasure]
        }
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: MeterRecognitionQuestion): Long {
        val onsets = computeOnsetTimes(question.meter, question.tempo, question.measureRepeat)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + CLICK_DURATION_MS + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个 click 波形。
     *
     * 正弦波 + 快速指数衰减包络，模拟木鱼/节拍器的短促脉冲声。
     * 不同重音级别使用不同频率和振幅。
     */
    private fun generateClick(numSamples: Int, accent: AccentLevel): FloatArray {
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CLICK_DECAY_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val sample = sin(2.0 * PI * accent.frequency * t) * envelope * accent.amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    /** 软限幅函数。 */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + kotlin.math.abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个 click 持续时间（毫秒）。 */
        const val CLICK_DURATION_MS = 90L

        /** Click 衰减时间常数（毫秒）。 */
        const val CLICK_DECAY_MS = 12.0

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
