package com.pianocompanion.voiceentryorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声部进入顺序辨识训练出题引擎单元测试。
 */
class VoiceEntryEngineTest {

    // ── 选项正确性 ──────────────────────────────────

    @Test
    fun `beginner generates exactly 2 choices`() {
        val engine = VoiceEntryEngine.withSeed(42)
        repeat(20) {
            val q = engine.generate(EntryDifficulty.BEGINNER)
            assertEquals(EntryDifficulty.BEGINNER.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `intermediate generates exactly 3 choices`() {
        val engine = VoiceEntryEngine.withSeed(7)
        repeat(30) {
            val q = engine.generate(EntryDifficulty.INTERMEDIATE)
            assertEquals(EntryDifficulty.INTERMEDIATE.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `advanced generates exactly 4 choices`() {
        val engine = VoiceEntryEngine.withSeed(99)
        repeat(30) {
            val q = engine.generate(EntryDifficulty.ADVANCED)
            assertEquals(EntryDifficulty.ADVANCED.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `correct answer is always among choices`() {
        val engine = VoiceEntryEngine.withSeed(3)
        EntryDifficulty.ALL.forEach { difficulty ->
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
        val engine = VoiceEntryEngine.withSeed(13)
        EntryDifficulty.ALL.forEach { difficulty ->
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

    @Test
    fun `choices are never empty`() {
        val engine = VoiceEntryEngine.withSeed(33)
        EntryDifficulty.ALL.forEach { difficulty ->
            repeat(5) {
                val q = engine.generate(difficulty)
                assertTrue("Choices should not be empty", q.answerChoices.isNotEmpty())
            }
        }
    }

    @Test
    fun `answer count matches difficulty choice count`() {
        val engine = VoiceEntryEngine.withSeed(33)
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty.choiceCount, q.answerChoices.size)
        }
    }

    @Test
    fun `all choices are distinct permutations`() {
        val engine = VoiceEntryEngine.withSeed(21)
        EntryDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                // 每个选项标签应对应该难度音区的一个合法排列
                q.answerChoices.forEach { label ->
                    val regs = difficulty.registers
                    val allLabels = VoiceEntryEngine.permutations(regs).map { orderLabel(it) }
                    assertTrue(
                        "Choice '$label' is not a valid permutation",
                        label in allLabels
                    )
                }
            }
        }
    }

    // ── 确定性 / 种子 ──────────────────────────────────

    @Test
    fun `deterministic generation with same seed`() {
        val engine1 = VoiceEntryEngine.withSeed(777)
        val engine2 = VoiceEntryEngine.withSeed(777)
        repeat(10) {
            val q1 = engine1.generate(EntryDifficulty.ADVANCED)
            val q2 = engine2.generate(EntryDifficulty.ADVANCED)
            assertEquals(q1.entryOrder, q2.entryOrder)
            assertEquals(q1.answerChoices, q2.answerChoices)
            assertEquals(q1.correctAnswer, q2.correctAnswer)
            assertEquals(q1.seed, q2.seed)
        }
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val questions = (0L..100).map { seed ->
            val e = VoiceEntryEngine.withSeed(seed)
            e.generate(EntryDifficulty.ADVANCED)
        }
        val distinctOrders = questions.map { it.entryOrder }.distinct()
        assertTrue(
            "Expected variety of entry orders across seeds, got ${distinctOrders.size}",
            distinctOrders.size >= 2
        )
    }

    @Test
    fun `different engines with different seeds produce different sequences`() {
        val engine1 = VoiceEntryEngine.withSeed(1)
        val engine2 = VoiceEntryEngine.withSeed(2)
        var anyDifferent = false
        repeat(20) {
            val q1 = engine1.generate(EntryDifficulty.ADVANCED)
            val q2 = engine2.generate(EntryDifficulty.ADVANCED)
            if (q1.entryOrder != q2.entryOrder || q1.answerChoices != q2.answerChoices) {
                anyDifferent = true
            }
        }
        assertTrue("Different seeds should produce different sequences", anyDifferent)
    }

    @Test
    fun `seed is captured in question`() {
        val engine = VoiceEntryEngine.withSeed(123)
        val q = engine.generate(EntryDifficulty.ADVANCED)
        assertNotNull(q.seed)
        assertNotEquals(0L, q.seed)
    }

    // ── 难度缩放 / 候选集约束 ──────────────────────────────────

    @Test
    fun `beginner uses only soprano and bass`() {
        val engine = VoiceEntryEngine.withSeed(2)
        repeat(50) {
            val q = engine.generate(EntryDifficulty.BEGINNER)
            assertTrue(
                "Beginner must use Soprano and Bass only, got ${q.entryOrder}",
                VoiceRegister.ALTO !in q.entryOrder &&
                    VoiceRegister.SOPRANO in q.entryOrder &&
                    VoiceRegister.BASS in q.entryOrder
            )
        }
    }

    @Test
    fun `intermediate uses all three registers`() {
        val engine = VoiceEntryEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(EntryDifficulty.INTERMEDIATE)
            assertEquals(
                "Intermediate must use all 3 registers",
                VoiceRegister.TRIPLE_REGISTERS.toSet(),
                q.entryOrder.toSet()
            )
        }
    }

    @Test
    fun `advanced uses all three registers`() {
        val engine = VoiceEntryEngine.withSeed(50)
        repeat(50) {
            val q = engine.generate(EntryDifficulty.ADVANCED)
            assertEquals(
                "Advanced must use all 3 registers",
                VoiceRegister.TRIPLE_REGISTERS.toSet(),
                q.entryOrder.toSet()
            )
        }
    }

    @Test
    fun `question difficulty matches requested`() {
        val engine = VoiceEntryEngine.withSeed(33)
        EntryDifficulty.ALL.forEach { difficulty ->
            val q = engine.generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    @Test
    fun `voice count matches difficulty`() {
        val engine = VoiceEntryEngine.withSeed(33)
        EntryDifficulty.ALL.forEach { difficulty ->
            repeat(10) {
                val q = engine.generate(difficulty)
                assertEquals(difficulty.voiceCount, q.voiceCount)
            }
        }
    }

    @Test
    fun `entry order is a permutation of difficulty registers`() {
        val engine = VoiceEntryEngine.withSeed(20)
        EntryDifficulty.ALL.forEach { difficulty ->
            repeat(30) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Entry order must be a permutation of ${difficulty.registers}",
                    difficulty.registers.toSet(),
                    q.entryOrder.toSet()
                )
                assertEquals(difficulty.voiceCount, q.entryOrder.size)
            }
        }
    }

    @Test
    fun `no duplicate registers within a question`() {
        val engine = VoiceEntryEngine.withSeed(20)
        EntryDifficulty.ALL.forEach { difficulty ->
            repeat(30) {
                val q = engine.generate(difficulty)
                assertEquals(
                    "Registers must be unique within entry order: ${q.entryOrder}",
                    q.entryOrder.size,
                    q.entryOrder.distinct().size
                )
            }
        }
    }

    // ── 排列覆盖率 ──────────────────────────────────

    @Test
    fun `beginner produces both possible orders across seeds`() {
        val orders = mutableSetOf<List<VoiceRegister>>()
        for (seed in 0L..500) {
            val engine = VoiceEntryEngine.withSeed(seed)
            val q = engine.generate(EntryDifficulty.BEGINNER)
            orders.add(q.entryOrder)
        }
        assertEquals(
            "Beginner (2 registers) should produce both permutations",
            VoiceEntryEngine.permutations(VoiceRegister.BEGINNER_REGISTERS).size,
            orders.size
        )
    }

    @Test
    fun `intermediate produces all 6 permutations across seeds`() {
        val orders = mutableSetOf<List<VoiceRegister>>()
        for (seed in 0L..3000) {
            val engine = VoiceEntryEngine.withSeed(seed)
            val q = engine.generate(EntryDifficulty.INTERMEDIATE)
            orders.add(q.entryOrder)
        }
        assertEquals(
            "Intermediate (3 registers) should produce all 6 permutations",
            6,
            orders.size
        )
    }

    // ── orderLabel & 标签 ──────────────────────────────────

    @Test
    fun `correct answer label matches entry order`() {
        val engine = VoiceEntryEngine.withSeed(8)
        repeat(30) {
            val q = engine.generate(EntryDifficulty.ADVANCED)
            assertEquals(orderLabel(q.entryOrder), q.correctAnswer)
        }
    }

    @Test
    fun `emojiOrderLabel has correct count`() {
        val engine = VoiceEntryEngine.withSeed(8)
        val q = engine.generate(EntryDifficulty.ADVANCED)
        assertEquals(q.voiceCount, q.emojiOrderLabel.split(" → ").size)
    }

    // ── permutations 直接测试 ──────────────────────────────────

    @Test
    fun `permutations of 2 items has 2 results`() {
        assertEquals(2, VoiceEntryEngine.permutations(listOf("a", "b")).size)
    }

    @Test
    fun `permutations of 3 items has 6 results`() {
        assertEquals(6, VoiceEntryEngine.permutations(listOf("a", "b", "c")).size)
    }

    @Test
    fun `permutations of empty list returns single empty`() {
        val result = VoiceEntryEngine.permutations(emptyList<String>())
        assertEquals(1, result.size)
        assertTrue(result[0].isEmpty())
    }

    @Test
    fun `permutations are all distinct`() {
        val items = listOf(1, 2, 3, 4)
        val perms = VoiceEntryEngine.permutations(items)
        assertEquals(perms.size, perms.distinct().size)
    }

    @Test
    fun `permutations contains all items`() {
        val items = listOf("x", "y", "z")
        val perms = VoiceEntryEngine.permutations(items)
        perms.forEach { p ->
            assertEquals(items.toSet(), p.toSet())
        }
    }
}
