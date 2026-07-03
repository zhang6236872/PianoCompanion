package com.pianocompanion.keysig

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * 调号识别训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [KeySigQuestion] 的调性音阶转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，**依次播放音阶各音（旋律上行）**，
 * 帮助用户建立调性的听觉认知。
 *
 * 结构：前导静音 → 音阶各音（依次播放，含间隔）→ 尾部静音
 *
 * @param synth 音色合成器实例
 */
class KeySigAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {

    /**
     * 为题目渲染音阶音频（各音依次上行播放）。
     *
     * @param question 调号识别训练题目
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(question: KeySigQuestion): FloatArray {
        return renderScale(question.keyInfo.scaleMidis)
    }

    /**
     * 渲染一组 MIDI 音符的旋律性音阶音频（依次播放）。
     *
     * 每个音符合成独立缓冲区后按时间轴拼接（前导静音 + 音阶 + 尾部静音）。
     * 相邻音符间有短间隔。
     *
     * @param midis MIDI 音符号列表
     * @return PCM Float 缓冲区
     */
    fun renderScale(midis: List<Int>): FloatArray {
        if (midis.isEmpty()) return FloatArray(0)

        // 合成各音符的独立缓冲区
        val noteBuffers = midis.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = NOTE_DURATION_MS,
                velocity = DEFAULT_VELOCITY
            )
        }

        // 拼接：前导静音 + [音符 + 间隔] × N + 尾部静音
        val noteSamples = (SAMPLE_RATE * NOTE_DURATION_MS / 1000.0).toInt()
        val gapSamples = (SAMPLE_RATE * GAP_MS / 1000.0).toInt()
        val perNote = noteSamples + gapSamples
        val totalSamples = SILENCE_LEAD_SAMPLES + perNote * midis.size + SILENCE_TAIL_SAMPLES
        val output = FloatArray(totalSamples)

        var offset = SILENCE_LEAD_SAMPLES
        for (buf in noteBuffers) {
            for (i in buf.indices) {
                output[offset + i] += buf[i]
            }
            offset += perNote
        }

        return softLimit(output)
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(noteCount: Int): Long {
        return SILENCE_LEAD_MS + noteCount * (NOTE_DURATION_MS + GAP_MS) + SILENCE_TAIL_MS
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
        const val NOTE_DURATION_MS = 350L

        /** 相邻音符间隔（毫秒）。 */
        const val GAP_MS = 30L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 65

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
