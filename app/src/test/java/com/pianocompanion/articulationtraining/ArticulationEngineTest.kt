package com.pianocompanion.articulationtraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 演奏法辨识出题引擎单元测试。
 */
class ArticulationEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = ArticulationTrainingEngine.withSeed(42)
        val e2 = ArticulationTrainingEngine.withSeed(42)
        val q1 = e1.generate(ArticulationTrainingDifficulty.BEGINNER)
        val q2 = e2.generate(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(q1.articulation, q2.articulation)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = ArticulationTrainingEngine.withSeed(1)
        val engine2 = ArticulationTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(ArticulationTrainingDifficulty.ADVANCED)
            val q2 = engine2.generate(ArticulationTrainingDifficulty.ADVANCED)
            if (q1.articulation != q2.articulation) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = ArticulationTrainingEngine.withSeed(1)
        val q = engine.generate(ArticulationTrainingDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = ArticulationTrainingEngine.withSeed(1)
        val q = engine.generate(ArticulationTrainingDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为5`() {
        val engine = ArticulationTrainingEngine.withSeed(1)
        val q = engine.generate(ArticulationTrainingDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = ArticulationTrainingEngine.withSeed(7)
        for (difficulty in ArticulationTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = ArticulationTrainingEngine.withSeed(3)
        for (difficulty in ArticulationTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度演奏法池 ──────────────────────────────────────

    @Test
    fun `初级演奏法池只含连音断音重音`() {
        assertEquals(
            listOf(
                ArticulationType.LEGATO,
                ArticulationType.STACCATO,
                ArticulationType.MARCATO
            ),
            ArticulationType.forDifficulty(ArticulationTrainingDifficulty.BEGINNER)
        )
    }

    @Test
    fun `中级演奏法池含连音断音保持音重音`() {
        assertEquals(
            listOf(
                ArticulationType.LEGATO,
                ArticulationType.STACCATO,
                ArticulationType.TENUTO,
                ArticulationType.MARCATO
            ),
            ArticulationType.forDifficulty(ArticulationTrainingDifficulty.INTERMEDIATE)
        )
    }

    @Test
    fun `高级演奏法池含全部5种`() {
        assertEquals(
            ArticulationType.ALL,
            ArticulationType.forDifficulty(ArticulationTrainingDifficulty.ADVANCED)
        )
        assertEquals(5, ArticulationType.forDifficulty(ArticulationTrainingDifficulty.ADVANCED).size)
    }

    @Test
    fun `初级不包含次断音`() {
        val pool = ArticulationType.forDifficulty(ArticulationTrainingDifficulty.BEGINNER)
        assertFalse(ArticulationType.PORTATO in pool)
    }

    @Test
    fun `中级不包含次断音`() {
        val pool = ArticulationType.forDifficulty(ArticulationTrainingDifficulty.INTERMEDIATE)
        assertFalse(ArticulationType.PORTATO in pool)
    }

    @Test
    fun `高级包含次断音`() {
        val pool = ArticulationType.forDifficulty(ArticulationTrainingDifficulty.ADVANCED)
        assertTrue(ArticulationType.PORTATO in pool)
    }

    // ── 难度全覆盖 ──────────────────────────────────────

    @Test
    fun `高级难度1000次生成覆盖所有5种演奏法`() {
        val engine = ArticulationTrainingEngine()
        val seen = mutableSetOf<ArticulationType>()
        repeat(1000) {
            val q = engine.generate(ArticulationTrainingDifficulty.ADVANCED)
            seen.add(q.articulation)
        }
        assertEquals(5, seen.size)
    }

    @Test
    fun `初级难度500次生成只覆盖初级池`() {
        val engine = ArticulationTrainingEngine()
        val seen = mutableSetOf<ArticulationType>()
        repeat(500) {
            val q = engine.generate(ArticulationTrainingDifficulty.BEGINNER)
            seen.add(q.articulation)
        }
        assertEquals(ArticulationType.BEGINNER_ARTICULATIONS.toSet(), seen)
    }

    // ── 答案正确性 ──────────────────────────────────────

    @Test
    fun `正确答案等于演奏法fullLabel`() {
        val engine = ArticulationTrainingEngine.withSeed(5)
        for (difficulty in ArticulationTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.articulation.fullLabel, q.correctAnswer)
            }
        }
    }

    @Test
    fun `所有选项都来自当前难度池`() {
        val engine = ArticulationTrainingEngine.withSeed(8)
        for (difficulty in ArticulationTrainingDifficulty.ALL) {
            val pool = ArticulationType.forDifficulty(difficulty)
            val poolLabels = pool.map { it.fullLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                for (choice in q.answerChoices) {
                    assertTrue("选项 $choice 不在难度 ${difficulty.name} 的池中", choice in poolLabels)
                }
            }
        }
    }

    @Test
    fun `题目音符数量正确`() {
        val engine = ArticulationTrainingEngine()
        val q = engine.generate(ArticulationTrainingDifficulty.BEGINNER, noteCount = 7)
        assertEquals(7, q.noteCount)
    }

    @Test
    fun `默认音符数量为5`() {
        val engine = ArticulationTrainingEngine()
        val q = engine.generate(ArticulationTrainingDifficulty.ADVANCED)
        assertEquals(ArticulationTrainingEngine.DEFAULT_NOTE_COUNT, q.noteCount)
    }

    // ── onset 时间计算 ──────────────────────────────────────

    @Test
    fun `onsetTimes首项等于前导静音`() {
        val engine = ArticulationTrainingEngine()
        val onsets = engine.computeOnsetTimes(5)
        assertEquals(ArticulationTrainingEngine.LEAD_SILENCE_MS, onsets[0], 0.001)
    }

    @Test
    fun `onsetTimes间距等于节拍间距`() {
        val engine = ArticulationTrainingEngine()
        val onsets = engine.computeOnsetTimes(5)
        for (i in 1 until onsets.size) {
            assertEquals(
                ArticulationTrainingEngine.NOTE_SPACING_MS,
                onsets[i] - onsets[i - 1],
                0.001
            )
        }
    }

    @Test
    fun `onsetTimes数量等于音符数`() {
        val engine = ArticulationTrainingEngine()
        assertEquals(3, engine.computeOnsetTimes(3).size)
        assertEquals(5, engine.computeOnsetTimes(5).size)
        assertEquals(8, engine.computeOnsetTimes(8).size)
    }

    // ── 演奏法参数验证 ──────────────────────────────────────

    @Test
    fun `断音持续时间比例小于保持音`() {
        assertTrue(ArticulationType.STACCATO.durationRatio < ArticulationType.TENUTO.durationRatio)
    }

    @Test
    fun `连音持续时间比例大于保持音`() {
        assertTrue(ArticulationType.LEGATO.durationRatio > ArticulationType.TENUTO.durationRatio)
    }

    @Test
    fun `重音的accent值最大`() {
        var maxAccent = ArticulationType.ALL[0].accent
        var maxType = ArticulationType.ALL[0]
        for (a in ArticulationType.ALL) {
            if (a.accent > maxAccent) {
                maxAccent = a.accent
                maxType = a
            }
        }
        assertEquals(ArticulationType.MARCATO, maxType)
    }

    @Test
    fun `连音衰减时间常数最长`() {
        var maxDecay = ArticulationType.ALL[0].decayTimeConstantMs
        var maxType = ArticulationType.ALL[0]
        for (a in ArticulationType.ALL) {
            if (a.decayTimeConstantMs > maxDecay) {
                maxDecay = a.decayTimeConstantMs
                maxType = a
            }
        }
        assertEquals(ArticulationType.LEGATO, maxType)
    }

    @Test
    fun `断音起音时间短于连音`() {
        assertTrue(ArticulationType.STACCATO.attackMs < ArticulationType.LEGATO.attackMs)
    }

    @Test
    fun `所有演奏法的durationRatio在有效范围内`() {
        for (a in ArticulationType.ALL) {
            assertTrue("${a.englishName} durationRatio=${a.durationRatio}", a.durationRatio in 0.05f..1.5f)
        }
    }

    @Test
    fun `所有演奏法的accent在有效范围内`() {
        for (a in ArticulationType.ALL) {
            assertTrue("${a.englishName} accent=${a.accent}", a.accent in 0.0f..1.0f)
        }
    }
}
