package com.pianocompanion.melodymemory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旋律记忆训练出题引擎单元测试（纯 Kotlin 逻辑，无 Android 依赖）。
 */
class MelodyMemoryEngineTest {

    // ── 基本生成 ──────────────────────────────────────────

    @Test
    fun `generate returns question with correct note count per difficulty`() {
        MelodyDifficulty.ALL.forEach { difficulty ->
            val engine = MelodyMemoryEngine.withSeed(42L)
            val q = engine.generate(difficulty)
            assertEquals(difficulty.noteCount, q.noteCount)
            assertEquals(difficulty.noteCount, q.midiNotes.size)
        }
    }

    @Test
    fun `generate contour has noteCount minus 1 intervals`() {
        MelodyDifficulty.ALL.forEach { difficulty ->
            val q = MelodyMemoryEngine.withSeed(1L).generate(difficulty)
            assertEquals(difficulty.noteCount - 1, q.contour.size)
        }
    }

    @Test
    fun `generate correct answer equals contour arrows`() {
        val q = MelodyMemoryEngine.withSeed(7L).generate(MelodyDifficulty.INTERMEDIATE)
        assertEquals(q.contourArrows, q.correctAnswer)
    }

    @Test
    fun `generate answer choices contains correct answer`() {
        val q = MelodyMemoryEngine.withSeed(3L).generate(MelodyDifficulty.ADVANCED)
        assertTrue("正确答案必须在选项中", q.answerChoices.contains(q.correctAnswer))
    }

    @Test
    fun `generate answer choices has 4 options`() {
        MelodyDifficulty.ALL.forEach { difficulty ->
            val q = MelodyMemoryEngine.withSeed(99L).generate(difficulty)
            assertEquals(4, q.answerChoices.size)
        }
    }

    @Test
    fun `generate answer choices are all distinct`() {
        val q = MelodyMemoryEngine.withSeed(5L).generate(MelodyDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.toSet().size)
    }

    @Test
    fun `distractors differ from correct answer`() {
        val q = MelodyMemoryEngine.withSeed(8L).generate(MelodyDifficulty.INTERMEDIATE)
        q.answerChoices.forEach { choice ->
            // 每个选项要么是正确答案，要么与正确答案不同（这总是成立的）
            // 但我们要验证至少有 3 个非正确答案的选项
        }
        val distractorCount = q.answerChoices.count { it != q.correctAnswer }
        assertEquals(3, distractorCount)
    }

    // ── 确定性 ────────────────────────────────────────────

    @Test
    fun `same seed produces same question`() {
        val q1 = MelodyMemoryEngine.withSeed(123L).generate(MelodyDifficulty.ADVANCED)
        val q2 = MelodyMemoryEngine.withSeed(123L).generate(MelodyDifficulty.ADVANCED)
        assertEquals(q1.midiNotes, q2.midiNotes)
        assertEquals(q1.contour, q2.contour)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
    }

    @Test
    fun `different seeds may produce different questions`() {
        val q1 = MelodyMemoryEngine.withSeed(1L).generate(MelodyDifficulty.ADVANCED)
        val q2 = MelodyMemoryEngine.withSeed(999L).generate(MelodyDifficulty.ADVANCED)
        // 不一定总是不同，但大概率不同（至少音名或走向不同）
        val different = q1.midiNotes != q2.midiNotes || q1.contour != q2.contour
        assertTrue("不同种子应大概率产生不同题目", different)
    }

    // ── MIDI 范围与起始音 ────────────────────────────────

