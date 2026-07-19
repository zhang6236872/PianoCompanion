package com.pianocompanion.melodiccontour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContourEngine] 单元测试。
 *
 * 验证确定性出题、难度缩放（音符数/音程池/速度/候选轮廓）、
 * 音高序列与轮廓类型一致、选项正确性、答案包含正确轮廓、音域约束等。
 *
 * 同时验证 [classifyContour] 纯函数的正确性。
 */
class ContourEngineTest {

    // ── 基础出题 ──────────────────────────────────────────

    @Test
    fun `generate produces valid question with all fields`() {
        val engine = ContourEngine.withSeed(42L)
        val q = engine.generate(ContourDifficulty.BEGINNER)

        assertNotNull(q)
        assertEquals(ContourDifficulty.BEGINNER, q.difficulty)
        assertTrue(q.contour in ContourDifficulty.BEGINNER.contourOptions)
        assertEquals(ContourDifficulty.BEGINNER.noteCount, q.noteCount)
        assertEquals(ContourDifficulty.BEGINNER.noteDurationMs, q.noteDurationMs, 0.0001)
    }

    @Test
    fun `deterministic - same seed produces same question`() {
        val e1 = ContourEngine.withSeed(123L)
        val e2 = ContourEngine.withSeed(123L)

        val q1 = e1.generate(ContourDifficulty.INTERMEDIATE)
        val q2 = e2.generate(ContourDifficulty.INTERMEDIATE)

        assertEquals(q1.contour, q2.contour)
        assertEquals(q1.pitches, q2.pitches)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val e1 = ContourEngine.withSeed(1L)
        val e2 = ContourEngine.withSeed(9999L)

        var anyDifferent = false
        repeat(20) {
            val q1 = e1.generate(ContourDifficulty.ADVANCED)
            val q2 = e2.generate(ContourDifficulty.ADVANCED)
            if (q1.pitches != q2.pitches || q1.contour != q2.contour) anyDifferent = true
        }
        assertTrue("不同种子应能产生不同题目", anyDifferent)
    }

    // ── 难度缩放 ──────────────────────────────────────────

    @Test
    fun `beginner uses 4 notes`() {
        val engine = ContourEngine.withSeed(7L)
        repeat(30) {
            val q = engine.generate(ContourDifficulty.BEGINNER)
            assertEquals(4, q.noteCount)
        }
    }

    @Test
    fun `intermediate uses 5 notes`() {
        val engine = ContourEngine.withSeed(7L)
        repeat(30) {
            val q = engine.generate(ContourDifficulty.INTERMEDIATE)
            assertEquals(5, q.noteCount)
        }
    }

    @Test
    fun `advanced uses 6 notes`() {
        val engine = ContourEngine.withSeed(7L)
        repeat(30) {
            val q = engine.generate(ContourDifficulty.ADVANCED)
            assertEquals(6, q.noteCount)
        }
    }

    @Test
    fun `beginner and intermediate have 4 choices`() {
        val engine = ContourEngine.withSeed(7L)
        assertEquals(4, engine.generate(ContourDifficulty.BEGINNER).answerChoices.size)
        assertEquals(4, engine.generate(ContourDifficulty.INTERMEDIATE).answerChoices.size)
    }

    @Test
    fun `advanced has 5 choices`() {
        val engine = ContourEngine.withSeed(7L)
        assertEquals(5, engine.generate(ContourDifficulty.ADVANCED).answerChoices.size)
    }

    @Test
    fun `beginner candidates exclude wave`() {
        val engine = ContourEngine.withSeed(7L)
        repeat(30) {
            val q = engine.generate(ContourDifficulty.BEGINNER)
            assertNotEquals(ContourType.WAVE, q.contour)
        }
    }

    @Test
    fun `difficulty note duration decreases`() {
        assertTrue(ContourDifficulty.BEGINNER.noteDurationMs > ContourDifficulty.INTERMEDIATE.noteDurationMs)
        assertTrue(ContourDifficulty.INTERMEDIATE.noteDurationMs > ContourDifficulty.ADVANCED.noteDurationMs)
    }

    // ── 选项正确性 ────────────────────────────────────────

