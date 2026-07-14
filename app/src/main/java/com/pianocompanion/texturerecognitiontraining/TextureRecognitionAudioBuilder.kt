package com.pianocompanion.texturerecognitiontraining

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 织体辨识训练音频构建器（纯 Kotlin，无 Android 依赖，完全可单元测试）。
 *
 * 将 [TextureQuestion] 的织体类型渲染为可直接播放的 PCM Float 缓冲区（[-1.0, 1.0]）。
 *
 * **核心原理 — 织体听辨：**
 * - 不同织体有不同的声部组合方式，用户需要通过听来辨识织体类型
 * - 每种织体使用不同的音符编排（声部数量、节奏关系），但都基于 C 大调
 * - 所有音符使用钢琴风格加法合成（基频 + 谐波）+ 指数衰减包络
 * - 多声部织体（和弦/琶音/复调/支声）通过将多个音符在时间轴上混合实现
 *
 * 各织体的音频特征：
 * - **单声部**：纯旋律，一次一个音（C4→E4→G4→C5 上行琶音）
 * - **柱式和弦**：旋律 + 同拍块状和弦伴奏（旋律在上，和弦在下同时敲响）
 * - **分解和弦**：旋律 + 滚动琶音伴奏（旋律在上，下方有依次滚动的分解和弦）
 * - **复调**：两条独立旋律线，各有不同节奏（上方四分音符 + 下方附点二分音符）
 * - **支声**：同一旋律由两个声部演奏，一个加装饰变化（经过音/助音）
 *
 * @param sampleRate 采样率
 */
