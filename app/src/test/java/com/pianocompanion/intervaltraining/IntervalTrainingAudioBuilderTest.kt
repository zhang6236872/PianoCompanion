package com.pianocompanion.intervaltraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音程听辨训练音频构建器单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 *
 * 验证渲染长度、采样率、不削波、旋律/和声模式差异。
 */
class IntervalTrainingAudioBuilderTest {

    private val builder = IntervalTrainingAudioBuilder()

    // ── 基本渲染 ──────────────────────────────────────────

    @Test
    fun `render empty note list returns empty buffer`() {
        val buffer = builder.renderNotes(emptyList(), PlayDirection.ASCENDING)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `render produces non-empty buffer for single note`() {
        val buffer = builder.renderNotes(listOf(60), PlayDirection.ASCENDING)
        assertTrue("单音渲染应非空", buffer.isNotEmpty())
    }

    @Test
    fun `render produces non-empty buffer for two notes ascending`() {
        val buffer = builder.renderNotes(listOf(60, 64), PlayDirection.ASCENDING)
        assertTrue("两音上行应非空", buffer.isNotEmpty())
    }

    @Test
    fun `render produces non-empty buffer for two notes harmonic`() {
        val buffer = builder.renderNotes(listOf(60, 64), PlayDirection.HARMONIC)
        assertTrue("两音和声应非空", buffer.isNotEmpty())
    }

    @Test
    fun `sample rate is 44100`() {
        assertEquals(44100, IntervalTrainingAudioBuilder.SAMPLE_RATE)
    }

    // ── 长度 ──────────────────────────────────────────────

    @Test
    fun `melodic mode is longer than single note`() {
        val single = builder.renderNotes(listOf(60), PlayDirection.ASCENDING)
        val melodic = builder.renderNotes(listOf(60, 64), PlayDirection.ASCENDING)
        assertTrue(
            "旋律两音应比单音长 (melodic=${melodic.size}, single=${single.size})",
            melodic.size > single.size
        )
    }

    @Test
    fun `descending same length as ascending`() {
        val ascending = builder.renderNotes(listOf(60, 64), PlayDirection.ASCENDING)
        val descending = builder.renderNotes(listOf(64, 60), PlayDirection.DESCENDING)
        assertEquals(ascending.size, descending.size)
    }

    @Test
    fun `harmonic and melodic modes have different lengths`() {
        val harmonic = builder.renderNotes(listOf(60, 64), PlayDirection.HARMONIC)
        val melodic = builder.renderNotes(listOf(60, 64), PlayDirection.ASCENDING)
        // 和声使用更长单音时长(1500ms)，旋律用2×700ms，二者长度不同
        assertTrue(
            "和声与旋律长度应不同 (harmonic=${harmonic.size}, melodic=${melodic.size})",
            harmonic.size != melodic.size
        )
    }

    // ── 不削波 ────────────────────────────────────────────

    @Test
    fun `render samples are within negative one to one`() {
        val buffer = builder.renderNotes(listOf(60, 64, 67), PlayDirection.ASCENDING)
        buffer.forEach { sample ->
            assertTrue("采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `harmonic render samples are within negative one to one`() {
        val buffer = builder.renderNotes(listOf(60, 67), PlayDirection.HARMONIC)
        buffer.forEach { sample ->
            assertTrue("和声采样值 $sample 超出 [-1,1]", sample in -1.0f..1.0f)
        }
    }

    // ── 信号质量 ──────────────────────────────────────────

    @Test
    fun `render produces non-zero audio signal`() {
        val buffer = builder.renderNotes(listOf(60, 64), PlayDirection.ASCENDING)
        val nonZeroCount = buffer.count { it != 0.0f }
        assertTrue("音频应有非零信号 (非零=${nonZeroCount})", nonZeroCount > buffer.size / 3)
    }

    @Test
    fun `render different notes produce different buffers`() {
        val close = builder.renderNotes(listOf(60, 61), PlayDirection.ASCENDING)
        val far = builder.renderNotes(listOf(60, 72), PlayDirection.ASCENDING)
        assertEquals(close.size, far.size)
        var diffCount = 0
        for (i in close.indices) {
            if (close[i] != far[i]) diffCount++
        }
        assertTrue("不同音程应产生不同音频", diffCount > 0)
    }

    // ── 静音区域 ──────────────────────────────────────────

    @Test
    fun `render includes lead silence`() {
        val buffer = builder.renderNotes(listOf(60), PlayDirection.ASCENDING)
        val leadSamples = (IntervalTrainingAudioBuilder.SAMPLE_RATE *
            IntervalTrainingAudioBuilder.LEAD_SILENCE_MS / 1000.0).toInt()
        val leadEnd = minOf(leadSamples, buffer.size / 2)
        var silentCount = 0
        for (i in 0 until leadEnd) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("前导应有静音区域 (静音=${silentCount})", silentCount > 0)
    }

    @Test
    fun `render includes tail silence`() {
        val buffer = builder.renderNotes(listOf(60), PlayDirection.ASCENDING)
        val tailSamples = (IntervalTrainingAudioBuilder.SAMPLE_RATE *
            IntervalTrainingAudioBuilder.TAIL_SILENCE_MS / 1000.0).toInt()
        val tailStart = buffer.size - tailSamples
        var silentCount = 0
        for (i in tailStart until buffer.size) {
            if (buffer[i] == 0.0f) silentCount++
        }
        assertTrue("尾部应有静音区域 (静音=${silentCount})", silentCount > 0)
    }

    // ── 时长预估 ──────────────────────────────────────────

    @Test
    fun `estimate duration for melodic mode`() {
        val q = IntervalTrainingEngine.withSeed(1L).generate(
            IntervalDifficulty.BEGINNER, PlayDirection.ASCENDING
        )
        val estimated = builder.estimateDurationMs(q)
        val expected = IntervalTrainingAudioBuilder.LEAD_SILENCE_MS +
            2 * IntervalTrainingAudioBuilder.MELODIC_NOTE_DURATION_MS +
            IntervalTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    @Test
    fun `estimate duration for harmonic mode`() {
        val q = IntervalTrainingEngine.withSeed(1L).generate(
            IntervalDifficulty.BEGINNER, PlayDirection.HARMONIC
        )
        val estimated = builder.estimateDurationMs(q)
        val expected = IntervalTrainingAudioBuilder.LEAD_SILENCE_MS +
            IntervalTrainingAudioBuilder.HARMONIC_NOTE_DURATION_MS +
            IntervalTrainingAudioBuilder.TAIL_SILENCE_MS
        assertEquals(expected, estimated)
    }

    // ── 全难度/方向渲染 ──────────────────────────────────

    @Test
    fun `render works for all difficulties`() {
        IntervalDifficulty.ALL.forEach { difficulty ->
            val engine = IntervalTrainingEngine.withSeed(1L)
            val q = engine.generate(difficulty)
            val buffer = builder.render(q)
            assertTrue("${difficulty.displayName} 渲染应非空", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render works for all play directions`() {
        PlayDirection.ALL.forEach { dir ->
            val buffer = builder.renderNotes(listOf(60, 64), dir)
            assertTrue("${dir.displayName} 渲染应非空", buffer.isNotEmpty())
        }
    }
}
