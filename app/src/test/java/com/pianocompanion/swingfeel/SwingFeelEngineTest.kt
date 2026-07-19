package com.pianocompanion.swingfeel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwingFeelEngine] 单元测试。
 *
 * 验证确定性出题、难度缩放（选项数/速度）、选项排序与正确性、种子复现等。
 */
class SwingFeelEngineTest {

    // ── 基础出题 ──────────────────────────────────────────

    @Test
    fun `generate returns question with correct difficulty`() {
        val engine = SwingFeelEngine.withSeed(1L)
        SwingDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(d, q.difficulty)
        }
    }

    @Test
    fun `generate picks ratio from candidate set`() {
        val engine = SwingFeelEngine.withSeed(2L)
        SwingDifficulty.ALL.forEach { d ->
            repeat(50) {
                val q = engine.generate(d)
                assertTrue(
                    "${q.ratio} 不在 ${d.displayName} 候选集 ${d.candidateRatios}",
                    q.ratio in d.candidateRatios
                )
            }
        }
    }

    @Test
    fun `correct answer is among choices`() {
        val engine = SwingFeelEngine.withSeed(3L)
        repeat(100) {
            val q = engine.generate(SwingDifficulty.ADVANCED)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    // ── 选项正确性 ────────────────────────────────────────

    @Test
    fun `beginner has exactly two options`() {
        val engine = SwingFeelEngine.withSeed(4L)
        repeat(20) {
            val q = engine.generate(SwingDifficulty.BEGINNER)
            assertEquals(2, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate and advanced have three options`() {
        val engine = SwingFeelEngine.withSeed(5L)
        listOf(SwingDifficulty.INTERMEDIATE, SwingDifficulty.ADVANCED).forEach { d ->
            repeat(20) {
                val q = engine.generate(d)
                assertEquals(3, q.answerChoices.size)
            }
        }
    }

    @Test
    fun `options are sorted by swing amount ascending`() {
        val engine = SwingFeelEngine.withSeed(6L)
        listOf(SwingDifficulty.INTERMEDIATE, SwingDifficulty.ADVANCED).forEach { d ->
            repeat(10) {
                val q = engine.generate(d)
                val amounts = q.answerChoices.map { name ->
                    SwingFeelEngine.swingAmount(SwingRatio.ALL.first { it.displayName == name })
                }
                // 应严格非递减（等分=0 < 轻摇摆=1 < 摇摆=2）
                for (i in 1 until amounts.size) {
                    assertTrue(
                        "选项顺序 ${q.answerChoices} 不是按摇摆程度升序",
                        amounts[i] > amounts[i - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `options contain no duplicates`() {
        val engine = SwingFeelEngine.withSeed(7L)
        SwingDifficulty.ALL.forEach { d ->
            repeat(20) {
                val q = engine.generate(d)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `correct answer equals ratio display name`() {
        val engine = SwingFeelEngine.withSeed(8L)
        repeat(100) {
            val q = engine.generate(SwingDifficulty.ADVANCED)
            assertEquals(q.ratio.displayName, q.correctAnswer)
        }
    }

    // ── swingFraction 一致性 ──────────────────────────────

    @Test
    fun `swingFraction equals ratio canonical fraction`() {
        val engine = SwingFeelEngine.withSeed(9L)
        SwingDifficulty.ALL.forEach { d ->
            repeat(30) {
                val q = engine.generate(d)
                assertEquals(q.ratio.fraction, q.swingFraction, 1e-9)
            }
        }
    }

    @Test
    fun `straight fraction is exactly half`() {
        val engine = SwingFeelEngine.withSeed(10L)
        // 找一个等分题
        val q = (0 until 200).map { engine.generate(SwingDifficulty.ADVANCED) }.first { it.ratio == SwingRatio.STRAIGHT }
        assertEquals(0.5, q.swingFraction, 1e-9)
    }

    @Test
    fun `swing fraction is two-thirds`() {
        val engine = SwingFeelEngine.withSeed(11L)
        val q = (0 until 200).map { engine.generate(SwingDifficulty.ADVANCED) }.first { it.ratio == SwingRatio.SWING }
        assertEquals(2.0 / 3.0, q.swingFraction, 1e-9)
    }

    // ── 难度缩放（速度）──────────────────────────────────

    @Test
    fun `tempo increases with difficulty`() {
        val engine = SwingFeelEngine.withSeed(12L)
        val t1 = engine.generate(SwingDifficulty.BEGINNER).tempoBpm
        val t2 = engine.generate(SwingDifficulty.INTERMEDIATE).tempoBpm
        val t3 = engine.generate(SwingDifficulty.ADVANCED).tempoBpm
        assertTrue("初级速度 $t1 应不大于中级 $t2", t1 <= t2)
        assertTrue("中级速度 $t2 应小于高级 $t3", t2 < t3)
    }

    @Test
    fun `beatMs decreases as tempo increases`() {
        val engine = SwingFeelEngine.withSeed(13L)
        val b1 = engine.generate(SwingDifficulty.BEGINNER).beatMs
        val b3 = engine.generate(SwingDifficulty.ADVANCED).beatMs
        assertTrue("初级 beatMs $b1 应大于高级 $b3", b1 > b3)
    }

    @Test
    fun `noteCount equals twice beatsPerQuestion`() {
        val engine = SwingFeelEngine.withSeed(14L)
        SwingDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(d.beatsPerQuestion * 2, q.noteCount)
        }
    }

    // ── 确定性（种子复现）────────────────────────────────

    @Test
    fun `same seed produces same question sequence`() {
        val e1 = SwingFeelEngine.withSeed(99L)
        val e2 = SwingFeelEngine.withSeed(99L)
        repeat(30) {
            val q1 = e1.generate(SwingDifficulty.ADVANCED)
            val q2 = e2.generate(SwingDifficulty.ADVANCED)
            assertEquals(q1.ratio, q2.ratio)
            assertEquals(q1.swingFraction, q2.swingFraction, 1e-12)
        }
    }

    @Test
    fun `different seeds likely produce different sequences`() {
        val e1 = SwingFeelEngine.withSeed(1L)
        val e2 = SwingFeelEngine.withSeed(2L)
        var anyDifferent = false
        repeat(50) {
            if (e1.generate(SwingDifficulty.ADVANCED).ratio != e2.generate(SwingDifficulty.ADVANCED).ratio) {
                anyDifferent = true
            }
        }
        assertTrue("不同种子应产生不同的题目序列", anyDifferent)
    }

    @Test
    fun `distribution covers all candidates over many draws`() {
        val engine = SwingFeelEngine.withSeed(42L)
        val seen = mutableSetOf<SwingRatio>()
        repeat(300) {
            seen += engine.generate(SwingDifficulty.ADVANCED).ratio
        }
        // 高级候选集有 3 种，300 次抽取应全部出现
        assertEquals(SwingDifficulty.ADVANCED.candidateRatios.toSet(), seen)
    }

    @Test
    fun `beginner never produces light swing`() {
        val engine = SwingFeelEngine.withSeed(7L)
        repeat(200) {
            val q = engine.generate(SwingDifficulty.BEGINNER)
            assertNotEquals(SwingRatio.LIGHT_SWING, q.ratio)
        }
    }
}
