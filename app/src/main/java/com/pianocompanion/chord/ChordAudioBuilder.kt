package com.pianocompanion.chord

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import kotlin.math.absoluteValue

/**
 * 和弦音频渲染器（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 使用 [PianoToneSynthesizer] 将和弦 [ChordVoicing] 渲染为 PCM 浮点采样。
 * 支持柱式（blocked，同时发声）和琶音（arpeggiated，从下到上依次弹奏）两种模式。
 *
 * 渲染流程：
 * 1. 每个音符用 PianoToneSynthesizer 合成（加法合成 + 指数衰减包络）
 * 2. 按时间轴将所有音符采样叠加（混合）
 * 3. 软限幅防止多音符叠加时削波
 * 4. 前导静音（200ms，给用户准备时间）+ 尾部衰减（自然结束）
 */
class ChordAudioBuilder(
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val LEAD_SILENCE_MS = 200L
        const val TAIL_SILENCE_MS = 500L
        const val NOTE_DURATION_MS = 1200L
        const val ARPEGGIO_DELAY_MS = 60L
        private const val SOFT_CLIP_K = 0.7f
    }

    /**
     * 渲染柱式和弦（所有音符同时发声）。
     *
     * @param voicing 和弦发音
     * @param velocity 力度（1-127）
     * @return PCM 浮点采样数组，值在 [-1.0, 1.0] 范围内
     */
    fun renderBlocked(voicing: ChordVoicing, velocity: Int = 70): FloatArray {
        return renderInternal(voicing, velocity, arpeggiated = false)
    }

    /**
     * 渲染琶音和弦（从下到上依次快速弹奏）。
     *
     * 每个音符比前一个晚 [ARPEGGIO_DELAY_MS] 毫秒，模拟手指依次弹奏的效果。
     *
     * @param voicing 和弦发音
     * @param velocity 力度（1-127）
     * @return PCM 浮点采样数组
     */
    fun renderArpeggiated(voicing: ChordVoicing, velocity: Int = 70): FloatArray {
        return renderInternal(voicing, velocity, arpeggiated = true)
    }

    private fun renderInternal(
        voicing: ChordVoicing,
        velocity: Int,
        arpeggiated: Boolean
    ): FloatArray {
        if (voicing.midiNotes.isEmpty()) return FloatArray(0)

        val synth = synthesizer
        val frequencies = ChordEngine.frequencies(voicing)

        // 为每个音符合成采样
        val noteBuffers = frequencies.map { freq ->
            synth.synthesize(freq, NOTE_DURATION_MS, velocity, Articulation.NONE)
        }

        // 计算每个音符在输出缓冲区中的偏移（样本数）
        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val noteStartOffsets = if (arpeggiated) {
            noteBuffers.indices.map { i ->
                leadSilenceSamples + (sampleRate * (i * ARPEGGIO_DELAY_MS) / 1000.0).toInt()
            }
        } else {
            noteBuffers.indices.map { leadSilenceSamples }
        }

        // 计算总输出长度 = 前导静音 + 最后一个音符的起始 + 最后一个音符长度 + 尾部静音
        val lastNoteEnd = (noteStartOffsets.last() + noteBuffers.last().size)
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

        // 软限幅（soft clip）防止叠加后削波
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 软限幅函数（tanh 近似）。
     * x / (1 + |x| / K) — 平滑地压缩大值到 [-1, 1] 范围。
     */
    private fun softClip(x: Float): Float {
        return (x / (1.0f + x.absoluteValue / SOFT_CLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    /**
     * 计算渲染输出的预期总时长（毫秒），用于 UI 进度显示。
     */
    fun estimateDurationMs(voicing: ChordVoicing, arpeggiated: Boolean): Long {
        val noteCount = voicing.midiNotes.size
        val arpeggioOffset = if (arpeggiated) (noteCount - 1) * ARPEGGIO_DELAY_MS else 0L
        return LEAD_SILENCE_MS + arpeggioOffset + NOTE_DURATION_MS + TAIL_SILENCE_MS
    }
}