class TextureAudioBuilder(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    /**
     * 单个音符事件（在时间轴上的位置和属性）。
     *
     * @param midi MIDI 编号
     * @param onsetMs 起始时间（毫秒，相对于音乐开始）
     * @param durationMs 持续时间（毫秒）
     * @param gain 相对增益（0.0-1.0，用于伴奏声部降低音量）
     */
    data class NoteEvent(
        val midi: Int,
        val onsetMs: Double,
        val durationMs: Double,
        val gain: Float = 1.0f
    )

    /**
     * 为题目渲染音频。
     */
    fun render(question: TextureQuestion): FloatArray {
        val events = buildEvents(question.texture)
        return renderEvents(events)
    }

    /**
     * 构建指定织体类型的音符事件序列。
     */
    fun buildEvents(texture: TextureType): List<NoteEvent> {
        return when (texture) {
            TextureType.MONOPHONIC -> monophonicEvents()
            TextureType.HOMOPHONIC_CHORDAL -> homophonicChordalEvents()
            TextureType.HOMOPHONIC_ARPEGGIATED -> homophonicArpeggiatedEvents()
            TextureType.POLYPHONIC -> polyphonicEvents()
            TextureType.HETEROPHONIC -> heterophonicEvents()
        }
    }

    // ── 各织体音符编排 ──────────────────────────────────

    /** 单声部：C4→E4→G4→C5 上行琶音，每音 500ms。 */
    private fun monophonicEvents(): List<NoteEvent> {
        val melody = listOf(60, 64, 67, 72) // C4, E4, G4, C5
        return melody.mapIndexed { i, midi ->
            NoteEvent(midi, i * NOTE_MS, NOTE_MS, gain = MELODY_GAIN)
        }
    }

    /** 柱式和弦伴奏：旋律 C4-E4-G4-C5 + 每拍同时 C3+E3+G3 和弦。 */
    private fun homophonicChordalEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val melody = listOf(60, 64, 67, 72) // C4, E4, G4, C5
        val chord = listOf(48, 52, 55)      // C3, E3, G3 (C major triad)
        for (i in melody.indices) {
            val onset = i * NOTE_MS
            // 旋律音
            events.add(NoteEvent(melody[i], onset, NOTE_MS, gain = MELODY_GAIN))
            // 和弦伴奏（同时响起）
            for (c in chord) {
                events.add(NoteEvent(c, onset, NOTE_MS, gain = ACCOMP_GAIN))
            }
        }
        return events
    }

    /** 分解和弦伴奏：旋律 C4-E4-G4-C5 + 每拍 2 个八分音符琶音。 */
    private fun homophonicArpeggiatedEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        val melody = listOf(60, 64, 67, 72) // C4, E4, G4, C5
        // 每拍弹两个八分音符的分解和弦模式
        val arpPatterns = listOf(
            listOf(48, 55),  // C3, G3
            listOf(52, 55),  // E3, G3
            listOf(48, 52),  // C3, E3
            listOf(55, 48)   // G3, C3
        )
        val eighth = NOTE_MS / 2
        for (i in melody.indices) {
            val onset = i * NOTE_MS
            // 旋律音（持续整拍）
            events.add(NoteEvent(melody[i], onset, NOTE_MS, gain = MELODY_GAIN))
            // 琶音伴奏（每拍 2 个八分音符）
            val arp = arpPatterns[i]
            arp.forEachIndexed { j, midi ->
                events.add(NoteEvent(midi, onset + j * eighth, eighth, gain = ACCOMP_GAIN))
            }
        }
        return events
    }

    /** 复调：上方 C4-E4-G4（3 四分音符）+ 下方 G3-E3（2 附点二分音符），不同节奏。 */
    private fun polyphonicEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        // 上方声部：C4→E4→G4（3 个四分音符，各 500ms）
        val upperMelody = listOf(60, 64, 67) // C4, E4, G4
        for (i in upperMelody.indices) {
            events.add(NoteEvent(upperMelody[i], i * NOTE_MS, NOTE_MS, gain = MELODY_GAIN))
        }
        // 下方声部：G3→E3（2 个附点四分音符，各 750ms）——节奏与上方不同
        val lowerMelody = listOf(55, 52) // G3, E3
        val dottedNote = NOTE_MS * 1.5
        for (i in lowerMelody.indices) {
            events.add(NoteEvent(lowerMelody[i], i * dottedNote, NOTE_MS, gain = MELODY_GAIN))
        }
        return events
    }

    /** 支声：声部 1（原旋律）+ 声部 2（加经过音装饰的同一旋律）。 */
    private fun heterophonicEvents(): List<NoteEvent> {
        val events = mutableListOf<NoteEvent>()
        // 声部 1：原始旋律 C4→E4→G4→C5
        val melody = listOf(60, 64, 67, 72)
        for (i in melody.indices) {
            events.add(NoteEvent(melody[i], i * NOTE_MS, NOTE_MS, gain = MELODY_GAIN))
        }
        // 声部 2：同一旋律但在 E4 和 C5 前加入经过音
        // C4(0-500ms) → D4(500-625ms)→E4(625-1000ms) → G4(1000-1500ms) → B4(1500-1625ms)→C5(1625-2000ms)
        val ornamented = listOf(
            NoteEvent(60, 0.0, NOTE_MS, gain = ORNAMENT_GAIN),         // C4 (同)
            NoteEvent(62, NOTE_MS, NOTE_MS / 2, gain = ORNAMENT_GAIN),  // D4 经过音
            NoteEvent(64, NOTE_MS * 1.5, NOTE_MS / 2, gain = ORNAMENT_GAIN), // E4
            NoteEvent(67, NOTE_MS * 2, NOTE_MS, gain = ORNAMENT_GAIN),     // G4 (同)
            NoteEvent(71, NOTE_MS * 3, NOTE_MS / 2, gain = ORNAMENT_GAIN),  // B4 经过音
            NoteEvent(72, NOTE_MS * 3.5, NOTE_MS / 2, gain = ORNAMENT_GAIN) // C5
        )
        events.addAll(ornamented)
        return events
    }

    // ── 渲染 ──────────────────────────────────────────

    /**
     * 将音符事件列表渲染为连续 PCM 采样（多声部混合）。
     *
     * 每个事件独立合成后按 onset 叠加到输出缓冲区，最后归一化。
     */
    fun renderEvents(events: List<NoteEvent>): FloatArray {
        if (events.isEmpty()) return FloatArray(0)

        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val leadSamples = (sampleRate * LEAD_SILENCE_MS / 1000.0).toInt()
        val musicSamples = (sampleRate * musicEndMs / 1000.0).toInt()
        val tailSamples = (sampleRate * TAIL_SILENCE_MS / 1000.0).toInt()

        val totalLength = leadSamples + musicSamples + tailSamples
        val output = FloatArray(totalLength)

        // 逐事件合成并叠加
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
        val maxAbs = output.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
        val norm = if (maxAbs > 0.0001f) MASTER_AMPLITUDE / maxAbs else MASTER_AMPLITUDE
        for (i in output.indices) {
            var sample = output[i] * norm
            // 软限幅保护
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
    fun estimateDurationMs(texture: TextureType): Long {
        val events = buildEvents(texture)
        val musicEndMs = if (events.isEmpty()) 0.0 else events.maxOf { it.onsetMs + it.durationMs }
        return (LEAD_SILENCE_MS + musicEndMs + TAIL_SILENCE_MS).toLong()
    }

    /**
     * 生成单个音符波形（钢琴风格加法合成 + 指数衰减包络）。
     *
     * 基频 + 5 个谐波（幅度递减），模拟钢琴音色。
     * 高于奈奎斯特频率的谐波自动跳过以避免混叠。
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
        const val LEAD_SILENCE_MS = 400.0

        /** 尾部静音（毫秒）。 */
        const val TAIL_SILENCE_MS = 300.0

        /** 单个旋律音符持续时间（毫秒）。 */
        const val NOTE_MS = 500.0

        /** 音符衰减时间常数（毫秒）。 */
        const val NOTE_DECAY_MS = 400.0

        /** 主振幅（整体音量）。 */
        const val MASTER_AMPLITUDE = 0.8f

        /** 旋律声部增益。 */
        const val MELODY_GAIN = 1.0f

        /** 伴奏声部增益（略低于旋律）。 */
        const val ACCOMP_GAIN = 0.55f

        /** 装饰声部增益（支声中第二声部，略低于主声部）。 */
        const val ORNAMENT_GAIN = 0.7f

        /** 钢琴谐波幅度（基频 + 4 个谐波）。 */
        private val HARMONICS = doubleArrayOf(1.0, 0.5, 0.25, 0.15, 0.08)
    }
}