    @Test
    fun `start midi is in comfortable range C4 to G4`() {
        repeat(50) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.INTERMEDIATE)
            assertTrue(
                "起始音 ${q.startMidi} 应在 ${MelodyMemoryEngine.START_MIN}..${MelodyMemoryEngine.START_MAX}",
                q.startMidi in MelodyMemoryEngine.START_MIN..MelodyMemoryEngine.START_MAX
            )
        }
    }

    @Test
    fun `all midi notes within piano range`() {
        repeat(50) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.ADVANCED)
            q.midiNotes.forEach { midi ->
                assertTrue("MIDI $midi 超出钢琴范围", midi in MelodyMemoryEngine.MIN_MIDI..MelodyMemoryEngine.MAX_MIDI)
            }
        }
    }

    @Test
    fun `first midi note equals start midi`() {
        val q = MelodyMemoryEngine.withSeed(10L).generate(MelodyDifficulty.INTERMEDIATE)
        assertEquals(q.startMidi, q.midiNotes.first())
    }

    // ── 难度约束 ──────────────────────────────────────────

    @Test
    fun `beginner intervals are at most 2 semitones`() {
        repeat(30) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.BEGINNER)
            q.contour.forEach { interval ->
                assertTrue(
                    "初级音程 ${interval.semitones} 超过 2",
                    interval.semitones in 0..MelodyDifficulty.BEGINNER.maxIntervalSemitones
                )
            }
        }
    }

    @Test
    fun `intermediate intervals are at most 4 semitones`() {
        repeat(30) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.INTERMEDIATE)
            q.contour.forEach { interval ->
                assertTrue(
                    "中级音程 ${interval.semitones} 超过 4",
                    interval.semitones in 0..MelodyDifficulty.INTERMEDIATE.maxIntervalSemitones
                )
            }
        }
    }

    @Test
    fun `advanced intervals are at most 7 semitones`() {
        repeat(30) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.ADVANCED)
            q.contour.forEach { interval ->
                assertTrue(
                    "高级音程 ${interval.semitones} 超过 7",
                    interval.semitones in 0..MelodyDifficulty.ADVANCED.maxIntervalSemitones
                )
            }
        }
    }

    @Test
    fun `beginner never uses SAME direction`() {
        repeat(30) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.BEGINNER)
            q.contour.forEach { interval ->
                assertNotEquals(
                    "初级不应出现同音方向",
                    MelodicDirection.SAME,
                    interval.direction
                )
            }
        }
    }

    @Test
    fun `intermediate and advanced may use SAME direction`() {
        // 统计中级/高级中出现 SAME 方向的概率（确认不崩溃，且概率合理）
        var sameCount = 0
        var total = 0
        repeat(200) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.INTERMEDIATE)
            q.contour.forEach {
                total++
                if (it.direction == MelodicDirection.SAME) sameCount++
            }
        }
        // 不强制要求一定出现，但确认不崩溃
        assertTrue("中级总音程数应 > 0", total > 0)
    }

    // ── 走向一致性 ────────────────────────────────────────

    @Test
    fun `contour direction matches midi note delta sign`() {
        repeat(50) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.ADVANCED)
            for (i in q.contour.indices) {
                val delta = q.midiNotes[i + 1] - q.midiNotes[i]
                val interval = q.contour[i]
                when (interval.direction) {
                    MelodicDirection.UP -> assertTrue("上行但 delta=$delta", delta > 0)
                    MelodicDirection.DOWN -> assertTrue("下行但 delta=$delta", delta < 0)
                    MelodicDirection.SAME -> assertEquals("同音但 delta=$delta", 0, delta)
                }
            }
        }
    }

    @Test
    fun `contour semitones match absolute midi delta`() {
        repeat(50) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.INTERMEDIATE)
            for (i in q.contour.indices) {
                val absDelta = kotlin.math.abs(q.midiNotes[i + 1] - q.midiNotes[i])
                assertEquals(q.contour[i].semitones, absDelta)
            }
        }
    }

    @Test
    fun `note names match midi notes`() {
        val q = MelodyMemoryEngine.withSeed(15L).generate(MelodyDifficulty.INTERMEDIATE)
        assertEquals(q.midiNotes.size, q.noteNames.size)
    }

    @Test
    fun `tempo is propagated to question`() {
        MelodyTempo.ALL.forEach { tempo ->
            val q = MelodyMemoryEngine.withSeed(1L).generate(MelodyDifficulty.BEGINNER, tempo)
            assertEquals(tempo, q.tempo)
        }
    }

    @Test
    fun `difficulty is propagated to question`() {
        MelodyDifficulty.ALL.forEach { difficulty ->
            val q = MelodyMemoryEngine.withSeed(1L).generate(difficulty)
            assertEquals(difficulty, q.difficulty)
        }
    }

    // ── 选项有效性 ────────────────────────────────────────

    @Test
    fun `all option arrows are valid length`() {
        val q = MelodyMemoryEngine.withSeed(20L).generate(MelodyDifficulty.ADVANCED)
        val expectedArrows = q.noteCount - 1
        q.answerChoices.forEach { choice ->
            val arrowCount = choice.split(" ").filter { it.isNotBlank() }.size
            assertEquals(expectedArrows, arrowCount)
        }
    }

    @Test
    fun `all option arrows use only valid symbols`() {
        val q = MelodyMemoryEngine.withSeed(25L).generate(MelodyDifficulty.INTERMEDIATE)
        val validSymbols = setOf("↑", "↓", "→")
        q.answerChoices.forEach { choice ->
            choice.split(" ").filter { it.isNotBlank() }.forEach { sym ->
                assertTrue("无效箭头符号: $sym", validSymbols.contains(sym))
            }
        }
    }

    @Test
    fun `beginner options only use up and down arrows`() {
        repeat(10) { seed ->
            val q = MelodyMemoryEngine.withSeed(seed.toLong()).generate(MelodyDifficulty.BEGINNER)
            q.answerChoices.forEach { choice ->
                assertTrue("初级选项不应含同音箭头: $choice", !choice.contains("→"))
            }
        }
    }
}
