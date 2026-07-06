package com.pianocompanion.moderecognition

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * 调式识别训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ModeQuestion] 的 MIDI 音符序列渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，依次播放音阶各音（上行，或上行+下行），
 * 帮助用户通过听辨音阶的音程结构识别调式。
 *
 * @param synth 音色合成器实例
 */
class ModeRecognitionAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {
    /**
     * 为题目渲染音频。
     *
     * 根据 [ModeQuestion.playMode] 决定渲染上行或上下行。
     */
    fun render(question: ModeQuestion): FloatArray {
        return if (question.playMode == PlayMode.ASCENDING_DESCENDING && question.descendingMidiNotes.isNotEmpty()) {
            renderSequence(question.ascendingMidiNotes, addTailSilence = false) +
                renderSequence(question.descendingMidiNotes, addLeadSilence = false)
        } else {
            renderSequence(question.ascendingMidiNotes)
        }
    }

    /**
     * 渲染任意 MIDI 音符序列为连续 PCM 采样。
     *
     * 每个音符依次播放（不重叠），前一个音符结束后紧接着下一个音符开始。
     *
     * @param midiNotes MIDI 音符序列
     * @param addLeadSilence 是否添加前导静音
     * @param addTailSilence 是否添加尾部静音
     */
    fun renderSequence(
        midiNotes: List<Int>,
        addLeadSilence: Boolean = true,
        addTailSilence: Boolean = true,
        velocity: Int = DEFAULT_VELOCITY
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val leadSamples = if (addLeadSilence) {
            (SAMPLE_RATE * LEAD_SILENCE_MS / 1000.0).toInt()
        } else 0
        val tailSamples = if (addTailSilence) {
            (SAMPLE_RATE * TAIL_SILENCE_MS / 1000.0).toInt()
        } else 0

        val noteBuffers = midiNotes.map { midi ->
            synth.synthesize(
                frequency = MusicUtils.midiToFrequency(midi),
                durationMs = NOTE_DURATION_MS,
                velocity = velocity
            )
        }

        val totalNoteSamples = noteBuffers.sumOf { it.size }
        val totalLength = leadSamples + totalNoteSamples + tailSamples
        val output = FloatArray(totalLength)

        var offset = leadSamples
        for (buffer in noteBuffers) {
            for (j in buffer.indices) {
                val outIdx = offset + j
                if (outIdx < totalLength) {
                    output[outIdx] = buffer[j]
                }
            }
            offset += buffer.size
        }

        return softLimit(output)
    }

    /** 预估渲染时长（毫秒），用于 UI 进度显示。 */
    fun estimateDurationMs(question: ModeQuestion): Long {
        val noteCount = question.ascendingMidiNotes.size +
            if (question.playMode == PlayMode.ASCENDING_DESCENDING) question.descendingMidiNotes.size else 0
        return LEAD_SILENCE_MS + noteCount * NOTE_DURATION_MS + TAIL_SILENCE_MS
    }

    /** 软限幅：保持低音量信号线性度，防止边界削波。 */
    private fun softLimit(input: FloatArray): FloatArray {
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + abs(x) / SOFTCLIP_K)).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 450L

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 150L

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 350L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 68

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.4f
    }
}
