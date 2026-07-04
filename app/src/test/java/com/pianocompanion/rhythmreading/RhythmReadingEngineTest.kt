package com.pianocompanion.rhythmreading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RhythmReadingEngine] 单元测试。
 *
 * 验证：
 * - 时值池（各难度对应的时值集合）
 * - 指纹与标签生成
 * - 节奏型生成：总拍数恰好 = 4.0、元素合法、贪心填充终止性
 * - 初级避免全四分音符
 * - 选项构建：4 个选项、含正确答案、选项指纹互异
 * - 出题确定性（相同种子相同题目）
 * - 各难度出题覆盖
 */
class RhythmReadingEngineTest {

    private val engine = RhythmReadingEngine()

    // ── 时值池 ────────────────────────────────────────────

    @Test
    fun `beginner pool contains quarter and eighth only`() {
        val pool = engine.poolFor(RhythmReadingDifficulty.BEGINNER)
        assertTrue(pool.contains(RhythmDuration.QUARTER))
        assertTrue(pool.contains(RhythmDuration.EIGHTH))
        assertEquals(2, pool.size)
    }

    @Test
    fun `intermediate pool contains half and quarter rest`() {
        val pool = engine.poolFor(RhythmReadingDifficulty.INTERMEDIATE)
        assertTrue(pool.contains(RhythmDuration.HALF))
        assertTrue(pool.contains(RhythmDuration.QUARTER_REST))
        assertTrue(pool.contains(RhythmDuration.QUARTER))
        assertTrue(pool.contains(RhythmDuration.EIGHTH))
    }

    @Test
    fun `advanced pool contains sixteenth and eighth rest`() {
        val pool = engine.poolFor(RhythmReadingDifficulty.ADVANCED)
        assertTrue(pool.contains(RhythmDuration.SIXTEENTH))
        assertTrue(pool.contains(RhythmDuration.EIGHTH_REST))
        assertTrue(pool.contains(RhythmDuration.HALF))
    }

    @Test
    fun `intermediate pool does not contain sixteenth`() {
        val pool = engine.poolFor(RhythmReadingDifficulty.INTERMEDIATE)
        assertFalse(pool.contains(RhythmDuration.SIXTEENTH))
    }

    // ── 指纹与标签 ──────────────────────────────────────────

    @Test
    fun `fingerprint joins duration names with pipe`() {
        val items = listOf(
            RhythmItem(RhythmDuration.QUARTER),
            RhythmItem(RhythmDuration.EIGHTH),
            RhythmItem(RhythmDuration.EIGHTH)
        )
        assertEquals("QUARTER|EIGHTH|EIGHTH", engine.fingerprint(items))
    }

    @Test
    fun `different patterns have different fingerprints`() {
        val a = listOf(RhythmItem(RhythmDuration.QUARTER), RhythmItem(RhythmDuration.QUARTER))
        val b = listOf(RhythmItem(RhythmDuration.HALF))
        assertNotEquals(engine.fingerprint(a), engine.fingerprint(b))
    }

    @Test
    fun `label uses short labels separated by spaces`() {
        val items = listOf(
            RhythmItem(RhythmDuration.QUARTER),
            RhythmItem(RhythmDuration.EIGHTH)
        )
        assertEquals("四 八", engine.patternLabel(items))
    }

    @Test
    fun `label for sixteenth and rest`() {
        val items = listOf(
            RhythmItem(RhythmDuration.SIXTEENTH),
            RhythmItem(RhythmDuration.QUARTER_REST)
        )
        assertEquals("十六 休", engine.patternLabel(items))
    }

    // ── 节奏型生成 ──────────────────────────────────────────

