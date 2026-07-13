package com.pianocompanion.tempotraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 速度辨识出题引擎单元测试。
 */
class TempoTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = TempoTrainingEngine.withSeed(42)
        val e2 = TempoTrainingEngine.withSeed(42)
        val q1 = e1.generate(TempoTrainingDifficulty.BEGINNER)
        val q2 = e2.generate(TempoTrainingDifficulty.BEGINNER)
        assertEquals(q1.tempo, q2.tempo)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = TempoTrainingEngine.withSeed(1)
        val engine2 = TempoTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(TempoTrainingDifficulty.ADVANCED)
            val q2 = engine2.generate(TempoTrainingDifficulty.ADVANCED)
            if (q1.tempo != q2.tempo) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = TempoTrainingEngine.withSeed(1)
        val q = engine.generate(TempoTrainingDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = TempoTrainingEngine.withSeed(1)
        val q = engine.generate(TempoTrainingDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为6`() {
        val engine = TempoTrainingEngine.withSeed(1)
        val q = engine.generate(TempoTrainingDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = TempoTrainingEngine.withSeed(7)
        for (difficulty in TempoTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = TempoTrainingEngine.withSeed(3)
        for (difficulty in TempoTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度速度池 ──────────────────────────────────────

    @Test
    fun `初级速度池只含广板中板急板`() {
        assertEquals(
            listOf(TempoCategory.LARGO, TempoCategory.MODERATO, TempoCategory.PRESTO),
            TempoCategory.BEGINNER_TEMPOS
        )
    }

    @Test
    fun `中级速度池含广板行板中板急板`() {
        assertEquals(
            listOf(TempoCategory.LARGO, TempoCategory.ANDANTE, TempoCategory.MODERATO, TempoCategory.PRESTO),
            TempoCategory.INTERMEDIATE_TEMPOS
        )
    }

    @Test
    fun `高级速度池含全部6种`() {
        assertEquals(6, TempoCategory.ALL.size)
        assertEquals(TempoCategory.ALL, TempoCategory.forDifficulty(TempoTrainingDifficulty.ADVANCED))
    }

    @Test
    fun `初级题目答案必在初级速度池中`() {
        val engine = TempoTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TempoTrainingDifficulty.BEGINNER)
            assertTrue(q.tempo in TempoCategory.BEGINNER_TEMPOS)
        }
    }

    @Test
    fun `中级题目答案必在中级速度池中`() {
        val engine = TempoTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TempoTrainingDifficulty.INTERMEDIATE)
            assertTrue(q.tempo in TempoCategory.INTERMEDIATE_TEMPOS)
        }
    }

    @Test
    fun `高级题目答案必在全部速度池中`() {
        val engine = TempoTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(TempoTrainingDifficulty.ADVANCED)
            assertTrue(q.tempo in TempoCategory.ALL)
        }
    }

    @Test
    fun `所有选项均来自该难度速度池`() {
        val engine = TempoTrainingEngine.withSeed(11)
        for (difficulty in TempoTrainingDifficulty.ALL) {
            val pool = TempoCategory.forDifficulty(difficulty)
            val poolLabels = pool.map { it.fullLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue("选项 $choice 不在难度池中", choice in poolLabels)
                }
            }
        }
    }

    // ── 高级难度可覆盖所有6种速度 ──────────────────────

    @Test
    fun `高级难度充分覆盖全部速度类型`() {
        val engine = TempoTrainingEngine.withSeed(100)
        val seen = mutableSetOf<TempoCategory>()
        for (i in 1..200) {
            val q = engine.generate(TempoTrainingDifficulty.ADVANCED)
            seen.add(q.tempo)
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun `初级难度可覆盖全部3种速度类型`() {
        val engine = TempoTrainingEngine.withSeed(100)
        val seen = mutableSetOf<TempoCategory>()
        for (i in 1..100) {
            val q = engine.generate(TempoTrainingDifficulty.BEGINNER)
            seen.add(q.tempo)
        }
        assertEquals(3, seen.size)
    }

    // ── clickCount ──────────────────────────────────────

    @Test
    fun `默认clickCount为8`() {
        val engine = TempoTrainingEngine()
        val q = engine.generate(TempoTrainingDifficulty.BEGINNER)
        assertEquals(8, q.clickCount)
    }

    @Test
    fun `可自定义clickCount`() {
        val engine = TempoTrainingEngine()
        val q = engine.generate(TempoTrainingDifficulty.ADVANCED, clickCount = 12)
        assertEquals(12, q.clickCount)
    }

    // ── onset 时间 ──────────────────────────────────────

    @Test
    fun `computeOnsetTimes首拍在LEAD_SILENCE`() {
        val engine = TempoTrainingEngine()
        val onsets = engine.computeOnsetTimes(TempoCategory.MODERATO, clickCount = 8)
        assertEquals(TempoTrainingEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于clickCount`() {
        val engine = TempoTrainingEngine()
        for (count in listOf(1, 4, 8, 16)) {
            val onsets = engine.computeOnsetTimes(TempoCategory.ALLEGRO, clickCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距等于BPM间距`() {
        val engine = TempoTrainingEngine()
        val onsets = engine.computeOnsetTimes(TempoCategory.ANDANTE, clickCount = 8)
        val expectedInterval = TempoCategory.ANDANTE.intervalMs
        for (i in 1 until onsets.size) {
            assertEquals(expectedInterval, onsets[i] - onsets[i - 1], 0.01)
        }
    }

    @Test
    fun `computeOnsetTimes慢速间距大于快速度`() {
        val engine = TempoTrainingEngine()
        val slowOnsets = engine.computeOnsetTimes(TempoCategory.LARGO, clickCount = 4)
        val fastOnsets = engine.computeOnsetTimes(TempoCategory.PRESTO, clickCount = 4)
        val slowGap = slowOnsets[1] - slowOnsets[0]
        val fastGap = fastOnsets[1] - fastOnsets[0]
        assertTrue("广板间距($slowGap)应大于急板间距($fastGap)", slowGap > fastGap)
    }

    @Test
    fun `computeOnsetTimes单调递增`() {
        val engine = TempoTrainingEngine()
        val onsets = engine.computeOnsetTimes(TempoCategory.MODERATO, clickCount = 10)
        for (i in 1 until onsets.size) {
            assertTrue(onsets[i] > onsets[i - 1])
        }
    }

    // ── intervalMs 验证 ────────────────────────────────

    @Test
    fun `intervalMs等于60000除以bpm`() {
        for (tempo in TempoCategory.ALL) {
            assertEquals(60_000.0 / tempo.bpm, tempo.intervalMs, 0.01)
        }
    }

    @Test
    fun `广板间距最大急板间距最小`() {
        val largoInterval = TempoCategory.LARGO.intervalMs
        val prestoInterval = TempoCategory.PRESTO.intervalMs
        assertTrue(largoInterval > prestoInterval)
    }

    @Test
    fun `BPM单调递增`() {
        val bpms = TempoCategory.ALL.map { it.bpm }
        for (i in 1 until bpms.size) {
            assertTrue("BPM应单调递增: ${bpms[i-1]} < ${bpms[i]}", bpms[i] > bpms[i - 1])
        }
    }

    // ── answerChoices 格式 ─────────────────────────────

    @Test
    fun `正确答案格式与fullLabel一致`() {
        val engine = TempoTrainingEngine.withSeed(8)
        for (difficulty in TempoTrainingDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.tempo.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `选项打乱后正确答案仍可匹配`() {
        val engine = TempoTrainingEngine.withSeed(22)
        for (i in 1..30) {
            val q = engine.generate(TempoTrainingDifficulty.ADVANCED)
            // 正确答案应在选项中且唯一
            val matchCount = q.answerChoices.count { it == q.correctAnswer }
            assertEquals(1, matchCount)
        }
    }
}
