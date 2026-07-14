package com.pianocompanion.melodicdirectiontraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 旋律方向辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [MelodicDirectionQuestion] 的旋律方向渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 旋律轮廓感知：**
 * - 所有题目播放 4 音符旋律，音高轮廓随方向类型变化
 * - 上行：C-D-E-G（持续升高）
 * - 下行：G-E-D-C（持续降低）
 * - 平行：C-C-C-C（保持不变）
 * - 拱形：C-E-G-E（先升后降）
 * - V形：G-E-C-E（先降后升）
 * - 音符使用钢琴风格的加法合成（基频 + 谐波）+ 指数衰减包络
 * - 不同方向的旋律具有不同的整体音高变化趋势
 *
 * @param sampleRate 采样率
 */
class MelodicDirectionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: MelodicDirectionQuestion): FloatArray {
        return renderDirection(question.direction)
    }

    /**
     * 将旋律方向渲染为连续 PCM 采样。
     *
     * @param direction 旋律方向类型
     * @return PCM Float 缓冲区
     */
    fun renderDirection(direction: MelodicDirection): FloatArray {
        val offsets = direction.semitoneOffsets
        val noteCount = offsets.size
        if (noteCount == 0) return FloatArray(0)

        val noteSamples = (sampleRate * NOTE_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = LEAD_SILENCE_SAMPLES + noteSamples * noteCount + tailSamples
        val output = FloatArray(totalLength)

        // 为每个音符生成波形并放置
        for (i in 0 until noteCount) {
            val freq = midiToFrequency(BASE_MIDI + offsets[i])
            val noteWave = generateNote(freq, noteSamples)
            val offset = LEAD_SILENCE_SAMPLES + i * noteSamples
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
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

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(direction: MelodicDirection): Long {
        val noteCount = direction.semitoneOffsets.size
        return (LEAD_SILENCE_MS + noteCount * NOTE_DURATION_MS + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 计算指定方向的各音符频率（用于测试验证）。
     */
    fun computeFrequencies(direction: MelodicDirection): DoubleArray {
        return direction.semitoneOffsets
            .map { midiToFrequency(BASE_MIDI + it) }
            .toDoubleArray()
    }

    /**
     * 将 MIDI 音符号转换为频率（Hz）。
     * 标准 A4 = MIDI 69 = 440Hz
     */
    private fun midiToFrequency(midi: Int): Double {
        return 440.0 * 2.0.pow((midi - 69.0) / 12.0)
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     *
     * 基频 + 5 个谐波（幅度递减），模拟钢琴音色。
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

        /** 基准 MIDI 音符号（C4 = 60）。 */
        const val BASE_MIDI = 60

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 前导静音采样数。 */
        val LEAD_SILENCE_SAMPLES = (DEFAULT_SAMPLE_RATE * LEAD_SILENCE_MS / 1000.0).toInt()

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 400.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 200.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
