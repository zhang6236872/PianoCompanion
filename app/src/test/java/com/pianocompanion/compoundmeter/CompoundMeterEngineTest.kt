package com.pianocompanion.compoundmeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复合节拍听辨训练出题引擎单元测试。
 */
class CompoundMeterEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = CompoundMeterEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(CompoundMeterDifficulty.BEGINNER)
            assertEquals(CompoundMeterDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = CompoundMeterEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(CompoundMeterDifficulty.INTERMEDIATE)
            assertEquals(CompoundMeterDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 5 choices`() {
        val engine = CompoundMeterEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(CompoundMeterDifficulty.ADVANCED)
            assertEquals(CompoundMeterDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = CompoundMeterEngine.withSeed(3)
        CompoundMeterDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertTrue(
                    "Correct answer not in choices: ${q.correctAnswer}",
                    q.answerChoices.contains(q.correctAnswer)
                )
            }
        }
    }

    @Test
    fun `choices contain no duplicates`() {
        val engine = CompoundMeterEngine.withSeed(13)
        CompoundMeterDifficulty.ALL.forEach { difficulty ->
            repeat(20) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Duplicate choices found: ${q.answerChoices}",
                    q.answerChoices.size,
                    q.answerChoices.distinct().size
                )
            }
        }
    }

    // ── 确定性种子 ──────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val engine1 = CompoundMeterEngine.withSeed(100)
        val engine2 = CompoundMeterEngine.withSeed(100)
        val q1 = engine1.generate(CompoundMeterDifficulty.ADVANCED)
        val q2 = engine2.generate(CompoundMeterDifficulty.ADVANCED)
        assertEquals(q1.targetMeter, q2.targetMeter)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds produce different sequences eventually`() {
        val engine1 = CompoundMeterEngine.withSeed(1)
        val engine2 = CompoundMeterEngine.withSeed(999)
        val targets1 = (1..50).map { engine1.generate(CompoundMeterDifficulty.ADVANCED).targetMeter }
        val targets2 = (1..50).map { engine2.generate(CompoundMeterDifficulty.ADVANCED).targetMeter }
        assertNotEquals(targets1, targets2)
    }

    // ── 难度缩放 ──────────────────────────────────

    @Test
    fun `beginner only uses six-eight and three-four`() {
        val engine = CompoundMeterEngine.withSeed(55)
        repeat(30) {
            val q = engine.generate(CompoundMeterDifficulty.BEGINNER)
            assertTrue(
                "Beginner should only use 6/8 and 3/4, got ${q.targetMeter}",
                q.targetMeter in CompoundMeterDifficulty.BEGINNER.meters
            )
            // 所有选项也应该在该难度的拍子集合中
            q.answerChoices.forEach { choice ->
                val matchingMeter = MeterType.entries.find { it.displayName == choice }
                assertTrue(
                    "Choice '$choice' not in beginner meters",
                    matchingMeter != null && matchingMeter in CompoundMeterDifficulty.BEGINNER.meters
                )
            }
        }
    }

    @Test
    fun `intermediate uses only compound meters`() {
        val engine = CompoundMeterEngine.withSeed(88)
        repeat(30) {
            val q = engine.generate(CompoundMeterDifficulty.INTERMEDIATE)
            assertTrue(
                "Intermediate should only use compound meters, got ${q.targetMeter}",
                q.targetMeter in CompoundMeterDifficulty.INTERMEDIATE.meters
            )
            assertTrue(
                "Target should be compound",
                q.targetMeter.isCompound
            )
        }
    }

    @Test
    fun `advanced uses all 5 meter types`() {
        val engine = CompoundMeterEngine.withSeed(77)
        repeat(50) {
            val q = engine.generate(CompoundMeterDifficulty.ADVANCED)
            assertTrue(
                "Advanced should only use 5 meter types, got ${q.targetMeter}",
                q.targetMeter in CompoundMeterDifficulty.ADVANCED.meters
            )
        }
    }

    // ── 目标拍子覆盖 ──────────────────────────────────

    @Test
    fun `advanced covers all 5 meter types over 500 seeds`() {
        val seen = mutableSetOf<MeterType>()
        for (seed in 0L..499L) {
            val engine = CompoundMeterEngine.withSeed(seed)
            val q = engine.generate(CompoundMeterDifficulty.ADVANCED)
            seen.add(q.targetMeter)
        }
        assertEquals(
            "Advanced should cover all 5 meter types",
            MeterType.ADVANCED_METERS.toSet(),
            seen
        )
    }

    @Test
    fun `beginner covers both meter types over 200 seeds`() {
        val seen = mutableSetOf<MeterType>()
        for (seed in 0L..199L) {
            val engine = CompoundMeterEngine.withSeed(seed)
            val q = engine.generate(CompoundMeterDifficulty.BEGINNER)
            seen.add(q.targetMeter)
        }
        assertEquals(
            "Beginner should cover both 6/8 and 3/4",
            MeterType.BEGINNER_METERS.toSet(),
            seen
        )
    }

    @Test
    fun `intermediate covers all 3 compound meters over 300 seeds`() {
        val seen = mutableSetOf<MeterType>()
        for (seed in 0L..299L) {
            val engine = CompoundMeterEngine.withSeed(seed)
            val q = engine.generate(CompoundMeterDifficulty.INTERMEDIATE)
            seen.add(q.targetMeter)
        }
        assertEquals(
            "Intermediate should cover all 3 compound meters",
            MeterType.INTERMEDIATE_METERS.toSet(),
            seen
        )
    }

    // ── 选项内容验证 ──────────────────────────────────

    @Test
    fun `all choices are valid meter display names`() {
        val engine = CompoundMeterEngine.withSeed(123)
        CompoundMeterDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue(
                        "Choice '$choice' is not a valid meter display name",
                        MeterType.entries.any { it.displayName == choice }
                    )
                }
            }
        }
    }

    @Test
    fun `target meter display name equals correct answer`() {
        val engine = CompoundMeterEngine.withSeed(456)
        repeat(20) {
            val q = engine.generate(CompoundMeterDifficulty.ADVANCED)
            assertEquals(q.targetMeter.displayName, q.correctAnswer)
        }
    }

    @Test
    fun `beginner choices include both compound and simple meter`() {
        val engine = CompoundMeterEngine.withSeed(789)
        repeat(20) {
            val q = engine.generate(CompoundMeterDifficulty.BEGINNER)
            val chosenMeters = q.answerChoices.mapNotNull { choice ->
                MeterType.entries.find { it.displayName == choice }
            }
            val hasCompound = chosenMeters.any { it.isCompound }
            val hasSimple = chosenMeters.any { !it.isCompound }
            assertTrue("Beginner should have at least one compound meter", hasCompound)
            assertTrue("Beginner should have at least one simple meter", hasSimple)
        }
    }
}
