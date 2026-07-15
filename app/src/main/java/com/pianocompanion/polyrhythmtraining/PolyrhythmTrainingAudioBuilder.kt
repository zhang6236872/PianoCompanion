package com.pianocompanion.polyrhythmtraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 复合节奏辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [PolyrhythmQuestion] 的复合节奏渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 核心合成方式：
 * - **高音声部**（880 Hz）奏 [PolyrhythmType.highCount] 个等距音符
 * - **低音声部**（440 Hz）奏 [PolyrhythmType.lowCount] 个等距音符
 * - 两条声部在同一周期内同时开始、同时结束
 * - 每个 click 为短促的钢琴风格打击音（基频 + 5 谐波 + 指数衰减包络）
 * - 两声部的 click 在周期起始点完全重叠（强调同步感）
 *
 * 渲染流程：
 * 1. 用 [PolyrhythmTrainingEngine] 计算两条声部的 onset 时间戳
 * 2. 为每个 onset 合成一个 click 波形
 * 3. 将高音和低音波形混合叠加到统一缓冲区
 * 4. 软限幅保护
 *
 * @param sampleRate 采样率
 */
class PolyrhythmTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: PolyrhythmQuestion): FloatArray {
        return renderPolyrhythm(
            polyrhythm = question.polyrhythm,
            cycleCount = question.cycleCount
        )
    }

    /**
     * 将复合节奏渲染为连续 PCM 采样。
     *
     * @param polyrhythm 复合节奏类型
     * @param cycleCount 周期数
     * @return PCM Float 缓冲区
     */
    fun renderPolyrhythm(
        polyrhythm: PolyrhythmType,
        cycleCount: Int = DEFAULT_CYCLE_COUNT
    ): FloatArray {
        val engine = PolyrhythmTrainingEngine()
        val (highOnsets, lowOnsets) = engine.computeOnsetTimes(polyrhythm, cycleCount)

        if (highOnsets.isEmpty() && lowOnsets.isEmpty()) return FloatArray(0)

        val clickSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt().coerceAtLeast(1)
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val allOnsets = (highOnsets + lowOnsets)
        val lastOnsetSample = (allOnsets.max() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + clickSamples + tailSamples

        val output = FloatArray(totalLength)

        // 高音声部 clicks
        for (onset in highOnsets) {
            val clickWave = generateClick(HIGH_FREQ, clickSamples)
            val offset = (onset * sampleRate / 1000.0).toInt()
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += clickWave[j] * HIGH_AMPLITUDE
                }
            }
        }

        // 低音声部 clicks
        for (onset in lowOnsets) {
            val clickWave = generateClick(LOW_FREQ, clickSamples)
            val offset = (onset * sampleRate / 1000.0).toInt()
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += clickWave[j] * LOW_AMPLITUDE
                }
            }
        }

        // 软限幅保护（两声部叠加可能超过 1.0）
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

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: PolyrhythmQuestion): Long {
        val engine = PolyrhythmTrainingEngine()
        val (highOnsets, lowOnsets) = engine.computeOnsetTimes(
            question.polyrhythm,
            question.cycleCount
        )
        val allOnsets = highOnsets + lowOnsets
        if (allOnsets.isEmpty()) return 0L
        return allOnsets.max().toLong() + CLICK_DURATION_MS.toLong() + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个 click 波形（钢琴风格加法合成 + 快速衰减包络）。
     *
     * 包络模型：
     * - **Attack**：极短线性上升（1ms），模拟打击起音
     * - **Decay**：指数衰减，时间常数 60ms，模拟钢琴琴弦阻尼
     *
     * 输出已归一化到 [-1, 1]。
     *
     * @param frequency 基频
     * @param numSamples click 采样数
     */
    private fun generateClick(
        frequency: Double,
        numSamples: Int
    ): FloatArray {
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * ATTACK_MS / 1000.0).toInt().coerceAtLeast(1)
        val decaySamples = sampleRate * DECAY_TIME_CONSTANT_MS / 1000.0

        // 先在临时缓冲区合成原始波形
        val raw = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // ── 包络 ──
            val attackEnv = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                1.0
            }
            val decayEnv = exp(-i / decaySamples)
            val envelope = attackEnv * decayEnv

            // 钢琴风格：基频 + 递减谐波（跳过超过奈奎斯特的谐波）
            var sample = 0.0
            for (h in HARMONICS.indices) {
                val harmonicFreq = frequency * (h + 1)
                if (harmonicFreq < NYQUIST_FREQ) {
                    sample += sin(2.0 * PI * harmonicFreq * t) * HARMONICS[h]
                }
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

        /** 奈奎斯特频率（采样率的一半）。 */
        private val NYQUIST_FREQ = DEFAULT_SAMPLE_RATE / 2.0

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 500.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个 click 的持续时长（毫秒）。 */
        const val CLICK_DURATION_MS = 180.0

        /** 起音时间（毫秒）—— 极短打击起音。 */
        const val ATTACK_MS = 1.0

        /** 衰减时间常数（毫秒）—— 钢琴风格的快速衰减。 */
        const val DECAY_TIME_CONSTANT_MS = 60.0

        /** 默认周期数。 */
        const val DEFAULT_CYCLE_COUNT = 2

        /** 单个复合节奏周期的持续时长（毫秒）。 */
        const val CYCLE_DURATION_MS = 2400.0

        /** 高音声部频率（A5）。 */
        const val HIGH_FREQ = 880.0

        /** 低音声部频率（A4）。 */
        const val LOW_FREQ = 440.0

        /** 高音声部振幅。 */
        const val HIGH_AMPLITUDE = 0.7f

        /** 低音声部振幅。 */
        const val LOW_AMPLITUDE = 0.7f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
