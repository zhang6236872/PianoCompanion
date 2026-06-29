package com.pianocompanion.audio

import com.pianocompanion.data.model.Articulation
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 轻量级钢琴音色合成器（纯 Kotlin，无 Android 依赖）。
 *
 * 使用加法合成（fundamental + 谐波）和指数衰减包络来近似钢琴音色。
 * 钢琴音色特征：
 * - 基频 + 多个谐波（泛音），高次谐波幅度递减
 * - 极快的起音（锤子敲击琴弦）
 * - 指数衰减（琴弦振动自然衰减）
 * - 无持续阶段（钢琴音符持续衰减，不会保持恒定音量）
 *
 * 输出 FloatArray，每个采样值在 [-1.0, 1.0] 范围内，可转换为 16-bit PCM。
 *
 * @param sampleRate 采样率（Hz），默认 44100
 */
class PianoToneSynthesizer(
    private val sampleRate: Int = 44100
) {
    /**
     * 谐波幅度表。下标 0 = 基频，后续为整数倍频谐波。
     * 幅度递减模拟琴弦高次泛音较弱的物理特性。
     */
    private val harmonicAmplitudes = doubleArrayOf(
        1.0,   // 基频
        0.50,  // 二次谐波（八度）
        0.28,  // 三次谐波（十二度）
        0.15,  // 四次谐波（两个八度）
        0.08,  // 五次谐波（十七度）
        0.05,  // 六次谐波
        0.03   // 七次谐波
    )

    /**
     * 演奏法对有效时值的影响（相对于记谱时值的比例）。
     */
    private fun articulationDurationFactor(articulation: Articulation): Double = when (articulation) {
        Articulation.STACCATO       -> 0.50
        Articulation.STACCATISSIMO  -> 0.30
        Articulation.TENUTO         -> 1.00
        else                         -> 0.90  // NONE, ACCENT, MARCATO — 略短于记谱
    }

    /**
     * 演奏法对力度的影响。
     */
    private fun articulationVelocityFactor(articulation: Articulation): Double = when (articulation) {
        Articulation.ACCENT         -> 1.20
        Articulation.STACCATISSIMO  -> 1.15
        Articulation.MARCATO        -> 1.30
        Articulation.TENUTO         -> 0.95
        else                         -> 1.00
    }

    /**
     * 合成单个音符的音频采样。
     *
     * @param frequency 基频（Hz）
     * @param durationMs 音符时值（毫秒）
     * @param velocity MIDI 力度（1-127），映射到振幅
     * @param articulation 演奏法标记，影响时值和力度
     * @return FloatArray 采样数据，值在 [-1.0, 1.0] 范围内
     */
    fun synthesize(
        frequency: Double,
        durationMs: Long,
        velocity: Int = 64,
        articulation: Articulation = Articulation.NONE
    ): FloatArray {
        val effectiveDuration = (durationMs * articulationDurationFactor(articulation)).toLong()
            .coerceAtLeast(1)
        val effectiveVelocity = (velocity * articulationVelocityFactor(articulation))
            .coerceIn(0.0, 127.0)

        // 基础振幅：力度映射（0-127 → 0.0-1.0），带非线性曲线使低力度更柔和
        val baseAmplitude = amplitudeFromVelocity(effectiveVelocity)

        val numSamples = (sampleRate * effectiveDuration / 1000.0).toInt().coerceAtLeast(1)
        val buffer = FloatArray(numSamples)

        // 衰减速率：高频衰减更快（钢琴物理特性）
        // 低音区（< 250Hz）衰减慢，高音区（> 2000Hz）衰减快
        val decayRate = computeDecayRate(frequency)

        // 起音时长（样本数）：快速起音，但避免咔哒声（DC offset）
        val attackSamples = (sampleRate * ATTACK_MS / 1000.0).toInt()
            .coerceAtMost(numSamples / 2)
            .coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // 起音包络：前 attackSamples 个样本线性渐入
            val attackEnv = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                1.0
            }

            // 指数衰减包络
            val decayEnv = exp(-t * decayRate)

            // 加法合成：基频 + 谐波
            var sample = 0.0
            for ((h, amp) in harmonicAmplitudes.withIndex()) {
                val harmonicFreq = frequency * (h + 1)
                // 高次谐波衰减更快
                val harmonicDecay = exp(-t * decayRate * (1.0 + h * 0.3))
                sample += amp * sin(2.0 * PI * harmonicFreq * t) * harmonicDecay
            }

            // 归一化（所有谐波幅度之和可能 > 1）
            sample /= harmonicAmplitudes.sum()

            buffer[i] = (sample * baseAmplitude * attackEnv * decayEnv).toFloat()
        }

        return buffer
    }

    /**
     * 力度到振幅的非线性映射。
     * 使用平方根曲线使低力度区间有更好的动态分辨率。
     */
    private fun amplitudeFromVelocity(velocity: Double): Double {
        val normalized = (velocity / 127.0).coerceIn(0.0, 1.0)
        return MAX_AMPLITUDE * Math.sqrt(normalized)
    }

    /**
     * 根据频率计算衰减速率。
     * 低频衰减慢（钢琴低音延续长），高频衰减快（钢琴高音短促）。
     */
    private fun computeDecayRate(frequency: Double): Double {
        // 基准衰减：中音区（A4=440Hz）约 3 秒衰减到 1%
        // 衰减常数 τ = ln(100) / 3.0 ≈ 1.535
        val baseDecay = BASE_DECAY_RATE
        // 频率越高衰减越快（幂律关系）
        val freqRatio = frequency / A4_REFERENCE
        return baseDecay * Math.pow(freqRatio, 0.5)
    }

    companion object {
        private const val ATTACK_MS = 4.0       // 起音时长（毫秒）
        private const val MAX_AMPLITUDE = 0.85   // 最大振幅（留余量避免削波）
        private const val BASE_DECAY_RATE = 2.5   // 中音区基准衰减速率
        private const val A4_REFERENCE = 440.0    // 基准频率（A4）

        const val DEFAULT_SAMPLE_RATE = 44100
    }
}
