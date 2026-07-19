package com.pianocompanion.swingfeel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 摇摆感辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [SwingQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 摇摆感听辨：**
 *
 * 渲染一段由若干拍构成的节奏：**每拍固定两个八分音符**，所有音符**同音高、同音量**。
 * 唯一的变量是**第二个音符在一拍中的时间占比** [SwingQuestion.swingFraction]：
 * - 0.5 → 两音均等（等分 / straight）
 * - 0.60 → 轻微长短（轻摇摆 3:2）
 * - 0.667 → 明显长短（摇摆 2:1）
 *
 * 关键设计：
 * - **单一音高 + 短促断奏（staccato）**：所有音符音高音量相同，使「长短时间比例」成为唯一显著线索；
 *   短促断奏 + 音符间明显间隙，让「长—短」的律动清晰可辨。
 * - **加法合成钢琴音色**：每个音 = 基频 + 4 个谐波（2f/3f/4f/5f，递减振幅）+ 指数衰减包络。
 *
 * 时间结构（设一拍时长为 B，第二音占比为 f）：
 * - 第 i 拍（i = 0..beats-1）的两个起音：
 *   - o₁ = LEAD + i·B
 *   - o₂ = LEAD + i·B + f·B
 * - 连续起音间距（IOI）交替：拍内（o₁→o₂）= f·B（长），跨拍（o₂→下一拍 o₁）= (1−f)·B（短）。
 *
 * @param sampleRate 采样率
 */
class SwingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /** 为题目渲染音频（含软限幅，输出范围 [-1, 1]）。 */
    fun render(question: SwingQuestion): FloatArray {
        val raw = renderRaw(question)
        for (i in raw.indices) {
            raw[i] = softClip(raw[i])
        }
        return raw
    }

    /** 为题目渲染音频（不限幅，用于测试验证时间/频率特征）。 */
    fun renderRaw(question: SwingQuestion): FloatArray {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty()) return FloatArray(0)

        val noteSamples = (sampleRate * noteDurationMs(question) / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)
        val freq = midiToFreq(NOTE_MIDI)

        for (onsetMs in onsets) {
            val wave = generateNote(noteSamples, freq, NOTE_AMPLITUDE)
            val offset = (onsetMs * sampleRate / 1000.0).toInt()
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
     * 计算每个八分音符的绝对时间戳（毫秒）。
     *
     * 每拍两个起音：拍头 + 拍头后 [SwingQuestion.swingFraction]·beatMs。
     */
    fun computeOnsetTimes(question: SwingQuestion): List<Double> {
        val onsets = mutableListOf<Double>()
        val beatMs = question.beatMs
        val f = question.swingFraction
        for (i in 0 until question.beatsPerQuestion) {
            onsets.add(LEAD_SILENCE_MS + i * beatMs)
            onsets.add(LEAD_SILENCE_MS + i * beatMs + f * beatMs)
        }
        return onsets
    }

    /**
     * 计算连续起音间距序列（毫秒）。
     *
     * - 等分：所有间距相等（= 0.5·beatMs）。
     * - 摇摆：间距交替（长 f·beatMs，短 (1−f)·beatMs）。
     */
    fun computeInterOnsetIntervals(question: SwingQuestion): List<Double> {
        val onsets = computeOnsetTimes(question)
        val iois = mutableListOf<Double>()
        for (i in 1 until onsets.size) {
            iois.add(onsets[i] - onsets[i - 1])
        }
        return iois
    }

    /**
     * 计算长短比（最长间距 / 最短间距）。
     *
     * - 等分 → 1.0；轻摇摆 → 1.5；摇摆 → 2.0。
     */
    fun swingRatio(question: SwingQuestion): Double {
        val iois = computeInterOnsetIntervals(question)
        if (iois.isEmpty()) return 1.0
        val maxIoi = iois.max()
        val minIoi = iois.min()
        return if (minIoi <= 0.0) 1.0 else maxIoi / minIoi
    }

    /** 每个八分音符的持续时间（毫秒），短促断奏。 */
    private fun noteDurationMs(question: SwingQuestion): Double =
        question.beatMs * NOTE_DURATION_RATIO

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: SwingQuestion): Double {
        val onsets = computeOnsetTimes(question)
        if (onsets.isEmpty()) return 0.0
        return onsets.last() + noteDurationMs(question) + TAIL_SILENCE_MS
    }

    /**
     * 计算 MIDI 音高对应的频率（Hz）。
     *
     * 使用 A4=440Hz、十二平均律：f = 440 × 2^((midi - 69) / 12)。
     */
    fun midiToFreq(midi: Int): Double = A4_FREQUENCY * 2.0.pow((midi - A4_MIDI).toDouble() / 12.0)

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

        /** 所有音符的固定 MIDI 音高（C5）。 */
        const val NOTE_MIDI = 72

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 450.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 350.0

        /** 八分音符时长占一拍的比例（短促断奏；需小于最短起音间距 (1−f)·beatMs）。 */
        const val NOTE_DURATION_RATIO = 0.22

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
        const val DECAY_TIME_CONSTANT_MS = 120.0

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
