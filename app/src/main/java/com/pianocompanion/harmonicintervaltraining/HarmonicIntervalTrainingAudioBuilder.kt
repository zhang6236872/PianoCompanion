package com.pianocompanion.harmonicintervaltraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 和声音程辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [HarmonicIntervalQuestion] 的音程渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 和声音程色彩感知：**
 * - 两个音**同时**响起，产生和声效果——用户需要辨识这个"和声块"的色彩
 * - 下方音固定为 C4（MIDI 60, 261.63Hz），上方音 = C4 + interval.semitones
 * - 两个音使用钢琴风格加法合成（基频 + 谐波）+ 指数衰减包络
 * - 两个音的波形在时间上**完全对齐**（同时起始、同时衰减），然后叠加
 * - 不同半音数的音程产生不同的拍频（beating）和协和/不协和感
 * - 三全音（6 半音）会产生强烈的拍频和紧张感
 * - 纯八度（12 半音）几乎完全融合，听起来像一个音
 *
 * @param sampleRate 采样率
 */
class HarmonicIntervalAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: HarmonicIntervalQuestion): FloatArray {
        return renderInterval(
            lowerMidi = question.lowerMidi,
            semitones = question.interval.semitones
        )
    }

    /**
     * 将和声音程渲染为连续 PCM 采样。
     *
     * @param lowerMidi 下方音 MIDI 编号
     * @param semitones 音程半音数
     * @return PCM Float 缓冲区
     */
    fun renderInterval(
        lowerMidi: Int = HarmonicIntervalQuestion.DEFAULT_LOWER_MIDI,
        semitones: Int
    ): FloatArray {
        val lowerFreq = midiToFreq(lowerMidi)
        val upperFreq = midiToFreq(lowerMidi + semitones)

        val noteSamples = (sampleRate * DURATION_MS / 1000.0).toInt()
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + noteSamples + tailSamples
        val output = FloatArray(totalLength)

        // 生成下方音和上方音的独立波形
        val lowerWave = generateNote(lowerFreq, noteSamples)
        val upperWave = generateNote(upperFreq, noteSamples)

        // 将两个音叠加（和声音程：同时响起）
        val offset = leadSamples
        for (j in 0 until noteSamples) {
            val outIdx = offset + j
            if (outIdx in output.indices) {
                output[outIdx] = (lowerWave[j] + upperWave[j]) * 0.5f
            }
        }

        // 归一化并应用主振幅
        val maxAbs = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) MASTER_AMPLITUDE / maxAbs else MASTER_AMPLITUDE
        for (i in output.indices) {
            var sample = output[i] * norm
            // 软限幅保护
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * MIDI 编号转频率（A4=440Hz, MIDI=69）。
     *
     * freq = 440 × 2^((midi-69)/12)
     */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(): Long {
        return (LEAD_SILENCE_MS + DURATION_MS + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     *
     * 基频 + 5 个谐波（幅度递减），模拟钢琴音色。
     * 高于奈奎斯特频率的谐波自动跳过以避免混叠。
     * 输出已归一化到 [-1, 1]。
     */
    private fun generateNote(frequency: Double, numSamples: Int): FloatArray {
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * NOTE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        // 先在临时缓冲区合成原始波形
        val raw = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            // 钢琴风格：基频 + 递减谐波
            var sample = 0.0
            for (h in HARMONICS.indices) {
                val freq = frequency * (h + 1)
                // 高于奈奎斯特频率的谐波跳过（防止混叠）
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * HARMONICS[h]
            }
            raw[i] = (sample * envelope).toFloat()
        }

        // 归一化到 [-1, 1]（按最大绝对值缩放）
        val maxAbs = raw.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) 1.0f / maxAbs else 1.0f
        for (i in raw.indices) {
            wave[i] = raw[i] * norm
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 和声音持续时间（毫秒）。 */
        const val DURATION_MS = 1200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 400.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
