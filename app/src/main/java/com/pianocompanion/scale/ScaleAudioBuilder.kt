package com.pianocompanion.scale

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.data.model.Articulation
import com.pianocompanion.util.MusicUtils

/**
 * 音阶音频渲染器（纯 Kotlin，无 Android 依赖，完全可单测）。
 *
 * 使用 [PianoToneSynthesizer] 将 [ScaleInfo] 渲染为 PCM 浮点采样。
 * 支持上行、下行、上下行（先上后下）三种播放模式。
 *
 * 渲染流程：
 * 1. 每个音符按顺序用 PianoToneSynthesizer 合成
 * 2. 按时间轴将所有音符的采样依次排列（非叠加，因为音阶是依次弹奏的）
 * 3. 软限幅防止边界处削波
 * 4. 前导静音（200ms）+ 尾部静音（400ms）
 */
class ScaleAudioBuilder(
    private val synthesizer: PianoToneSynthesizer = PianoToneSynthesizer(),
    private val sampleRate: Int = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val LEAD_SILENCE_MS = 200L
        const val TAIL_SILENCE_MS = 400L
        const val NOTE_DURATION_MS = 500L
        private const val SOFT_CLIP_K = 0.85f
    }

    /**
     * 渲染上行音阶。
     */
    fun renderAscending(info: ScaleInfo, velocity: Int = 70): FloatArray {
        return renderSequence(info.ascendingMidiNotes, velocity)
    }

    /**
     * 渲染下行音阶。
     */
    fun renderDescending(info: ScaleInfo, velocity: Int = 70): FloatArray {
        return renderSequence(info.descendingMidiNotes, velocity)
    }

    /**
     * 渲染上下行音阶（先上行后下行）。
     */
    fun renderAscendingDescending(info: ScaleInfo, velocity: Int = 70): FloatArray {
        val upBuffer = renderSequence(info.ascendingMidiNotes, velocity, addTailSilence = false)
        val downBuffer = renderSequence(info.descendingMidiNotes, velocity, addLeadSilence = false)
        return upBuffer + downBuffer
    }

    /**
     * 核心渲染方法：将 MIDI 音符序列渲染为连续的 PCM 采样。
     *
     * 每个音符依次播放（不重叠），前一个音符结束后紧接着下一个音符开始。
     * 音符之间有极短的间隙（10ms），模拟真实的断奏效果。
     *
     * @param midiNotes MIDI 音符序列
     * @param velocity 力度（1-127）
     * @param addLeadSilence 是否添加前导静音
     * @param addTailSilence 是否添加尾部静音
     */
    private fun renderSequence(
        midiNotes: List<Int>,
        velocity: Int,
        addLeadSilence: Boolean = true,
        addTailSilence: Boolean = true
    ): FloatArray {
        if (midiNotes.isEmpty()) return FloatArray(0)

        val leadSamples = if (addLeadSilence) {
            (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        } else 0

        val tailSamples = if (addTailSilence) {
            (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()
        } else 0

        // 为每个音符合成采样
        val noteBuffers = midiNotes.map { midi ->
            val freq = MusicUtils.midiToFrequency(midi)
            synthesizer.synthesize(freq, NOTE_DURATION_MS, velocity, Articulation.TENUTO)
        }

        // 计算总长度
        val totalNoteSamples = noteBuffers.sumOf { it.size }
        val totalLength = leadSamples + totalNoteSamples + tailSamples

        val output = FloatArray(totalLength)

        // 依次填充每个音符
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

        // 软限幅
        for (i in output.indices) {
            output[i] = softClip(output[i])
        }

        return output
    }

    /**
     * 软限幅函数（tanh 近似）。
     */
    private fun softClip(x: Float): Float {
        val absX = if (x < 0) -x else x
        return (x / (1.0f + absX / SOFT_CLIP_K)).coerceIn(-1.0f, 1.0f)
    }

    /**
     * 计算上行音阶渲染输出的预期总时长（毫秒）。
     */
    fun estimateDurationMs(info: ScaleInfo, direction: PlayDirection): Long {
        val noteCount = when (direction) {
            PlayDirection.ASCENDING -> info.ascendingMidiNotes.size
            PlayDirection.DESCENDING -> info.descendingMidiNotes.size
            PlayDirection.ASCENDING_DESCENDING ->
                info.ascendingMidiNotes.size + info.descendingMidiNotes.size
        }
        return LEAD_SILENCE_MS + noteCount * NOTE_DURATION_MS + TAIL_SILENCE_MS
    }
}

/**
 * 播放方向。
 */
enum class PlayDirection(val displayName: String) {
    ASCENDING("上行"),
    DESCENDING("下行"),
    ASCENDING_DESCENDING("上下行")
}
