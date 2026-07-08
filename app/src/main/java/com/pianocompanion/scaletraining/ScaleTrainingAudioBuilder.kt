package com.pianocompanion.scaletraining

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 音阶听辨训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ScaleQuestion] 的音阶序列渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色。
 *
 * 渲染流程：
 * 1. 每个音符用 PianoToneSynthesizer 合成（依次播放，旋律式 = 音符一个接一个）
 * 2. 每个音符渲染为一段 PCM 采样（固定时长）
 * 3. 多个音符按时间轴依次排列（音符之间有微小间隔）
 * 4. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class ScaleTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频（音阶序列）。
     */
    fun render(question: ScaleQuestion): FloatArray {
        return renderScale(question.midiNotes)
    }

    /**
     * 将音阶序列渲染为连续 PCM 采样。
     *
     * 每个音符依次播放（旋律式），音符之间有微小间隔。
     *
     * @param midiNotes 音阶的 MIDI 音符号列表（按播放顺序）
     * @param velocity 力度（1-127）
     */
    fun renderScale(
        midiNotes: List<Int>,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val noteGapSamples = (sampleRate * NOTE_GAP_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 为每个音符合成 PCM 采样
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = NOTE_DURATION_MS,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        // 计算各音符在输出缓冲区中的起始偏移
        val noteStartOffsets = noteBuffers.indices.map { noteIndex ->
            var offset = leadSilenceSamples
            for (i in 0 until noteIndex) {
                offset += noteBuffers[i].size + noteGapSamples
            }
            offset
        }

        // 总输出长度
        val lastNoteEnd = noteStartOffsets.last() + noteBuffers.last().size
        val totalLength = lastNoteEnd + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个音符的采样按偏移写入输出缓冲区
        for ((i, buffer) in noteBuffers.withIndex()) {
            val offset = noteStartOffsets[i]
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] += buffer[j]
                }
            }
        }

        // 软限幅（单个音符不会削波，但保持一致性）
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: ScaleQuestion): Long {
        val noteCount = question.midiNotes.size
        val gaps = (noteCount - 1) * NOTE_GAP_MS
        return LEAD_SILENCE_MS + noteCount * NOTE_DURATION_MS + gaps + TAIL_SILENCE_MS
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

        /** 单个音符持续时间（毫秒）。音阶需要流畅但足够听辨色彩变化。 */
        const val NOTE_DURATION_MS = 600L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 150L

        /** 音符之间的间隔（毫秒），让每个音符清晰可辨。 */
        const val NOTE_GAP_MS = 80L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
