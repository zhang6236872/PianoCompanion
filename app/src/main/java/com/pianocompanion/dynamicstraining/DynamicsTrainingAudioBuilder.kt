package com.pianocompanion.dynamicstraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 力度辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [DynamicsTrainingQuestion] 的力度渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 与速度辨识不同，力度辨识的核心是**振幅（即响度）**而非间距或音高。因此：
 * - 所有题目的**音高和节奏完全相同**（C 大调琶音 C4-E4-G4-C5），
 *   唯一区分依据是整体振幅水平
 * - 音符使用钢琴风格的加法合成（基频 + 谐波）+ 指数衰减包络
 * - 力度越强振幅越大（pp=0.15 → ff=0.95）
 *
 * 渲染流程：
 * 1. 用 [computeOnsetTimes] 计算每个音符的时间戳
 * 2. 将时间戳转换为采样偏移
 * 3. 在每个偏移处叠加该音符的波形（乘以力度振幅）
 *
 * @param sampleRate 采样率
 */
class DynamicsTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: DynamicsTrainingQuestion): FloatArray {
        return renderDynamic(
            dynamic = question.dynamic,
            noteCount = question.noteCount
        )
    }

    /**
     * 将力度渲染为连续 PCM 采样。
     *
     * @param dynamic 力度类型
     * @param noteCount 音符数量
     * @return PCM Float 缓冲区
     */
    fun renderDynamic(
        dynamic: DynamicLevel,
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): FloatArray {
        val onsets = computeOnsetTimes(noteCount)
        if (onsets.isEmpty()) return FloatArray(0)

        val noteSamples = (sampleRate * NOTE_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)

        // 为每个音符生成波形并叠加
        for ((index, onset) in onsets.withIndex()) {
            val freq = ARPPEGIO_FREQS[index % ARPPEGIO_FREQS.size]
            val noteWave = generateNote(freq, noteSamples)
            val offset = (onset * sampleRate / 1000.0).toInt()
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
            }
        }

        // 统一缩放到力度振幅水平
        val amplitude = dynamic.amplitude
        for (i in output.indices) {
            var sample = output[i] * amplitude
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
     * 与 [DynamicsTrainingEngine.computeOnsetTimes] 逻辑一致，独立实现以保持自包含性。
     */
    fun computeOnsetTimes(
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in 0 until noteCount) {
            onsets.add(LEAD_SILENCE_MS + i * NOTE_DURATION_MS)
        }
        return onsets
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: DynamicsTrainingQuestion): Long {
        val onsets = computeOnsetTimes(question.noteCount)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + NOTE_DURATION_MS.toLong() + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     *
     * 基频 + 5 个谐波（幅度递减），模拟钢琴音色。
     * 输出已归一化到 [-1, 1]，调用方按力度振幅缩放。
     */
    private fun generateNote(frequency: Double, numSamples: Int): FloatArray {
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * NOTE_DECAY_MS / 1000.0

        // 先在临时缓冲区合成原始波形
        val raw = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            // 钢琴风格：基频 + 递减谐波
            val sample = (
                sin(2.0 * PI * frequency * t) * HARMONICS[0] +
                sin(2.0 * PI * frequency * 2 * t) * HARMONICS[1] +
                sin(2.0 * PI * frequency * 3 * t) * HARMONICS[2] +
                sin(2.0 * PI * frequency * 4 * t) * HARMONICS[3] +
                sin(2.0 * PI * frequency * 5 * t) * HARMONICS[4]
            ) * envelope
            raw[i] = sample.toFloat()
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

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 400.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 200.0

        /** 默认音符数量。 */
        const val DEFAULT_NOTE_COUNT = 4

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)

        /** C 大调琶音频率（C4-E4-G4-C5）。 */
        private val ARPPEGIO_FREQS = doubleArrayOf(
            261.63, // C4
            329.63, // E4
            392.00, // G4
            523.25  // C5
        )
    }
}
