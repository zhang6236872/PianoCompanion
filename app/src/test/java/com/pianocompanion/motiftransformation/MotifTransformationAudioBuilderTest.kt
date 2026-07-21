package com.pianocompanion.motiftransformation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动机发展辨识训练音频构建器单元测试。
 */
class MotifTransformationAudioBuilderTest {

    private val builder = MotifTransformationAudioBuilder(sampleRate = 44100)
    private val engine = MotifTransformationEngine.withSeed(42L)

    private fun makeQuestion(
        transformation: MotifTransformation,
        difficulty: MotifTransformationDifficulty = MotifTransformationDifficulty.ADVANCED
    ): MotifTransformationQuestion {
        var q: MotifTransformationQuestion? = null
        repeat(200) {
            val e = MotifTransformationEngine.withSeed(it.toLong())
            val candidate = e.generate(difficulty)
            if (candidate.transformation == transformation && q == null) {
                q = candidate
            }
        }
        return q ?: engine.generate(difficulty)
    }

    @Test
    fun `render produces non-empty buffer`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val audio = builder.render(q)
        assertTrue("渲染缓冲区不应为空", audio.isNotEmpty())
    }

    @Test
    fun `rendered samples are in valid range`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val audio = builder.render(q)
        audio.forEach { sample ->
            assertTrue("样本 $sample 在 [-1, 1]", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `render is deterministic`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val audio1 = builder.render(q)
        val audio2 = builder.render(q)
        assertEquals(audio1.size, audio2.size)
        for (i in audio1.indices) {
            assertEquals(audio1[i], audio2[i], 0.0001f)
        }
    }

    @Test
    fun `audio buffer is not all silence`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val audio = builder.render(q)
        val nonZero = audio.count { kotlin.math.abs(it) > 0.001f }
        assertTrue("音频应有非零样本", nonZero > audio.size / 10)
    }

    @Test
    fun `buildToneEvents has events for both sections`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        val originalEvents = events.filter { it.section == MotifTransformationAudioBuilder.SECTION_ORIGINAL }
        val transformedEvents = events.filter { it.section == MotifTransformationAudioBuilder.SECTION_TRANSFORMED }
        assertTrue("原始动机应有事件", originalEvents.isNotEmpty())
        assertTrue("变换后动机应有事件", transformedEvents.isNotEmpty())
    }

    @Test
    fun `original section has correct note count`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val originalEvents = builder.originalSectionEvents(q)
        assertEquals(q.originalNotes.size, originalEvents.size)
    }

    @Test
    fun `transformed section has correct note count`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val transformedEvents = builder.transformedSectionEvents(q)
        assertEquals(q.transformedNotes.size, transformedEvents.size)
    }

    @Test
    fun `total event count equals sum of both sections`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        assertEquals(q.originalNotes.size + q.transformedNotes.size, events.size)
    }

    @Test
    fun `onsets are monotonically non-decreasing`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val onsets = builder.computeOnsets(q)
        for (i in 1 until onsets.size) {
            assertTrue(
                "起音时间应单调非递减: onsets[$i]=${onsets[i]} >= onsets[${i - 1}]=${onsets[i - 1]}",
                onsets[i] >= onsets[i - 1]
            )
        }
    }

    @Test
    fun `first original onset is at zero`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val onsets = builder.computeOnsets(q)
        assertEquals(0.0, onsets[0], 0.001)
    }

    @Test
    fun `gap exists between original and transformed sections`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val events = builder.buildToneEvents(q)
        val originalEnd = events
            .filter { it.section == MotifTransformationAudioBuilder.SECTION_ORIGINAL }
            .maxOf { it.onsetMs + it.durationMs }
        val transformedStart = events
            .filter { it.section == MotifTransformationAudioBuilder.SECTION_TRANSFORMED }
            .minOf { it.onsetMs }
        val gap = transformedStart - originalEnd
        assertTrue("段间间隔应至少 ${MotifTransformationAudioBuilder.GAP_BETWEEN_MOTIFS_MS}ms", gap >= MotifTransformationAudioBuilder.GAP_BETWEEN_MOTIFS_MS)
    }

    @Test
    fun `REPETITION produces identical pitches in both sections`() {
        val q = makeQuestion(MotifTransformation.REPETITION)
        assertEquals(q.originalPitches, q.transformedPitches)
        assertEquals(q.originalDurations, q.transformedDurations)
    }

    @Test
    fun `SEQUENCE produces different first pitch`() {
        val q = makeQuestion(MotifTransformation.SEQUENCE)
        assertTrue(
            "模进后第一音应不同",
            q.originalPitches[0] != q.transformedPitches[0]
        )
    }

    @Test
    fun `SEQUENCE shifts all pitches by same amount`() {
        val q = makeQuestion(MotifTransformation.SEQUENCE)
        val shift = q.transformedPitches[0] - q.originalPitches[0]
        for (i in q.originalPitches.indices) {
            assertEquals("所有音移位一致", shift, q.transformedPitches[i] - q.originalPitches[i])
        }
    }

    @Test
    fun `INVERSION keeps first pitch same`() {
        val q = makeQuestion(MotifTransformation.INVERSION)
        assertEquals(
            "倒影保持第一音不变",
            q.originalPitches[0],
            q.transformedPitches[0]
        )
    }

    @Test
    fun `RETROGRADE reverses pitch order`() {
        val q = makeQuestion(MotifTransformation.RETROGRADE)
        assertEquals(q.originalPitches.reversed(), q.transformedPitches)
    }

    @Test
    fun `AUGMENTATION doubles durations`() {
        val q = makeQuestion(MotifTransformation.AUGMENTATION)
        for (i in q.originalDurations.indices) {
            assertEquals(
                "扩张后时值加倍",
                q.originalDurations[i] * 2.0,
                q.transformedDurations[i],
                0.001
            )
        }
    }

    @Test
    fun `AUGMENTATION keeps pitches same`() {
        val q = makeQuestion(MotifTransformation.AUGMENTATION)
        assertEquals(q.originalPitches, q.transformedPitches)
    }

    @Test
    fun `DIMINUTION halves durations`() {
        val q = makeQuestion(MotifTransformation.DIMINUTION)
        for (i in q.originalDurations.indices) {
            assertEquals(
                "紧缩后时值减半",
                q.originalDurations[i] / 2.0,
                q.transformedDurations[i],
                0.001
            )
        }
    }

    @Test
    fun `DIMINUTION keeps pitches same`() {
        val q = makeQuestion(MotifTransformation.DIMINUTION)
        assertEquals(q.originalPitches, q.transformedPitches)
    }

    @Test
    fun `AUGMENTATION transformed section longer than original`() {
        val q = makeQuestion(MotifTransformation.AUGMENTATION)
        val originalEvents = builder.originalSectionEvents(q)
        val transformedEvents = builder.transformedSectionEvents(q)
        val originalTotal = originalEvents.sumOf { it.durationMs }
        val transformedTotal = transformedEvents.sumOf { it.durationMs }
        assertTrue("扩张后段落应更长", transformedTotal > originalTotal)
    }

    @Test
    fun `DIMINUTION transformed section shorter than original`() {
        val q = makeQuestion(MotifTransformation.DIMINUTION)
        val originalEvents = builder.originalSectionEvents(q)
        val transformedEvents = builder.transformedSectionEvents(q)
        val originalTotal = originalEvents.sumOf { it.durationMs }
        val transformedTotal = transformedEvents.sumOf { it.durationMs }
        assertTrue("紧缩后段落应更短", transformedTotal < originalTotal)
    }

    @Test
    fun `REPETITION sections have equal total duration`() {
        val q = makeQuestion(MotifTransformation.REPETITION)
        val originalEvents = builder.originalSectionEvents(q)
        val transformedEvents = builder.transformedSectionEvents(q)
        val originalTotal = originalEvents.sumOf { it.durationMs }
        val transformedTotal = transformedEvents.sumOf { it.durationMs }
        assertEquals(originalTotal, transformedTotal, 0.001)
    }

    @Test
    fun `midiToFreq converts correctly`() {
        // A4 = 440 Hz at MIDI 69
        assertEquals(440.0, builder.midiToFreq(69), 0.1)
        // C4 ≈ 261.63 Hz at MIDI 60
        assertEquals(261.63, builder.midiToFreq(60), 0.1)
        // A5 = 880 Hz at MIDI 81
        assertEquals(880.0, builder.midiToFreq(81), 0.1)
    }

    @Test
    fun `estimateDurationMs is positive`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val duration = builder.estimateDurationMs(q)
        assertTrue(duration > 0)
    }

    @Test
    fun `estimateDurationMs includes lead and tail silence`() {
        val q = engine.generate(MotifTransformationDifficulty.BEGINNER)
        val duration = builder.estimateDurationMs(q)
        val events = builder.buildToneEvents(q)
        val musicEndMs = events.maxOf { it.onsetMs + it.durationMs }
        val expected = (MotifTransformationAudioBuilder.LEAD_SILENCE_MS + musicEndMs + MotifTransformationAudioBuilder.TAIL_SILENCE_MS).toLong()
        assertEquals(expected, duration)
    }

    @Test
    fun `all transformation types render without error`() {
        MotifTransformation.ALL.forEach { t ->
            val q = makeQuestion(t)
            val audio = builder.render(q)
            assertTrue("${t.name} 渲染非空", audio.isNotEmpty())
        }
    }

    @Test
    fun `custom sample rate produces different buffer size`() {
        val q = engine.generate(MotifTransformationDifficulty.ADVANCED)
        val b44100 = MotifTransformationAudioBuilder(44100)
        val b22050 = MotifTransformationAudioBuilder(22050)
        val a44100 = b44100.render(q)
        val a22050 = b22050.render(q)
        assertTrue("44100Hz 缓冲区应更大", a44100.size > a22050.size)
    }

    @Test
    fun `renderEvents with empty list returns empty`() {
        val audio = builder.renderEvents(emptyList())
        assertEquals(0, audio.size)
    }

    @Test
    fun `BEGINNER tempo is slower than ADVANCED`() {
        val qBeginner = engine.generate(MotifTransformationDifficulty.BEGINNER)
        val qAdvanced = MotifTransformationEngine.withSeed(42L).generate(MotifTransformationDifficulty.ADVANCED)
        val bEvents = builder.originalSectionEvents(qBeginner)
        val aEvents = builder.originalSectionEvents(qAdvanced)
        val bTotal = bEvents.sumOf { it.durationMs }
        val aTotal = aEvents.sumOf { it.durationMs }
        assertTrue("初级原始动机应更长（更慢）", bTotal > aTotal)
    }
}
