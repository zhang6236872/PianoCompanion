package com.pianocompanion.articulationtraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.min

/**
 * 演奏法辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ArticulationTrainingQuestion] 的演奏法渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 与力度辨识不同，演奏法辨识的核心是**音符的持续时间占比、起音速度和包络形状**。
 * 所有题目的**音高和节拍间距完全相同**（C 大调五声音阶 C4-D4-E4-G4-A4），唯一区分依据是：
 * - **持续时间占比**（durationRatio）：连音 >1.0（微重叠）、断音 ~0.3（短促）、保持音 ~0.95（几乎满拍）
 * - **起音速度**（attackMs）：重音 ~1ms（极锐利）、连音 ~25ms（平滑）、断音 ~2ms（锐利）
 * - **衰减时间常数**（decayTimeConstantMs）：连音 ~400ms（持续）、断音 ~80ms（快速消失）
 * - **重音强调**（accent）：重音 ~0.7（前 50ms 强烈冲击），其他接近 0
 *
 * 渲染流程：
 * 1. 用 [computeOnsetTimes] 计算每个音符的时间戳
 * 2. 每个音符的实际持续时间 = 节拍间距 × durationRatio
 * 3. 为每个音符合成带 ADSR 风格包络的波形，按起音偏移叠加
 *
 * @param sampleRate 采样率
 */
class ArticulationTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: ArticulationTrainingQuestion): FloatArray {
        return renderArticulation(
            articulation = question.articulation,
            noteCount = question.noteCount
        )
    }

    /**
     * 将演奏法渲染为连续 PCM 采样。
     *
     * @param articulation 演奏法类型
     * @param noteCount 音符数量
     * @return PCM Float 缓冲区
     */
    fun renderArticulation(
        articulation: ArticulationType,
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): FloatArray {
        val onsets = computeOnsetTimes(noteCount)
        if (onsets.isEmpty()) return FloatArray(0)

        // 每个音符的实际持续时间（毫秒）= 节拍间距 × durationRatio
        val noteDurationMs = NOTE_SPACING_MS * articulation.durationRatio
        val noteSamples = (sampleRate * noteDurationMs / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + noteSamples + tailSamples

        val output = FloatArray(totalLength)

        // 为每个音符生成带包络的波形并叠加
        for ((index, onset) in onsets.withIndex()) {
            val freq = SCALE_FREQS[index % SCALE_FREQS.size]
            val noteWave = generateNote(freq, noteSamples, articulation)
            val offset = (onset * sampleRate / 1000.0).toInt()
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
            }
        }

        // 软限幅保护（连音重叠可能超过 1.0）
        var maxAbs = 0.0f
        for (sample in output) {
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
        }
        if (maxAbs > 1.0f) {
            val norm = 1.0f / maxAbs
            for (i in output.indices) {
                output[i] *= norm
            }
        }

        return output
    }

    /**
     * 计算每个音符的绝对时间戳（毫秒）。
     *
     * 与 [ArticulationTrainingEngine.computeOnsetTimes] 逻辑一致，独立实现以保持自包含性。
     */
    fun computeOnsetTimes(
        noteCount: Int = DEFAULT_NOTE_COUNT
    ): List<Double> {
        val onsets = mutableListOf<Double>()
        for (i in 0 until noteCount) {
            onsets.add(LEAD_SILENCE_MS + i * NOTE_SPACING_MS)
        }
        return onsets
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: ArticulationTrainingQuestion): Long {
        val onsets = computeOnsetTimes(question.noteCount)
        if (onsets.isEmpty()) return 0L
        val noteDurationMs = (NOTE_SPACING_MS * question.articulation.durationRatio).toLong()
        return onsets.last().toLong() + noteDurationMs + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + ADSR 风格包络）。
     *
     * 包络模型：
     * - **Attack**：线性上升，时长 = articulation.attackMs
     * - **Decay/Sustain**：指数衰减，时间常数 = articulation.decayTimeConstantMs
     * - **Accent**（Marcato 专用）：前 ACCENT_DURATION_MS 内幅度额外增强
     *
     * 输出已归一化到 [-1, 1]。
     *
     * @param frequency 基频
     * @param numSamples 音符采样数
     * @param articulation 演奏法（决定包络形状）
     */
    private fun generateNote(
        frequency: Double,
        numSamples: Int,
        articulation: ArticulationType
    ): FloatArray {
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * articulation.attackMs / 1000.0).toInt()
        val decaySamples = sampleRate * articulation.decayTimeConstantMs / 1000.0
        val accentSamples = (sampleRate * ACCENT_DURATION_MS / 1000.0).toInt()

        // 先在临时缓冲区合成原始波形
        val raw = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // ── 包络 ──
            // Attack：线性上升
            val attackEnv = if (attackSamples > 0 && i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                1.0
            }
            // Decay/Sustain：指数衰减
            val decayEnv = exp(-i / decaySamples)
            var envelope = attackEnv * decayEnv

            // Accent：Marcato 重音——前 ACCENT_DURATION_MS 内幅度额外增强
            if (articulation.accent > 0.0f) {
                val accentEnv = if (i < accentSamples) {
                    1.0 + articulation.accent * (1.0 - i.toDouble() / accentSamples)
                } else {
                    1.0
                }
                envelope *= accentEnv
            }

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

        /** 节拍间距（毫秒）——音符之间的时间间隔。 */
        const val NOTE_SPACING_MS = 380.0

        /** 默认音符数量。 */
        const val DEFAULT_NOTE_COUNT = 5

        /** 重音持续时长（毫秒）——Marcato accent 的作用窗口。 */
        const val ACCENT_DURATION_MS = 50.0

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)

        /** C 大调五声音阶频率（C4-D4-E4-G4-A4）。 */
        private val SCALE_FREQS = doubleArrayOf(
            261.63, // C4
            293.66, // D4
            329.63, // E4
            392.00, // G4
            440.00  // A4
        )
    }
}
