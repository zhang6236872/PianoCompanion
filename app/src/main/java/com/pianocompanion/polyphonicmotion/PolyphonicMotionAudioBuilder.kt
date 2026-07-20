package com.pianocompanion.polyphonicmotion

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 复调运动辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [MotionQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 复调运动听辨：**
 *
 * 渲染**两个同时进行的声部**（高声部 upper + 低声部 lower），它们逐音对齐——
 * 每个「节拍点」上高声部和低声部同时鸣响各自的音符，形成一系列二音「对」。
 * 用户聆听这两条线条的相对运动关系（同向 / 反向 / 斜向）。
 *
 * 为帮助听辨：
 * - **声部分离**：高声部位于中高音区、低声部位于中低音区（天然约两个八度分离），
 *   使耳朵能独立追踪每条线条；
 * - **音色统一**：两声部使用相同的钢琴风格加法合成（基频 + 谐波），运动关系成为
 *   唯一显著特征（而非音色差异）；
 * - **断奏 + 微间隙**：每个音符末尾留出短暂静默，使每个「节拍点」的起音清晰可辨，
 *   帮助用户逐拍比较两条线条的走向。
 *
 * @param sampleRate 采样率
 */
class MotionAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个声部音符事件。
     *
     * @param midi 音符的 MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double
    )

    /** 为题目渲染音频。 */
    fun render(question: MotionQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的全部音符事件（高声部 + 低声部，逐音对齐）。
     *
     * 每个节拍点 i 的起始时间为 i × (noteDuration + GAP_MS)。
     * 高声部和低声部在同一节拍点同时起音。
     */
    fun buildNoteEvents(question: MotionQuestion): List<NoteEvent> {
        val noteDuration = question.difficulty.noteDurationMs.toDouble()
        val events = mutableListOf<NoteEvent>()
        for (i in question.upperVoice.indices) {
            val onset = i * (noteDuration + GAP_MS)
            events.add(NoteEvent(question.upperVoice[i], onset, noteDuration))
            events.add(NoteEvent(question.lowerVoice[i], onset, noteDuration))
        }
        return events
    }

    /**
     * 计算每个节拍点的起始时间（毫秒），公开以便单元测试验证。
     */
    fun computeOnsetTimes(question: MotionQuestion): DoubleArray {
        val noteDuration = question.difficulty.noteDurationMs.toDouble()
        return DoubleArray(question.upperVoice.size) { i ->
            i * (noteDuration + GAP_MS)
        }
    }

    /**
     * 计算高声部逐拍移动方向序列（+1 上行 / -1 下行 / 0 保持），用于测试。
     */
    fun upperDirections(question: MotionQuestion): List<Int> =
        directions(question.upperVoice)

    /**
     * 计算低声部逐拍移动方向序列（+1 上行 / -1 下行 / 0 保持），用于测试。
     */
    fun lowerDirections(question: MotionQuestion): List<Int> =
        directions(question.lowerVoice)

    private fun directions(voice: List<Int>): List<Int> {
        if (voice.size < 2) return emptyList()
        val result = mutableListOf<Int>()
        for (i in 1 until voice.size) {
            val d = voice[i] - voice[i - 1]
            result.add(when { d > 0 -> 1; d < 0 -> -1; else -> 0 })
        }
        return result
    }

    /** 计算 MIDI 音高的频率（用于测试验证）。 */
    fun noteFrequency(midi: Int): Double = midiToFreq(midi)

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
            val noteWave = generateNote(event.midi, noteSamples)
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
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
    fun estimateDurationMs(question: MotionQuestion): Long {
        val events = buildNoteEvents(question)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     */
    private fun generateNote(midi: Int, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * NOTE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0
        val frequency = midiToFreq(midi)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (h in HARMONICS.indices) {
                val freq = frequency * (h + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * HARMONICS[h]
            }
            wave[i] = (sample * envelope * NOTE_GAIN).toFloat()
        }

        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 150.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 节拍点之间的间隔（毫秒）—— 加在每个音符持续时间之后。 */
        const val GAP_MS = 80.0

        /** 单音衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 280.0

        /** 单音增益。 */
        const val NOTE_GAIN = 0.32f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
