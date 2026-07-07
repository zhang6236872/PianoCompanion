package com.pianocompanion.melodymemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旋律记忆训练音频构建器单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 验证渲染长度、采样率、不削波、走向相关属性。
 */
class MelodyMemoryAudioBuilderTest {

    private val builder = MelodyMemoryAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────────

    @Test
    fun `render empty note list returns empty buffer`() {
        val buffer = builder.renderNotes(emptyList(), MelodyTempo.SLOW)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `render produces non-empty buffer for single note`() {
        val buffer = builder.renderNotes(listOf(60), MelodyTempo.SLOW)
        assertTrue("单音渲染应非空", buffer.isNotEmpty())
    }

    @Test
    fun `render sample rate is 44100`() {
        assertEquals(44100, MelodyMemoryAudioBuilder.SAMPLE_RATE)
    }

    // ── 长度 ──────────────────────────────────────────────

    @Test
    fun `render length increases with more notes`() {
        val threeNotes = builder.renderNotes(listOf(60, 62, 64), MelodyTempo.SLOW)
        val fiveNotes = builder.renderNotes(listOf(60, 62, 64, 65, 67), MelodyTempo.SLOW)
        assertTrue(
            "5 音旋律应比 3 音长 (5=${fiveNotes.size}, 3=${threeNotes.size})",
            fiveNotes.size > threeNotes.size
        )
    }

    @Test
    fun `render slow tempo longer than normal`() {
        val notes = listOf(60, 62, 64)
        val slow = builder.renderNotes(notes, MelodyTempo.SLOW)
        val normal = builder.renderNotes(notes, MelodyTempo.NORMAL)
        assertTrue(
            "慢速应比正常长 (slow=${slow.size}, normal=${normal.size})",
            slow.size > normal.size
        )
    }

    @Test
    fun `render duration estimate is reasonable`() {
        val question = MelodyQuestion(
            difficulty = MelodyDifficulty.INTERMEDIATE,
            tempo = MelodyTempo.SLOW,
            startMidi = 60,
            midiNotes = listOf(60, 62, 64, 65),
            contour = listOf(
                ContourInterval(MelodicDirection.UP, 2),
                ContourInterval(MelodicDirection.UP, 2),
                ContourInterval(MelodicDirection.UP, 1)
            ),
            answerChoices = listOf("↑ ↑ ↑", "↑ ↓ ↑", "↓ ↑ ↑", "↑ ↑ ↓"),
            correctAnswer = "↑ ↑ ↑"
        )
        val estimated = builder.estimateDurationMs(question)
        // 4 音 × 550ms + lead 250ms + tail 400ms = 2850ms
        val expected = MelodyMemoryAudioBuilder.LEAD_SILENCE_MS +
            4 * MelodyTempo.SLOW.noteDurationMs +
            MelodyMemoryAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    // ── 不削波 ────────────────────────────────────────────

    @Test
    fun `render samples are within negative one to one`() {
        val buffer = builder.renderNotes(listOf(60, 64, 67, 72), MelodyTempo.NORMAL)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    // ── 走向/难度渲染 ────────────────────────────────────

    @Test
    fun `render works for all difficulties`() {
        MelodyDifficulty.ALL.forEach { difficulty ->
            val engine = MelodyMemoryEngine.withSeed(1L)
            val q = engine.generate(difficulty)
            val buffer = builder.render(q)
            assertTrue("${difficulty.displayName} 渲染应非空", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render works for all tempos`() {
        MelodyTempo.ALL.forEach { tempo ->
            val buffer = builder.renderNotes(listOf(60, 62, 64), tempo)
            assertTrue("${tempo.displayName} 渲染应非空", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render produces non-zero audio signal`() {
        val buffer = builder.renderNotes(listOf(60, 64, 67), MelodyTempo.SLOW)
        val nonZeroCount = buffer.count { it != 0.0f }
        assertTrue("音频应有非零信号 (非零=${nonZeroCount})", nonZeroCount > buffer.size / 2)
    }

    @Test
    fun `render different melodies produce different buffers`() {
        val ascending = builder.renderNotes(listOf(60, 62, 64, 65, 67), MelodyTempo.NORMAL)
        val descending = builder.renderNotes(listOf(67, 65, 64, 62, 60), MelodyTempo.NORMAL)
        // 长度相同（同音数同速度），但内容不同
        assertEquals(ascending.size, descending.size)
        var diffCount = 0
        for (i in ascending.indices) {
            if (ascending[i] != descending[i]) diffCount++
        }
        assertTrue("不同旋律应产生不同音频", diffCount > 0)
    }

    @Test
    fun `render includes lead silence`() {
        val buffer = builder.renderNotes(listOf(60), MelodyTempo.SLOW)
        val leadSamples = (MelodyMemoryAudioBuilder.SAMPLE_RATE *
            MelodyMemoryAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        // 前导静音区域应全为零（或接近零）
        val leadEnd = minOf(leadSamples, buffer.size / 2)
        var silentCount = 0
        for (i in 0 until leadEnd) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("前导应有静音区域 (静音=${silentCount})", silentCount > 0)
    }

    @Test
    fun `render includes tail silence`() {
        val buffer = builder.renderNotes(listOf(60), MelodyTempo.SLOW)
        val tailSamples = (MelodyMemoryAudioBuilder.SAMPLE_RATE *
            MelodyMemoryAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val tailStart = buffer.size - tailSamples
        var silentCount = 0
        for (i in tailStart until buffer.size) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("尾部应有静音区域 (静音=${silentCount})", silentCount > 0)
    }
}
