package com.pianocompanion.training

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.tanh

/**
 * 听音训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [EarTrainingQuestion] 转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成单个音符的音色，再按 [PlayMode] 混合：
 *
 * - **上行旋律**：音符从低到高依次弹奏，每个音符有一定时值和间隔
 * - **柱式和弦**：所有音符同时弹奏（叠加），和声效果
 * - **下行旋律**：音符从高到低依次弹奏
 *
 * 多音符叠加时使用软限幅（tanh 近似）防止削波。
 *
 * @param synth 音色合成器实例
 */
class EarTrainingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {
    /** 单个旋律音符的持续时间（毫秒）。 */
    val melodicNoteMs: Long = 600L

    /** 旋律音符之间的间隔（毫秒）。 */
    val noteGapMs: Long = 80L

    /**
     * 为题目渲染音频。
     *
     * @param question 听音训练题目
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(question: EarTrainingQuestion): FloatArray {
        return when (question.playMode) {
            PlayMode.BLOCK -> renderBlock(question.midiNotes)
            PlayMode.ASCENDING, PlayMode.DESCENDING -> renderMelodic(question.midiNotes)
        }
    }

    /**
     * 渲染柱式和弦（所有音符同时发声）。
     */
    private fun renderBlock(midiNotes: List<Int>): FloatArray {
        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = CHORD_DURATION_MS,
                velocity = DEFAULT_VELOCITY
            )
        }
        if (noteBuffers.isEmpty()) return FloatArray(0)

        val maxLen = noteBuffers.maxOf { it.size }
        val output = FloatArray(maxLen)

        for (buf in noteBuffers) {
            for (i in buf.indices) {
                output[i] += buf[i]
            }
        }

        // 软限幅，防止多音叠加削波
        return softLimit(output)
    }

    /**
     * 渲染旋律（音符依次弹奏）。
     */
    private fun renderMelodic(midiNotes: List<Int>): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val noteSampleLens = midiNotes.map { midi ->
            (synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = melodicNoteMs,
                velocity = DEFAULT_VELOCITY
            ).size)
        }

        // 每个音符的实际采样数 + 间隔采样数
        val gapSamples = msToSamples(noteGapMs)
        val totalSamples = noteSampleLens.sum() + gapSamples * (midiNotes.size - 1) + SILENCE_TAIL_SAMPLES

        val output = FloatArray(totalSamples)
        var offset = 0

        for ((index, midi) in midiNotes.withIndex()) {
            val buf = synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = melodicNoteMs,
                velocity = DEFAULT_VELOCITY
            )
            for (i in buf.indices) {
                if (offset + i < output.size) {
                    output[offset + i] += buf[i]
                }
            }
            offset += buf.size
            if (index < midiNotes.size - 1) {
                offset += gapSamples
            }
        }

        return output
    }

    /**
     * 软限幅：使用 tanh 近似 x / (1 + |x| / k)，将输出限制在 [-1.0, 1.0]，
     * 同时保持低音量信号的线性度。
     */
    private fun softLimit(input: FloatArray): FloatArray {
        val k = SOFTCLIP_K
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + kotlin.math.abs(x) / k)).coerceIn(-1f, 1f)
        }
    }

    private fun msToSamples(ms: Long): Int =
        (synth.let { SAMPLE_RATE } * ms / 1000.0).toInt()

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 柱式和弦持续时间（毫秒）。 */
        const val CHORD_DURATION_MS = 1200L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 70

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 结尾静音采样数（让最后一个音符自然衰减）。 */
        val SILENCE_TAIL_SAMPLES = (SAMPLE_RATE * 0.15).toInt()
    }
}
