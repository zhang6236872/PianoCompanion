package com.pianocompanion.notation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteReadingAudioBuilder] 单元测试。
 *
 * 验证：
 * - 渲染缓冲区非空且长度正确（前导静音 + 音符 + 尾部静音）
 * - 前导/尾部静音区域采样值为 0
 * - 有声音区域采样值非零
 * - 采样值在 [-1.0, 1.0] 范围内（不削波）
 * - 确定性渲染（相同参数相同输出）
 * - 不同 MIDI 音符产生不同的音频输出
 * - estimateDurationMs 返回合理值
 */
class NoteReadingAudioBuilderTest {

    private val builder = NoteReadingAudioBuilder()

    private fun createQuestion(midi: Int, step: Int = 0): NoteReadingQuestion {
        return NoteReadingQuestion(
            clef = NoteReadingClef.TREBLE,
            difficulty = NoteReadingDifficulty.BEGINNER,
            staffStep = step,
            midiNote = midi,
            letterName = "E",
            fullNoteName = "E4",
            answerChoices = listOf("E", "G", "B", "D")
        )
    }

    @Test
    fun `render produces non-empty buffer`() {
        val audio = builder.render(createQuestion(64)) // E4
        assertTrue("音频缓冲区不应为空", audio.isNotEmpty())
    }

    @Test
    fun `render buffer length includes silence padding`() {
        val audio = builder.render(createQuestion(64))
        // 至少应该包含前导静音 + 音符 + 尾部静音
        val minExpected = NoteReadingAudioBuilder.SILENCE_LEAD_SAMPLES +
            NoteReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        assertTrue(
            "音频长度 ${audio.size} 应大于最小静音长度 $minExpected",
            audio.size > minExpected
        )
    }

    @Test
    fun `leading silence region is zero`() {
        val audio = builder.render(createQuestion(64))
        val leadSamples = NoteReadingAudioBuilder.SILENCE_LEAD_SAMPLES
        for (i in 0 until leadSamples) {
            assertEquals(
                "前导静音区 sample[$i] = ${audio[i]} 不为零",
                0.0f, audio[i], 0.0001f
            )
        }
    }

    @Test
    fun `trailing silence region is zero`() {
        val audio = builder.render(createQuestion(64))
        val tailSamples = NoteReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        val start = audio.size - tailSamples
        for (i in start until audio.size) {
            assertEquals(
                "尾部静音区 sample[$i] = ${audio[i]} 不为零",
                0.0f, audio[i], 0.0001f
            )
        }
    }

    @Test
    fun `audio region has non-zero content`() {
        val audio = builder.render(createQuestion(64))
        val leadSamples = NoteReadingAudioBuilder.SILENCE_LEAD_SAMPLES
        val tailSamples = NoteReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        val end = audio.size - tailSamples
        var nonZeroCount = 0
        for (i in leadSamples until end) {
            if (audio[i] != 0.0f) nonZeroCount++
        }
        assertTrue(
            "有声音区域应有非零采样，实际只有 $nonZeroCount 个",
            nonZeroCount > 100
        )
    }

    @Test
    fun `all samples within valid range`() {
        val audio = builder.render(createQuestion(64))
        for (i in audio.indices) {
            assertTrue(
                "sample[$i] = ${audio[i]} 超出 [-1, 1] 范围",
                audio[i] in -1.0f..1.0f
            )
        }
    }

    @Test
    fun `deterministic rendering same input same output`() {
        val audio1 = builder.render(createQuestion(64))
        val audio2 = builder.render(createQuestion(64))
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `different midi notes produce different audio`() {
        val audio1 = builder.render(createQuestion(64)) // E4
        val audio2 = builder.render(createQuestion(67)) // G4
        // Lengths should be similar (same duration), but content should differ
        var diffCount = 0
        val minLen = minOf(audio1.size, audio2.size)
        for (i in 0 until minLen) {
            if (audio1[i] != audio2[i]) diffCount++
        }
        assertTrue(
            "不同 MIDI 音符应产生不同音频，但差异样本数仅 $diffCount",
            diffCount > 100
        )
    }

    @Test
    fun `different octaves produce different audio`() {
        val audio1 = builder.render(createQuestion(60)) // C4
        val audio2 = builder.render(createQuestion(72)) // C5
        var diffCount = 0
        val minLen = minOf(audio1.size, audio2.size)
        for (i in 0 until minLen) {
            if (audio1[i] != audio2[i]) diffCount++
        }
        assertTrue("不同八度应产生不同音频", diffCount > 100)
    }

    @Test
    fun `renderNote produces non-empty buffer`() {
        val audio = builder.renderNote(60)
        assertTrue(audio.isNotEmpty())
    }

    @Test
    fun `renderNote leading silence is zero`() {
        val audio = builder.renderNote(60)
        for (i in 0 until NoteReadingAudioBuilder.SILENCE_LEAD_SAMPLES) {
            assertEquals(0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `renderNote trailing silence is zero`() {
        val audio = builder.renderNote(60)
        val tail = NoteReadingAudioBuilder.SILENCE_TAIL_SAMPLES
        for (i in audio.size - tail until audio.size) {
            assertEquals(0.0f, audio[i], 0.0001f)
        }
    }

    @Test
    fun `estimateDurationMs returns reasonable value`() {
        val ms = builder.estimateDurationMs()
        // Should be lead + note + tail = 100 + 800 + 300 = 1200ms
        assertEquals(1200L, ms)
    }

    @Test
    fun `estimateDurationMs is consistent regardless of midi`() {
        val ms60 = builder.estimateDurationMs(60)
        val ms72 = builder.estimateDurationMs(72)
        assertEquals(ms60, ms72)
    }

    @Test
    fun `render does not clip for low notes`() {
        // Low A0 = MIDI 21
        val audio = builder.render(createQuestion(21))
        for (i in audio.indices) {
            assertTrue(audio[i] in -1.0f..1.0f)
        }
    }

    @Test
    fun `render does not clip for high notes`() {
        // High C8 = MIDI 108
        val audio = builder.render(createQuestion(108))
        for (i in audio.indices) {
            assertTrue(audio[i] in -1.0f..1.0f)
        }
    }

    @Test
    fun `bass clef question renders correctly`() {
        val question = NoteReadingQuestion(
            clef = NoteReadingClef.BASS,
            difficulty = NoteReadingDifficulty.INTERMEDIATE,
            staffStep = 4,
            midiNote = 50, // D3
            letterName = "D",
            fullNoteName = "D3",
            answerChoices = listOf("D", "F", "A", "C")
        )
        val audio = builder.render(question)
        assertTrue(audio.isNotEmpty())
        // Audio region should be non-zero
        val leadSamples = NoteReadingAudioBuilder.SILENCE_LEAD_SAMPLES
        var nonZero = false
        for (i in leadSamples until audio.size - NoteReadingAudioBuilder.SILENCE_TAIL_SAMPLES) {
            if (audio[i] != 0.0f) { nonZero = true; break }
        }
        assertTrue("低音谱号题目应产生有声音频", nonZero)
    }

    @Test
    fun `sample rate constant is 44100`() {
        assertEquals(44100, NoteReadingAudioBuilder.SAMPLE_RATE)
    }
}
