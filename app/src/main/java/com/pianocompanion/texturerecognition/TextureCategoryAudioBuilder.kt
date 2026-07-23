package com.pianocompanion.texturerecognition

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 织体类型辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TextureCategoryQuestion] 渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 4 类基础织体的听觉差异：**
 *
 * 四类织体由**声部数量**与**声部关系**两个维度决定，音频渲染据此构造截然不同的听感：
 *
 * - **单声部（Monophonic）**：只有一条旋律线，一次只响一个音 → 渲染为单声部音符序列。
 * - **主调（Homophonic）**：突出主旋律 + 和声伴奏（块状和弦），二者节奏一致 →
 *   渲染为高音旋律 + 低音块状和弦，旋律振幅略大以突出。
 * - **复调（Polyphonic）**：两条**独立**旋律线，节奏与走向不同 →
 *   渲染为高音长音线 + 低音独立短音线，两线音高素材**不同**、节奏**错开**。
 * - **支声复调（Heterophonic）**：**同一**旋律由两声部演奏，一声部加装饰 →
 *   渲染为低八度朴素旋律 + 高八度「同源」旋律（每拍加回音式装饰音 mordent），
 *   两线音高素材**相同**（仅八度差 + 装饰），听感是「一首曲子被加花」。
 *
 * **复调 vs 支声复调的渲染差异（本模块核心难点）：**
 * - 复调两线的骨架音高**互不相同**（独立性）；
 * - 支声复调两线的骨架音高**完全一致**（同源性），装饰音仅在骨架音附近做上邻音回旋。
 *
 * 音色：加法合成（基频 + 3 阶谐波）+ 指数衰减包络 + tanh 软限幅，模拟钢琴音色。
 *
 * @param sampleRate 采样率
 */
class TextureCategoryAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（用于测试验证）。
     *
     * @param onsetMs 起始时间（毫秒）
     * @param durationMs 持续时间（毫秒）
     * @param midi MIDI 音高
     * @param voice 声部编号（0=旋律/上方，1=伴奏/下方/装饰声部）
     * @param amplitude 振幅（0.0-1.0）
     */
    data class TextureCategoryNoteEvent(
        val onsetMs: Double,
        val durationMs: Double,
        val midi: Int,
        val voice: Int,
        val amplitude: Float
    ) {
        /** 频率（Hz）。 */
        val frequencyHz: Double get() = midiToFrequency(midi)
    }

    /** 为题目渲染音频。 */
    fun render(question: TextureCategoryQuestion): FloatArray {
        val events = buildNoteEvents(question)
        val musicMs = estimateMusicMs(question)
        return renderEvents(events, musicMs)
    }

    /**
     * 根据题目构建该织体的全部音符事件。
     *
     * 织体类型与复杂度共同决定片段的音符序列。
     */
    fun buildNoteEvents(question: TextureCategoryQuestion): List<TextureCategoryNoteEvent> {
        val beatMs = 60_000.0 / question.tempoBpm
        return when (question.targetTexture) {
            MusicTextureType.MONOPHONIC -> monophonicEvents(beatMs, question.complexity)
            MusicTextureType.HOMOPHONIC -> homophonicEvents(beatMs, question.complexity)
            MusicTextureType.POLYPHONIC -> polyphonicEvents(beatMs, question.complexity)
            MusicTextureType.HETEROPHONIC -> heterophonicEvents(beatMs, question.complexity)
        }
    }

    /** 计算音符事件数量。 */
    fun noteCount(question: TextureCategoryQuestion): Int = buildNoteEvents(question).size

    /** 计算音频总时长（毫秒，含前后静音）。 */
    fun estimateDurationMs(question: TextureCategoryQuestion): Long {
        return (LEAD_SILENCE_MS + estimateMusicMs(question) + NOTE_TAIL_DECAY_MS + TAIL_SILENCE_MS).toLong()
    }

    /** 估算纯音乐时长（毫秒，不含前后静音，含尾部衰减）。 */
    private fun estimateMusicMs(question: TextureCategoryQuestion): Double {
        val beatMs = 60_000.0 / question.tempoBpm
        val beats = if (question.complexity >= 3) 6 else 4
        return beats * beatMs
    }

    // ── 各织体片段生成 ────────────────────────────────────

    /** 单声部：单一旋律线，一次一个音。 */
    private fun monophonicEvents(beatMs: Double, complexity: Int): List<TextureCategoryNoteEvent> {
        // 骨架旋律（C 大调五声/自然音阶片段）
        val skeleton = if (complexity >= 3) {
            listOf(0, 2, 4, 7, 9, 7, 4, 0)  // C D E G A G E C — 8 拍
        } else {
            listOf(0, 2, 4, 7)  // C D E G — 4 拍
        }
        val base = 60  // C4
        return skeleton.mapIndexed { i, offset ->
            TextureCategoryNoteEvent(
                onsetMs = i * beatMs,
                durationMs = beatMs,
                midi = base + offset,
                voice = 0,
                amplitude = MELODY_AMPLITUDE
            )
        }
    }

    /** 主调：高音旋律 + 低音块状和弦伴奏，节奏一致。 */
    private fun homophonicEvents(beatMs: Double, complexity: Int): List<TextureCategoryNoteEvent> {
        val events = mutableListOf<TextureCategoryNoteEvent>()
        val melodyBase = 72  // C5
        // 旋律轮廓（相对 C5）
        val melody = if (complexity >= 3) {
            listOf(0, 4, 7, 4, 2, 0)  // C E G E D C
        } else {
            listOf(0, 4, 2, 0)  // C E D C
        }
        // 和弦（每拍一个块状和弦）：I 或 V
        // true = C 大三和弦 (C E G)，false = G 大三和弦 (G B D)
        val chordIsC = if (complexity >= 3) {
            listOf(true, true, true, false, false, true)
        } else {
            listOf(true, true, false, true)
        }
        // C 大三和弦低音区 MIDI
        val cMajor = listOf(48, 52, 55)   // C3 E3 G3
        val gMajor = listOf(43, 47, 50)   // G2 B2 D3
        melody.forEachIndexed { i, mOff ->
            // 旋律音（上方，振幅略大）
            events.add(
                TextureCategoryNoteEvent(
                    onsetMs = i * beatMs,
                    durationMs = beatMs,
                    midi = melodyBase + mOff,
                    voice = 0,
                    amplitude = MELODY_AMPLITUDE
                )
            )
            // 和弦伴奏（下方块状和弦，3 个音同时响）
            val chord = if (chordIsC[i]) cMajor else gMajor
            chord.forEach { midi ->
                events.add(
                    TextureCategoryNoteEvent(
                        onsetMs = i * beatMs,
                        durationMs = beatMs,
                        midi = midi,
                        voice = 1,
                        amplitude = ACCOMPANIMENT_AMPLITUDE
                    )
                )
            }
        }
        return events
    }

    /** 复调：两条独立旋律线，节奏与走向不同。 */
    private fun polyphonicEvents(beatMs: Double, complexity: Int): List<TextureCategoryNoteEvent> {
        val events = mutableListOf<TextureCategoryNoteEvent>()
        // 上方声部（voice 0）：长音（每 2 拍一个），独立旋律素材 A
        val upperPitches = if (complexity >= 3) {
            // E5 C5 A4 — 3 个长音，每个 2 拍
            listOf(76, 72, 69)
        } else {
            // C5 A4 — 2 个长音，每个 2 拍
            listOf(72, 69)
        }
        upperPitches.forEachIndexed { i, midi ->
            events.add(
                TextureCategoryNoteEvent(
                    onsetMs = i * 2.0 * beatMs,
                    durationMs = 2.0 * beatMs,
                    midi = midi,
                    voice = 0,
                    amplitude = EQUAL_AMPLITUDE
                )
            )
        }
        // 下方声部（voice 1）：短音（每拍一个），独立旋律素材 B（与上方不同）
        val lowerPitches = if (complexity >= 3) {
            listOf(55, 59, 62, 60, 59, 57)  // G3 B3 D4 C4 B3 A3 — 6 拍
        } else {
            listOf(55, 59, 62, 60)  // G3 B3 D4 C4 — 4 拍
        }
        lowerPitches.forEachIndexed { i, midi ->
            events.add(
                TextureCategoryNoteEvent(
                    onsetMs = i * beatMs,
                    durationMs = beatMs,
                    midi = midi,
                    voice = 1,
                    amplitude = EQUAL_AMPLITUDE
                )
            )
        }
        return events
    }

    /**
     * 支声复调：同一旋律由两声部演奏，一声部朴素、一声部加装饰（回音 mordent）。
     *
     * 关键：两声部骨架音高**完全相同**（仅八度差），装饰声部在每拍做「骨架音→上邻音→骨架音」
     * 的回旋，使听感为「一首旋律被加花」而非「两条独立旋律」。
     */
    private fun heterophonicEvents(beatMs: Double, complexity: Int): List<TextureCategoryNoteEvent> {
        val events = mutableListOf<TextureCategoryNoteEvent>()
        // 共享旋律骨架（相对根音的半音偏移）
        val skeleton = if (complexity >= 3) {
            listOf(0, 2, 4, 5, 4, 2)  // C D E F E D — 6 拍
        } else {
            listOf(0, 2, 4, 2)  // C D E D — 4 拍
        }
        val plainBase = 60   // C4（朴素声部）
        val ornateBase = 72  // C5（装饰声部，高八度同源）

        skeleton.forEachIndexed { i, offset ->
            // 朴素声部（voice 0）：每拍一个骨架音
            events.add(
                TextureCategoryNoteEvent(
                    onsetMs = i * beatMs,
                    durationMs = beatMs,
                    midi = plainBase + offset,
                    voice = 0,
                    amplitude = EQUAL_AMPLITUDE
                )
            )
            // 装饰声部（voice 1）：同源骨架音 + 上邻音回音（三等分一拍）
            val skel = ornateBase + offset
            val third = beatMs / 3.0
            listOf(skel to 0.0, (skel + 2) to third, skel to (2.0 * third)).forEach { (midi, subOnset) ->
                events.add(
                    TextureCategoryNoteEvent(
                        onsetMs = i * beatMs + subOnset,
                        durationMs = third,
                        midi = midi,
                        voice = 1,
                        amplitude = ACCOMPANIMENT_AMPLITUDE
                    )
                )
            }
        }
        return events
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样（加法合成 + 指数衰减 + tanh 软限幅）。
     */
    fun renderEvents(events: List<TextureCategoryNoteEvent>, musicMs: Double): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * (musicMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        for (event in events) {
            val noteSamples = (sampleRate * (event.durationMs + NOTE_TAIL_DECAY_MS) / 1000.0).toInt()
            val offset = leadSamples + (sampleRate * event.onsetMs / 1000.0).toInt()
            val noteWave = generatePianoTone(event.frequencyHz, noteSamples, event.amplitude)
            for (j in noteWave.indices) {
                val outIdx = offset + j
                if (outIdx in output.indices) {
                    output[outIdx] += noteWave[j]
                }
            }
        }

        // tanh 软限幅防止削波
        for (i in output.indices) {
            var sample = tanh(output[i].toDouble() * NORMALIZATION_FACTOR).toFloat()
            if (sample > 1.0f) sample = 1.0f
            else if (sample < -1.0f) sample = -1.0f
            output[i] = sample
        }

        return output
    }

    /**
     * 生成钢琴式音色（加法合成 + 指数衰减包络 + 软起音）。
     */
    private fun generatePianoTone(freq: Double, numSamples: Int, amplitude: Float): FloatArray {
        if (numSamples <= 0) return FloatArray(0)
        val wave = FloatArray(numSamples)
        val attackSamples = (sampleRate * ATTACK_MS / 1000.0).toInt().coerceAtLeast(1)
        val decaySamples = numSamples.toDouble()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val attackEnv = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decayEnv = exp(-i / (decaySamples * DECAY_TAU_RATIO))

            val fundamental = sin(2.0 * PI * freq * t)
            val h2 = sin(2.0 * PI * freq * 2.0 * t) * HARMONIC_2_GAIN
            val h3 = sin(2.0 * PI * freq * 3.0 * t) * HARMONIC_3_GAIN
            val h4 = sin(2.0 * PI * freq * 4.0 * t) * HARMONIC_4_GAIN

            val sample = (fundamental + h2 + h3 + h4) * attackEnv * decayEnv * amplitude
            wave[i] = sample.toFloat()
        }
        return wave
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100

        const val LEAD_SILENCE_MS = 250.0
        const val TAIL_SILENCE_MS = 400.0
        const val NOTE_TAIL_DECAY_MS = 350.0
        const val ATTACK_MS = 3.0
        const val DECAY_TAU_RATIO = 0.35

        const val HARMONIC_2_GAIN = 0.35
        const val HARMONIC_3_GAIN = 0.15
        const val HARMONIC_4_GAIN = 0.08

        /** 旋律声部振幅（突出）。 */
        const val MELODY_AMPLITUDE = 0.40f

        /** 伴奏/和弦声部振幅（略低）。 */
        const val ACCOMPANIMENT_AMPLITUDE = 0.30f

        /** 平等声部振幅（复调/支声的两条独立线）。 */
        const val EQUAL_AMPLITUDE = 0.36f

        const val NORMALIZATION_FACTOR = 1.2

        fun midiToFrequency(midi: Int): Double {
            return 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        }
    }
}
