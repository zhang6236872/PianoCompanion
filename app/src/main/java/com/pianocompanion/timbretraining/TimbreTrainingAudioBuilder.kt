package com.pianocompanion.timbretraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 音色辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TimbreTrainingQuestion] 的乐器渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 加法合成 + ADSR 包络：**
 * 所有乐器演奏同一个基音频率（440Hz = A4），但通过不同的**谐波结构**和**幅度包络**
 * 产生截然不同的音色：
 *
 * - **谐波结构**：每个乐器有不同的谐波幅度数组 `harmonics[n]`（第 n 次谐波的相对振幅）
 *   - 钢琴：1.0, 0.5, 0.25, 0.15, 0.08（快速衰减）
 *   - 小提琴：锯齿波 1/n（所有谐波，明亮温暖）
 *   - 吉他：1.0, 0.6, 0.35, 0.15, 0.06（中等衰减）
 *   - 长笛：1.0, 0.10, 0.04, 0.01（几乎纯正弦，纯净柔和）
 *   - 单簧管：1.0, 0.0, 0.5, 0.0, 0.25（奇次谐波主导）
 *   - 小号：1.0, 0.5, 0.4, 0.3, 0.2, 0.15, 0.1（极丰富，明亮穿透）
 *
 * - **包络（ADSR）**：
 *   - 钢琴：极快起音（2ms）+ 指数衰减（无持续）
 *   - 小提琴：缓慢起音（80ms）+ 持续 + 缓慢释放（150ms）
 *   - 吉他：快速起音（5ms）+ 缓慢衰减
 *   - 长笛：柔和起音（60ms）+ 持续 + 柔和释放
 *   - 单簧管：中等起音（20ms）+ 长持续
 *   - 小号：快速起音（15ms）+ 持续 + 较快释放
 *
 * @param sampleRate 采样率
 */
class TimbreTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: TimbreTrainingQuestion): FloatArray {
        return renderInstrument(
            instrument = question.instrument,
            durationMs = question.noteDurationMs
        )
    }

    /**
     * 将乐器音色渲染为连续 PCM 采样。
     *
     * @param instrument 乐器类型
     * @param durationMs 音符持续时间（毫秒）
     * @return PCM Float 缓冲区
     */
    fun renderInstrument(
        instrument: TimbreInstrument,
        durationMs: Long = TimbreTrainingEngine.DEFAULT_NOTE_DURATION_MS
    ): FloatArray {
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val noteSamples = (sampleRate * durationMs / 1000.0).toInt()

        val totalLength = leadSamples + noteSamples + tailSamples
        val output = FloatArray(totalLength)

        val harmonics = getHarmonics(instrument)
        val envelope = getEnvelopeProfile(instrument)

        val fundamental = instrument.baseFrequency

        for (i in 0 until noteSamples) {
            val t = i.toDouble() / sampleRate
            val env = computeEnvelope(i.toDouble(), noteSamples.toDouble(), envelope)
            var sample = 0.0
            for (h in harmonics.indices) {
                val amp = harmonics[h]
                if (amp <= 0.0) continue
                val freq = fundamental * (h + 1)
                // 防止极高谐波超过奈奎斯特频率
                if (freq >= sampleRate / 2.0) break
                sample += amp * sin(2.0 * PI * freq * t)
            }
            // 归一化到 [-1, 1]
            val maxAmp = harmonics.sum()
            if (maxAmp > 0.0) sample /= maxAmp
            output[leadSamples + i] = (sample * env * MASTER_AMPLITUDE).toFloat()
        }

        // 安全裁剪
        for (i in output.indices) {
            if (output[i] > 1.0f) output[i] = 1.0f
            else if (output[i] < -1.0f) output[i] = -1.0f
        }

        return output
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: TimbreTrainingQuestion): Long {
        return (LEAD_SILENCE_MS + question.noteDurationMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 获取乐器的谐波幅度数组。
     *
     * 数组索引 0 = 基频（第 1 次谐波），索引 1 = 第 2 次谐波，以此类推。
     * 值为 0.0 表示该谐波不存在。
     */
    private fun getHarmonics(instrument: TimbreInstrument): DoubleArray {
        return when (instrument) {
            TimbreInstrument.PIANO -> doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08, 0.04)
            TimbreInstrument.VIOLIN -> doubleArrayOf(1.0, 0.5, 0.333, 0.25, 0.2, 0.167, 0.143, 0.125)
            TimbreInstrument.GUITAR -> doubleArrayOf(1.0, 0.6, 0.35, 0.15, 0.06, 0.03)
            TimbreInstrument.FLUTE -> doubleArrayOf(1.0, 0.10, 0.04, 0.01)
            TimbreInstrument.CLARINET -> doubleArrayOf(1.0, 0.0, 0.5, 0.0, 0.25, 0.0, 0.12)
            TimbreInstrument.TRUMPET -> doubleArrayOf(1.0, 0.5, 0.4, 0.3, 0.2, 0.15, 0.1)
        }
    }

    /**
     * 获取乐器的包络参数。
     *
     * @return EnvelopeProfile 包含 attack（起音比例）、decay（衰减系数）、sustainLevel（持续电平）、release（释放比例）
     */
    private fun getEnvelopeProfile(instrument: TimbreInstrument): EnvelopeProfile {
        return when (instrument) {
            // 钢琴：极快起音，指数衰减，无持续
            TimbreInstrument.PIANO -> EnvelopeProfile(
                attackRatio = 0.005, decayLambda = 3.5, sustainRatio = 0.0, releaseRatio = 0.0
            )
            // 小提琴：缓慢起音，持续，缓慢释放
            TimbreInstrument.VIOLIN -> EnvelopeProfile(
                attackRatio = 0.06, decayLambda = 0.0, sustainRatio = 0.9, releaseRatio = 0.12
            )
            // 吉他：快速起音，缓慢衰减
            TimbreInstrument.GUITAR -> EnvelopeProfile(
                attackRatio = 0.01, decayLambda = 1.8, sustainRatio = 0.0, releaseRatio = 0.0
            )
            // 长笛：柔和起音，持续，柔和释放
            TimbreInstrument.FLUTE -> EnvelopeProfile(
                attackRatio = 0.05, decayLambda = 0.0, sustainRatio = 0.95, releaseRatio = 0.10
            )
            // 单簧管：中等起音，长持续
            TimbreInstrument.CLARINET -> EnvelopeProfile(
                attackRatio = 0.02, decayLambda = 0.2, sustainRatio = 0.85, releaseRatio = 0.08
            )
            // 小号：快速起音，持续，较快释放
            TimbreInstrument.TRUMPET -> EnvelopeProfile(
                attackRatio = 0.012, decayLambda = 0.5, sustainRatio = 0.8, releaseRatio = 0.06
            )
        }
    }

    /**
     * 计算给定时刻的包络值（0.0-1.0）。
     *
     * - 起音阶段（[0, attackRatio)）：线性上升至 1.0
     * - 衰减/持续阶段（[attackRatio, 1-releaseRatio)）：
     *   - sustainRatio <= 0：纯指数衰减 `exp(-λ * progress)`（钢琴/吉他等衰减型乐器）
     *   - sustainRatio > 0：快速从 1.0 衰减到 sustainRatio 后保持（管弦乐器等持续型）
     *     decayLambda 控制初始衰减速度（仅影响前 15% 的过渡区）
     * - 释放阶段（[1-releaseRatio, 1]）：线性下降至 0
     */
    private fun computeEnvelope(
        sampleIndex: Double,
        totalSamples: Double,
        profile: EnvelopeProfile
    ): Double {
        val progress = sampleIndex / totalSamples

        val releaseStart = 1.0 - profile.releaseRatio

        val sustainedLevel = if (profile.sustainRatio > 0.0) {
            profile.sustainRatio
        } else {
            // 纯衰减型乐器在释放起点的电平
            kotlin.math.exp(-profile.decayLambda * releaseStart)
        }

        // 释放阶段
        if (progress >= releaseStart && profile.releaseRatio > 0.0) {
            val releaseProgress = (progress - releaseStart) / profile.releaseRatio
            return sustainedLevel * (1.0 - releaseProgress)
        }

        // 起音阶段
        if (progress < profile.attackRatio && profile.attackRatio > 0.0) {
            return progress / profile.attackRatio
        }

        // 衰减/持续阶段
        val midProgress = if (profile.attackRatio > 0.0) {
            (progress - profile.attackRatio) / (releaseStart - profile.attackRatio)
        } else {
            progress / releaseStart
        }
        midProgress.coerceIn(0.0, 1.0)

        return if (profile.sustainRatio <= 0.0) {
            // 纯衰减型（钢琴/吉他）: 全程指数衰减
            exp(-profile.decayLambda * midProgress)
        } else {
            // 持续型（小提琴/长笛/单簧管/小号）: 快速衰减到 sustainRatio 后保持
            val settleRatio = 0.15
            if (midProgress < settleRatio) {
                val settleProgress = midProgress / settleRatio
                1.0 - (1.0 - profile.sustainRatio) * settleProgress
            } else {
                profile.sustainRatio
            }
        }
    }

    /** 包络参数。 */
    private data class EnvelopeProfile(
        val attackRatio: Double,     // 起音占音符总时长的比例
        val decayLambda: Double,     // 衰减系数（>0 表示衰减型乐器）
        val sustainRatio: Double,    // 持续电平（0.0-1.0）
        val releaseRatio: Double     // 释放占音符总时长的比例
    )

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 300.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8
    }
}
