package com.pianocompanion.melodymemory

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils
import kotlin.math.absoluteValue

/**
 * 旋律记忆训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [MelodyQuestion] 的 MIDI 音符序列渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，依次弹奏旋律各音（按时间轴排列）。
 *
 * 渲染流程：
 * 1. 每个音符用 PianoToneSynthesizer 合成（加法合成 + 指数衰减包络）
 * 2. 按时间轴将各音符采样放置到各自的起始位置（依次排列）
 * 3. 软限幅防止叠加时削波
 *
 * @param synth 音色合成器实例
 * @param sampleRate 采样率
 */
class MelodyMemoryAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    /**
     * 为题目渲染音频。
     *
     * 根据 [MelodyQuestion.tempo] 决定每音时长。
     */
    fun render(question: MelodyQuestion): FloatArray {
        return renderNotes(question.midiNotes, question.tempo)
    }

    /**
     * 将 MIDI 音符序列渲染为连续 PCM 采样（依次弹奏）。
     *
     * @param midiNotes MIDI 音符列表（按旋律顺序）
     * @param tempo 播放速度
     * @param velocity 力度（1-127）
     */
    fun renderNotes(
        midiNotes: List<Int>,
        tempo: MelodyTempo,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val noteDurationMs = tempo.noteDurationMs
        val noteDurationSamples = (sampleRate * noteDurationMs / 1000.0).toInt()
        val noteSpacingSamples = noteDurationSamples // 音符依次排列，前一个结束后下一个立即开始

        // 为每个音符合成采样
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = noteDurationMs,
                velocity = velocity,
                articulation = Articulation.NONE
            )
        }

        val leadSilenceSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val tailSilenceSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        // 每个音符的起始偏移
        val noteStartOffsets = noteBuffers.indices.map { i ->
            leadSilenceSamples + i * noteSpacingSamples
        }

        // 总输出长度
        val lastNoteEnd = noteStartOffsets.last() + noteBuffers.last().size
        val totalLength = lastNoteEnd + tailSilenceSamples

        val output = FloatArray(totalLength)

        // 时间轴混合：将每个音符的采样放置到输出缓冲区
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
    fun estimateDurationMs(question: MelodyQuestion): Long {
        val noteCount = question.midiNotes.size
        return LEAD_SILENCE_MS + noteCount * question.tempo.noteDurationMs + TAIL_SILENCE_MS
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
        const val LEAD_SILENCE_MS = 250L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 72

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 0.7f
    }
}
