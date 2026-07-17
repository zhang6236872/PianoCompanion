package com.pianocompanion.scaledegreetraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 调内音级辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ScaleDegreeQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 调内音级听辨（相对音高训练）：**
 *
 * 音频分两段呈现：
 * 1. **主和弦（建立调性）**：播放主三和弦 I（根音=主音 Do，三音=Mi，五音=Sol）
 *    以琶音方式依次响起后叠置延音，让听者建立「DO 在哪里」的调性中心感。
 *    这是相对音高训练的关键——没有调性参照，音级无从判断。
 * 2. **目标音**：间隔 [TARGET_GAP_MS] 后播放目标音，用户判断它是调内的第几级。
 *
 * 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络。多音叠加后归一化
 * 并用 tanh 软限幅防止削波。
 *
 * @param sampleRate 采样率
 */
class ScaleDegreeAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param gain 该音符的增益（默认 NOTE_GAIN）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float = NOTE_GAIN
    )

    /** 主和弦的三个音级 MIDI 音高（根音/三音/五音）。 */
    data class TonicChord(val root: Int, val third: Int, val fifth: Int) {
        /** 三音相对根音的半音数（大调 = 4）。 */
        val thirdSemitones: Int get() = third - root
        /** 五音相对根音的半音数（纯五度 = 7）。 */
        val fifthSemitones: Int get() = fifth - root
    }

    /**
     * 为题目渲染音频。
     */
    fun render(question: ScaleDegreeQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符事件序列。
     *
     * 时序：
     * - 主和弦琶音：Do(0ms) → Mi(ARPEGGIO_STEP_MS) → Sol(2*ARPEGGIO_STEP_MS)，
     *   三个音各自持续 CHORD_NOTE_MS，且后续叠置延音到 CHORD_TOTAL_MS
     * - 目标音：在 CHORD_TOTAL_MS + TARGET_GAP_MS 处开始，持续 TARGET_NOTE_MS
     */
    fun buildNoteEvents(question: ScaleDegreeQuestion): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()

        // 主和弦琶音（Do-Mi-Sol 依次响起）
        val chord = buildTonicChord(question.tonicMidi)
        events += NoteEvent(chord.root, 0.0, CHORD_TOTAL_MS, gain = CHORD_NOTE_GAIN)
        events += NoteEvent(chord.third, ARPEGGIO_STEP_MS, CHORD_TOTAL_MS - ARPEGGIO_STEP_MS, gain = CHORD_NOTE_GAIN)
        events += NoteEvent(chord.fifth, ARPEGGIO_STEP_MS * 2, CHORD_TOTAL_MS - ARPEGGIO_STEP_MS * 2, gain = CHORD_NOTE_GAIN)

        // 目标音
        events += NoteEvent(question.targetMidi, CHORD_TOTAL_MS + TARGET_GAP_MS, TARGET_NOTE_MS, gain = TARGET_GAIN)

        return events
    }

    /**
     * 构建主三和弦（I 和弦 = 根音 Do + 三音 Mi + 五音 Sol）。
     */
    fun buildTonicChord(tonicMidi: Int): TonicChord {
        return TonicChord(
            root = tonicMidi,
            third = tonicMidi + 4,  // 大三度
            fifth = tonicMidi + 7   // 纯五度
        )
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样（多音叠加）。
     */
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

        // 归一化
        val maxAbs = output.maxOfOrNull { abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) MASTER_AMPLITUDE / maxAbs else MASTER_AMPLITUDE
        for (i in output.indices) {
            var sample = output[i] * norm
            // tanh 软限幅防止叠加削波
            sample = tanh(sample.toDouble()).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * MIDI 编号转频率（A4=440Hz, MIDI=69）。
     */
    fun midiToFreq(midi: Int): Double {
        return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
    }

    /** 预估渲染时长（毫秒）。 */
    fun estimateDurationMs(question: ScaleDegreeQuestion): Long {
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

        /** 主和弦总持续时间（毫秒，琶音后叠置延音）。 */
        const val CHORD_TOTAL_MS = 900.0

        /** 琶音各音之间的间隔（毫秒）。 */
        const val ARPEGGIO_STEP_MS = 130.0

        /** 主和弦与目标音之间的间隔（毫秒）。 */
        const val TARGET_GAP_MS = 250.0

        /** 目标音持续时间（毫秒）。 */
        const val TARGET_NOTE_MS = 750.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 600.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.85f

        /** 单音增益。 */
        const val NOTE_GAIN = 0.7f

        /** 主和弦音符增益（略低，避免三音叠置削波）。 */
        const val CHORD_NOTE_GAIN = 0.5f

        /** 目标音增益（略高，使其突出于主和弦之后）。 */
        const val TARGET_GAIN = 0.75f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
