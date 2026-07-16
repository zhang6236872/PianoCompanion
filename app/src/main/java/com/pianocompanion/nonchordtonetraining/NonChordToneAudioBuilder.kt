package com.pianocompanion.nonchordtonetraining

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 和弦外音辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [NonChordToneQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **渲染策略：**
 * - 在低八度持续响起的**和弦**（根三五音，如 C3-E3-G3）提供和声背景——
 *   和弦音为 {0, +4, +7}（大三和弦）。
 * - 在中高音区依次播放 3 音**旋律**（[NonChordToneQuestion.melodyMidi]），每个音持续
 *   [MELODY_NOTE_MS]。
 * - 旋律的中间音为和弦外音，它与持续和弦碰撞产生瞬间不协和（紧张感），随后级进/跳进
 *   解决到和弦音（释放）——这正是和弦外音听辨的物理基础。
 * - 每个音使用钢琴风格加法合成（基频 + 4 谐波）+ 指数衰减包络。旋律音略亮（增益较高），
 *   和弦音更柔和（增益较低）以突出旋律轮廓。
 *
 * @param sampleRate 采样率
 */
class NonChordToneAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param gain 单音增益
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float
    )

    /** 题目中实际使用的旋律 MIDI 音高（3 个）。 */
    data class MelodyPitches(val notes: List<Int>) {
        /** 中间音（和弦外音）的 MIDI 音高。 */
        val nonChordMidi: Int get() = notes[1]
    }

    /**
     * 为题目渲染音频。
     */
    fun render(question: NonChordToneQuestion): FloatArray {
        val events = buildNoteEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建指定题目的音符事件序列：持续和弦 + 3 音旋律。
     */
    fun buildNoteEvents(question: NonChordToneQuestion): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()

        // 和弦音（低八度，持续整个旋律时值）
        val chordDurationMs = MELODY_NOTE_MS * 3 + (2 * MELODY_GAP_MS)
        val chordRoot = question.rootMidi - 12
        CHORD_INTERVALS.forEach { interval ->
            events.add(
                NoteEvent(
                    midi = chordRoot + interval,
                    onsetMs = 0.0,
                    durationMs = chordDurationMs,
                    gain = CHORD_GAIN
                )
            )
        }

        // 旋律音（依次）
        question.melodyMidi.forEachIndexed { index, midi ->
            val onset = index * (MELODY_NOTE_MS + MELODY_GAP_MS)
            events.add(
                NoteEvent(
                    midi = midi,
                    onsetMs = onset,
                    durationMs = MELODY_NOTE_MS,
                    gain = MELODY_GAIN
                )
            )
        }

        return events
    }

    /**
     * 提取题目中实际使用的旋律音高（3 个）。
     */
    fun extractPitches(question: NonChordToneQuestion): MelodyPitches =
        MelodyPitches(question.melodyMidi)

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

        // 归一化并应用主振幅
        val maxAbs = output.maxOfOrNull { abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) MASTER_AMPLITUDE / maxAbs else MASTER_AMPLITUDE
        for (i in output.indices) {
            var sample = output[i] * norm
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
    fun estimateDurationMs(question: NonChordToneQuestion): Long {
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
        const val LEAD_SILENCE_MS = 250.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 350.0

        /** 单个旋律音持续时间（毫秒）。 */
        const val MELODY_NOTE_MS = 620.0

        /** 旋律音之间的间隔（毫秒）。 */
        const val MELODY_GAP_MS = 60.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 520.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.85f

        /** 旋律音增益（略高，突出轮廓）。 */
        const val MELODY_GAIN = 0.62f

        /** 和弦音增益（较低，作为背景）。 */
        const val CHORD_GAIN = 0.32f

        /** 大三和弦音程（根三五，半音数）。 */
        val CHORD_INTERVALS: IntArray = intArrayOf(0, 4, 7)

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
