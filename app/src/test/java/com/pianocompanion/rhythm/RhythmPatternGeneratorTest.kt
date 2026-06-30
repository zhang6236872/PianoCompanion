package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * [RhythmPatternGenerator] 节奏型生成器单元测试。
 *
 * 覆盖：
 * - 确定性（相同种子相同输出）
 * - 各难度时值池正确性
 * - 生成结果恰好填满目标拍数
 * - 音符音域范围
 * - 附点四分音符后自动补八分
 * - 不同难度生成不同复杂度
 */
class RhythmPatternGeneratorTest {

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `相同种子生成相同节奏型`() {
        val gen1 = RhythmPatternGenerator.withSeed(123)
        val gen2 = RhythmPatternGenerator.withSeed(123)
        val p1 = gen1.generate(RhythmDifficulty.BEGINNER)
        val p2 = gen2.generate(RhythmDifficulty.BEGINNER)
        assertEquals(p1.events.size, p2.events.size)
        for (i in p1.events.indices) {
            assertEquals(p1.events[i].duration, p2.events[i].duration)
            assertEquals(p1.events[i].midiNote, p2.events[i].midiNote)
        }
    }

    @Test
    fun `不同种子生成不同节奏型`() {
        val gen1 = RhythmPatternGenerator.withSeed(1)
        val gen2 = RhythmPatternGenerator.withSeed(999)
        val p1 = gen1.generate(RhythmDifficulty.INTERMEDIATE)
        val p2 = gen2.generate(RhythmDifficulty.INTERMEDIATE)
        // 事件序列可能碰巧相同，但 MIDI 音符几乎不会全相同
        val allSame = p1.events.indices.all {
            p1.events[it].duration == p2.events.getOrNull(it)?.duration &&
            p1.events[it].midiNote == p2.events.getOrNull(it)?.midiNote
        }
        assertFalse(allSame)
    }

    // ── 拍数填满 ──────────────────────────────────────────

