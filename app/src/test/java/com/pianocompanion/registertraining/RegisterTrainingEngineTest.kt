package com.pianocompanion.registertraining

import org.junit.Assert.*
import org.junit.Test

/**
 * 音区辨识出题引擎单元测试。
 */
class RegisterTrainingEngineTest {

    // ── 确定性 ──────────────────────────────────────────

    @Test
    fun `相同种子产生相同题目`() {
        val e1 = RegisterTrainingEngine.withSeed(42)
        val e2 = RegisterTrainingEngine.withSeed(42)
        val q1 = e1.generate(RegisterTrainingDifficulty.BEGINNER)
        val q2 = e2.generate(RegisterTrainingDifficulty.BEGINNER)
        assertEquals(q1.register, q2.register)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
    }

    @Test
    fun `不同种子可能产生不同题目`() {
        val engine = RegisterTrainingEngine.withSeed(1)
        val engine2 = RegisterTrainingEngine.withSeed(999)
        var foundDifferent = false
        for (i in 1..30) {
            val q1 = engine.generate(RegisterTrainingDifficulty.ADVANCED)
            val q2 = engine2.generate(RegisterTrainingDifficulty.ADVANCED)
            if (q1.register != q2.register) {
                foundDifferent = true
                break
            }
        }
        assertTrue(foundDifferent)
    }

    // ── 选项完整性 ──────────────────────────────────────

    @Test
    fun `初级难度选项数量为3`() {
        val engine = RegisterTrainingEngine.withSeed(1)
        val q = engine.generate(RegisterTrainingDifficulty.BEGINNER)
        assertEquals(3, q.answerChoices.size)
    }

    @Test
    fun `中级难度选项数量为4`() {
        val engine = RegisterTrainingEngine.withSeed(1)
        val q = engine.generate(RegisterTrainingDifficulty.INTERMEDIATE)
        assertEquals(4, q.answerChoices.size)
    }

    @Test
    fun `高级难度选项数量为6`() {
        val engine = RegisterTrainingEngine.withSeed(1)
        val q = engine.generate(RegisterTrainingDifficulty.ADVANCED)
        assertEquals(6, q.answerChoices.size)
    }

