package com.pianocompanion.accentrecognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 强拍 / 重音辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [AccentQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 强拍辨识听辨：**
 *
 * 渲染一段等间隔的 click 序列（N 拍 × 重复次数）。所有 click 时间间距相同，唯一区分依据
 * 是**重音级别**：强拍用更高音高 + 更大音量，普通拍用更低音高 + 更小音量。用户据此判断
 * 重音落在第几拍。
 *
 * 与拍号听辨（[com.pianocompanion.meterrecognition]）不同，本模块的强拍位置是**随机**的
 * （不一定是第 1 拍），用户必须专注追踪重音的周期位置，而非数拍子分组。
 *
 * 渲染流程：
 * 1. [computeOnsetTimes] 计算每个 click 的绝对时间戳
 * 2. [computeAccentFlags] 计算每个 click 是否为强拍（对应 [AccentQuestion.accentPosition]）
 * 3. 预生成强拍 / 普通拍两种 click 波形
 * 4. 在各时间戳叠加对应波形 + 软限幅
 *
 * @param sampleRate 采样率
 */
class AccentAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /** 为题目渲染音频。 */
    fun render(question: AccentQuestion): FloatArray {
        val onsets = computeOnsetTimes(question)
        val accentFlags = computeAccentFlags(question)
        if (onsets.isEmpty()) return FloatArray(0)

        val clickSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + clickSamples + tailSamples

        val output = FloatArray(totalLength)

        // 预生成强拍 / 普通拍两种 click 波形（按题目难度重音强度）
        val strength = question.strength
        val accentClick = generateClick(clickSamples, strength.accentFrequency, strength.accentAmplitude)
        val baseClick = generateClick(clickSamples, strength.baseFrequency, strength.baseAmplitude)

        for (i in onsets.indices) {
            val offset = (onsets[i] * sampleRate / 1000.0).toInt()
            val clickWave = if (accentFlags[i]) accentClick else baseClick
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += clickWave[j]
                }
            }
        }

        // 软限幅防止叠加削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 计算每个 click 的绝对时间戳（毫秒）。
     *
     * 时序：前导静音 → 每小节 N 个等间隔 click，小节连续播放（无小节间额外间隔）。
     */
    fun computeOnsetTimes(question: AccentQuestion): List<Double> {
        val intervalMs = question.beatIntervalMs
        val onsets = mutableListOf<Double>()
        for (rep in 0 until question.measureRepeat) {
            val measureStart = LEAD_SILENCE_MS + rep * question.beatsPerMeasure * intervalMs
            for (beat in 0 until question.beatsPerMeasure) {
                onsets.add(measureStart + beat * intervalMs)
            }
        }
        return onsets
    }

    /**
     * 计算每个 click 是否为强拍（true=强拍）。
     *
     * 强拍位于每小节第 [AccentQuestion.accentPosition] 拍（从 1 开始计数）。
     */
    fun computeAccentFlags(question: AccentQuestion): List<Boolean> {
        val accentIdx = question.accentPosition - 1
        return List(question.measureRepeat * question.beatsPerMeasure) { idx ->
            (idx % question.beatsPerMeasure) == accentIdx
        }
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: AccentQuestion): Long {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + CLICK_DURATION_MS + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 计算指定 click 索引在输出缓冲区中的能量（RMS）。
     *
     * 用于单元测试验证重音轮廓：强拍位置能量应显著高于普通拍。
     */
    fun clickRmsEnergy(buffer: FloatArray, question: AccentQuestion, clickIndex: Int): Double {
        val onsets = computeOnsetTimes(question)
        if (clickIndex !in onsets.indices) return 0.0
        val onsetSample = (onsets[clickIndex] * sampleRate / 1000.0).toInt()
        val windowSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        var sumSq = 0.0
        var count = 0
        for (i in onsetSample until minOf(onsetSample + windowSamples, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) kotlin.math.sqrt(sumSq / count) else 0.0
    }

    /**
     * 生成单个 click 波形（正弦波 + 快速指数衰减包络，模拟节拍器/木鱼短促脉冲）。
     */
    private fun generateClick(numSamples: Int, frequency: Double, amplitude: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CLICK_DECAY_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val sample = sin(2.0 * PI * frequency * t) * envelope * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    /** 软限幅函数（保留强拍与普通拍的绝对响度差，不做峰值归一化）。 */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 450.0

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
