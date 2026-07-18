package com.pianocompanion.dynamicsdirectiontraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 力度变化方向辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [DynamicsDirectionQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 力度变化方向听辨：**
 *
 * 渲染一段由 [MELODY_STEPS].size 个音符组成的短句。每个音符的 **增益（gain）** 按所选
 * [DynamicsDirection] 的轮廓（contour）缩放，而旋律本身（音高序列）保持固定 ——
 * 这样「力度变化」就成为音频中唯一显著的特征，用户据此判断方向。
 *
 * **增益轮廓（gain contour）**：归一化位置 t∈[0,1] 映射到增益
 * - 渐强（CRESCENDO）：从 [MIN_GAIN] 线性升到 [MAX_GAIN]
 * - 渐弱（DECRESCENDO）：从 [MAX_GAIN] 线性降到 [MIN_GAIN]
 * - 持平（STEADY）：恒定 [MID_GAIN]
 * - 渐强渐弱（SWELL）：山丘形（sin(πt)），两端 [MIN_GAIN]、中间 [MAX_GAIN]
 * - 渐弱渐强（REVERSE_SWELL）：山谷形，两端 [MAX_GAIN]、中间 [MIN_GAIN]
 *
 * **不做峰值归一化**：仅用 tanh 软限幅，保留各音符间绝对的响度关系（渐强的尾音必须比
 * 首音响），这是力度听辨的基础。若做峰值归一化会把所有方向的最响音拉到同一电平，
 * 模糊绝对力度差异。
 *
 * 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络。
 *
 * @param sampleRate 采样率
 */
class DynamicsDirectionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param gain 该音符的增益（按力度方向轮廓缩放）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float
    )

    /** 为题目渲染音频。 */
    fun render(question: DynamicsDirectionQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符事件序列。
     *
     * 时序：每个音符间隔 [NOTE_ONSET_SPACING_MS]，持续 [NOTE_DURATION_MS]。
     * 音高 = 主音 + 固定旋律步进 [MELODY_STEPS]；增益 = 力度方向轮廓。
     */
    fun buildNoteEvents(question: DynamicsDirectionQuestion): List<NoteEvent> {
        val gains = gainContour(question.direction)
        val events = mutableListOf<NoteEvent>()
        for ((idx, step) in MELODY_STEPS.withIndex()) {
            val midi = (question.tonicMidi + step).coerceIn(0, 127)
            val onset = idx * NOTE_ONSET_SPACING_MS
            events += NoteEvent(midi, onset, NOTE_DURATION_MS, gains[idx])
        }
        return events
    }

    /**
     * 计算给定力度方向的逐音符增益数组（长度 = [MELODY_STEPS].size）。
     */
    fun gainContour(direction: DynamicsDirection): FloatArray {
        val n = MELODY_STEPS.size
        val gains = FloatArray(n)
        for (i in 0 until n) {
            val t = if (n > 1) i.toDouble() / (n - 1) else 0.5
            gains[i] = contourValue(direction, t)
        }
        return gains
    }

    /**
     * 归一化位置 t∈[0,1] 处的增益值（按力度方向轮廓）。
     * 公开以便单元测试验证轮廓形状。
     */
    fun contourValue(direction: DynamicsDirection, t: Double): Float {
        val tClamped = t.coerceIn(0.0, 1.0)
        val mountain = sin(PI * tClamped) // 山丘：两端 0，中间 1
        return when (direction) {
            DynamicsDirection.CRESCENDO -> lerp(MIN_GAIN, MAX_GAIN, tClamped).toFloat()
            DynamicsDirection.DECRESCENDO -> lerp(MAX_GAIN, MIN_GAIN, tClamped).toFloat()
            DynamicsDirection.STEADY -> MID_GAIN
            DynamicsDirection.SWELL -> lerp(MIN_GAIN, MAX_GAIN, mountain).toFloat()
            DynamicsDirection.REVERSE_SWELL -> lerp(MAX_GAIN, MIN_GAIN, mountain).toFloat()
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    // ── 渲染 ──────────────────────────────────────────

    /** 将音符事件列表渲染为连续 PCM 采样（多音叠加 + tanh 软限幅，不峰值归一化）。 */
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

        // tanh 软限幅防止削波 —— 不做峰值归一化，保留绝对力度关系（力度听辨的关键）
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
    fun estimateDurationMs(question: DynamicsDirectionQuestion): Long {
        val events = buildNoteEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 计算指定音符索引在输出缓冲区中的能量（RMS）。
     *
     * 用于单元测试验证力度轮廓：渐强应使能量递增、渐弱递减、山丘中间最大等。
     * 返回该音符窗口内样本的均方根（root mean square）。
     */
    fun noteRmsEnergy(buffer: FloatArray, noteIndex: Int): Double {
        val onsetSample = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt() +
            (sampleRate * noteIndex * NOTE_ONSET_SPACING_MS / 1000.0).toInt()
        val windowSamples = (sampleRate * NOTE_DURATION_MS / 1000.0).toInt()
        var sumSq = 0.0
        var count = 0
        for (i in onsetSample until minOf(onsetSample + windowSamples, buffer.size)) {
            sumSq += buffer[i].toDouble() * buffer[i]
            count++
        }
        return if (count > 0) kotlin.math.sqrt(sumSq / count) else 0.0
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
        const val TAIL_SILENCE_MS = 350.0

        /** 相邻音符起始间隔（毫秒）。 */
        const val NOTE_ONSET_SPACING_MS = 340.0

        /** 单个音符持续时间（毫秒）。 */
        const val NOTE_DURATION_MS = 300.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 400.0

        /** 最弱音符增益（渐强起点 / 渐弱终点 / 山丘两端）。 */
        const val MIN_GAIN = 0.16

        /** 最强音符增益（渐强终点 / 渐弱起点 / 山丘峰）。 */
        const val MAX_GAIN = 0.80

        /** 中等增益（持平轮廓使用）。 */
        const val MID_GAIN = 0.48f

        /**
         * 固定旋律步进（相对主音的半音偏移）。围绕主音的邻音式波纹，
         * 避免旋律走向与力度走向混淆。9 个音符。
         */
        val MELODY_STEPS: IntArray = intArrayOf(0, 2, 0, 4, 0, 2, 0, 4, 0)

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
