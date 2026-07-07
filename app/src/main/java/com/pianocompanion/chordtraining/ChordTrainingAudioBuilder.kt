package com.pianocompanion.chordtraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 和弦听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ChordEarQuestion] 的 MIDI 音符列表渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，支持两种播放方式：
 * - **柱式（BLOCK）**：所有音同时发声（标准听辨方式）
 * - **琶音（ARPEGGIO）**：从低到高依次快速弹奏（帮助初学者分辨各音程）
 *
 * 渲染流程：
 * 1. 每个音符用 PianoToneSynthesizer 合成（加法合成 + 指数衰减包络）
 * 2. 按时间轴将所有音符采样叠加（混合）
 * 3. 软限幅防止多音符叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class ChordTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     *
     * 根据 [ChordEarQuestion.playStyle] 决定柱式或琶音。
     */
    fun render(question: ChordEarQuestion): FloatArray {
        return renderNotes(question.midiNotes, question.playStyle)
    }

    /**
     * 将 MIDI 音符列表渲染为连续 PCM 采样。
     *
     * @param midiNotes MIDI 音符列表（升序）
     * @param playStyle 播放方式
     * @param velocity 力度（1-127）
     */
    fun renderNotes(
        midiNotes: List<Int>,
        playStyle: ChordPlayStyle,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        // 为每个音符合成采样
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = NOTE_DURATION_MS,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        // 计算每个音符在输出缓冲区中的偏移（样本数）
        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val noteStartOffsets = if (playStyle == ChordPlayStyle.ARPEGGIO) {
            noteBuffers.indices.map { i ->
                leadSilenceSamples + (sampleRate * (i * ARPEGGIO_DELAY_MS) / 1000.0).toInt()
            }
        } else {
            noteBuffers.indices.map { leadSilenceSamples }
        }

        // 总输出长度 = 前导静音 + 最后一个音符起始 + 最后一个音符长度 + 尾部静音
        val lastNoteEnd = noteStartOffsets.last() + noteBuffers.last().size
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        val totalLength = lastNoteEnd + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个音符的采样叠加到输出缓冲区
        for ((i, buffer) in noteBuffers.withIndex()) {
            val offset = noteStartOffsets[i]
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅防止叠加后削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: ChordEarQuestion): Long {
        val noteCount = question.midiNotes.size
        val arpeggioOffset = if (question.playStyle == ChordPlayStyle.ARPEGGIO) {
            (noteCount - 1) * ARPEGGIO_DELAY_MS
        } else {
            0L
        }
        return LEAD_SILENCE_MS + arpeggioOffset + NOTE_DURATION_MS + TAIL_SILENCE_MS
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

        /** 单个音符持续时间（毫秒）。和弦需要更长的延音以便听辨色彩。 */
        const val NOTE_DURATION_MS = 1300L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 500L

        /** 琶音模式中各音之间的延迟（毫秒）。 */
        const val ARPEGGIO_DELAY_MS = 80L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