    @Test
    fun `初级生成恰好4拍`() {
        val gen = RhythmPatternGenerator.withSeed(42)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER)
        assertEquals(4.0, pattern.totalBeats, 0.05)
    }

    @Test
    fun `中级生成恰好4拍`() {
        val gen = RhythmPatternGenerator.withSeed(42)
        val pattern = gen.generate(RhythmDifficulty.INTERMEDIATE)
        assertEquals(4.0, pattern.totalBeats, 0.05)
    }

    @Test
    fun `高级生成恰好4拍`() {
        val gen = RhythmPatternGenerator.withSeed(42)
        val pattern = gen.generate(RhythmDifficulty.ADVANCED)
        assertEquals(4.0, pattern.totalBeats, 0.05)
    }

    @Test
    fun `自定义拍数生成`() {
        val gen = RhythmPatternGenerator.withSeed(42)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER, targetBeats = 3.0)
        assertEquals(3.0, pattern.totalBeats, 0.05)
    }

    @Test
    fun `8拍生成`() {
        val gen = RhythmPatternGenerator.withSeed(7)
        val pattern = gen.generate(RhythmDifficulty.INTERMEDIATE, targetBeats = 8.0)
        assertEquals(8.0, pattern.totalBeats, 0.05)
    }

    @Test
    fun `多次生成均恰好填满拍数`() {
        val gen = RhythmPatternGenerator.withSeed(100)
        for (seed in 0L..50L) {
            val g = RhythmPatternGenerator.withSeed(seed)
            val p = g.generate(RhythmDifficulty.ADVANCED)
            assertEquals("seed=$seed: ${p.totalBeats}", 4.0, p.totalBeats, 0.1)
        }
    }

    // ── 难度时值池 ────────────────────────────────────────

    @Test
    fun `初级只使用四分和二分音符`() {
        val gen = RhythmPatternGenerator.withSeed(55)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER)
        for (event in pattern.events) {
            val d = event.duration
            assertTrue("初级不应出现 $d", d == RhythmDuration.QUARTER || d == RhythmDuration.HALF)
        }
    }

    @Test
    fun `中级可使用八分和附点四分`() {
        // 用多个种子确保八分/附点四分有机会出现
        val durations = mutableSetOf<RhythmDuration>()
        for (seed in 0L..200L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val pattern = gen.generate(RhythmDifficulty.INTERMEDIATE)
            pattern.events.forEach { durations.add(it.duration) }
        }
        assertTrue("中级应包含八分音符", durations.contains(RhythmDuration.EIGHTH))
        assertTrue("中级应包含附点四分", durations.contains(RhythmDuration.DOTTED_QUARTER))
    }

    @Test
    fun `高级可使用十六分和休止符`() {
        val durations = mutableSetOf<RhythmDuration>()
        for (seed in 0L..300L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val pattern = gen.generate(RhythmDifficulty.ADVANCED)
            pattern.events.forEach { durations.add(it.duration) }
        }
        assertTrue("高级应包含十六分音符", durations.contains(RhythmDuration.SIXTEENTH))
        assertTrue("高级应包含休止符",
            durations.contains(RhythmDuration.QUARTER_REST) ||
            durations.contains(RhythmDuration.EIGHTH_REST))
    }

    @Test
    fun `初级不出现十六分音符`() {
        for (seed in 0L..100L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val pattern = gen.generate(RhythmDifficulty.BEGINNER)
            for (event in pattern.events) {
                assertNotEquals(RhythmDuration.SIXTEENTH, event.duration)
            }
        }
    }

    @Test
    fun `初级和中级不出现休止符`() {
        for (seed in 0L..100L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val beginPattern = gen.generate(RhythmDifficulty.BEGINNER)
            val interPattern = gen.generate(RhythmDifficulty.INTERMEDIATE)
            for (e in beginPattern.events) assertFalse(e.isRest)
            for (e in interPattern.events) assertFalse(e.isRest)
        }
    }

    // ── 音符音域 ──────────────────────────────────────────

    @Test
    fun `非休止音符在舒适音域C4到B4`() {
        val gen = RhythmPatternGenerator.withSeed(42)
        val pattern = gen.generate(RhythmDifficulty.ADVANCED)
        for (event in pattern.events) {
            if (!event.isRest) {
                assertTrue("midi ${event.midiNote} < 60", event.midiNote >= 60)
                assertTrue("midi ${event.midiNote} > 71", event.midiNote <= 71)
            }
        }
    }

    @Test
    fun `多次生成音域始终正确`() {
        for (seed in 0L..100L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val pattern = gen.generate(RhythmDifficulty.ADVANCED)
            for (event in pattern.events) {
                if (!event.isRest) {
                    assertTrue(event.midiNote in 60..71)
                }
            }
        }
    }

    // ── 附点四分音符补八分 ────────────────────────────────

    @Test
    fun `附点四分音符后面跟八分音符`() {
        // 搜索一个包含附点四分的生成
        var found = false
        for (seed in 0L..500L) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            val pattern = gen.generate(RhythmDifficulty.INTERMEDIATE)
            for (i in pattern.events.indices) {
                if (pattern.events[i].duration == RhythmDuration.DOTTED_QUARTER &&
                    i + 1 < pattern.events.size) {
                    // 附点四分后必须有足够剩余（2拍），因此后面跟八分
                    assertEquals(
                        RhythmDuration.EIGHTH,
                        pattern.events[i + 1].duration
                    )
                    found = true
                }
            }
        }
        assertTrue("应在某些种子下生成附点四分音符", found)
    }

    // ── 速度 ──────────────────────────────────────────────

    @Test
    fun `生成的pattern使用指定速度`() {
        val gen = RhythmPatternGenerator.withSeed(1)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER, tempoBpm = 140)
        assertEquals(140, pattern.tempoBpm)
    }

    @Test
    fun `默认速度为90`() {
        val gen = RhythmPatternGenerator.withSeed(1)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER)
        assertEquals(90, pattern.tempoBpm)
    }

    // ── 难度区分 ──────────────────────────────────────────

    @Test
    fun `高级通常比初级有更多事件`() {
        // 统计平均事件数
        var beginTotal = 0
        var advancedTotal = 0
        val n = 50
        for (seed in 0L until n) {
            val gen = RhythmPatternGenerator.withSeed(seed)
            beginTotal += gen.generate(RhythmDifficulty.BEGINNER).events.size
            advancedTotal += gen.generate(RhythmDifficulty.ADVANCED).events.size
        }
        // 高级平均事件数 >= 初级（十六分音符/八分音符使事件更多）
        assertTrue(advancedTotal >= beginTotal)
    }

    @Test
    fun `默认Random实例也能生成`() {
        val gen = RhythmPatternGenerator()
        val pattern = gen.generate(RhythmDifficulty.BEGINNER)
        assertEquals(4.0, pattern.totalBeats, 0.05)
        assertTrue(pattern.events.isNotEmpty())
    }

    @Test
    fun `beatsPerMeasure正确`() {
        val gen = RhythmPatternGenerator.withSeed(1)
        val pattern = gen.generate(RhythmDifficulty.BEGINNER, targetBeats = 4.0)
        assertEquals(4, pattern.beatsPerMeasure)
    }
}
