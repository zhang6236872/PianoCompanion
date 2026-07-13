package com.pianocompanion.tempotraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 速度辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TempoTrainingQuestion] 的速度渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 与拍号听辨不同，速度辨识的核心是**节拍间距（即 BPM）**而非重音分组。因此：
 * - 所有 click 的**振幅和频率完全相同**（等高等响），唯一区分依据是间距
 * - click 声为短促的节拍器脉冲（正弦波 + 指数衰减包络）
 * - 固定数量的 click（默认 8 次），用户通过感知间距判断速度
 *
 * 渲染流程：
 * 1. 用 [computeOnsetTimes] 计算 click 时间戳
 * 2. 将时间戳转换为采样偏移
 * 3. 在每个偏移处叠加 click 波形
 *
 * @param sampleRate 采样率
 */
class TempoTrainingAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: TempoTrainingQuestion): FloatArray {
        return renderTempo(
            tempo = question.tempo,
            clickCount = question.clickCount
        )
    }

    /**
     * 将速度渲染为连续 PCM 采样。
     *
     * @param tempo 速度类型
     * @param clickCount click 总次数
     * @return PCM Float 缓冲区
     */
    fun renderTempo(
        tempo: TempoCategory,
        clickCount: Int = 8
    ): FloatArray {
        val onsets = computeOnsetTimes(tempo, clickCount)
        if (onsets.isEmpty()) return FloatArray(0)

        val clickSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + clickSamples + tailSamples

        val output = FloatArray(totalLength)

        // 预生成 click 波形（所有 click 等高）
        val clickWave = generateClick(clickSamples)

        for (onset in onsets) {
            val offset = (onset * sampleRate / 1000.0).toInt()
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] = clickWave[j]
                }
            }
        }

        // 单声道 click 不需要软限幅（无叠加），但仍做安全裁剪
        for (i in output.indices) {
            if (output[i] > 1.0f) output[i] = 1.0f
            else if (output[i] < -1.0f) output[i] = -1.0f
        }

        return output
    }

    /**
     * 计算每个 click 的绝对时间戳（毫秒）。
     *
     * 与 [TempoTrainingEngine.computeOnsetTimes] 逻辑一致，独立实现以保持自包含性。
     */
    fun computeOnsetTimes(
        tempo: TempoCategory,
        clickCount: Int = 8
    ): List<Double> {
        val intervalMs = tempo.intervalMs
        val onsets = mutableListOf<Double>()

        for (i in 0 until clickCount) {
            onsets.add(LEAD_SILENCE_MS + i * intervalMs)
        }
        return onsets
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: TempoTrainingQuestion): Long {
        val onsets = computeOnsetTimes(question.tempo, question.clickCount)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + CLICK_DURATION_MS + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个 click 波形。
     *
     * 正弦波 + 快速指数衰减包络，模拟节拍器的短促脉冲声。
     * 固定频率 880Hz、振幅 0.7，所有 click 完全一致。
     */
    private fun generateClick(numSamples: Int): FloatArray {
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CLICK_DECAY_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val sample = sin(2.0 * PI * CLICK_FREQUENCY * t) * envelope * CLICK_AMPLITUDE
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个 click 持续时间（毫秒）。 */
        const val CLICK_DURATION_MS = 90L

        /** Click 衰减时间常数（毫秒）。 */
        const val CLICK_DECAY_MS = 12.0

        /** Click 频率（Hz）。 */
        const val CLICK_FREQUENCY = 880.0

        /** Click 振幅（0.0-1.0）。 */
        const val CLICK_AMPLITUDE = 0.7f
    }
}
