package com.pianocompanion.chordfunctiontraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 和弦功能听辨训练音频构建器单元测试。
 *
 * 验证 PCM 合成输出：长度、范围、形状、归一化。
 */
class ChordFunctionTrainingAudioBuilderTest {

    private val builder = ChordFunctionTrainingAudioBuilder()

    private fun newQuestion(notes: List<Int>): ChordFunctionQuestion {
        return ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = notes[0],
            difficulty = ChordFunctionDifficulty.BEGINNER,
            useSeventh = false,
            midiNotes = notes,
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
    }

    private fun newSeventhQuestion(notes: List<Int>): ChordFunctionQuestion {
        return ChordFunctionQuestion(
            scaleDegree = ScaleDegree.I,
            function = HarmonicFunction.TONIC,
            key = MusicalKey.C_MAJOR,
            chordRootMidi = notes[0],
            difficulty = ChordFunctionDifficulty.ADVANCED,
            useSeventh = true,
            midiNotes = notes,
            answerChoices = listOf("主功能"),
            correctAnswer = "主功能"
        )
    }

    // ── 基本输出 ──────────────────────────────────────────────

    @Test
    fun `rendered audio is not empty`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        assertTrue("PCM 应有数据", pcm.isNotEmpty())
    }

    @Test
    fun `rendered audio has expected duration`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        val expectedDurationMs = ChordFunctionTrainingAudioBuilder.LEAD_SILENCE_MS +
            ChordFunctionTrainingAudioBuilder.CHORD_DURATION_MS +
            ChordFunctionTrainingAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (expectedDurationMs * ChordFunctionTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        // PianoToneSynthesizer 可能在音符衰减后截断输出，允许较大容差
        assertTrue("PCM 长度 ${pcm.size} 应在期望值 $expectedSamples 附近（±15%）",
            pcm.size in (expectedSamples * 0.85).toInt()..(expectedSamples * 1.15).toInt())
    }

    @Test
    fun `estimateDurationMs matches expected`() {
        val q = newQuestion(listOf(48, 52, 55))
        val estimated = builder.estimateDurationMs(q)
        val expected = ChordFunctionTrainingAudioBuilder.LEAD_SILENCE_MS +
            ChordFunctionTrainingAudioBuilder.CHORD_DURATION_MS +
            ChordFunctionTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    // ── 归一化范围 ────────────────────────────────────────────

    @Test
    fun `all samples are in valid range after soft clipping`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        for ((i, s) in pcm.withIndex()) {
            assertTrue("PCM[$i]=$s 超出 [-1,1]", s in -1.0f..1.0f)
        }
    }

    // ── 不同和弦 ──────────────────────────────────────────────

    @Test
    fun `triad produces chord audio`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `seventh chord audio is valid`() {
        val q = newSeventhQuestion(listOf(48, 52, 55, 59))
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `renderChord with note list produces audio`() {
        val pcm = builder.renderChord(listOf(48, 52, 55))
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `different chords produce different audio`() {
        val q1 = newQuestion(listOf(48, 52, 55)) // C E G (C major)
        val q2 = newQuestion(listOf(55, 59, 62)) // G B D (G major)
        val pcm1 = builder.render(q1)
        val pcm2 = builder.render(q2)
        var foundDiff = false
        val minLen = minOf(pcm1.size, pcm2.size)
        for (i in 0 until minLen step 100) {
            if (Math.abs(pcm1[i] - pcm2[i]) > 0.01f) {
                foundDiff = true
                break
            }
        }
        assertTrue("不同和弦的音频应有差异", foundDiff)
    }

    // ── 确定性 ────────────────────────────────────────────────

    @Test
    fun `same question produces same audio`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm1 = builder.render(q)
        val pcm2 = builder.render(q)
        assertEquals(pcm1.size, pcm2.size)
        for (i in pcm1.indices) {
            assertEquals(pcm1[i], pcm2[i], 0.0001f)
        }
    }

    // ── 音量 ──────────────────────────────────────────────────

    @Test
    fun `audio has non-zero energy`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        var maxAbs = 0f
        for (s in pcm) {
            maxAbs = maxOf(maxAbs, Math.abs(s))
        }
        assertTrue("音频应有非零能量", maxAbs > 0.01f)
    }

    @Test
    fun `peak amplitude is reasonable`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        var peak = 0f
        for (s in pcm) {
            peak = maxOf(peak, Math.abs(s))
        }
        assertTrue("峰值应在合理范围 (实际 $peak)", peak in 0.1f..1.0f)
    }

    // ── 包络/淡入淡出 ────────────────────────────────────────

    @Test
    fun `audio starts with low amplitude (lead silence)`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        // 前导静音区域应为接近零
        val leadSamples = (ChordFunctionTrainingAudioBuilder.LEAD_SILENCE_MS *
            ChordFunctionTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        val earlyEnergy = (0 until leadSamples / 2).sumOf { Math.abs(pcm[it].toDouble()) }
        assertTrue("前导静音区能量应很低 (实际 $earlyEnergy)", earlyEnergy < 1.0)
    }

    @Test
    fun `audio ends with low amplitude (tail silence)`() {
        val q = newQuestion(listOf(48, 52, 55))
        val pcm = builder.render(q)
        val tailSamples = (ChordFunctionTrainingAudioBuilder.TAIL_SILENCE_MS *
            ChordFunctionTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        val tailStart = pcm.size - tailSamples / 2
        val tailEnergy = (tailStart until pcm.size).sumOf { Math.abs(pcm[it].toDouble()) }
        // 尾部静音区域应非常低
        assertTrue("尾部静音区能量应很低 (实际 $tailEnergy)", tailEnergy < 1.0)
    }

    // ── 边界和弦 ──────────────────────────────────────────────

    @Test
    fun `lowest piano note chord renders`() {
        val q = newQuestion(listOf(21, 25, 28)) // A0 C#1 E1
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `highest piano note chord renders`() {
        val q = newQuestion(listOf(102, 105, 108)) // F#7 A7 C8
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `empty note list returns empty array`() {
        val pcm = builder.renderChord(emptyList())
        assertEquals(0, pcm.size)
    }

    // ── 不同音数量 ────────────────────────────────────────────

    @Test
    fun `three-note and four-note chords both render`() {
        val pcm3 = builder.renderChord(listOf(48, 52, 55))
        val pcm4 = builder.renderChord(listOf(48, 52, 55, 59))
        assertTrue(pcm3.isNotEmpty())
        assertTrue(pcm4.isNotEmpty())
    }

    // ── 常量验证 ──────────────────────────────────────────────

    @Test
    fun `chord duration is reasonable for ear training`() {
        assertTrue(
            "和弦时长应 >= 1秒",
            ChordFunctionTrainingAudioBuilder.CHORD_DURATION_MS >= 1000L
        )
        assertTrue(
            "和弦时长应 <= 5秒",
            ChordFunctionTrainingAudioBuilder.CHORD_DURATION_MS <= 5000L
        )
    }

    @Test
    fun `sample rate is standard`() {
        assertTrue(
            "采样率应为标准值 (44100 或 48000)",
            ChordFunctionTrainingAudioBuilder.SAMPLE_RATE in listOf(44100, 48000)
        )
    }
}
