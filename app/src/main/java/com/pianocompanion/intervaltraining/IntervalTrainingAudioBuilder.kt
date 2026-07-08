package com.pianocompanion.intervaltraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 音程听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [IntervalQuestion] 的两个 MIDI 音符渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，支持三种播放方式：
 * - **上行/下行旋律**：两个音先后弹奏（每个音持续固定时长），训练旋律音程听辨
 * - **和声**：两个音同时弹奏（叠加），训练和声音程听辨
 *
 * 渲染流程：
 * 1. 每个音符用 PianoToneSynthesizer 合成（加法合成 + 指数衰减包络）
 * 2. 旋律模式：按时间轴依次放置；和声模式：同时放置叠加
 * 3. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class IntervalTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     *
     * 根据 [IntervalQuestion.playDirection] 决定旋律或和声。
     */
    fun render(question: IntervalQuestion): FloatArray {
        return renderNotes(question.playOrder, question.playDirection)
    }

    /**
     * 将 MIDI 音符列表渲染为连续 PCM 采样。
     *
     * @param midiNotes MIDI 音符列表（按播放顺序；和声模式下顺序不影响——同时发声）
     * @param playDirection 播放方向
     * @param velocity 力度（1-127）
     */
    fun renderNotes(
        midiNotes: List<Int>,
        playDirection: PlayDirection,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        // 为每个音符合成采样
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = if (playDirection.harmonic) HARMONIC_NOTE_DURATION_MS else MELODIC_NOTE_DURATION_MS,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 和声模式：两音同时开始（偏移 = 前导静音）
        // 旋律模式：依次排列，间隔 = 旋律单音时长
        val noteStartOffsets = if (playDirection.harmonic) {
            noteBuffers.indices.map { leadSilenceSamples }
        } else {
            val melodicSpacing = (sampleRate * MELODIC_NOTE_DURATION_MS / 1000.0).toInt()
            noteBuffers.indices.map { i -> leadSilenceSamples + i * melodicSpacing }
        }

        // 总输出长度
        val lastNoteEnd = noteStartOffsets.last() + noteBuffers.last().size
        val totalLength = lastNoteEnd + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个音符的采样放置/叠加到输出缓冲区
        for ((i, buffer) in noteBuffers.withIndex()) {
            val offset = noteStartOffsets[i]
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: IntervalQuestion): Long {
        return if (question.playDirection.harmonic) {
            LEAD_SILENCE_MS + HARMONIC_NOTE_DURATION_MS + TAIL_SILENCE_MS
        } else {
            LEAD_SILENCE_MS + 2 * MELODIC_NOTE_DURATION_MS + TAIL_SILENCE_MS
        }
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

        /** 旋律模式单音持续时长（毫秒）。 */
        const val MELODIC_NOTE_DURATION_MS = 700L

        /** 和声模式单音持续时长（毫秒，更长以便听辨色彩）。 */
        const val HARMONIC_NOTE_DURATION_MS = 1500L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 500L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
