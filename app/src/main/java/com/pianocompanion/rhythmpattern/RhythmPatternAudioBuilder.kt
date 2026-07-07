package com.pianocompanion.rhythmpattern

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 节奏型听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [RhythmPatternQuestion] 的节奏型渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 与和弦/调式听辨不同，节奏听辨的核心是**时间间距**而非音高色彩。因此：
 * - 所有 click 使用**相同的音高和音量**（等高哒声），唯一区分依据是 onset 间距
 * - Click 为短促的木鱼/节拍器式脉冲声（正弦波 + 指数衰减包络）
 * - 节奏型可重复播放多次，帮助用户多听几遍
 *
 * 渲染流程：
 * 1. 用 [computeOnsetTimes] 计算每个 click 的绝对时间戳（毫秒）
 * 2. 将时间戳转换为采样偏移
 * 3. 在每个偏移处叠加一个 click 波形
 * 4. 软限幅防止叠加时削波
 *
 * @param sampleRate 采样率
 */
class RhythmPatternAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     */
    fun render(question: RhythmPatternQuestion): FloatArray {
        return renderPattern(
            pattern = question.type,
            tempo = question.tempo,
            repeatCount = question.repeatCount
        )
    }

    /**
     * 将节奏型渲染为连续 PCM 采样。
     *
     * @param pattern 节奏型
     * @param tempo 播放速度
     * @param repeatCount 重复次数
     * @return PCM Float 缓冲区
     */
    fun renderPattern(
        pattern: RhythmPatternType,
        tempo: RhythmTempo,
        repeatCount: Int = 1
    ): FloatArray {
        val onsets = computeOnsetTimes(pattern, tempo, repeatCount)
        if (onsets.isEmpty()) return FloatArray(0)

        val clickSamples = (sampleRate * CLICK_DURATION_MS / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 最后一个 onset + click 长度 + 尾部静音
        val lastOnsetSample = (onsets.last() * sampleRate / 1000.0).toInt()
        val totalLength = lastOnsetSample + clickSamples + tailSamples

        val output = FloatArray(totalLength)

        // 生成 click 波形（所有 click 相同，可复用）
        val clickWave = generateClick(clickSamples)

        // 在每个 onset 位置叠加 click
        for (onsetMs in onsets) {
            val offset = (onsetMs * sampleRate / 1000.0).toInt()
            for (j in clickWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += clickWave[j]
                }
            }
        }

        // 软限幅
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 计算节奏型中每个音符 onset 的绝对时间戳（毫秒）。
     *
     * 与 [RhythmPatternEngine.computeOnsetTimes] 逻辑一致，独立实现以保持本类的自包含性。
     */
    fun computeOnsetTimes(
        pattern: RhythmPatternType,
        tempo: RhythmTempo,
        repeatCount: Int = 1
    ): List<Double> {
        val beatMs = tempo.beatMs
        val onsets = mutableListOf<Double>()
        val measureMs = pattern.totalBeats * beatMs

        for (rep in 0 until repeatCount) {
            val measureStart = LEAD_SILENCE_MS + rep * measureMs
            var time = measureStart
            for (duration in pattern.durations) {
                onsets.add(time)
                time += duration * beatMs
            }
        }
        return onsets
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: RhythmPatternQuestion): Long {
        val onsets = computeOnsetTimes(question.type, question.tempo, question.repeatCount)
        if (onsets.isEmpty()) return 0L
        return onsets.last().toLong() + CLICK_DURATION_MS + TAIL_SILENCE_MS.toLong()
    }

    /**
     * 生成单个 click 波形。
     *
     * 正弦波 + 快速指数衰减包络，模拟木鱼/节拍器的短促脉冲声。
     * - 频率 CLICK_FREQ Hz（880 Hz = A5，清晰明亮）
     * - 衰减时间常数 CLICK_DECAY_MS（10ms，极快衰减产生"哒"声质感）
     */
    private fun generateClick(numSamples: Int): FloatArray {
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * CLICK_DECAY_MS / 1000.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            val sample = sin(2.0 * PI * CLICK_FREQ * t) * envelope * CLICK_AMP
            wave[i] = sample.toFloat()
        }
        return wave
    }

    /**
     * 软限幅函数（tanh 近似）。
     */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + kotlin.math.abs(x) / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 300.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个 click 持续时间（毫秒）。 */
        const val CLICK_DURATION_MS = 100L

        /** Click 频率（Hz）。 */
        const val CLICK_FREQ = 880.0

        /** Click 衰减时间常数（毫秒）。 */
        const val CLICK_DECAY_MS = 10.0

        /** Click 振幅。 */
        const val CLICK_AMP = 0.7

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