    @Test
    fun `选项无重复`() {
        val engine = RegisterTrainingEngine.withSeed(7)
        for (difficulty in RegisterTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertEquals(q.answerChoices.size, q.answerChoices.toSet().size)
            }
        }
    }

    @Test
    fun `正确答案包含在选项中`() {
        val engine = RegisterTrainingEngine.withSeed(3)
        for (difficulty in RegisterTrainingDifficulty.ALL) {
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                assertTrue(q.correctAnswer in q.answerChoices)
            }
        }
    }

    // ── 难度音区池 ──────────────────────────────────────

    @Test
    fun `初级音区池只含低低音中音高音`() {
        assertEquals(
            listOf(MusicRegister.DEEP_BASS, MusicRegister.TENOR, MusicRegister.SOPRANO),
            MusicRegister.BEGINNER_REGISTERS
        )
    }

    @Test
    fun `中级音区池含低低音低音中高音高音`() {
        assertEquals(
            listOf(MusicRegister.DEEP_BASS, MusicRegister.BASS, MusicRegister.ALTO, MusicRegister.SOPRANO),
            MusicRegister.INTERMEDIATE_REGISTERS
        )
    }

    @Test
    fun `高级音区池含全部6种`() {
        assertEquals(6, MusicRegister.ALL.size)
        assertEquals(MusicRegister.ALL, MusicRegister.forDifficulty(RegisterTrainingDifficulty.ADVANCED))
    }

    @Test
    fun `初级题目答案必在初级音区池中`() {
        val engine = RegisterTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(RegisterTrainingDifficulty.BEGINNER)
            assertTrue(q.register in MusicRegister.BEGINNER_REGISTERS)
        }
    }

    @Test
    fun `中级题目答案必在中级音区池中`() {
        val engine = RegisterTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(RegisterTrainingDifficulty.INTERMEDIATE)
            assertTrue(q.register in MusicRegister.INTERMEDIATE_REGISTERS)
        }
    }

    @Test
    fun `高级题目答案必在全部音区池中`() {
        val engine = RegisterTrainingEngine.withSeed(5)
        for (i in 1..50) {
            val q = engine.generate(RegisterTrainingDifficulty.ADVANCED)
            assertTrue(q.register in MusicRegister.ALL)
        }
    }

    @Test
    fun `所有选项均来自该难度音区池`() {
        val engine = RegisterTrainingEngine.withSeed(11)
        for (difficulty in RegisterTrainingDifficulty.ALL) {
            val pool = MusicRegister.forDifficulty(difficulty)
            val poolLabels = pool.map { it.fullLabel }.toSet()
            for (i in 1..20) {
                val q = engine.generate(difficulty)
                q.answerChoices.forEach { choice ->
                    assertTrue("选项 $choice 不在难度池中", choice in poolLabels)
                }
            }
        }
    }

    // ── 高级难度可覆盖所有6种音区 ──────────────────────

    @Test
    fun `高级难度充分覆盖全部音区类型`() {
        val engine = RegisterTrainingEngine.withSeed(100)
        val seen = mutableSetOf<MusicRegister>()
        for (i in 1..200) {
            val q = engine.generate(RegisterTrainingDifficulty.ADVANCED)
            seen.add(q.register)
        }
        assertEquals(6, seen.size)
    }

    @Test
    fun `初级难度可覆盖全部3种音区类型`() {
        val engine = RegisterTrainingEngine.withSeed(100)
        val seen = mutableSetOf<MusicRegister>()
        for (i in 1..100) {
            val q = engine.generate(RegisterTrainingDifficulty.BEGINNER)
            seen.add(q.register)
        }
        assertEquals(3, seen.size)
    }

    // ── noteCount ──────────────────────────────────────

    @Test
    fun `默认noteCount为4`() {
        val engine = RegisterTrainingEngine()
        val q = engine.generate(RegisterTrainingDifficulty.BEGINNER)
        assertEquals(4, q.noteCount)
    }

    @Test
    fun `可自定义noteCount`() {
        val engine = RegisterTrainingEngine()
        val q = engine.generate(RegisterTrainingDifficulty.ADVANCED, noteCount = 6)
        assertEquals(6, q.noteCount)
    }

    // ── onset 时间 ──────────────────────────────────────

    @Test
    fun `computeOnsetTimes首音在LEAD_SILENCE`() {
        val engine = RegisterTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 4)
        assertEquals(RegisterTrainingEngine.LEAD_SILENCE_MS, onsets[0], 0.01)
    }

    @Test
    fun `computeOnsetTimes数量等于noteCount`() {
        val engine = RegisterTrainingEngine()
        for (count in listOf(1, 2, 4, 8)) {
            val onsets = engine.computeOnsetTimes(noteCount = count)
            assertEquals(count, onsets.size)
        }
    }

    @Test
    fun `computeOnsetTimes间距等于NOTE_DURATION`() {
        val engine = RegisterTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 4)
        val expectedInterval = RegisterTrainingEngine.NOTE_DURATION_MS
        for (i in 1 until onsets.size) {
            assertEquals(expectedInterval, onsets[i] - onsets[i - 1], 0.01)
        }
    }

    @Test
    fun `computeOnsetTimes单调递增`() {
        val engine = RegisterTrainingEngine()
        val onsets = engine.computeOnsetTimes(noteCount = 6)
        for (i in 1 until onsets.size) {
            assertTrue(onsets[i] > onsets[i - 1])
        }
    }

    // ── baseC 验证 ──────────────────────────────────────

    @Test
    fun `baseC从低到高单调递增`() {
        val frequencies = MusicRegister.ALL.map { it.baseC }
        for (i in 1 until frequencies.size) {
            assertTrue("频率应单调递增: ${frequencies[i-1]} < ${frequencies[i]}", frequencies[i] > frequencies[i - 1])
        }
    }

    @Test
    fun `低低音区baseC最小极高音区baseC最大`() {
        assertTrue(MusicRegister.DEEP_BASS.baseC < MusicRegister.TOP.baseC)
    }

    @Test
    fun `baseC全部为正数`() {
        for (register in MusicRegister.ALL) {
            assertTrue("${register.englishName} baseC=${register.baseC}", register.baseC > 0.0)
        }
    }

    // ── 琶音频率验证 ───────────────────────────────────

    @Test
    fun `琶音频率数组长度为4`() {
        for (register in MusicRegister.ALL) {
            assertEquals(4, register.arpeggioFrequencies.size)
        }
    }

    @Test
    fun `琶音第四音为第一音的两倍频率`() {
        for (register in MusicRegister.ALL) {
            val freqs = register.arpeggioFrequencies
            assertEquals(register.baseC * 2.0, freqs[3], 0.1)
        }
    }

    @Test
    fun `琶音频率单调递增`() {
        for (register in MusicRegister.ALL) {
            val freqs = register.arpeggioFrequencies
            for (i in 1 until freqs.size) {
                assertTrue(freqs[i] > freqs[i - 1])
            }
        }
    }

    // ── answerChoices 格式 ─────────────────────────────

    @Test
    fun `正确答案格式与fullLabel一致`() {
        val engine = RegisterTrainingEngine.withSeed(8)
        for (difficulty in RegisterTrainingDifficulty.ALL) {
            val q = engine.generate(difficulty)
            assertEquals(q.register.fullLabel, q.correctAnswer)
        }
    }

    @Test
    fun `选项打乱后正确答案仍可匹配`() {
        val engine = RegisterTrainingEngine.withSeed(22)
        for (i in 1..30) {
            val q = engine.generate(RegisterTrainingDifficulty.ADVANCED)
            val matchCount = q.answerChoices.count { it == q.correctAnswer }
            assertEquals(1, matchCount)
        }
    }
}
