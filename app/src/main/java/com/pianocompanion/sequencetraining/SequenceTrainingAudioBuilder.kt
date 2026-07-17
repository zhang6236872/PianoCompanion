package com.pianocompanion.sequencetraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 模进辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [SequenceQuestion] 的旋律 MIDI 序列渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 旋律的每个音符按顺序依次发声（legato 衔接，模拟旋律的连贯性）
 * 2. 每个音符的频率由 MIDI 音符号计算
 * 3. 音符序列按时轴依次排列（前导静音 + 序列 + 尾部静音）
 * 4. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class SequenceTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（完整旋律）。
     */
    fun render(question: SequenceQuestion): FloatArray {
        return renderMelody(question.noteMidiSequence, question.noteDurationMs)
    }

    /**
     * 将旋律 MIDI 序列渲染为连续 PCM 采样。
     *
     * 音符按顺序依次发声（旋律是连贯的单线条，不需要和弦叠加）。
     *
     * @param noteMidiSequence 旋律的 MIDI 音符号序列
     * @param noteDurationMs 每个音符的持续时长（毫秒）
     * @param velocity 力度（1-127）
     */
    fun renderMelody(
        noteMidiSequence: List<Int>,
        noteDurationMs: Long,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (noteMidiSequence.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 为每个音符渲染 PCM 采样
        val noteBuffers = noteMidiSequence.map { midi ->
            val clampedMidi = midi.coerceIn(MIN_MIDI, MAX_MIDI)
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(clampedMidi),
                durationMs = noteDurationMs,
                velocity = velocity,
                articulation = Articulation.TENUTO
            )
        }

        // 顺序排列：前导静音 + 各音符依次 + 尾部静音
        val notesTotal = noteBuffers.sumOf { it.size }
        val totalLength = leadSilenceSamples + notesTotal + tailSilenceSamples

        val output = FloatArray(totalLength)
        var offset = leadSilenceSamples
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
            offset += buffer.size
        }

        // 软限幅防止削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: SequenceQuestion): Long {
        return LEAD_SILENCE_MS + question.sequenceDurationMs + TAIL_SILENCE_MS
    }

    /**
     * 软限幅函数（tanh 近似）。
     * x / (1 + |x| / K) — 平滑地压缩大值到 [-1, 1] 范围。
     */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + x.absoluteValue / SOFTCLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400L

        /** 基础力度。 */
        const val DEFAULT_VELOCITY = 74

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f

        /** 钢琴最低音 A0。 */
        const val MIN_MIDI = 21

        /** 钢琴最高音 C8。 */
        const val MAX_MIDI = 108
    }
}
