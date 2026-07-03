package com.pianocompanion.chordreading

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * 和弦识别训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ChordReadingQuestion] 的所有音符转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，**同时播放所有音符（柱式和弦）**，
 * 帮助用户建立和弦的听觉认知。
 *
 * 结构：前导静音 → 柱式和弦（所有音符叠加）→ 尾部静音
 *
 * @param synth 音色合成器实例
 */
class ChordReadingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {

    /**
     * 为题目渲染柱式和弦音频（所有音符同时发声）。
     *
     * @param question 和弦识别训练题目
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(question: ChordReadingQuestion): FloatArray {
        return renderChord(question.noteMidis)
    }

    /**
     * 渲染一组 MIDI 音符的柱式和弦音频。
     *
     * 所有音符的合成缓冲区叠加（mix）到统一输出缓冲区中，实现同时发声。
     * 多音叠加后按音符数缩放并软限幅，防止削波。
     *
     * @param midis MIDI 音符号列表（从低到高）
     * @return PCM Float 缓冲区
     */
    fun renderChord(midis: List<Int>): FloatArray {
        if (midis.isEmpty()) return FloatArray(0)

        // 合成各音符的独立缓冲区
        val noteBuffers = midis.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = NOTE_DURATION_MS,
                velocity = DEFAULT_VELOCITY
            )
        }

        val maxLen = noteBuffers.maxOf { it.size }
        val totalSamples = SILENCE_LEAD_SAMPLES + maxLen + SILENCE_TAIL_SAMPLES
        val output = FloatArray(totalSamples)

        // 将所有音符叠加到和弦区（从前导静音之后开始）
        val chordStart = SILENCE_LEAD_SAMPLES
        for (buf in noteBuffers) {
            for (i in buf.indices) {
                output[chordStart + i] += buf[i]
            }
        }

        return softLimit(output)
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(): Long {
        return SILENCE_LEAD_MS + NOTE_DURATION_MS + SILENCE_TAIL_MS
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
        const val NOTE_DURATION_MS = 900L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 前导静音（毫秒）。 */
        const val SILENCE_LEAD_MS = 100L

        /** 尾部静音（毫秒）。 */
        const val SILENCE_TAIL_MS = 400L

        /** 前导静音采样数。 */
        val SILENCE_LEAD_SAMPLES = (SAMPLE_RATE * SILENCE_LEAD_MS / 1000.0).toInt()

        /** 尾部静音采样数。 */
        val SILENCE_TAIL_SAMPLES = (SAMPLE_RATE * SILENCE_TAIL_MS / 1000.0).toInt()
    }
}
