package com.pianocompanion.rhythmreading

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.abs

/**
 * 节奏视读训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将节奏序列转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]），
 * 生成一条**节拍点击音轨（click track）**：每个音符在起始时刻发出一声短促的钢琴音，
 * 音与音之间的间距严格按各自时值（拍数 × 每拍毫秒）排列，让用户听到节奏型的真实律动。
 * 休止符不发声但同样推进时间游标。
 *
 * 结构：前导静音 → [音符起始点击 + 时值间距] × N → 尾部静音
 *
 * 复用 [PianoToneSynthesizer] 合成钢琴音色，所有点击音叠加到统一输出缓冲区并软限幅防削波。
 *
 * @param synth 音色合成器实例
 */
class RhythmReadingAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {

    /**
     * 为节奏序列渲染节拍点击音轨。
     *
     * @param items 节奏序列
     * @param bpm 速度（每分钟四分音符数），默认 100
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(items: List<RhythmItem>, bpm: Int = DEFAULT_BPM): FloatArray {
        if (items.isEmpty()) return FloatArray(0)

        val beatMs = 60000.0 / bpm // 每拍（四分音符）毫秒数
        val patternMs = items.sumOf { it.beats * beatMs }
        val totalMs = SILENCE_LEAD_MS + patternMs + SILENCE_TAIL_MS
        val totalSamples = (SAMPLE_RATE * totalMs / 1000.0).toInt()
        val output = FloatArray(totalSamples)

        var cursorMs = SILENCE_LEAD_MS.toDouble()
        for (item in items) {
            if (!item.isRest) {
                // 在当前游标位置渲染一声短促点击音
                val tone = synth.synthesize(
                    frequency = MusicUtils.midiToFrequency(CLICK_MIDI),
                    durationMs = CLICK_DURATION_MS,
                    velocity = DEFAULT_VELOCITY
                )
                val startSample = (SAMPLE_RATE * cursorMs / 1000.0).toInt()
                for (i in tone.indices) {
                    val idx = startSample + i
                    if (idx in output.indices) {
                        output[idx] += tone[i]
                    }
                }
            }
            // 按时值推进游标（音符和休止符都推进）
            cursorMs += item.beats * beatMs
        }

        return softLimit(output)
    }

    /**
     * 预估渲染时长（毫秒），用于 UI 进度显示。
     *
     * @param items 节奏序列
     * @param bpm 速度
     */
    fun estimateDurationMs(items: List<RhythmItem>, bpm: Int = DEFAULT_BPM): Long {
        val beatMs = 60000.0 / bpm
        val patternMs = items.sumOf { it.beats * beatMs }
        return (SILENCE_LEAD_MS + patternMs + SILENCE_TAIL_MS).toLong()
    }

    /**
     * 软限幅：使用有理函数近似将输出限制在 [-1.0, 1.0]，
     * 同时保持低音量信号的线性度。
     */
    private fun softLimit(input: FloatArray): FloatArray {
        val k = SOFTCLIP_K
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + abs(x) / k)).coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 默认速度（BPM）。 */
        const val DEFAULT_BPM = 100

        /** 点击音的 MIDI 音符号（E5 = 76，明亮清晰的点击感）。 */
        const val CLICK_MIDI = 76

        /** 单次点击音持续时间（毫秒）。 */
        const val CLICK_DURATION_MS = 130L

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 80

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 前导静音（毫秒）。 */
        const val SILENCE_LEAD_MS = 150L

        /** 尾部静音（毫秒）。 */
        const val SILENCE_TAIL_MS = 400L
    }
}
