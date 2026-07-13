package com.pianocompanion.timbretraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音色辨识出题引擎单元测试。
 */
class TimbreTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = TimbreTrainingEngine.withSeed(42)
        val e2 = TimbreTrainingEngine.withSeed(42)
        val q1 = e1.generate(TimbreTrainingDifficulty.BEGINNER)
        val q2 = e2.generate(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(q1.instrument, q2.instrument)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = TimbreTrainingEngine.withSeed(1)
        val engine2 = TimbreTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(TimbreTrainingDifficulty.ADVANCED)
            val q2 = engine2.generate(TimbreTrainingDifficulty.ADVANCED)
            if (q1.instrument != q2.instrument) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = TimbreTrainingEngine.withSeed(1)
        val q = engine.generate(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = TimbreTrainingEngine.withSeed(1)
        val q = engine.generate(TimbreTrainingDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为6`() {
        val engine = TimbreTrainingEngine.withSeed(1)
        val q = engine.generate(TimbreTrainingDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = TimbreTrainingEngine.withSeed(7)
        for (difficulty in TimbreTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = TimbreTrainingEngine.withSeed(3)
        for (difficulty in TimbreTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度乐器池 ──────────────────────────────────────

    @Test
    fun `初级乐器池只含钢琴长笛小号`() {
        assertEquals(
            listOf(TimbreInstrument.PIANO, TimbreInstrument.FLUTE, TimbreInstrument.TRUMPET),
            TimbreInstrument.BEGINNER_INSTRUMENTS
        )
    }

    @Test
    fun `中级乐器池含钢琴小提琴长笛小号`() {
        assertEquals(
            listOf(TimbreInstrument.PIANO, TimbreInstrument.VIOLIN, TimbreInstrument.FLUTE, TimbreInstrument.TRUMPET),
            TimbreInstrument.INTERMEDIATE_INSTRUMENTS
        )
    }

    @Test
    fun `高级乐器池含全部6种`() {
        assertEquals(6, TimbreInstrument.ALL.size)
        assertEquals(TimbreInstrument.ALL, TimbreInstrument.forDifficulty(TimbreTrainingDifficulty.ADVANCED))
    }

    @Test
    fun `初级题目答案必在初级乐器池中`() {
        val engine = TimbreTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TimbreTrainingDifficulty.BEGINNER)
            assertTrue(q.instrument in TimbreInstrument.BEGINNER_INSTRUMENTS)
        }
    }

    @Test
    fun `中级题目答案必在中级乐器池中`() {
        val engine = TimbreTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TimbreTrainingDifficulty.INTERMEDIATE)
            assertTrue(q.instrument in TimbreInstrument.INTERMEDIATE_INSTRUMENTS)
        }
    }

    @Test
    fun `高级题目答案必在全部乐器池中`() {
        val engine = TimbreTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TimbreTrainingDifficulty.ADVANCED)
            assertTrue(q.instrument in TimbreInstrument.ALL)
        }
    }

    @Test
    fun `所有选项均来自该难度乐器池`() {
        val engine = TimbreTrainingEngine.withSeed(11)
        for (difficulty in TimbreTrainingDifficulty.ALL) {
            val pool = TimbreInstrument.forDifficulty(difficulty)
            val poolLabels = pool.map { it.shortLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue("选项 $choice 不在难度池中", choice in poolLabels)
                }
            }
        }
    }

    // ── 高级难度可覆盖所有6种乐器 ──────────────────────

    @Test
    fun `高级难度充分覆盖全部乐器类型`() {
        val engine = TimbreTrainingEngine.withSeed(100)
        val seen = mutableSetOf<TimbreInstrument>()
        for (i in 1..200) {
            val q = engine.generate(TimbreTrainingDifficulty.ADVANCED)
            seen.add(q.instrument)
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun `初级难度可覆盖全部3种乐器类型`() {
        val engine = TimbreTrainingEngine.withSeed(100)
        val seen = mutableSetOf<TimbreInstrument>()
        for (i in 1..100) {
            val q = engine.generate(TimbreTrainingDifficulty.BEGINNER)
            seen.add(q.instrument)
        }
        assertEquals(3, seen.size)
    }

    @Test
    fun `中级难度可覆盖全部4种乐器类型`() {
        val engine = TimbreTrainingEngine.withSeed(100)
        val seen = mutableSetOf<TimbreInstrument>()
        for (i in 1..100) {
            val q = engine.generate(TimbreTrainingDifficulty.INTERMEDIATE)
            seen.add(q.instrument)
        }
        assertEquals(4, seen.size)
    }

    // ── noteDurationMs ──────────────────────────────────

    @Test
    fun `默认noteDurationMs为1500`() {
        val engine = TimbreTrainingEngine()
        val q = engine.generate(TimbreTrainingDifficulty.BEGINNER)
        assertEquals(1500L, q.noteDurationMs)
    }

    @Test
    fun `可自定义noteDurationMs`() {
        val engine = TimbreTrainingEngine()
        val q = engine.generate(TimbreTrainingDifficulty.ADVANCED, noteDurationMs = 2000)
        assertEquals(2000L, q.noteDurationMs)
    }

    // ── answerChoices 格式 ─────────────────────────────

    @Test
    fun `正确答案格式与shortLabel一致`() {
        val engine = TimbreTrainingEngine.withSeed(8)
        for (difficulty in TimbreTrainingDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.instrument.shortLabel, q.correctAnswer)
        }
    }

    @Test
    fun `选项打乱后正确答案仍可匹配`() {
        val engine = TimbreTrainingEngine.withSeed(22)
        for (i in 1..30) {
            val q = engine.generate(TimbreTrainingDifficulty.ADVANCED)
            val matchCount = q.answerChoices.count { it == q.correctAnswer }
            assertEquals(1, matchCount)
        }
    }

    // ── baseFrequency 验证 ─────────────────────────────

    @Test
    fun `所有乐器基频为440Hz`() {
        for (instrument in TimbreInstrument.ALL) {
            assertEquals(440.0, instrument.baseFrequency, 0.01)
        }
    }

    // ── emoji 和名称 ────────────────────────────────────

    @Test
    fun `每种乐器有不同的emoji`() {
        val emojis = TimbreInstrument.ALL.map { it.emoji }
        assertEquals(emojis.size, emojis.toSet().size)
    }

    @Test
    fun `每种乐器有不同的englishName`() {
        val names = TimbreInstrument.ALL.map { it.englishName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `每种乐器有不同的displayName`() {
        val names = TimbreInstrument.ALL.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `fullLabel包含emoji和名称`() {
        for (instrument in TimbreInstrument.ALL) {
            assertTrue(instrument.fullLabel.contains(instrument.emoji))
            assertTrue(instrument.fullLabel.contains(instrument.englishName))
            assertTrue(instrument.fullLabel.contains(instrument.displayName))
        }
    }

    @Test
    fun `shortLabel不含emoji但含名称`() {
        for (instrument in TimbreInstrument.ALL) {
            assertFalse(instrument.shortLabel.contains(instrument.emoji))
            assertTrue(instrument.shortLabel.contains(instrument.englishName))
            assertTrue(instrument.shortLabel.contains(instrument.displayName))
        }
    }

    @Test
    fun `description非空`() {
        for (instrument in TimbreInstrument.ALL) {
            assertTrue(instrument.description.isNotEmpty())
        }
    }
}
