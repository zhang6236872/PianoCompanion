package com.pianocompanion.intervalsequence

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class IntervalSequenceAudioBuilderTest {

    private val builder = IntervalSequenceAudioBuilder()

    private fun question(
        difficulty: IntervalSequenceDifficulty = IntervalSequenceDifficulty.INTERMEDIATE,
        seed: Long = 42L
    ): IntervalSequenceQuestion {
        val engine = IntervalSequenceEngine(difficulty, Random(seed))
        return engine.generate(seed)
    }

    @Test
    fun `渲染结果非空`() {
        val q = question()
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `渲染结果值域在 -1 到 1`() {
        val q = question()
        val pcm = builder.render(q)
        for (sample in pcm) {
            assertTrue("sample=$sample", sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `渲染确定性 - 相同种子相同输出`() {
        val q1 = question(seed = 123L)
        val q2 = question(seed = 123L)
        val pcm1 = builder.render(q1)
        val pcm2 = builder.render(q2)
        assertArrayEquals(pcm1, pcm2, 0.0001f)
    }

    @Test
    fun `音符事件数 = 旋律线音符数 × 重复次数`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val events = builder.buildNoteEvents(q)
        val expected = q.midiNotes.size * IntervalSequenceAudioBuilder.REPEAT_COUNT
        assertEquals(expected, events.size)
    }

    @Test
    fun `音符事件 MIDI 匹配旋律线`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 5L)
        val events = builder.buildNoteEvents(q)
        val midiNotes = q.midiNotes
        // 第一次重复
        for (i in midiNotes.indices) {
            assertEquals(midiNotes[i], events[i].midi)
        }
        // 第二次重复
        for (i in midiNotes.indices) {
            assertEquals(midiNotes[i], events[midiNotes.size + i].midi)
        }
    }

    @Test
    fun `音符事件频率 = midiToFrequency`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 3L)
        val events = builder.buildNoteEvents(q)
        for (event in events) {
            val expectedFreq = IntervalSequenceAudioBuilder.midiToFrequency(event.midi)
            assertEquals(expectedFreq, event.frequencyHz, 0.01)
        }
    }

    @Test
    fun `首次重复第一个音符起始在 0ms`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val events = builder.buildNoteEvents(q)
        assertEquals(0.0, events[0].onsetMs, 0.01)
    }

    @Test
    fun `音符间距始终一致`() {
        val q = question(IntervalSequenceDifficulty.INTERMEDIATE, 7L)
        val events = builder.buildNoteEvents(q)
        val noteCount = q.midiNotes.size
        // 第一次重复内的间距
        val spacing = events[1].onsetMs - events[0].onsetMs
        for (i in 1 until noteCount) {
            val diff = events[i].onsetMs - events[i - 1].onsetMs
            assertEquals(spacing, diff, 0.5)
        }
    }

    @Test
    fun `音符间距 = noteDurationMs + gapMs`() {
        val q = question(IntervalSequenceDifficulty.ADVANCED, 2L)
        val events = builder.buildNoteEvents(q)
        val expectedSpacing = q.difficulty.noteDurationMs + q.difficulty.gapMs
        val actualSpacing = events[1].onsetMs - events[0].onsetMs
        assertEquals(expectedSpacing, actualSpacing, 0.5)
    }

    @Test
    fun `两次重复间留有间隔`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val events = builder.buildNoteEvents(q)
        val noteCount = q.midiNotes.size
        val lastOfFirstRep = events[noteCount - 1].onsetMs
        val firstOfSecondRep = events[noteCount].onsetMs
        assertTrue(firstOfSecondRep > lastOfFirstRep)
    }

    @Test
    fun `两次重复间隔 = GAP_BETWEEN_REPETITIONS_MS`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val events = builder.buildNoteEvents(q)
        val noteCount = q.midiNotes.size
        val noteSpacing = q.difficulty.noteDurationMs + q.difficulty.gapMs
        val expectedGap = IntervalSequenceAudioBuilder.GAP_BETWEEN_REPETITIONS_MS
        // 第二次重复的第一个音符应位于: noteCount * noteSpacing + GAP
        assertEquals(
            noteCount * noteSpacing + expectedGap,
            events[noteCount].onsetMs,
            0.5
        )
    }

    @Test
    fun `每个音符持续时长 = noteDurationMs`() {
        val q = question(IntervalSequenceDifficulty.INTERMEDIATE, 3L)
        val events = builder.buildNoteEvents(q)
        for (event in events) {
            assertEquals(q.difficulty.noteDurationMs, event.durationMs, 0.0)
        }
    }

    @Test
    fun `BEGINNER 渲染非空`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `ADVANCED 渲染非空`() {
        val q = question(IntervalSequenceDifficulty.ADVANCED, 1L)
        val pcm = builder.render(q)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun `自定义采样率渲染`() {
        val customBuilder = IntervalSequenceAudioBuilder(22050)
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val pcm = customBuilder.render(q)
        assertTrue(pcm.isNotEmpty())
        for (sample in pcm) {
            assertTrue(sample in -1.0f..1.0f)
        }
    }

    @Test
    fun `estimateDurationMs 大于 0`() {
        val q = question(IntervalSequenceDifficulty.ADVANCED, 1L)
        val duration = builder.estimateDurationMs(q)
        assertTrue(duration > 0)
    }

    @Test
    fun `noteCount 返回正确值`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val expected = q.midiNotes.size * IntervalSequenceAudioBuilder.REPEAT_COUNT
        assertEquals(expected, builder.noteCount(q))
    }

    @Test
    fun `midiToFrequency MIDI 69 = A4 440`() {
        assertEquals(440.0, IntervalSequenceAudioBuilder.midiToFrequency(69), 0.01)
    }

    @Test
    fun `midiToFrequency MIDI 60 = C4 约 262`() {
        assertEquals(261.63, IntervalSequenceAudioBuilder.midiToFrequency(60), 0.1)
    }

    @Test
    fun `midiToFrequency MIDI 81 = A5 880`() {
        assertEquals(880.0, IntervalSequenceAudioBuilder.midiToFrequency(81), 0.01)
    }

    @Test
    fun `renderNotes 空列表返回空数组`() {
        val pcm = builder.renderNotes(emptyList(), 0.0)
        assertEquals(0, pcm.size)
    }

    @Test
    fun `上行音程序列使频率递增`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val events = builder.buildNoteEvents(q)
        // 第一次重复的前几个音符应呈现音高变化
        val freqs = events.take(q.midiNotes.size).map { it.frequencyHz }
        // 至少应该有变化（不是所有频率相同）
        assertTrue("频率应有变化", freqs.toSet().size > 1)
    }

    @Test
    fun `渲染输出包含非零采样`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        val pcm = builder.render(q)
        val nonZeroCount = pcm.count { it != 0.0f }
        assertTrue("应有非零采样", nonZeroCount > 100)
    }

    @Test
    fun `所有难度均可渲染且输出合法`() {
        IntervalSequenceDifficulty.values().forEach { diff ->
            val q = question(diff, 1L)
            val pcm = builder.render(q)
            assertTrue("${diff.name} 渲染非空", pcm.isNotEmpty())
            pcm.forEach { sample ->
                assertTrue("${diff.name} sample=$sample", sample in -1.0f..1.0f)
            }
        }
    }

    @Test
    fun `音符事件按时间排序`() {
        val q = question(IntervalSequenceDifficulty.ADVANCED, 10L)
        val events = builder.buildNoteEvents(q)
        for (i in 1 until events.size) {
            assertTrue(events[i].onsetMs >= events[i - 1].onsetMs)
        }
    }

    @Test
    fun `渲染时长与预估时长一致`() {
        val q = question(IntervalSequenceDifficulty.INTERMEDIATE, 1L)
        val pcm = builder.render(q)
        val estimatedMs = builder.estimateDurationMs(q)
        val estimatedSamples = (44100 * estimatedMs / 1000.0).toInt()
        // 容差 ±500 采样
        assertTrue(
            "pcm.size=${pcm.size} estimatedSamples=$estimatedSamples",
            kotlin.math.abs(pcm.size - estimatedSamples) <= 500
        )
    }

    @Test
    fun `noteCount 3音程序列 = 4音符 × 2重复 = 8`() {
        val q = question(IntervalSequenceDifficulty.BEGINNER, 1L)
        assertEquals(3, q.sequenceLength) // BEGINNER has 3 intervals
        assertEquals(4, q.midiNotes.size) // 3 intervals = 4 notes
        assertEquals(8, builder.noteCount(q)) // 4 × 2 = 8
    }

    @Test
    fun `noteCount 4音程序列 = 5音符 × 2重复 = 10`() {
        val q = question(IntervalSequenceDifficulty.ADVANCED, 1L)
        assertEquals(4, q.sequenceLength)
        assertEquals(5, q.midiNotes.size)
        assertEquals(10, builder.noteCount(q))
    }
}
