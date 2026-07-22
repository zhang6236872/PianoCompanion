package com.pianocompanion.modescale

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 调式音阶色彩对比训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [ModeScaleQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 调式色彩听辨：**
 *
 * 渲染音频的结构：
 * 1. **主音参照**：以复合音色播放主音，帮助用户建立调式中心（tonic center）的参照。
 * 2. **间隔**：短暂静默。
 * 3. **上行音阶**：从主音到八度，逐音播放（音阶向上行进）。
 * 4. **下行音阶**：从八度回到主音，逐音播放（音阶向下行进）。
 *
 * 上下行音阶的完整呈现让用户能充分感知调式的「色彩」——
 * 不同调式的特征音（如利底亚的增四度、多利亚的大六度）在上下行中
 * 与邻音的音程关系形成鲜明的听觉对比。
 *
 * **音频设计理由：**
 * - 主音参照帮助用户「锚定」调式中心，使色彩差异更容易被感知。
 * - 上下行音阶完整呈现调式的音程结构，而非仅靠片段。
 * - 使用带谐波的复合音色（而非纯音），更接近真实乐器的听感，
 *   帮助用户在泛音丰富的音色中感知调式色彩。
 *
 * @param sampleRate 采样率
 */
class ModeScaleAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（用于测试验证）。
     *
     * @param midi MIDI 音高
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param isReference 是否为主音参照段
     * @param degreeName 音阶级数名称（如「主音」「三度」「八度」）
     */
    data class ToneEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val isReference: Boolean,
        val degreeName: String
    )

    /** 为题目渲染音频。 */
    fun render(question: ModeScaleQuestion): FloatArray {
        val events = buildToneEvents(question)
        return renderEvents(events)
    }

    /**
     * 构建题目的全部音符事件。
     *
     * 结构：
     * - 主音参照（如启用）
     * - 上行音阶（主音 → 八度）
     * - 下行音阶（八度 → 主音）
     */
    fun buildToneEvents(question: ModeScaleQuestion): List<ToneEvent> {
        val d = question.difficulty
        val mode = question.targetMode
        val tonicMidi = d.tonicMidi
        val noteDur = d.noteDurationMs.toDouble()
        val gap = d.gapMs.toDouble()

        val events = mutableListOf<ToneEvent>()
        var currentTime = 0.0

        // 主音参照段
        if (d.playReference) {
            val refDuration = noteDur * 1.5 // 主音参照时长是普通音符的 1.5 倍
            events.add(
                ToneEvent(
                    midi = tonicMidi,
                    onsetMs = currentTime,
                    durationMs = refDuration,
                    isReference = true,
                    degreeName = "主音参照"
                )
            )
            currentTime += refDuration + gap * 3
        }

        // 上行音阶：主音(0) → 八度(最后一个音)
        for (i in mode.semitones.indices) {
            val semitone = mode.semitones[i]
            val midi = tonicMidi + semitone
            val degreeName = if (i == 0) "主音" else if (i == mode.semitones.lastIndex) "八度" else "第${i + 1}音"
            events.add(
                ToneEvent(
                    midi = midi,
                    onsetMs = currentTime,
                    durationMs = noteDur,
                    isReference = false,
                    degreeName = degreeName
                )
            )
            currentTime += noteDur + gap
        }

        // 下行音阶：八度-1 → 主音（不含八度，因为上行已播放）
        // 即从倒数第二个音到主音
        for (i in (mode.semitones.lastIndex - 1) downTo 0) {
            val semitone = mode.semitones[i]
            val midi = tonicMidi + semitone
            val degreeName = if (i == 0) "主音" else "第${i + 1}音"
            events.add(
                ToneEvent(
                    midi = midi,
                    onsetMs = currentTime,
                    durationMs = noteDur,
                    isReference = false,
                    degreeName = degreeName
                )
            )
            currentTime += noteDur + gap
        }

        return events
    }

    /** 计算音阶音符数量（不含主音参照）。 */
    fun scaleNoteCount(question: ModeScaleQuestion): Int {
        val mode = question.targetMode
        return mode.semitones.size + (mode.semitones.size - 1) // 上行 + 下行（下行不含八度）
    }

    /** 计算音频总时长（毫秒，不含前后静音）。 */
    fun musicDurationMs(question: ModeScaleQuestion): Double {
        val events = buildToneEvents(question)
        return if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
    }

    /** 计算 MIDI 音高的频率（A4=440Hz, 十二平均律）。 */
    fun midiToFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样。
     *
     * 所有音符使用复合音色（基频 + 3 阶谐波叠加 + 指数衰减包络 + tanh 软限幅）。
     */
    fun renderEvents(events: List<ToneEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val freq = midiToFreq(event.midi)
            val noteSamples = (sampleRate * event.durationMs / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val noteWave = generateComplexTone(freq, noteSamples)
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
    fun estimateDurationMs(question: ModeScaleQuestion): Long {
        val musicMs = musicDurationMs(question)
        return (LEAD_SILENCE_MS + musicMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成复合音色波形（基频 + 3 阶谐波叠加 + 指数衰减包络）。
     *
     * 用于所有音符——模拟自然乐器的泛音结构，帮助用户在丰富的音色中
     * 感知调式色彩的细微差异。
     */
    private fun generateComplexTone(fundamentalFreq: Double, numSamples: Int): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val decaySamples = sampleRate * TONE_DECAY_MS / 1000.0
        val nyquist = sampleRate / 2.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-i / decaySamples)
            var sample = 0.0
            for (h in COMPLEX_HARMONICS.indices) {
                val freq = fundamentalFreq * (h + 1)
                if (freq >= nyquist) break
                sample += sin(2.0 * PI * freq * t) * COMPLEX_HARMONICS[h]
            }
            wave[i] = (sample * envelope * COMPLEX_GAIN).toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 前导静音（毫秒）。 */
        const val LEAD_SILENCE_MS = 150.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 音符衰减时间常数（毫秒）。 */
        const val TONE_DECAY_MS = 280.0

        /** 复合音色增益。 */
        const val COMPLEX_GAIN = 0.30f

        /** 复合音色谐波幅度（基频 + 3 个谐波，1/n 衰减）。 */
        private val COMPLEX_HARMONICS = doubleArrayOf(1.0, 0.5, 0.333, 0.25)
    }
}
