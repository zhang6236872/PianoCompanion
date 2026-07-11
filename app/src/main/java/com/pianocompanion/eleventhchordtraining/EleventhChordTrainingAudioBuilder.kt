package com.pianocompanion.eleventhchordtraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 十一和弦色彩听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [EleventhChordQuestion] 的十一和弦渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 和弦的 6 个音符用 PianoToneSynthesizer 分别合成（同时播放 = 柱式和弦/block chord）
 * 2. 多个音符按时间轴叠加混合（同一时刻开始）
 * 3. 软限幅防止叠加时削波（6 个音叠加可能超过 1.0）
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class EleventhChordTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（柱式十一和弦）。
     */
    fun render(question: EleventhChordQuestion): FloatArray {
        return renderChord(question.midiNotes)
    }

    /**
     * 将十一和弦渲染为 PCM 采样（柱式和弦 = 所有音同时发声）。
     *
     * @param midiNotes 和弦的 MIDI 音符号列表（6 个音，从低到高）
     * @param velocity 力度（1-127）
     */
    fun renderChord(
        midiNotes: List<Int>,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 为每个音符合成 PCM 采样（柱式和弦 = 同时刻开始）
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = CHORD_DURATION_MS,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        // 柱式和弦：所有音符从 leadSilenceSamples 处同时开始
        val maxNoteLength = noteBuffers.maxOf { it.size }
        val totalLength = leadSilenceSamples + maxNoteLength + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个音符的采样从同一偏移开始叠加
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                val outIdx = leadSilenceSamples + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅（6 个音叠加可能削波）
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     * 注意：PianoToneSynthesizer 对 NONE 运音法施加 0.90 时长因子，实际音符合成时长 = CHORD_DURATION_MS × 0.90。
     */
    fun estimateDurationMs(question: EleventhChordQuestion): Long {
        val effectiveChordMs = (CHORD_DURATION_MS * ARTICULATION_DURATION_FACTOR).toLong()
        return LEAD_SILENCE_MS + effectiveChordMs + TAIL_SILENCE_MS
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

        /** 和弦持续时间（毫秒）。十一和弦需要足够长让用户辨识色彩与空灵感。 */
        const val CHORD_DURATION_MS = 2200L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 500L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.6f

        /** PianoToneSynthesizer 对 NONE 运音法的时长因子（必须与 synth 内部一致）。 */
        const val ARTICULATION_DURATION_FACTOR = 0.90
    }
}
