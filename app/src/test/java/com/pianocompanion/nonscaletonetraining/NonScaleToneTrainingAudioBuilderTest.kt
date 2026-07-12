package com.pianocompanion.nonscaletonetraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调外音听辨训练音频构建器单元测试。
 *
 * 验证旋律型 PCM 合成输出：长度、范围、形状、归一化。
 */
class NonScaleToneTrainingAudioBuilderTest {

    private val builder = NonScaleToneTrainingAudioBuilder()

    private fun newQuestion(notes: List<Int>): NonScaleToneQuestion {
        return NonScaleToneQuestion(
            type = NonScaleToneType.DIATONIC,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = notes[0],
            midiNotes = notes,
            answerChoices = listOf("调内（自然大调）"),
            correctAnswer = "调内（自然大调）"
        )
    }

    // ── 基本输出 ──────────────────────────────────────────────

    @Test
    fun `rendered audio is not empty`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        assertTrue("PCM 应有数据", pcm.isNotEmpty())
    }

    @Test
    fun `rendered audio has expected duration`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        val expectedDurationMs = NonScaleToneTrainingAudioBuilder.LEAD_SILENCE_MS +
            5 * NonScaleToneTrainingAudioBuilder.NOTE_DURATION_MS +
            NonScaleToneTrainingAudioBuilder.TAIL_SILENCE_MS
        val expectedSamples = (expectedDurationMs * NonScaleToneTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        // PianoToneSynthesizer 可能在音符衰减后截断输出，允许较大容差
        assertTrue("PCM 长度 ${pcm.size} 应在期望值 $expectedSamples 附近（±15%）",
            pcm.size in (expectedSamples * 0.85).toInt()..(expectedSamples * 1.15).toInt())
    }

    @Test
    fun `estimateDurationMs matches expected`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val estimated = builder.estimateDurationMs(q)
        val expected = NonScaleToneTrainingAudioBuilder.LEAD_SILENCE_MS +
            5 * NonScaleToneTrainingAudioBuilder.NOTE_DURATION_MS +
            NonScaleToneTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    // ── 归一化范围 ────────────────────────────────────────────

    @Test
    fun `all samples are in valid range after soft clipping`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        for ((i, s) in pcm.withIndex()) {
            assertTrue("PCM[$i]=$s 超出 [-1,1]", s in -1.0f..1.0f)
        }
    }

    // ── 不同音符序列 ──────────────────────────────────────────

    @Test
    fun `diatonic melody produces audio`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `chromatic melody produces audio`() {
        val q = NonScaleToneQuestion(
            type = NonScaleToneType.FLATTED_THIRD,
            key = NstMusicalKey.C_MAJOR,
            difficulty = NonScaleToneDifficulty.BEGINNER,
            tonicMidi = 48,
            midiNotes = listOf(48, 50, 51, 53, 55), // C D Eb F G
            answerChoices = listOf("调内（自然大调）", "降三度（♭3）"),
            correctAnswer = "降三度（♭3）"
        )
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `renderNotes with note list produces audio`() {
        val pcm = builder.renderNotes(listOf(48, 50, 52, 53, 55))
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `different melodies produce different audio`() {
        val pcm1 = builder.renderNotes(listOf(48, 50, 52, 53, 55)) // diatonic
        val pcm2 = builder.renderNotes(listOf(48, 50, 51, 53, 55)) // flattened third
        var foundDiff = false
        val minLen = minOf(pcm1.size, pcm2.size)
        for (i in 0 until minLen step 100) {
            if (Math.abs(pcm1[i] - pcm2[i]) > 0.01f) {
                foundDiff = true
                break
            }
        }
        assertTrue("不同旋律的音频应有差异", foundDiff)
    }

    // ── 确定性 ────────────────────────────────────────────────

    @Test
    fun `same question produces same audio`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
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
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        var maxAbs = 0f
        for (s in pcm) {
            maxAbs = maxOf(maxAbs, Math.abs(s))
        }
        assertTrue("音频应有非零能量", maxAbs > 0.01f)
    }

    @Test
    fun `peak amplitude is reasonable`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
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
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        // 前导静音区域应为接近零
        val leadSamples = (NonScaleToneTrainingAudioBuilder.LEAD_SILENCE_MS *
            NonScaleToneTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        val earlyEnergy = (0 until leadSamples / 2).sumOf { Math.abs(pcm[it].toDouble()) }
        assertTrue("前导静音区能量应很低 (实际 $earlyEnergy)", earlyEnergy < 1.0)
    }

    @Test
    fun `audio ends with low amplitude (tail silence)`() {
        val q = newQuestion(listOf(48, 50, 52, 53, 55))
        val pcm = builder.render(q)
        val tailSamples = (NonScaleToneTrainingAudioBuilder.TAIL_SILENCE_MS *
            NonScaleToneTrainingAudioBuilder.SAMPLE_RATE / 1000).toInt()
        val tailStart = pcm.size - tailSamples / 2
        val tailEnergy = (tailStart until pcm.size).sumOf { Math.abs(pcm[it].toDouble()) }
        // 尾部静音区域应非常低
        assertTrue("尾部静音区能量应很低 (实际 $tailEnergy)", tailEnergy < 1.0)
    }

    // ── 边界音符 ──────────────────────────────────────────────

    @Test
    fun `lowest piano note melody renders`() {
        val pcm = builder.renderNotes(listOf(21, 23, 25, 26, 28)) // A0 B0 C#1 D1 E1
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `highest piano note melody renders`() {
        val pcm = builder.renderNotes(listOf(101, 103, 105, 106, 108))
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `empty note list returns empty array`() {
        val pcm = builder.renderNotes(emptyList())
        assertEquals(0, pcm.size)
    }

    // ── 常量验证 ──────────────────────────────────────────────

    @Test
    fun `note duration is reasonable for ear training`() {
        assertTrue(
            "单音时长应 >= 300ms",
            NonScaleToneTrainingAudioBuilder.NOTE_DURATION_MS >= 300L
        )
        assertTrue(
            "单音时长应 <= 1000ms",
            NonScaleToneTrainingAudioBuilder.NOTE_DURATION_MS <= 1000L
        )
    }

    @Test
    fun `sample rate is standard`() {
        assertTrue(
            "采样率应为标准值 (44100 或 48000)",
            NonScaleToneTrainingAudioBuilder.SAMPLE_RATE in listOf(44100, 48000)
        )
    }

    @Test
    fun `lead silence is reasonable`() {
        assertTrue(NonScaleToneTrainingAudioBuilder.LEAD_SILENCE_MS >= 100L)
        assertTrue(NonScaleToneTrainingAudioBuilder.LEAD_SILENCE_MS <= 500L)
    }

    @Test
    fun `tail silence is reasonable`() {
        assertTrue(NonScaleToneTrainingAudioBuilder.TAIL_SILENCE_MS >= 200L)
        assertTrue(NonScaleToneTrainingAudioBuilder.TAIL_SILENCE_MS <= 800L)
    }
}
