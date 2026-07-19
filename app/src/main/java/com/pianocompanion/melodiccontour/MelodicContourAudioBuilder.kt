package com.pianocompanion.melodiccontour

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 旋律轮廓辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ContourQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 旋律轮廓听辨：**
 *
 * 渲染一段由多个音符构成的旋律：每个音符按其 MIDI 音高发音，按 [ContourQuestion.noteDurationMs]
 * 等间距排列。用户据此判断旋律的整体形状（上行/下行/拱形/谷形/波浪）。
 *
 * 关键设计：
 * - **加法合成钢琴音色**：每个音 = 基频 + 4 个谐波（2f/3f/4f/5f，递减振幅）+ 指数衰减包络，
 *   使音色温暖、有钢琴质感，便于辨识音高走向。
 * - **等间距排列**：所有音符时长相同、间距相同，使「轮廓形状」成为唯一显著的旋律特征，
 *   而非依赖节奏变化。
 * - **音符间微小间隙**（占音符时长的 [GAP_RATIO]），让每个音的起音清晰可辨。
 *
 * 渲染流程：
 * 1. [computeOnsetTimes] 计算每个音符的绝对时间戳
 * 2. 在各时间戳叠加对应音高的波形 + 软限幅
 *
 * @param sampleRate 采样率
 */
class ContourAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /** 为题目渲染音频（含软限幅，输出范围 [-1, 1]）。 */
    fun render(question: ContourQuestion): FloatArray {
        val raw = renderRaw(question)
        for (i in raw.indices) {
            raw[i] = softClip(raw[i])
        }
        return raw
    }

    /**
     * 为题目渲染音频（不限幅，用于测试验证能量/频率特征）。
     */
    fun renderRaw(question: ContourQuestion): FloatArray {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty() || question.pitches.isEmpty()) return FloatArray(0)

        val noteLengthMs = question.noteDurationMs * (1.0 - GAP_RATIO)
        val noteSamples = (sampleRate * noteLengthMs / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)

        for (i in onsets.indices) {
            val midi = question.pitches[i]
            val freq = midiToFreq(midi)
            val wave = generateNote(noteSamples, freq, NOTE_AMPLITUDE)
            val offset = (onsets[i] * sampleRate / 1000.0).toInt()
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
                }
            }
        }

        return output
    }

    /**
     * 计算每个音符的绝对时间戳（毫秒）。
     *
     * 时序：前导静音 → 每个音符按 [ContourQuestion.noteDurationMs] 等间距排列。
     */
    fun computeOnsetTimes(question: ContourQuestion): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in question.pitches.indices) {
            onsets.add(LEAD_SILENCE_MS + i * question.noteDurationMs)
        }
        return onsets
    }

    /** 预估渲染时长（毫秒，保留小数以与 [renderRaw] 的样本计算一致）。 */
    fun estimateDurationMs(question: ContourQuestion): Double {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty()) return 0.0
        val noteLengthMs = question.noteDurationMs * (1.0 - GAP_RATIO)
        return onsets.last() + noteLengthMs + TAIL_SILENCE_MS
    }

    /**
     * 计算 MIDI 音高对应的频率（Hz）。
     *
     * 使用 A4=440Hz、十二平均律：f = 440 × 2^((midi - 69) / 12)。
     */
    fun midiToFreq(midi: Int): Double = A4_FREQUENCY * 2.0.pow((midi - A4_MIDI).toDouble() / 12.0)

    /**
     * 计算指定音符索引在输出缓冲区中的能量（RMS）。
     *
     * 用于单元测试验证各音符能量大致相当（无某个音异常响/异常轻）。
     */
    fun noteRmsEnergy(buffer: FloatArray, question: ContourQuestion, noteIndex: Int): Double {
        val onsets = computeOnsetTimes(question)
        if (noteIndex !in onsets.indices) return 0.0
        val noteLengthMs = question.noteDurationMs * (1.0 - GAP_RATIO)
        val onsetSample = (onsets[noteIndex] * sampleRate / 1000.0).toInt()
        val windowSamples = (sampleRate * noteLengthMs / 1000.0).toInt()
        var sumSq = 0.0
        var count = 0
        for (i in onsetSample until minOf(onsetSample + windowSamples, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) sqrt(sumSq / count) else 0.0
    }

    /**
     * 估算指定音符窗口内的主导频率（通过过零率近似）。
     *
     * 用于单元测试验证音高单调性（上行旋律的主导频率应递增）。
     */
    fun estimateDominantFrequency(buffer: FloatArray, question: ContourQuestion, noteIndex: Int): Double {
        val onsets = computeOnsetTimes(question)
        if (noteIndex !in onsets.indices) return 0.0
        val noteLengthMs = question.noteDurationMs * (1.0 - GAP_RATIO)
        val onsetSample = (onsets[noteIndex] * sampleRate / 1000.0).toInt()
        val windowSamples = (sampleRate * noteLengthMs / 1000.0).toInt()
        val endSample = minOf(onsetSample + windowSamples, buffer.size)
        var zeroCrossings = 0
        for (i in onsetSample + 1 until endSample) {
            if ((buffer[i] >= 0.0f && buffer[i - 1] < 0.0f) || (buffer[i] < 0.0f && buffer[i - 1] >= 0.0f)) {
                zeroCrossings++
            }
        }
        val durationSec = (endSample - onsetSample).toDouble() / sampleRate
        return if (durationSec > 0) zeroCrossings.toDouble() / (2.0 * durationSec) else 0.0
    }

    /**
     * 生成单个音符波形（加法合成：基频 + 4 谐波，指数衰减包络）。
     */
    private fun generateNote(
        numSamples: Int,
        frequency: Double,
        amplitude: Float
    ): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * DECAY_TIME_CONSTANT_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val fundamental = sin(2.0 * PI * frequency * t)
            val h2 = sin(2.0 * PI * frequency * 2.0 * t) * HARMONIC_2_GAIN
            val h3 = sin(2.0 * PI * frequency * 3.0 * t) * HARMONIC_3_GAIN
            val h4 = sin(2.0 * PI * frequency * 4.0 * t) * HARMONIC_4_GAIN
            val h5 = sin(2.0 * PI * frequency * 5.0 * t) * HARMONIC_5_GAIN
            val sample = (fundamental + h2 + h3 + h4 + h5) * envelope * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    /** 软限幅函数。 */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** A4 音高 MIDI 值。 */
        const val A4_MIDI = 69

        /** A4 频率（Hz）。 */
        const val A4_FREQUENCY = 440.0

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 450.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 音符间间隙占音符时长的比例（让起音清晰）。 */
        const val GAP_RATIO = 0.12

        /** 音符振幅。 */
        const val NOTE_AMPLITUDE = 0.45f

        /** 2 次谐波增益。 */
        const val HARMONIC_2_GAIN = 0.35

        /** 3 次谐波增益。 */
        const val HARMONIC_3_GAIN = 0.20

        /** 4 次谐波增益。 */
        const val HARMONIC_4_GAIN = 0.12

        /** 5 次谐波增益。 */
        const val HARMONIC_5_GAIN = 0.06

        /** 指数衰减时间常数（毫秒）。 */
        const val DECAY_TIME_CONSTANT_MS = 180.0

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
