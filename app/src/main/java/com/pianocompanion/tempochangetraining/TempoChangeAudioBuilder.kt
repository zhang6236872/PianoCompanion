package com.pianocompanion.tempochangetraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 速度变化方向辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TempoChangeQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 速度变化方向听辨：**
 *
 * 渲染一段由 [MELODY_STEPS].size 个音符组成的短句。每个音符的 **音高与响度保持固定**
 * （全部使用相同的 [MID_GAIN]），唯一变化的是相邻音符的**起音时间间距（inter-onset
 * interval）**——间距按所选 [TempoChange] 的速度轮廓（tempo contour）缩放。
 * 这样「速度变化方向」就成为音频中唯一显著的特征，用户据此判断方向。
 *
 * **速度轮廓（tempo contour）**：归一化位置 t∈[0,1] 映射到「相邻音符间距（毫秒）」
 * - 渐快（ACCELERANDO）：从 [MAX_INTERVAL_MS] 线性降到 [MIN_INTERVAL_MS]（越走越快）
 * - 渐慢（RITARDANDO）：从 [MIN_INTERVAL_MS] 线性升到 [MAX_INTERVAL_MS]（越走越慢）
 * - 稳定（STEADY）：恒定 [MID_INTERVAL_MS]
 * - 渐快渐慢（ACCEL_RIT）：山丘形速度——两端慢（大间距）、中间快（小间距）
 * - 渐慢渐快（RIT_ACCEL）：山谷形速度——两端快（小间距）、中间慢（大间距）
 *
 * 这与「力度变化方向」[com.pianocompanion.dynamicsdirectiontraining.DynamicsDirectionAudioBuilder]
 * 在结构上完全对称：那里变化的是逐音符增益，这里变化的是逐音符间距。
 *
 * 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络。
 *
 * @param sampleRate 采样率
 */
class TempoChangeAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param gain 该音符的增益（速度方向训练中所有音符增益恒定）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float
    )

    /** 为题目渲染音频。 */
    fun render(question: TempoChangeQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符事件序列。
     *
     * 时序：相邻音符的间距由 [intervalAt] 决定（按速度方向轮廓），累加得到每个音符的起始时间。
     * 音高 = 主音 + 固定旋律步进 [MELODY_STEPS]；增益恒定 [MID_GAIN]。
     */
    fun buildNoteEvents(question: TempoChangeQuestion): List<NoteEvent> {
        val onsets = computeOnsetTimes(question)
        val events = mutableListOf<NoteEvent>()
        for ((idx, step) in MELODY_STEPS.withIndex()) {
            val midi = (question.tonicMidi + step).coerceIn(0, 127)
            events += NoteEvent(midi, onsets[idx], NOTE_DURATION_MS, MID_GAIN)
        }
        return events
    }

    /**
     * 计算指定题目的逐音符起始时间（毫秒）。
     *
     * onset[0] = 0；onset[i] = onset[i-1] + intervalAt(direction, t_{i-1})，
     * 其中 t_j = j / (n-1)。公开以便单元测试验证间距走势。
     */
    fun computeOnsetTimes(question: TempoChangeQuestion): DoubleArray {
        val n = MELODY_STEPS.size
        val onsets = DoubleArray(n)
        for (i in 1 until n) {
            val tPrev = if (n > 1) (i - 1).toDouble() / (n - 1) else 0.5
            onsets[i] = onsets[i - 1] + intervalAt(question.direction, tPrev)
        }
        return onsets
    }

    /**
     * 计算指定题目的相邻音符间距数组（长度 = n-1）。
     *
     * 渐快应为严格递减、渐慢严格递增、稳定恒定、山丘中间最小、山谷中间最大。
     */
    fun computeInterOnsetIntervals(question: TempoChangeQuestion): DoubleArray {
        val onsets = computeOnsetTimes(question)
        val n = onsets.size
        val gaps = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            gaps[i] = onsets[i + 1] - onsets[i]
        }
        return gaps
    }

    /**
     * 归一化位置 t∈[0,1] 处的相邻音符间距（毫秒），按速度方向轮廓。
     * 公开以便单元测试验证轮廓形状。
     */
    fun intervalAt(direction: TempoChange, t: Double): Double {
        val tClamped = t.coerceIn(0.0, 1.0)
        val mountain = sin(PI * tClamped) // 山丘：两端 0，中间 1
        return when (direction) {
            TempoChange.ACCELERANDO -> lerp(MAX_INTERVAL_MS, MIN_INTERVAL_MS, tClamped)
            TempoChange.RITARDANDO -> lerp(MIN_INTERVAL_MS, MAX_INTERVAL_MS, tClamped)
            TempoChange.STEADY -> MID_INTERVAL_MS
            TempoChange.ACCEL_RIT -> lerp(MAX_INTERVAL_MS, MIN_INTERVAL_MS, mountain) // 两端慢、中间快
            TempoChange.RIT_ACCEL -> lerp(MIN_INTERVAL_MS, MAX_INTERVAL_MS, mountain) // 两端快、中间慢
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    // ── 渲染 ──────────────────────────────────────────

    /** 将音符事件列表渲染为连续 PCM 采样（多音叠加 + tanh 软限幅）。 */
    fun renderEvents(events: List<NoteEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐音符合成并叠加
        for (event in events) {
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val wave = generateNote(midiToFreq(event.midi), noteSamples, event.gain)
            for (j in wave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += wave[j]
                }
            }
        }

        // tanh 软限幅防止削波
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble()).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /** MIDI 编号转频率（A4=440Hz, MIDI=69）。 */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: TempoChangeQuestion): Long {
        val events = buildNoteEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     */
    private fun generateNote(frequency: Double, numSamples: Int, gain: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * NOTE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (h in HARMONICS.indices) {
                val freq = frequency * (h + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * HARMONICS[h]
            }
            wave[i] = (sample * envelope * gain).toFloat()
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 200.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 400.0

        /** 单个音符持续时间（毫秒）—— 小于最小间距，保证起音清晰不重叠。 */
        const val NOTE_DURATION_MS = 190.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 180.0

        /** 最快速度（最小间距，毫秒）—— 渐快终点 / 渐慢起点 / 山丘中间。 */
        const val MIN_INTERVAL_MS = 210.0

        /** 最慢速度（最大间距，毫秒）—— 渐慢终点 / 渐快起点 / 山谷中间。 */
        const val MAX_INTERVAL_MS = 470.0

        /** 中等间距（稳定轮廓使用，毫秒）。 */
        const val MID_INTERVAL_MS = 340.0

        /** 所有音符恒定增益（速度方向训练中响度不是线索）。 */
        const val MID_GAIN = 0.5f

        /**
         * 固定旋律步进（相对主音的半音偏移）。围绕主音的邻音式波纹，
         * 避免旋律走向与速度走向混淆。9 个音符。
         */
        val MELODY_STEPS: IntArray = intArrayOf(0, 2, 0, 4, 0, 2, 0, 4, 0)

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
