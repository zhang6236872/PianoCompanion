package com.pianocompanion.interval

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * 音程识别训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [IntervalQuestion] 的两个音符转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，依次播放较低音 → 较高音（旋律性音程），
 * 帮助用户建立音程的听觉认知。
 *
 * @param synth 音色合成器实例
 */
class IntervalAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {

    /**
     * 为题目渲染两个音符的音频（较低音 → 间隔 → 较高音）。
     *
     * @param question 音程识别训练题目
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(question: IntervalQuestion): FloatArray {
        return renderInterval(question.lowerMidi, question.higherMidi)
    }

    /**
     * 渲染两个 MIDI 音符的音频（较低音 → 间隔 → 较高音）。
     *
     * 结构：前导静音 → 较低音 → 间隔静音 → 较高音 → 尾部静音
     */
    fun renderInterval(lowerMidi: Int, higherMidi: Int): FloatArray {
        val lowerBuf = synth.synthesize(
            frequency = MusicUtils.midiToFrequency(lowerMidi),
            durationMs = NOTE_DURATION_MS,
            velocity = DEFAULT_VELOCITY
        )
        val higherBuf = synth.synthesize(
            frequency = MusicUtils.midiToFrequency(higherMidi),
            durationMs = NOTE_DURATION_MS,
            velocity = DEFAULT_VELOCITY
        )

        val gapSamples = (SAMPLE_RATE * GAP_MS / 1000.0).toInt()
        val totalSamples = SILENCE_LEAD_SAMPLES + lowerBuf.size + gapSamples + higherBuf.size + SILENCE_TAIL_SAMPLES
        val output = FloatArray(totalSamples)

        var offset = SILENCE_LEAD_SAMPLES
        // 写入较低音
        for (i in lowerBuf.indices) {
            output[offset + i] = lowerBuf[i]
        }
        offset += lowerBuf.size + gapSamples
        // 写入较高音
        for (i in higherBuf.indices) {
            output[offset + i] = higherBuf[i]
        }

        return softLimit(output)
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(): Long {
        return SILENCE_LEAD_MS + NOTE_DURATION_MS + GAP_MS + NOTE_DURATION_MS + SILENCE_TAIL_MS
    }

    /**
     * 软限幅：使用有理函数近似将输出限制在 [-1.0, 1.0]，
     * 同时保持低音量信号的线性度。
     */
    private fun softLimit(input: FloatArray): FloatArray {
        val k = SOFTCLIP_K
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + abs(x) / k)).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 700L

        /** 两音之间的间隔（毫秒）。 */
        const val GAP_MS = 80L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 前导静音（毫秒）。 */
        const val SILENCE_LEAD_MS = 100L

        /** 尾部静音（毫秒）。 */
        const val SILENCE_TAIL_MS = 300L

        /** 前导静音采样数。 */
        val SILENCE_LEAD_SAMPLES = (SAMPLE_RATE * SILENCE_LEAD_MS / 1000.0).toInt()

        /** 尾部静音采样数。 */
        val SILENCE_TAIL_SAMPLES = (SAMPLE_RATE * SILENCE_TAIL_MS / 1000.0).toInt()
    }
}
