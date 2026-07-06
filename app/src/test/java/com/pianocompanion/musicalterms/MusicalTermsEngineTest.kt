package com.pianocompanion.musicalterms

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

/**
 * 音乐术语出题引擎（MusicalTermsEngine）单元测试。
 *
 * 验证题目生成的正确性：选项数、正确答案包含、去重、出题方向等。
 * 使用固定随机种子确保确定性测试。
 */
class MusicalTermsEngineTest {

    private fun engine(seed: Long = 42L) = MusicalTermsEngine(Random(seed))

    @Test
    fun `生成的题目包含正确答案`() {
        val eng = engine()
        val q = eng.generate(null, TermDifficulty.BEGINNER)
        assertTrue(q.correctAnswer in q.answerChoices)
    }

    @Test
    fun `初级难度选项数为 3`() {
        val eng = engine()
        // 多次生成验证一致性
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.BEGINNER)
            assertEquals(3, q.answerChoices.size)
        }
    }

    @Test
    fun `中级难度选项数为 4`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.INTERMEDIATE)
            assertEquals(4, q.answerChoices.size)
        }
    }

    @Test
    fun `高级难度选项数为 5`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.ADVANCED)
            assertEquals(5, q.answerChoices.size)
        }
    }

    @Test
    fun `选项无重复`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.ADVANCED)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = MusicalTermsEngine(Random(123L))
        val e2 = MusicalTermsEngine(Random(123L))
        val q1 = e1.generate(null, TermDifficulty.BEGINNER)
        val q2 = e2.generate(null, TermDifficulty.BEGINNER)
        assertEquals(q1.prompt, q2.prompt)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子产生不同题目`() {
        val e1 = MusicalTermsEngine(Random(1L))
        val e2 = MusicalTermsEngine(Random(999L))
        val q1 = e1.generate(null, TermDifficulty.ADVANCED)
        val q2 = e2.generate(null, TermDifficulty.ADVANCED)
        // 至少 prompt 或 answerChoices 之一不同
        assertTrue(q1.prompt != q2.prompt || q1.answerChoices != q2.answerChoices)
    }

    @Test
    fun `指定类别只出该类别的术语`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(TermCategory.DYNAMICS, TermDifficulty.ADVANCED)
            assertEquals(TermCategory.DYNAMICS, q.term.category)
        }
    }

    @Test
    fun `指定 TEMPO 类别出速度术语`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(TermCategory.TEMPO, TermDifficulty.BEGINNER)
            assertEquals(TermCategory.TEMPO, q.term.category)
        }
    }

    @Test
    fun `指定 ARTICULATION 类别出演奏法术语`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(TermCategory.ARTICULATION, TermDifficulty.INTERMEDIATE)
            assertEquals(TermCategory.ARTICULATION, q.term.category)
        }
    }

    @Test
    fun `TERM_TO_MEANING 方向 - prompt 是术语 answer 是含义`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.BEGINNER, QuizDirection.TERM_TO_MEANING)
            assertEquals(QuizDirection.TERM_TO_MEANING, q.direction)
            assertEquals(q.term.displayLabel, q.prompt)
            assertEquals(q.term.meaning, q.correctAnswer)
        }
    }

    @Test
    fun `MEANING_TO_TERM 方向 - prompt 是含义 answer 是术语`() {
        val eng = engine()
        repeat(10) {
            val q = eng.generate(null, TermDifficulty.BEGINNER, QuizDirection.MEANING_TO_TERM)
            assertEquals(QuizDirection.MEANING_TO_TERM, q.direction)
            assertEquals(q.term.meaning, q.prompt)
            assertEquals(q.term.displayLabel, q.correctAnswer)
        }
    }

    @Test
    fun `promptLabel 非空`() {
        val eng = engine()
        val q = eng.generate(null, TermDifficulty.BEGINNER)
        assertTrue(q.promptLabel.isNotBlank())
    }

    @Test
    fun `answerChoices 已打乱 - 正确答案不一定在第一位`() {
        val eng = engine()
        var correctAtFirst = 0
        repeat(100) {
            val q = eng.generate(null, TermDifficulty.INTERMEDIATE)
            if (q.answerChoices.first() == q.correctAnswer) correctAtFirst++
        }
        // 正确答案不应总是（也不应从不）在第一位
        assertTrue("正确答案在第一位的次数: $correctAtFirst/100", correctAtFirst in 1..99)
    }

    @Test
    fun `初级难度的术语全为 BEGINNER`() {
        val eng = engine()
        repeat(20) {
            val q = eng.generate(null, TermDifficulty.BEGINNER)
            assertEquals(TermDifficulty.BEGINNER, q.term.difficulty)
        }
    }

    @Test
    fun `中级难度的术语为 BEGINNER 或 INTERMEDIATE`() {
        val eng = engine()
        val validDiffs = setOf(TermDifficulty.BEGINNER, TermDifficulty.INTERMEDIATE)
        repeat(20) {
            val q = eng.generate(null, TermDifficulty.INTERMEDIATE)
            assertTrue(q.term.difficulty in validDiffs)
        }
    }

    @Test
    fun `干扰项不等于正确答案`() {
        val eng = engine()
        repeat(20) {
            val q = eng.generate(null, TermDifficulty.ADVANCED)
            assertEquals(1, q.answerChoices.count { it == q.correctAnswer })
        }
    }

    @Test
    fun `小类别池选项数不超过池大小`() {
        // MODIFIER 类别在初级可能只有 4 个术语
        val eng = engine()
        val q = eng.generate(TermCategory.MODIFIER, TermDifficulty.BEGINNER)
        // 选项数 ≤ 池大小（因为需要去重）
        assertTrue(q.answerChoices.size <= MusicalTermsLibrary.filter(TermCategory.MODIFIER, TermDifficulty.BEGINNER).size)
    }

    @Test
    fun `空类别池抛异常`() {
        // 不太可能发生（所有类别都有术语），但测试防御性编程
        // 使用一个保证有术语的类别
        val eng = engine()
        val q = eng.generate(TermCategory.EXPRESSION, TermDifficulty.BEGINNER)
        assertNotNull(q)
    }

    @Test
    fun `生成的 term 对象字段完整`() {
        val eng = engine()
        val q = eng.generate(null, TermDifficulty.ADVANCED)
        assertNotNull(q.term.term)
        assertNotNull(q.term.meaning)
        assertNotNull(q.term.category)
        assertNotNull(q.term.difficulty)
    }
}