    @Test
    fun `generated pattern sums to exactly 4 beats`() {
        repeat(50) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.BEGINNER)
            val total = pattern.sumOf { it.beats }
            assertEquals(4.0, total, 1e-9)
        }
    }

    @Test
    fun `intermediate pattern sums to exactly 4 beats`() {
        repeat(50) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.INTERMEDIATE)
            assertEquals(4.0, pattern.sumOf { it.beats }, 1e-9)
        }
    }

    @Test
    fun `advanced pattern sums to exactly 4 beats`() {
        repeat(50) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.ADVANCED)
            assertEquals(4.0, pattern.sumOf { it.beats }, 1e-9)
        }
    }

    @Test
    fun `beginner pattern uses only quarter and eighth`() {
        repeat(30) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.BEGINNER)
            pattern.forEach { item ->
                assertTrue(
                    "unexpected ${item.duration}",
                    item.duration == RhythmDuration.QUARTER || item.duration == RhythmDuration.EIGHTH
                )
            }
        }
    }

    @Test
    fun `beginner pattern is not all quarters`() {
        repeat(20) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.BEGINNER)
            val allQuarter = pattern.all { it.duration == RhythmDuration.QUARTER }
            assertFalse("pattern should not be all quarters", allQuarter)
        }
    }

    @Test
    fun `advanced pattern may contain sixteenth`() {
        var foundSixteenth = false
        repeat(100) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.ADVANCED)
            if (pattern.any { it.duration == RhythmDuration.SIXTEENTH }) {
                foundSixteenth = true
            }
        }
        assertTrue("sixteenth should appear in advanced patterns", foundSixteenth)
    }

    @Test
    fun `pattern has at least 2 elements`() {
        repeat(30) {
            val pattern = engine.generatePattern(RhythmReadingDifficulty.INTERMEDIATE)
            assertTrue("size=${pattern.size}", pattern.size >= 2)
        }
    }

    @Test
    fun `pattern elements are from the correct pool`() {
        repeat(20) {
            val pool = engine.poolFor(RhythmReadingDifficulty.ADVANCED).toSet()
            val pattern = engine.generatePattern(RhythmReadingDifficulty.ADVANCED)
            pattern.forEach { item ->
                assertTrue(pool.contains(item.duration))
            }
        }
    }

    // ── 完整题目生成 ─────────────────────────────────────────

    @Test
    fun `generated question has exactly 4 options`() {
        val q = engine.generate(RhythmReadingDifficulty.BEGINNER)
        assertEquals(4, q.answerOptions.size)
    }

    @Test
    fun `correct answer fingerprint is among options`() {
        val q = engine.generate(RhythmReadingDifficulty.INTERMEDIATE)
        val optionFingerprints = q.answerOptions.map { it.fingerprint }
        assertTrue(optionFingerprints.contains(q.correctAnswer))
    }

    @Test
    fun `all option fingerprints are distinct`() {
        val q = engine.generate(RhythmReadingDifficulty.ADVANCED)
        val fps = q.answerOptions.map { it.fingerprint }
        assertEquals(4, fps.toSet().size)
    }

    @Test
    fun `question pattern matches correct answer fingerprint`() {
        val q = engine.generate(RhythmReadingDifficulty.BEGINNER)
        assertEquals(engine.fingerprint(q.pattern), q.correctAnswer)
    }

    @Test
    fun `exactly one option matches correct answer`() {
        val q = engine.generate(RhythmReadingDifficulty.INTERMEDIATE)
        val matchCount = q.answerOptions.count { it.fingerprint == q.correctAnswer }
        assertEquals(1, matchCount)
    }

    @Test
    fun `question total beats is 4`() {
        val q = engine.generate(RhythmReadingDifficulty.ADVANCED)
        assertEquals(4.0, q.totalBeats, 1e-9)
    }

    // ── 确定性 ──────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val e1 = RhythmReadingEngine.withSeed(42L)
        val e2 = RhythmReadingEngine.withSeed(42L)
        val q1 = e1.generate(RhythmReadingDifficulty.BEGINNER)
        val q2 = e2.generate(RhythmReadingDifficulty.BEGINNER)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerOptions.map { it.fingerprint }, q2.answerOptions.map { it.fingerprint })
    }

    @Test
    fun `same seed produces same pattern sequence`() {
        val e1 = RhythmReadingEngine.withSeed(100L)
        val e2 = RhythmReadingEngine.withSeed(100L)
        repeat(5) {
            val p1 = e1.generatePattern(RhythmReadingDifficulty.ADVANCED)
            val p2 = e2.generatePattern(RhythmReadingDifficulty.ADVANCED)
            assertEquals(engine.fingerprint(p1), engine.fingerprint(p2))
        }
    }

    @Test
    fun `different seeds likely produce different questions`() {
        val e1 = RhythmReadingEngine.withSeed(1L)
        val e2 = RhythmReadingEngine.withSeed(999L)
        val q1 = e1.generate(RhythmReadingDifficulty.BEGINNER)
        val q2 = e2.generate(RhythmReadingDifficulty.BEGINNER)
        // 不要求一定不同，但大概率不同
        var anyDiff = false
        repeat(10) {
            val a = RhythmReadingEngine.withSeed(it.toLong() * 7 + 3).generate(RhythmReadingDifficulty.BEGINNER)
            val b = RhythmReadingEngine.withSeed(it.toLong() * 7 + 4).generate(RhythmReadingDifficulty.BEGINNER)
            if (a.correctAnswer != b.correctAnswer) anyDiff = true
        }
        assertTrue(anyDiff)
    }

    // ── 各难度覆盖 ──────────────────────────────────────────

    @Test
    fun `all difficulties generate valid questions`() {
        RhythmReadingDifficulty.ALL.forEach { diff ->
            val q = engine.generate(diff)
            assertEquals(diff, q.difficulty)
            assertEquals(4.0, q.totalBeats, 1e-9)
            assertEquals(4, q.answerOptions.size)
        }
    }

    @Test
    fun `many generations always produce valid questions`() {
        repeat(100) {
            val q = engine.generate(RhythmReadingDifficulty.ADVANCED)
            assertEquals(4, q.answerOptions.size)
            assertEquals(4, q.answerOptions.map { it.fingerprint }.toSet().size)
            assertEquals(4.0, q.totalBeats, 1e-9)
        }
    }
}
