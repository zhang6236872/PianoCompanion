package com.pianocompanion.harmonicseries

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 泛音列辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [HarmonicSeriesQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 泛音列听辨：**
 *
 * 渲染两段音频：
 * 1. **基频段**：以基频播放一个**复合音色**（基频 + 5 阶谐波叠加），使其听起来像
 *    自然乐器而非干瘪的纯音——帮助用户的耳朵「锚定」基频音高。
 * 2. **间隔**：短暂的静默，让用户区分两段。
 * 3. **泛音段**：以目标泛音的**纯音**（纯正弦波）播放——用户需要判断这个纯音在
 *    泛音列中排第几。
 *
 * **音频设计理由**：
 * - 基频用复合音色（而非纯音）：真实的乐音总是含泛音的，用户更容易在复合音中
 *   感知到基频音高（残留音高效应），这是训练听辨泛音列的起点。
 * - 泛音用纯音（而非复合音）：隔绝其他泛音的干扰，让用户专注于「这个频率是基频的
 *   几倍」，纯粹测试频率比的感知能力。
 * - 泛音频率 = 基频 × 泛音阶数（纯律精确整数倍，非十二平均律近似）。
 *
 * @param sampleRate 采样率
 */
class HarmonicSeriesAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（用于测试验证）。
     *
     * @param frequencyHz 频率（赫兹）
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param isFundamental 是否为基频段（true=复合音色，false=泛音纯音）
     * @param harmonicNumber 泛音阶数（泛音段的标注用）
     */
    data class ToneEvent(
        val frequencyHz: Double,
        val onsetMs: Double,
        val durationMs: Double,
        val isFundamental: Boolean,
        val harmonicNumber: Int
    )

    /** 为题目渲染音频。 */
    fun render(question: HarmonicSeriesQuestion): FloatArray {
        val events = buildToneEvents(question)
        return renderEvents(events, question)
    }

    /**
     * 构建题目的全部音符事件。
     *
     * 包含两段：
     * - 基频段：复合音色（基频 + 5 阶谐波），起始时间 = 0
     * - 泛音段：纯音，起始时间 = fundamentalDurationMs + gapMs
     */
    fun buildToneEvents(question: HarmonicSeriesQuestion): List<ToneEvent> {
        val d = question.difficulty
        val fundamentalFreq = midiToFreq(d.fundamentalMidi)
        val harmonicFreq = fundamentalFreq * question.targetHarmonic.ratio
        val harmonicOnset = d.fundamentalDurationMs + d.gapMs

        return listOf(
            ToneEvent(
                frequencyHz = fundamentalFreq,
                onsetMs = 0.0,
                durationMs = d.fundamentalDurationMs.toDouble(),
                isFundamental = true,
                harmonicNumber = 1
            ),
            ToneEvent(
                frequencyHz = harmonicFreq,
                onsetMs = harmonicOnset.toDouble(),
                durationMs = d.harmonicDurationMs.toDouble(),
                isFundamental = false,
                harmonicNumber = question.targetHarmonic.number
            )
        )
    }

    /** 计算目标泛音的频率（赫兹），公开以便测试验证。 */
    fun targetHarmonicFrequency(question: HarmonicSeriesQuestion): Double {
        return midiToFreq(question.difficulty.fundamentalMidi) * question.targetHarmonic.ratio
    }

    /** 计算基频频率（赫兹）。 */
    fun fundamentalFrequency(question: HarmonicSeriesQuestion): Double {
        return midiToFreq(question.difficulty.fundamentalMidi)
    }

    /** 计算音频总时长（毫秒，不含前后静音）。 */
    fun musicDurationMs(question: HarmonicSeriesQuestion): Double {
        val events = buildToneEvents(question)
        return if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
    }

    /** 计算 MIDI 音高的频率（A4=440Hz, 十二平均律）。 */
    fun midiToFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样。
     *
     * 基频段使用复合音色（5 阶谐波叠加 + 指数衰减包络 + tanh 软限幅），
     * 泛音段使用纯正弦波（指数衰减包络）。
     */
    fun renderEvents(events: List<ToneEvent>, question: HarmonicSeriesQuestion): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val noteWave = if (event.isFundamental) {
                generateComplexTone(event.frequencyHz, noteSamples)
            } else {
                generatePureTone(event.frequencyHz, noteSamples)
            }
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
            }
        }

        // tanh 软限幅防止削波
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble()).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /** 预估渲染时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: HarmonicSeriesQuestion): Long {
        val musicMs = musicDurationMs(question)
        return (LEAD_SILENCE_MS + musicMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成复合音色波形（基频 + 5 阶谐波叠加 + 指数衰减包络）。
     *
     * 用于基频段——模拟自然乐器的泛音结构，帮助用户锚定基频音高。
     */
    private fun generateComplexTone(fundamentalFreq: Double, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * TONE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (h in COMPLEX_HARMONICS.indices) {
                val freq = fundamentalFreq * (h + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * COMPLEX_HARMONICS[h]
            }
            wave[i] = (sample * envelope * COMPLEX_GAIN).toFloat()
        }
        return wave
    }

    /**
     * 生成纯音波形（纯正弦波 + 指数衰减包络）。
     *
     * 用于泛音段——隔绝其他频率成分，纯粹测试频率比感知。
     */
    private fun generatePureTone(frequency: Double, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * TONE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0
        if (frequency >= nyquist) return FloatArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            wave[i] = (sin(2.0 * PI * frequency * t) * envelope * PURE_GAIN).toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 150.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 音符衰减时间常数（毫秒）。 */
        const val TONE_DECAY_MS = 320.0

        /** 复合音色增益。 */
        const val COMPLEX_GAIN = 0.28f

        /** 纯音增益。 */
        const val PURE_GAIN = 0.38f

        /** 复合音色谐波幅度（基频 + 5 个谐波，1/n 衰减）。 */
        private val COMPLEX_HARMONICS = doubleArrayOf(1.0, 0.5, 0.333, 0.25, 0.2, 0.167)
    }
}
