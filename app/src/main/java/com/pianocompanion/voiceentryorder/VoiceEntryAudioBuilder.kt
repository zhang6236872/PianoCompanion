package com.pianocompanion.voiceentryorder

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 声部进入顺序辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [EntryOrderQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 声部进入顺序听辨：**
 *
 * 渲染**多个先后进入的声部**——每个声部在其音区演奏一段短小动机（D-F-A 琶音），
 * 各声部按 [EntryOrderQuestion.entryOrder] 的顺序依次进入（每个声部的起始时间错开
 * `entryGapMs`）。进入后声部继续重复动机，形成各声部先后加入、最终合奏（tutti）的织体。
 *
 * 用户聆听各音区出现的时间先后，判断进入顺序。
 *
 * 为帮助听辨：
 * - **音区分离**：高/中/低声部相隔约一个八度，使耳朵能清晰区分每条线条；
 * - **统一音色**：所有声部使用相同的钢琴风格加法合成（基频 + 谐波），使音区成为
 *   唯一显著特征（而非音色差异）；
 * - **断奏 + 间隙**：每个音符末尾留出短暂静默，使每个音符起音清晰可辨。
 *
 * @param sampleRate 采样率
 */
class VoiceEntryAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个声部音符事件。
     *
     * @param midi 音符的 MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param register 所属音区
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val register: VoiceRegister
    )

    /** 为题目渲染音频。 */
    fun render(question: EntryOrderQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的全部音符事件（多声部，按进入顺序错开起始）。
     *
     * 处于进入顺序第 `entryPos` 位（0-indexed）的声部，其首个音符起始时间为
     * `entryPos × entryGapMs`，后续音符按 `noteStep`（noteDuration + GAP）等距排列。
     */
    fun buildNoteEvents(question: EntryOrderQuestion): List<NoteEvent> {
        val d = question.difficulty
        val noteStep = d.noteDurationMs + GAP_MS
        val events = mutableListOf<NoteEvent>()

        for ((entryPos, register) in question.entryOrder.withIndex()) {
            val entryOnset = entryPos * d.entryGapMs.toDouble()
            val motif = register.motif
            for (j in 0 until d.notesPerVoice) {
                val onset = entryOnset + j * noteStep
                val midi = motif[j % motif.size]
                events.add(NoteEvent(midi, onset, d.noteDurationMs.toDouble(), register))
            }
        }
        return events
    }

    /**
     * 计算每个声部的进入起始时间（毫秒），公开以便单元测试验证。
     *
     * @return 按进入顺序排列的 (音区 → 进入时间) 映射
     */
    fun entryOnsetsMs(question: EntryOrderQuestion): List<Pair<VoiceRegister, Double>> {
        return question.entryOrder.mapIndexed { index, _ ->
            question.entryOrder[index] to (index * question.difficulty.entryGapMs.toDouble())
        }
    }

    /** 计算音频总时长（毫秒，不含前后静音）。 */
    fun musicDurationMs(question: EntryOrderQuestion): Double {
        val events = buildNoteEvents(question)
        return if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
    }

    /** 计算 MIDI 音高的频率（用于测试验证）。 */
    fun midiToFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)

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

    /** 预估渲染时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: EntryOrderQuestion): Long {
        val musicMs = musicDurationMs(question)
        return (LEAD_SILENCE_MS + musicMs + TAIL_SILENCE_MS).toLong()
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

        /** 音符之间的间隔（毫秒）—— 加在每个音符持续时间之后。 */
        const val GAP_MS = 70.0

        /** 单音衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 280.0

        /** 单音增益。 */
        const val NOTE_GAIN = 0.30f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
