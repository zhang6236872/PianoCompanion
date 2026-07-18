package com.pianocompanion.accentrecognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccentEngine] 单元测试。
 *
 * 验证确定性出题、难度缩放（候选拍数/重音强度）、选项正确性、答案包含正确位置等。
 */
class AccentRecognitionEngineTest {

    @Test
    fun `generate produces valid question with all fields`() {
        val engine = AccentEngine.withSeed(42L)
        val q = engine.generate(AccentDifficulty.BEGINNER)

        assertNotNull(q)
        assertEquals(AccentDifficulty.BEGINNER, q.difficulty)
        assertEquals(AccentStrength.STRONG, q.strength)
        assertEquals(AccentDifficulty.BEGINNER.tempoIntervalMs, q.beatIntervalMs, 0.0001)
        assertEquals(AccentDifficulty.BEGINNER.measureRepeat, q.measureRepeat)
        assertTrue(q.beatsPerMeasure in AccentDifficulty.BEGINNER.beatsPerMeasureOptions)
        assertTrue(q.accentPosition in 1..q.beatsPerMeasure)
    }

    @Test
    fun `deterministic - same seed produces same question`() {
        val e1 = AccentEngine.withSeed(123L)
        val e2 = AccentEngine.withSeed(123L)

        val q1 = e1.generate(AccentDifficulty.INTERMEDIATE)
        val q2 = e2.generate(AccentDifficulty.INTERMEDIATE)

        assertEquals(q1.beatsPerMeasure, q2.beatsPerMeasure)
        assertEquals(q1.accentPosition, q2.accentPosition)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val e1 = AccentEngine.withSeed(1L)
        val e2 = AccentEngine.withSeed(9999L)
        // 在高级难度（候选拍数多、位置多）下，不同种子几乎必然产生不同题目
        val q1 = e1.generate(AccentDifficulty.ADVANCED)
        val q2 = e2.generate(AccentDifficulty.ADVANCED)
        val different = q1.beatsPerMeasure != q2.beatsPerMeasure || q1.accentPosition != q2.accentPosition
        assertTrue("不同种子应产生不同题目", different)
    }

    @Test
    fun `beginner always uses 4 beats`() {
        val engine = AccentEngine.withSeed(7L)
        repeat(20) {
            val q = engine.generate(AccentDifficulty.BEGINNER)
            assertEquals(4, q.beatsPerMeasure)
        }
    }

    @Test
    fun `beginner has exactly 4 choices`() {
        val engine = AccentEngine.withSeed(7L)
        val q = engine.generate(AccentDifficulty.BEGINNER)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `intermediate uses 3 or 4 beats`() {
        val engine = AccentEngine.withSeed(7L)
        repeat(20) {
            val q = engine.generate(AccentDifficulty.INTERMEDIATE)
            assertTrue(q.beatsPerMeasure in listOf(3, 4))
            assertEquals(q.beatsPerMeasure, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced uses 2 to 5 beats`() {
        val engine = AccentEngine.withSeed(7L)
        val observed = mutableSetOf<Int>()
        repeat(60) {
            val q = engine.generate(AccentDifficulty.ADVANCED)
            assertTrue(q.beatsPerMeasure in listOf(2, 3, 4, 5))
            assertEquals(q.beatsPerMeasure, q.answerChoices.size)
            observed.add(q.beatsPerMeasure)
        }
        // 高级难度下，60 题应覆盖多种拍数（统计覆盖）
        assertTrue("高级应覆盖多种拍数: $observed", observed.size >= 2)
    }

    @Test
    fun `choices are ordered position labels`() {
        val engine = AccentEngine.withSeed(7L)
        val q = engine.generate(AccentDifficulty.BEGINNER)
        val expected = listOf("第 1 拍", "第 2 拍", "第 3 拍", "第 4 拍")
        assertEquals(expected, q.answerChoices)
    }

    @Test
    fun `correct answer matches accent position`() {
        val engine = AccentEngine.withSeed(7L)
        val q = engine.generate(AccentDifficulty.ADVANCED)
        assertEquals("第 ${q.accentPosition} 拍", q.correctAnswer)
    }

    @Test
    fun `correct answer is contained in choices`() {
        val engine = AccentEngine.withSeed(7L)
        for (difficulty in AccentDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertTrue("正确答案必须在选项中", q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `accent position is within valid range`() {
        val engine = AccentEngine.withSeed(7L)
        repeat(50) {
            val q = engine.generate(AccentDifficulty.ADVANCED)
            assertTrue(q.accentPosition in 1..q.beatsPerMeasure)
        }
    }

    @Test
    fun `difficulty strength scales with difficulty`() {
        val engine = AccentEngine.withSeed(7L)
        assertEquals(AccentStrength.STRONG, engine.generate(AccentDifficulty.BEGINNER).strength)
        assertEquals(AccentStrength.MEDIUM, engine.generate(AccentDifficulty.INTERMEDIATE).strength)
        assertEquals(AccentStrength.SUBTLE, engine.generate(AccentDifficulty.ADVANCED).strength)
    }

    @Test
    fun `all accent positions are reachable over many questions`() {
        // 验证引擎能产生各种强拍位置（不只是第 1 拍）
        val engine = AccentEngine.withSeed(100L)
        val positions = mutableSetOf<Int>()
        repeat(100) {
            val q = engine.generate(AccentDifficulty.BEGINNER)
            positions.add(q.accentPosition)
        }
        assertEquals(setOf(1, 2, 3, 4), positions)
    }

    @Test
    fun `all difficulties are generatable`() {
        val engine = AccentEngine()
        for (d in AccentDifficulty.ALL) {
            val q = engine.generate(d)
            assertEquals(d, q.difficulty)
        }
    }

    @Test
    fun `total clicks equals beats times repeat`() {
        val engine = AccentEngine.withSeed(7L)
        val q = engine.generate(AccentDifficulty.BEGINNER)
        assertEquals(q.beatsPerMeasure * q.measureRepeat, q.totalClicks)
    }

    @Test
    fun `positionLabel helper formats correctly`() {
        assertEquals("第 1 拍", AccentEngine.positionLabel(1))
        assertEquals("第 5 拍", AccentEngine.positionLabel(5))
    }

    @Test
    fun `question fullDescription contains key info`() {
        val engine = AccentEngine.withSeed(7L)
        val q = engine.generate(AccentDifficulty.BEGINNER)
        val desc = q.fullDescription
        assertTrue("4" in desc)
        assertTrue("第 ${q.accentPosition} 拍" in desc)
        assertTrue(q.strength.displayName in desc)
    }

    @Test
    fun `tempo interval differs by difficulty`() {
        val engine = AccentEngine.withSeed(7L)
        val beginner = engine.generate(AccentDifficulty.BEGINNER)
        val advanced = engine.generate(AccentDifficulty.ADVANCED)
        // 初级更慢（间隔更大），高级更快（间隔更小）
        assertTrue(beginner.beatIntervalMs > advanced.beatIntervalMs)
    }
}
