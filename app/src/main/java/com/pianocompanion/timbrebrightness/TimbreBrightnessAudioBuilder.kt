package com.pianocompanion.timbrebrightness

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 音色亮度辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TimbreBrightnessQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 音色亮度听辨：**
 *
 * 渲染一个**固定音高的单音**，其泛音（谐波）数量与强度决定亮度。
 * 不同亮度等级的区别**完全来自泛音结构**（谐波的个数与衰减速率），而非音高、
 * 持续时间或响度——这些在所有题目中保持一致。因此「音色亮度」成为音频中唯一显著的特征。
 *
 * **泛音合成**：
 * - 基频（k=1）幅度恒为 1.0
 * - 第 k 泛音幅度 = `harmonicStrength^(k-1)`，即等比衰减
 * - PURE 无泛音（纯正弦）；BRILLIANT 有 10 个强泛音（strength=0.75 衰减极慢）
 *
 * **RMS 响度归一化**（关键设计）：渲染后对所有亮度等级进行相同的 RMS 归一化，
 * 使感知响度一致——**响度不作为辨识线索**，唯一可辨别的特征是频谱形状（亮度）。
 *
 * 为帮助听辨，单音会**播放两遍**（中间留一段静音间隔），给耳朵两次感知机会。
 *
 * 每个音使用指数衰减包络（钢琴风格起音-衰减），最后 tanh 软限幅。
 *
 * @param sampleRate 采样率
 */
class TimbreBrightnessAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单次鸣响事件。
     *
     * @param fundamentalMidi 基频 MIDI 音高
     * @param brightness 亮度等级
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     */
    data class ToneEvent(
        val fundamentalMidi: Int,
        val brightness: TimbreBrightness,
        val onsetMs: Double,
        val durationMs: Double
    )

    /** 为题目渲染音频。 */
    fun render(question: TimbreBrightnessQuestion): FloatArray {
        val events = buildToneEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的单音鸣响事件序列。
     *
     * 单音播放两遍：第一次 onset=0，第二次 onset=TONE_DURATION_MS + GAP_MS。
     */
    fun buildToneEvents(question: TimbreBrightnessQuestion): List<ToneEvent> {
        return listOf(
            ToneEvent(question.fundamentalMidi, question.brightness, FIRST_ONSET_MS, TONE_DURATION_MS),
            ToneEvent(question.fundamentalMidi, question.brightness, SECOND_ONSET_MS, TONE_DURATION_MS)
        )
    }

    /**
     * 计算两次鸣响的起始时间（毫秒），公开以便单元测试验证。
     */
    fun computeOnsets(question: TimbreBrightnessQuestion): DoubleArray =
        doubleArrayOf(FIRST_ONSET_MS, SECOND_ONSET_MS)

    /** 计算基频的频率（用于测试验证）。 */
    fun noteFrequency(midi: Int): Double = midiToFreq(midi)

    /**
     * 计算指定亮度等级的泛音幅度序列（基频 + harmonicCount 个泛音）。
     * 公开以便单元测试验证泛音结构。
     *
     * @return 幅度数组，索引 0 = 基频，索引 k = 第 (k+1) 泛音
     */
    fun harmonicAmplitudes(brightness: TimbreBrightness): DoubleArray {
        if (brightness.harmonicCount == 0) return doubleArrayOf(1.0)
        val amps = DoubleArray(brightness.harmonicCount + 1)
        amps[0] = 1.0 // 基频
        for (k in 1..brightness.harmonicCount) {
            amps[k] = Math.pow(brightness.harmonicStrength, k.toDouble())
        }
        return amps
    }

    // ── 渲染 ──────────────────────────────────────────

    /** 将单音鸣响事件列表渲染为连续 PCM 采样。 */
    fun renderEvents(events: List<ToneEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐鸣响合成并叠加
        for (event in events) {
            val toneSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val toneWave = generateTone(event.fundamentalMidi, event.brightness, toneSamples)
            for (j in toneWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += toneWave[j]
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

    /** MIDI 编号转频率（A4=440Hz, MIDI=69）。 */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: TimbreBrightnessQuestion): Long {
        val events = buildToneEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个单音波形（基频 + 泛音 + 指数衰减包络 + RMS 归一化）。
     *
     * **关键**：RMS 归一化保证不同亮度等级的感知响度一致，使亮度成为唯一可辨别特征。
     */
    private fun generateTone(fundamentalMidi: Int, brightness: TimbreBrightness, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * TONE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0
        val fundamentalFreq = midiToFreq(fundamentalMidi)

        // 获取泛音幅度序列
        val amps = harmonicAmplitudes(brightness)

        // 第一遍：合成原始波形（基频 + 泛音 × 衰减包络）
        var maxAmplitude = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (k in amps.indices) {
                val freq = fundamentalFreq * (k + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * amps[k]
            }
            sample *= envelope
            wave[i] = sample.toFloat()
            if (kotlin.math.abs(sample) > maxAmplitude) {
                maxAmplitude = kotlin.math.abs(sample)
            }
        }

        // RMS 归一化：使感知响度在不同亮度等级间一致
        var sumSq = 0.0
        for (i in 0 until numSamples) {
            sumSq += wave[i].toDouble() * wave[i].toDouble()
        }
        val rms = kotlin.math.sqrt(sumSq / numSamples)
        if (rms > 1e-9) {
            val scaleFactor = TARGET_RMS / rms
            for (i in 0 until numSamples) {
                wave[i] = (wave[i] * scaleFactor * TONE_GAIN).toFloat()
            }
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 单次单音持续时间（毫秒）。 */
        const val TONE_DURATION_MS = 900.0

        /** 单音衰减时间常数（毫秒）—— 较长，让泛音充分展开。 */
        const val TONE_DECAY_MS = 500.0

        /** 第一次鸣响起始时间（毫秒）。 */
        const val FIRST_ONSET_MS = 0.0

        /** 两次鸣响之间的间隔（毫秒）。 */
        const val GAP_MS = 250.0

        /** 第二次鸣响起始时间（毫秒）。 */
        val SECOND_ONSET_MS: Double = TONE_DURATION_MS + GAP_MS

        /** 单音总增益。 */
        const val TONE_GAIN = 0.7f

        /** RMS 归一化目标值（保证感知响度一致）。 */
        const val TARGET_RMS = 0.15
    }
}
