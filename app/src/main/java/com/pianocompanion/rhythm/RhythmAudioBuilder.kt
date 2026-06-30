package com.pianocompanion.rhythm

import com.pianocompanion.audio.PianoToneSynthesizer
import com.pianocompanion.util.MusicUtils
import kotlin.math.tanh

/**
 * 节奏型音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [RhythmPattern] 转换为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * 音频结构：
 * 1. **预备拍（Count-off）**：节拍器「嗒嗒嗒嗒」预备拍，帮助用户做好心理准备
 * 2. **节奏型本体**：按时间轴排列各音符，休止符处保持静音
 * 3. **结尾衰减**：让最后一个音符自然衰减
 *
 * 复用 [PianoToneSynthesizer] 合成音符音色，节拍器嗒声使用短促正弦脉冲。
 *
 * @param synth 音色合成器实例
 */
class RhythmAudioBuilder(
    private val synth: PianoToneSynthesizer = PianoToneSynthesizer()
) {
    /**
     * 为节奏型渲染音频（含预备拍）。
     *
     * @param pattern 节奏型
     * @param countOffBeats 预备拍数（默认 4 拍），设为 0 则无预备拍
     * @return PCM Float 缓冲区，值在 [-1.0, 1.0] 范围内
     */
    fun render(pattern: RhythmPattern, countOffBeats: Int = 4): FloatArray {
        val msPerBeat = pattern.msPerBeat
        val onsetTimes = pattern.toOnsetTimes()

        // ── 计算总长度 ────────────────────────────────────
        val countOffMs = countOffBeats * msPerBeat
        val patternMs = pattern.totalDurationMs
        val tailMs = 200L // 结尾衰减 200ms
        val totalMs = countOffMs + patternMs + tailMs
        val totalSamples = msToSamples(totalMs)

        val output = FloatArray(totalSamples)

        // ── 写入预备拍（节拍器嗒声）──────────────────────
        for (beat in 0 until countOffBeats) {
            val clickMs = beat * msPerBeat
            val clickStart = msToSamples(clickMs)
            val click = renderClick(CLICK_DURATION_MS, isAccent = (beat == 0))
            mixInto(output, click, clickStart)
        }

        // ── 写入节奏型音符 ────────────────────────────────
        for (onset in onsetTimes) {
            if (onset.isRest) continue
            val absMs = countOffMs + onset.onsetMs
            val startSample = msToSamples(absMs)
            val noteBuf = synth.synthesize(
                frequency = MusicUtils.midiToFrequency(onset.midiNote),
                durationMs = onset.durationMs,
                velocity = DEFAULT_VELOCITY
            )
            mixInto(output, noteBuf, startSample)
        }

        // 软限幅防削波
        return softLimit(output)
    }

    /**
     * 渲染节拍器「嗒」声（短促正弦脉冲 + 快速衰减）。
     *
     * @param durationMs 时长（毫秒）
     * @param isAccent 是否为强拍（音量更大）
     * @return PCM Float 缓冲区
     */
    fun renderClick(durationMs: Long, isAccent: Boolean = false): FloatArray {
        val numSamples = msToSamples(durationMs).coerceAtLeast(1)
        val buffer = FloatArray(numSamples)
        val freq = if (isAccent) ACCENT_CLICK_FREQ else NORMAL_CLICK_FREQ
        val amp = if (isAccent) ACCENT_CLICK_AMP else NORMAL_CLICK_AMP

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // 快速指数衰减（节拍器嗒声特征）
            val env = kotlin.math.exp(-t * CLICK_DECAY_RATE)
            buffer[i] = (amp * env * kotlin.math.sin(2.0 * Math.PI * freq * t)).toFloat()
        }
        return buffer
    }

    // ── 工具方法 ──────────────────────────────────────────

    /** 将 src 混入 output 从 startSample 开始的位置（叠加）。 */
    private fun mixInto(output: FloatArray, src: FloatArray, startSample: Int) {
        for (i in src.indices) {
            val idx = startSample + i
            if (idx in output.indices) {
                output[idx] += src[i]
            }
        }
    }

    /** 软限幅：x / (1 + |x| / k)。 */
    private fun softLimit(input: FloatArray): FloatArray {
        val k = SOFTCLIP_K
        return FloatArray(input.size) { i ->
            val x = input[i]
            (x / (1.0f + kotlin.math.abs(x) / k)).coerceIn(-1f, 1f)
        }
    }

    private fun msToSamples(ms: Long): Int =
        (SAMPLE_RATE * ms / 1000.0).toInt()

    companion object {
        const val SAMPLE_RATE = PianoToneSynthesizer.DEFAULT_SAMPLE_RATE

        /** 默认力度。 */
        const val DEFAULT_VELOCITY = 75

        /** 软限幅拐点。 */
        const val SOFTCLIP_K = 1.5f

        /** 节拍器嗒声时长（毫秒）。 */
        const val CLICK_DURATION_MS = 30L

        /** 节拍器嗒声频率。 */
        const val NORMAL_CLICK_FREQ = 1000.0
        const val ACCENT_CLICK_FREQ = 1500.0

        /** 节拍器嗒声音量。 */
        const val NORMAL_CLICK_AMP = 0.3
        const val ACCENT_CLICK_AMP = 0.45

        /** 节拍器嗒声衰减速率。 */
        const val CLICK_DECAY_RATE = 200.0
    }
}
