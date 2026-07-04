package com.pianocompanion.mixedpractice

import org.junit.Assert.*
import org.junit.Test

/**
 * 综合练习引擎单元测试。
 */
class MixedPracticeEngineTest {

    @Test
    fun `generate 返回包装后的 MixedQuestion`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        val question = engine.generate(MixedDifficulty.BEGINNER)
        assertNotNull(question)
        assertTrue(question is MixedQuestion.Note ||
            question is MixedQuestion.Interval ||
            question is MixedQuestion.Chord ||
            question is MixedQuestion.KeySig ||
            question is MixedQuestion.Rhythm)
    }

    @Test
    fun `generate 后 choices 和 correctAnswer 非空`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        val question = engine.generate(MixedDifficulty.INTERMEDIATE)
        assertNotNull(question.choices)
        assertTrue(question.choices.isNotEmpty())
        assertTrue(question.correctAnswer.isNotEmpty())
    }

    @Test
    fun `correctAnswer 必须在 choices 中`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        for (i in 1..30) {
            val question = engine.generate(MixedDifficulty.ADVANCED)
            assertTrue(
                "第 $i 题（${question.type}）的正确答案 '${question.correctAnswer}' 必须在选项中: ${question.choices}",
                question.correctAnswer in question.choices
            )
        }
    }

    @Test
    fun `连续 5 题不连续重复同一题型`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        // 生成 20 题，检查不连续重复
        var prevType: MixedQuestionType? = null
        for (i in 1..20) {
            val question = engine.generate(MixedDifficulty.BEGINNER)
            if (prevType != null) {
                assertNotEquals(
                    "第 $i 题与上一题题型不应相同",
                    prevType,
                    question.type
                )
            }
            prevType = question.type
        }
    }

    @Test
    fun `连续 20 题覆盖全部 5 种题型`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        val typesSeen = mutableSetOf<MixedQuestionType>()
        for (i in 1..20) {
            val question = engine.generate(MixedDifficulty.BEGINNER)
            typesSeen.add(question.type)
        }
        assertEquals(5, typesSeen.size)
    }

    @Test
    fun `相同种子产生相同题目序列`() {
        val engine1 = MixedPracticeEngine.withSeed(100L)
        val engine2 = MixedPracticeEngine.withSeed(100L)
        for (i in 1..10) {
            val q1 = engine1.generate(MixedDifficulty.BEGINNER)
            val q2 = engine2.generate(MixedDifficulty.BEGINNER)
            assertEquals(q1.type, q2.type)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
        }
    }

    @Test
    fun `generateByType 指定题型生成正确类型`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        MixedQuestionType.ALL.forEach { type ->
            val question = engine.generateByType(type, MixedDifficulty.BEGINNER)
            assertEquals(type, question.type)
        }
    }

    @Test
    fun `不同难度都能正常出题`() {
        val engine = MixedPracticeEngine.withSeed(42L)
        MixedDifficulty.ALL.forEach { difficulty ->
            val question = engine.generate(difficulty)
            assertNotNull(question)
            assertTrue(question.choices.isNotEmpty())
        }
    }
}
