package com.pianocompanion.melodicdirectiontraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 旋律方向辨识出题引擎单元测试。
 */
class MelodicDirectionEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = MelodicDirectionEngine.withSeed(42)
        val e2 = MelodicDirectionEngine.withSeed(42)
        val q1 = e1.generate(MelodicDirectionDifficulty.BEGINNER)
        val q2 = e2.generate(MelodicDirectionDifficulty.BEGINNER)
        assertEquals(q1.direction, q2.direction)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = MelodicDirectionEngine.withSeed(1)
        val engine2 = MelodicDirectionEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(MelodicDirectionDifficulty.ADVANCED)
            val q2 = engine2.generate(MelodicDirectionDifficulty.ADVANCED)
            if (q1.direction != q2.direction) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = MelodicDirectionEngine.withSeed(1)
        val q = engine.generate(MelodicDirectionDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = MelodicDirectionEngine.withSeed(1)
        val q = engine.generate(MelodicDirectionDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为5`() {
        val engine = MelodicDirectionEngine.withSeed(1)
        val q = engine.generate(MelodicDirectionDifficulty.ADVANCED)
        assertEquals(5, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = MelodicDirectionEngine.withSeed(7)
        for (difficulty in MelodicDirectionDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = MelodicDirectionEngine.withSeed(3)
        for (difficulty in MelodicDirectionDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度方向池 ──────────────────────────────────────

    @Test
    fun `初级方向池只含上行下行平行`() {
        assertEquals(
            listOf(MelodicDirection.ASCENDING, MelodicDirection.DESCENDING, MelodicDirection.STATIC),
            MelodicDirection.BEGINNER_DIRECTIONS
        )
    }

    @Test
    fun `中级方向池含上行下行平行拱形`() {
        assertEquals(
            listOf(MelodicDirection.ASCENDING, MelodicDirection.DESCENDING, MelodicDirection.STATIC, MelodicDirection.ARCH),
            MelodicDirection.INTERMEDIATE_DIRECTIONS
        )
    }

    @Test
    fun `高级方向池含全部5种`() {
        assertEquals(5, MelodicDirection.ALL.size)
        assertEquals(MelodicDirection.ALL, MelodicDirection.forDifficulty(MelodicDirectionDifficulty.ADVANCED))
    }

    @Test
    fun `初级题目答案必在初级方向池中`() {
        val engine = MelodicDirectionEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(MelodicDirectionDifficulty.BEGINNER)
            assertTrue(q.direction in MelodicDirection.BEGINNER_DIRECTIONS)
        }
    }

    @Test
    fun `中级题目答案必在中级方向池中`() {
        val engine = MelodicDirectionEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(MelodicDirectionDifficulty.INTERMEDIATE)
            assertTrue(q.direction in MelodicDirection.INTERMEDIATE_DIRECTIONS)
        }
    }

    @Test
    fun `高级题目答案必在全部方向池中`() {
        val engine = MelodicDirectionEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(MelodicDirectionDifficulty.ADVANCED)
            assertTrue(q.direction in MelodicDirection.ALL)
        }
    }

    @Test
    fun `所有选项均来自该难度方向池`() {
        val engine = MelodicDirectionEngine.withSeed(11)
        for (difficulty in MelodicDirectionDifficulty.ALL) {
            val pool = MelodicDirection.forDifficulty(difficulty)
            val poolLabels = pool.map { it.fullLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue("选项 $choice 不在难度池中", choice in poolLabels)
                }
            }
        }
    }

    // ── 难度覆盖 ────────────────────────────────────────

    @Test
    fun `高级难度充分覆盖全部5种方向类型`() {
        val engine = MelodicDirectionEngine.withSeed(100)
        val seen = mutableSetOf<MelodicDirection>()
        for (i in 1..200) {
            val q = engine.generate(MelodicDirectionDifficulty.ADVANCED)
            seen.add(q.direction)
        }
        assertEquals(5, seen.size)
    }

    @Test
    fun `初级难度可覆盖全部3种方向类型`() {
        val engine = MelodicDirectionEngine.withSeed(100)
        val seen = mutableSetOf<MelodicDirection>()
        for (i in 1..100) {
            val q = engine.generate(MelodicDirectionDifficulty.BEGINNER)
            seen.add(q.direction)
        }
        assertEquals(3, seen.size)
    }

    // ── noteCount ──────────────────────────────────────

    @Test
    fun `默认noteCount为4`() {
        val engine = MelodicDirectionEngine()
        val q = engine.generate(MelodicDirectionDifficulty.BEGINNER)
        assertEquals(4, q.noteCount)
    }

    // ── answerChoices 格式 ─────────────────────────────

    @Test
    fun `正确答案格式与fullLabel一致`() {
        val engine = MelodicDirectionEngine.withSeed(8)
        for (difficulty in MelodicDirectionDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.direction.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `选项打乱后正确答案仍可匹配`() {
        val engine = MelodicDirectionEngine.withSeed(22)
        for (i in 1..30) {
            val q = engine.generate(MelodicDirectionDifficulty.ADVANCED)
            val matchCount = q.answerChoices.count { it == q.correctAnswer }
            assertEquals(1, matchCount)
        }
    }

    // ── semitoneOffsets 验证 ────────────────────────────

    @Test
    fun `所有方向semitoneOffsets长度为4`() {
        for (direction in MelodicDirection.ALL) {
            assertEquals(4, direction.semitoneOffsets.size)
        }
    }

    @Test
    fun `上行semitoneOffsets单调递增`() {
        val offsets = MelodicDirection.ASCENDING.semitoneOffsets
        for (i in 1 until offsets.size) {
            assertTrue(offsets[i] > offsets[i - 1])
        }
    }

    @Test
    fun `下行semitoneOffsets单调递减`() {
        val offsets = MelodicDirection.DESCENDING.semitoneOffsets
        for (i in 1 until offsets.size) {
            assertTrue(offsets[i] < offsets[i - 1])
        }
    }

    @Test
    fun `平行semitoneOffsets全为零`() {
        val offsets = MelodicDirection.STATIC.semitoneOffsets
        for (offset in offsets) {
            assertEquals(0, offset)
        }
    }

    @Test
    fun `拱形先升后降`() {
        val offsets = MelodicDirection.ARCH.semitoneOffsets
        assertTrue(offsets[0] < offsets[1])
        assertTrue(offsets[1] < offsets[2])
        assertTrue(offsets[2] > offsets[3])
    }

    @Test
    fun `V形先降后升`() {
        val offsets = MelodicDirection.V_SHAPE.semitoneOffsets
        assertTrue(offsets[0] > offsets[1])
        assertTrue(offsets[1] > offsets[2])
        assertTrue(offsets[2] < offsets[3])
    }

    @Test
    fun `上行和下行互为逆序`() {
        val asc = MelodicDirection.ASCENDING.semitoneOffsets
        val desc = MelodicDirection.DESCENDING.semitoneOffsets
        // 如果上行是 [0,2,4,7]，下行应该是 [7,4,2,0]
        assertEquals(asc.reversed().toList(), desc.toList())
    }
}
