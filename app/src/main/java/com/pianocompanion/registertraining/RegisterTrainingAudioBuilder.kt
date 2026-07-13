package com.pianocompanion.registertraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 音区辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [RegisterTrainingQuestion] 的音区渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 八度定位感知：**
 * - 所有题目播放相同的 C 大调琶音（C-E-G-C），但位于**不同的八度区域**
 * - 唯一区分依据是**整体音高范围**——训练对音区（register）的感知能力
 * - 音符使用钢琴风格的加法合成（基频 + 谐波）+ 指数衰减包络
 * - 不同音区的琶音基频不同：从 C2（65.41Hz）到 C7（2093Hz）
 * - 极高音区的部分高次谐波会超过奈奎斯特频率，自动跳过以避免混叠
 *
 * @param sampleRate 采样率
 */
class RegisterTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: RegisterTrainingQuestion): FloatArray {
        return renderRegister(
            register = question.register,
            noteCount = question.noteCount
        )
    }

    /**
     * 将音区渲染为连续 PCM 采样。
     *
     * @param register 音区类型
     * @param noteCount 音符数量
     * @return PCM Float 缓冲区
     */
    fun renderRegister(
        register: MusicRegister,
        noteCount: Int = RegisterTrainingEngine.DEFAULT_NOTE_COUNT
    ): FloatArray {
        val onsets = computeOnsetTimes(noteCount)
        if (onsets.isEmpty()) return FloatArray(0)

        val noteSamples = (sampleRate * NOTE_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)

        // 为每个音符生成波形并叠加
        val freqs = register.arpeggioFrequencies
        for ((index, onset) in onsets.withIndex()) {
            val freq = freqs[index % freqs.size]
            val noteWave = generateNote(freq, noteSamples)
            val offset = (onset * sampleRate / 1000.0).toInt()
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

    /**
     * 计算每个音符的绝对时间戳（毫秒）。
     *
     * 与 [RegisterTrainingEngine.computeOnsetTimes] 逻辑一致，独立实现以保持自包含性。
     */
    fun computeOnsetTimes(
        noteCount: Int = RegisterTrainingEngine.DEFAULT_NOTE_COUNT
    ): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in 0 until noteCount) {
            onsets.add(RegisterTrainingEngine.LEAD_SILENCE_MS + i * NOTE_DURATION_MS)
        }
        return onsets
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: RegisterTrainingQuestion): Long {
        val onsets = computeOnsetTimes(question.noteCount)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + NOTE_DURATION_MS.toLong() + TAIL_SILENCE_MS.toLong()
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
