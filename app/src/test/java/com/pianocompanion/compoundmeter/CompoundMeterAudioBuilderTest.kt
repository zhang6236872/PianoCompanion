package com.pianocompanion.compoundmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复合节拍听辨训练音频构建器单元测试。
 */
class CompoundMeterAudioBuilderTest {

    private val builder = CompoundMeterAudioBuilder()

    // ── 辅助：构建指定拍子的测试题目 ──────────────────────────────────

    private fun questionFor(meter: MeterType): CompoundMeterQuestion {
        val difficulty = when (meter) {
            MeterType.SIX_EIGHT, MeterType.THREE_FOUR -> CompoundMeterDifficulty.BEGINNER
            MeterType.NINE_EIGHT -> CompoundMeterDifficulty.INTERMEDIATE
            MeterType.FOUR_FOUR, MeterType.TWELVE_EIGHT -> CompoundMeterDifficulty.ADVANCED
        }
        return CompoundMeterQuestion(
            difficulty = difficulty,
            seed = 0L,
            targetMeter = meter,
            answerChoices = difficulty.meters.map { it.displayName },
            correctAnswer = meter.displayName
        )
    }

    // ── 拍子结构 ──────────────────────────────────

    @Test
    fun `6_8 has 2 beats per bar`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        val bar0Beats = events.filter { it.barNumber == 0 && it.isBeat }
        val bar1Beats = events.filter { it.barNumber == 1 && it.isBeat }
        assertEquals(2, bar0Beats.size)
        assertEquals(2, bar1Beats.size)
    }

    @Test
    fun `9_8 has 3 beats per bar`() {
        val q = questionFor(MeterType.NINE_EIGHT)
        val events = builder.buildBeatEvents(q)
        val bar0Beats = events.filter { it.barNumber == 0 && it.isBeat }
        assertEquals(3, bar0Beats.size)
    }

    @Test
    fun `12_8 has 4 beats per bar`() {
        val q = questionFor(MeterType.TWELVE_EIGHT)
        val events = builder.buildBeatEvents(q)
        val bar0Beats = events.filter { it.barNumber == 0 && it.isBeat }
        assertEquals(4, bar0Beats.size)
    }

    @Test
    fun `3_4 has 3 beats per bar`() {
        val q = questionFor(MeterType.THREE_FOUR)
        val events = builder.buildBeatEvents(q)
        val bar0Beats = events.filter { it.barNumber == 0 && it.isBeat }
        assertEquals(3, bar0Beats.size)
    }

    @Test
    fun `4_4 has 4 beats per bar`() {
        val q = questionFor(MeterType.FOUR_FOUR)
        val events = builder.buildBeatEvents(q)
        val bar0Beats = events.filter { it.barNumber == 0 && it.isBeat }
        assertEquals(4, bar0Beats.size)
    }

    // ── 关键区分：6/8 vs 3/4 ──────────────────────────────────

    @Test
    fun `6_8 and 3_4 have same eighth note count but different beat count`() {
        assertEquals(
            "6/8 and 3/4 should have the same eighth notes per bar",
            MeterType.SIX_EIGHT.eighthNotesPerBar,
            MeterType.THREE_FOUR.eighthNotesPerBar
        )
        assertNotEquals(
            "6/8 and 3/4 should have different beat counts",
            MeterType.SIX_EIGHT.beatCount,
            MeterType.THREE_FOUR.beatCount
        )
    }

    @Test
    fun `6_8 beats are spaced 3 apart, 3_4 beats are spaced 2 apart`() {
        val q68 = questionFor(MeterType.SIX_EIGHT)
        val q34 = questionFor(MeterType.THREE_FOUR)
        val events68 = builder.buildBeatEvents(q68).filter { it.barNumber == 0 && it.isBeat }
        val events34 = builder.buildBeatEvents(q34).filter { it.barNumber == 0 && it.isBeat }

        // 6/8: beat positions should be 0, 3 (spacing 3)
        val positions68 = events68.map { it.positionInBar }
        assertEquals(listOf(0, 3), positions68)

        // 3/4: beat positions should be 0, 2, 4 (spacing 2)
        val positions34 = events34.map { it.positionInBar }
        assertEquals(listOf(0, 2, 4), positions34)
    }

    @Test
    fun `6_8 and 3_4 have different accent patterns`() {
        assertNotEquals(
            MeterType.SIX_EIGHT.accentPattern,
            MeterType.THREE_FOUR.accentPattern
        )
    }

    // ── 事件数量 ──────────────────────────────────

    @Test
    fun `6_8 beginner has 12 total events (6 per bar times 2 bars)`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        assertEquals(12, events.size)
        assertEquals(12, builder.totalBeatEventCount(q))
    }

    @Test
    fun `9_8 intermediate has 18 total events`() {
        val q = questionFor(MeterType.NINE_EIGHT)
        val events = builder.buildBeatEvents(q)
        assertEquals(18, events.size)
        assertEquals(18, builder.totalBeatEventCount(q))
    }

    @Test
    fun `12_8 advanced has 24 total events`() {
        val q = questionFor(MeterType.TWELVE_EIGHT)
        val events = builder.buildBeatEvents(q)
        assertEquals(24, events.size)
        assertEquals(24, builder.totalBeatEventCount(q))
    }

    @Test
    fun `3_4 beginner has 12 total events`() {
        val q = questionFor(MeterType.THREE_FOUR)
        val events = builder.buildBeatEvents(q)
        assertEquals(12, events.size)
    }

    @Test
    fun `4_4 advanced has 16 total events`() {
        val q = questionFor(MeterType.FOUR_FOUR)
        val events = builder.buildBeatEvents(q)
        assertEquals(16, events.size)
    }

    // ── 强拍位置 ──────────────────────────────────

    @Test
    fun `downbeat is always at position 0 in each bar`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val events = builder.buildBeatEvents(q)
            val downbeats = events.filter { it.isDownbeat }
            assertTrue(
                "Meter ${meter.name} should have downbeats",
                downbeats.isNotEmpty()
            )
            downbeats.forEach { db ->
                assertEquals(
                    "Downbeat should be at position 0 in bar ${db.barNumber}",
                    0,
                    db.positionInBar
                )
            }
        }
    }

    @Test
    fun `downbeat has highest accent level`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val events = builder.buildBeatEvents(q)
            val maxAccent = events.maxOf { it.accent }
            val downbeatAccent = events.first { it.isDownbeat }.accent
            assertEquals(maxAccent, downbeatAccent, 0.001f)
        }
    }

    // ── 频率区分 ──────────────────────────────────

    @Test
    fun `downbeat has higher frequency than subdivisions`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        val downbeat = events.first { it.isDownbeat }
        val subdivision = events.first { !it.isBeat }
        assertTrue(
            "Downbeat freq (${downbeat.frequencyHz}) should be > subdivision freq (${subdivision.frequencyHz})",
            downbeat.frequencyHz > subdivision.frequencyHz
        )
    }

    @Test
    fun `beat has higher frequency than subdivision`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        val beat = events.filter { it.isBeat && !it.isDownbeat }.first()
        val subdivision = events.first { !it.isBeat }
        assertTrue(
            "Beat freq (${beat.frequencyHz}) should be > subdivision freq (${subdivision.frequencyHz})",
            beat.frequencyHz > subdivision.frequencyHz
        )
    }

    @Test
    fun `downbeat frequency is 880 Hz`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        val downbeat = events.first { it.isDownbeat }
        assertEquals(880.0, downbeat.frequencyHz, 0.1)
    }

    @Test
    fun `subdivision frequency is 440 Hz`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val events = builder.buildBeatEvents(q)
        val subdivision = events.first { !it.isBeat }
        assertEquals(440.0, subdivision.frequencyHz, 0.1)
    }

    // ── 时间线 ──────────────────────────────────

    @Test
    fun `events are sequential with no overlap`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val events = builder.buildBeatEvents(q)
            for (i in 1 until events.size) {
                assertTrue(
                    "Meter ${meter.name}: event $i overlaps with event ${i - 1}",
                    events[i].onsetMs >= events[i - 1].onsetMs + events[i - 1].durationMs - 1.0
                )
            }
        }
    }

    @Test
    fun `events span exactly 2 bars`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val events = builder.buildBeatEvents(q)
            val barNumbers = events.map { it.barNumber }.distinct()
            assertEquals(listOf(0, 1), barNumbers)
        }
    }

    @Test
    fun `first event onset is zero`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val events = builder.buildBeatEvents(q)
            assertEquals(0.0, events[0].onsetMs, 0.01)
        }
    }

    // ── 时长 ──────────────────────────────────

    @Test
    fun `music duration is positive for all difficulties`() {
        CompoundMeterDifficulty.ALL.forEach { difficulty ->
            val engine = CompoundMeterEngine.withSeed(95)
            val q = engine.generate(difficulty)
            val dur = builder.musicDurationMs(q)
            assertTrue("Duration should be positive for ${difficulty.name}", dur > 0)
        }
    }

    @Test
    fun `estimateDurationMs is positive and includes silence padding`() {
        val engine = CompoundMeterEngine.withSeed(140)
        val q = engine.generate(CompoundMeterDifficulty.BEGINNER)
        val estimated = builder.estimateDurationMs(q)
        val musicDur = builder.musicDurationMs(q)
        assertTrue("Estimated should be > music duration", estimated > musicDur.toLong())
    }

    @Test
    fun `longer pattern produces longer music duration`() {
        // 12/8 (12 eighths/bar) should be longer than 6/8 (6 eighths/bar) at same tempo
        val q12 = CompoundMeterQuestion(
            difficulty = CompoundMeterDifficulty.ADVANCED,
            seed = 0L,
            targetMeter = MeterType.TWELVE_EIGHT,
            answerChoices = MeterType.ADVANCED_METERS.map { it.displayName },
            correctAnswer = MeterType.TWELVE_EIGHT.displayName
        )
        val q6 = CompoundMeterQuestion(
            difficulty = CompoundMeterDifficulty.BEGINNER,
            seed = 0L,
            targetMeter = MeterType.SIX_EIGHT,
            answerChoices = MeterType.BEGINNER_METERS.map { it.displayName },
            correctAnswer = MeterType.SIX_EIGHT.displayName
        )
        // Note: different difficulties have different eighth durations, so compare event counts
        assertTrue(
            "12/8 should have more events than 6/8",
            builder.totalBeatEventCount(q12) > builder.totalBeatEventCount(q6)
        )
    }

    // ── 渲染 ──────────────────────────────────

    @Test
    fun `render produces non-empty buffer`() {
        MeterType.entries.forEach { meter ->
            val q = questionFor(meter)
            val buffer = builder.render(q)
            assertTrue("Buffer should be non-empty for ${meter.name}", buffer.isNotEmpty())
        }
    }

    @Test
    fun `render buffer samples are within valid range`() {
        val q = questionFor(MeterType.TWELVE_EIGHT)
        val buffer = builder.render(q)
        buffer.forEach { sample ->
            assertTrue("Sample $sample out of range", sample >= -1.0f && sample <= 1.0f)
        }
    }

    @Test
    fun `render buffer is not silent`() {
        val q = questionFor(MeterType.SIX_EIGHT)
        val buffer = builder.render(q)
        val maxAmp = buffer.maxOf { kotlin.math.abs(it) }
        assertTrue("Buffer should have audible content, max amplitude = $maxAmp", maxAmp > 0.01f)
    }

    // ── 拍子属性 ──────────────────────────────────

    @Test
    fun `compound meters have ternary subdivision`() {
        MeterType.COMPOUND_METERS.forEach { meter ->
            assertEquals(
                "Compound meter ${meter.name} should have 3 subdivisions per beat",
                3,
                meter.subdivisionsPerBeat
            )
        }
    }

    @Test
    fun `simple meters have binary subdivision`() {
        listOf(MeterType.THREE_FOUR, MeterType.FOUR_FOUR).forEach { meter ->
            assertEquals(
                "Simple meter ${meter.name} should have 2 subdivisions per beat",
                2,
                meter.subdivisionsPerBeat
            )
        }
    }

    @Test
    fun `isCompound is true for 6_8 9_8 12_8`() {
        assertTrue(MeterType.SIX_EIGHT.isCompound)
        assertTrue(MeterType.NINE_EIGHT.isCompound)
        assertTrue(MeterType.TWELVE_EIGHT.isCompound)
    }

    @Test
    fun `isCompound is false for 3_4 4_4`() {
        assertTrue(!MeterType.THREE_FOUR.isCompound)
        assertTrue(!MeterType.FOUR_FOUR.isCompound)
    }

    @Test
    fun `all meter accent patterns start with downbeat`() {
        MeterType.entries.forEach { meter ->
            assertTrue(
                "Meter ${meter.name} accent pattern should start with high accent (downbeat)",
                meter.accentPattern[0] >= 0.8f
            )
        }
    }

    // ── 空事件 ──────────────────────────────────

    @Test
    fun `renderEvents with empty list returns empty array`() {
        val result = builder.renderEvents(emptyList())
        assertEquals(0, result.size)
    }

    // ── 不同拍子产生不同事件 ──────────────────────────────────

    @Test
    fun `different meters produce different event counts`() {
        val events68 = builder.buildBeatEvents(questionFor(MeterType.SIX_EIGHT)).size
        val events98 = builder.buildBeatEvents(questionFor(MeterType.NINE_EIGHT)).size
        val events128 = builder.buildBeatEvents(questionFor(MeterType.TWELVE_EIGHT)).size
        assertNotEquals("6/8 and 9/8 should differ", events68, events98)
        assertNotEquals("9/8 and 12/8 should differ", events98, events128)
        assertNotEquals("6/8 and 12/8 should differ", events68, events128)
    }
}