    @Test
    fun `answer choices contain correct answer`() {
        val engine = ContourEngine.withSeed(7L)
        ContourDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    @Test
    fun `answer choices are sorted by complexity`() {
        val engine = ContourEngine.withSeed(7L)
        val q = engine.generate(ContourDifficulty.ADVANCED)
        // 顺序应为：上行 → 下行 → 拱形 → 谷形 → 波浪
        assertEquals(ContourType.ASCENDING.displayName, q.answerChoices[0])
        assertEquals(ContourType.DESCENDING.displayName, q.answerChoices[1])
        assertEquals(ContourType.ARCH.displayName, q.answerChoices[2])
        assertEquals(ContourType.VALLEY.displayName, q.answerChoices[3])
        assertEquals(ContourType.WAVE.displayName, q.answerChoices[4])
    }

    @Test
    fun `answer choices are unique`() {
        val engine = ContourEngine.withSeed(7L)
        ContourDifficulty.ALL.forEach { d ->
            val q = engine.generate(d)
            assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
        }
    }

    // ── 音高序列与轮廓一致性 ──────────────────────────────

    @Test
    fun `generated pitches match claimed contour`() {
        val engine = ContourEngine.withSeed(99L)
        // 大量取样，确保 init 校验通过（无 IllegalArgumentException）
        repeat(200) {
            ContourDifficulty.ALL.forEach { d ->
                val q = engine.generate(d)
                assertEquals(q.contour, classifyContour(q.pitches))
            }
        }
    }

    @Test
    fun `ascending pitches are monotonically increasing`() {
        val engine = ContourEngine.withSeed(99L)
        repeat(50) {
            val q = engine.generate(ContourDifficulty.BEGINNER)
            if (q.contour == ContourType.ASCENDING) {
                for (i in 1 until q.pitches.size) {
                    assertTrue(q.pitches[i] > q.pitches[i - 1])
                }
            }
        }
    }

    @Test
    fun `descending pitches are monotonically decreasing`() {
        val engine = ContourEngine.withSeed(99L)
        repeat(50) {
            val q = engine.generate(ContourDifficulty.BEGINNER)
            if (q.contour == ContourType.DESCENDING) {
                for (i in 1 until q.pitches.size) {
                    assertTrue(q.pitches[i] < q.pitches[i - 1])
                }
            }
        }
    }

    @Test
    fun `arch has peak in middle`() {
        val engine = ContourEngine.withSeed(99L)
        var found = false
        repeat(100) {
            val q = engine.generate(ContourDifficulty.INTERMEDIATE)
            if (q.contour == ContourType.ARCH) {
                found = true
                val peakIdx = q.pitches.size / 2
                assertEquals(q.pitches.max(), q.pitches[peakIdx])
                // 前半段严格上升
                for (i in 1..peakIdx) assertTrue(q.pitches[i] > q.pitches[i - 1])
                // 后半段严格下降
                for (i in peakIdx + 1 until q.pitches.size) assertTrue(q.pitches[i] < q.pitches[i - 1])
            }
        }
        assertTrue("应在 100 次取样中至少出现一次拱形", found)
    }

    @Test
    fun `valley has trough in middle`() {
        val engine = ContourEngine.withSeed(99L)
        var found = false
        repeat(100) {
            val q = engine.generate(ContourDifficulty.INTERMEDIATE)
            if (q.contour == ContourType.VALLEY) {
                found = true
                val troughIdx = q.pitches.size / 2
                assertEquals(q.pitches.min(), q.pitches[troughIdx])
                // 前半段严格下降
                for (i in 1..troughIdx) assertTrue(q.pitches[i] < q.pitches[i - 1])
                // 后半段严格上升
                for (i in troughIdx + 1 until q.pitches.size) assertTrue(q.pitches[i] > q.pitches[i - 1])
            }
        }
        assertTrue("应在 100 次取样中至少出现一次谷形", found)
    }

    @Test
    fun `wave has multiple direction changes`() {
        val engine = ContourEngine.withSeed(99L)
        var found = false
        repeat(200) {
            val q = engine.generate(ContourDifficulty.ADVANCED)
            if (q.contour == ContourType.WAVE) {
                found = true
                val signs = q.pitches.zipWithNext { a, b -> b.compareTo(a) }
                    .filter { it != 0 }
                    .map { if (it > 0) 1 else -1 }
                var changes = 0
                for (i in 1 until signs.size) {
                    if (signs[i] != signs[i - 1]) changes++
                }
                assertTrue("波浪应有多于一次方向变化", changes >= 2)
            }
        }
        assertTrue("应在 200 次取样中至少出现一次波浪", found)
    }

    // ── 音域约束 ──────────────────────────────────────────

    @Test
    fun `all pitches within valid MIDI range`() {
        val engine = ContourEngine.withSeed(55L)
        repeat(200) {
            ContourDifficulty.ALL.forEach { d ->
                val q = engine.generate(d)
                q.pitches.forEach { p ->
                    assertTrue("音高 $p 低于下限", p >= 48)
                    assertTrue("音高 $p 高于上限", p <= 84)
                }
            }
        }
    }

    @Test
    fun `steps are within difficulty step pool`() {
        val engine = ContourEngine.withSeed(55L)
        repeat(100) {
            ContourDifficulty.ALL.forEach { d ->
                val q = engine.generate(d)
                for (i in 1 until q.pitches.size) {
                    val step = kotlin.math.abs(q.pitches[i] - q.pitches[i - 1])
                    assertTrue("步进 $step 不在难度 ${d.displayName} 的步进池 ${d.stepPool} 中",
                        step in d.stepPool)
                }
            }
        }
    }

    @Test
    fun `ascending start note is in low register`() {
        val engine = ContourEngine.withSeed(55L)
        repeat(100) {
            val q = engine.generate(ContourDifficulty.BEGINNER)
            if (q.contour == ContourType.ASCENDING) {
                assertTrue(q.pitches[0] in 60..79)
            }
        }
    }

    // ── classifyContour 纯函数测试 ─────────────────────────

    @Test
    fun `classifyContour ascending`() {
        assertEquals(ContourType.ASCENDING, classifyContour(listOf(60, 62, 64, 65)))
    }

    @Test
    fun `classifyContour descending`() {
        assertEquals(ContourType.DESCENDING, classifyContour(listOf(72, 70, 67, 65)))
    }

    @Test
    fun `classifyContour arch`() {
        assertEquals(ContourType.ARCH, classifyContour(listOf(60, 64, 67, 64)))
    }

    @Test
    fun `classifyContour valley`() {
        assertEquals(ContourType.VALLEY, classifyContour(listOf(67, 64, 60, 64)))
    }

    @Test
    fun `classifyContour wave`() {
        assertEquals(ContourType.WAVE, classifyContour(listOf(60, 64, 62, 67, 65, 69)))
    }

    @Test
    fun `classifyContour ignores unison steps`() {
        // 含同音的上升仍判为上行（0 被忽略）
        assertEquals(ContourType.ASCENDING, classifyContour(listOf(60, 60, 62, 62, 64)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `classifyContour rejects single note`() {
        classifyContour(listOf(60))
    }

    // ── ContourQuestion init 校验 ─────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `question rejects pitch count mismatch`() {
        ContourQuestion(
            difficulty = ContourDifficulty.BEGINNER,
            contour = ContourType.ASCENDING,
            pitches = listOf(60, 62, 64), // 3 音，但 BEGINNER 要 4
            noteDurationMs = 500.0,
            answerChoices = listOf("上行", "下行"),
            correctAnswer = "上行"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question rejects contour not in difficulty options`() {
        ContourQuestion(
            difficulty = ContourDifficulty.BEGINNER,
            contour = ContourType.WAVE, // WAVE 不在 BEGINNER 候选中
            pitches = listOf(60, 62, 60, 62),
            noteDurationMs = 500.0,
            answerChoices = listOf("波浪"),
            correctAnswer = "波浪"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question rejects answer not in choices`() {
        ContourQuestion(
            difficulty = ContourDifficulty.BEGINNER,
            contour = ContourType.ASCENDING,
            pitches = listOf(60, 62, 64, 65),
            noteDurationMs = 500.0,
            answerChoices = listOf("上行"),
            correctAnswer = "下行" // 不在选项中
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `question rejects pitches not matching contour`() {
        ContourQuestion(
            difficulty = ContourDifficulty.BEGINNER,
            contour = ContourType.ASCENDING,
            pitches = listOf(65, 64, 62, 60), // 实际是下行
            noteDurationMs = 500.0,
            answerChoices = listOf("上行"),
            correctAnswer = "上行"
        )
    }

    @Test
    fun `directions derived property is correct`() {
        val engine = ContourEngine.withSeed(7L)
        val q = engine.generate(ContourDifficulty.BEGINNER)
        assertEquals(q.noteCount - 1, q.directions.size)
        q.directions.forEach { d -> assertTrue(d == 1 || d == -1 || d == 0) }
    }

    @Test
    fun `arrowVisualization has correct number of arrows`() {
        val engine = ContourEngine.withSeed(7L)
        val q = engine.generate(ContourDifficulty.INTERMEDIATE)
        val arrows = q.arrowVisualization.split(" ")
        assertEquals(q.noteCount - 1, arrows.size)
        arrows.forEach { a -> assertTrue(a == "↑" || a == "↓" || a == "→") }
    }
}
