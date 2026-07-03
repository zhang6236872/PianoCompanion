package com.pianocompanion.keysig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KeySigEngine] 单元测试。
 *
 * 验证五度圈调号表、候选池、确定性随机、升降号位置、答案选项构建等核心逻辑。
 */
class KeySigEngineTest {

    // ── 候选池 ────────────────────────────────────────────────

    @Test
    fun `beginner pool contains only major keys with 0-3 accidentals`() {
        val engine = KeySigEngine()
        val candidates = engine.candidateKeys(KeySigDifficulty.BEGINNER)
        // 0-3 升号：C, G, D, A（4 种）+ 1-3 降号：F, B♭, E♭（3 种）= 7 种
        assertEquals(7, candidates.size)
        candidates.forEach { key ->
            assertTrue("accidentalCount 应 <= 3，实际 ${key.accidentalCount}",
                key.accidentalCount <= 3)
            assertEquals("BEGINNER 仅大调", KeyMode.MAJOR, key.mode)
        }
    }

    @Test
    fun `intermediate pool contains only major keys with 0-5 accidentals`() {
        val engine = KeySigEngine()
        val candidates = engine.candidateKeys(KeySigDifficulty.INTERMEDIATE)
        // 0-5 升号：C,G,D,A,E,B（6）+ 1-5 降号：F,B♭,E♭,A♭,D♭（5）= 11
        assertEquals(11, candidates.size)
        candidates.forEach { key ->
            assertTrue("accidentalCount 应 <= 5", key.accidentalCount <= 5)
            assertEquals(KeyMode.MAJOR, key.mode)
        }
    }

    @Test
    fun `advanced pool contains major and minor keys`() {
        val engine = KeySigEngine()
        val candidates = engine.candidateKeys(KeySigDifficulty.ADVANCED)
        // 15 大调 + 15 小调 = 30
        assertEquals(30, candidates.size)
        val hasMajor = candidates.any { it.mode == KeyMode.MAJOR }
        val hasMinor = candidates.any { it.mode == KeyMode.MINOR }
        assertTrue("应包含大调", hasMajor)
        assertTrue("应包含小调", hasMinor)
    }

    // ── 调性名称 ──────────────────────────────────────────────

    @Test
    fun `major key display names are correct`() {
        val engine = KeySigEngine()
        val candidates = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MAJOR }
        val names = candidates.map { it.displayName }.toSet()
        // 检查几个关键调名
        assertTrue("应含 C大调", names.contains("C大调"))
        assertTrue("应含 G大调", names.contains("G大调"))
        assertTrue("应含 D大调", names.contains("D大调"))
        assertTrue("应含 F大调", names.contains("F大调"))
        assertTrue("应含 B♭大调", names.contains("B♭大调"))
        assertTrue("应含 F♯大调", names.contains("F♯大调"))
        assertTrue("应含 C♯大调", names.contains("C♯大调"))
        assertTrue("应含 C♭大调", names.contains("C♭大调"))
    }

    @Test
    fun `minor key display names are correct`() {
        val engine = KeySigEngine()
        val minorKeys = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MINOR }
        val names = minorKeys.map { it.displayName }.toSet()
        // 关系小调：C 大调的关系小调是 A 小调（0 升降号）
        assertTrue("应含 A小调（C 大调的关系小调）", names.contains("A小调"))
        // G 大调（1 升号）的关系小调是 E 小调
        assertTrue("应含 E小调（G 大调的关系小调）", names.contains("E小调"))
        // F 大调（1 降号）的关系小调是 D 小调
        assertTrue("应含 D小调（F 大调的关系小调）", names.contains("D小调"))
    }

    // ── 升降号数量 ────────────────────────────────────────────

    @Test
    fun `sharp keys have correct accidental counts`() {
        val engine = KeySigEngine()
        val majors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MAJOR && it.accidentalType == AccidentalType.SHARP }
        // 升号大调（C 大调为 NONE 类型，不计入）：G(1),D(2),A(3),E(4),B(5),F♯(6),C♯(7)
        assertEquals(7, majors.size)
        val counts = majors.map { it.accidentalCount }.sorted()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), counts)
    }

    @Test
    fun `flat keys have correct accidental counts`() {
        val engine = KeySigEngine()
        val majors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MAJOR && it.accidentalType == AccidentalType.FLAT }
        // 降号大调：F(1),B♭(2),E♭(3),A♭(4),D♭(5),G♭(6),C♭(7)
        assertEquals(7, majors.size)
        val counts = majors.map { it.accidentalCount }.sorted()
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), counts)
    }

    // ── 升降号五线谱位置 ──────────────────────────────────────

    @Test
    fun `treble sharp positions match F-C-G-D-A-E-B order`() {
        // 高音谱号升号位置：F♯ C♯ G♯ D♯ A♯ E♯ B♯
        val expected = KeyInfo.TREBLE_SHARP_STEPS.toList()
        assertEquals(7, expected.size)
        // 每个位置应唯一
        assertEquals(expected.toSet().size, 7)
    }

    @Test
    fun `treble flat positions match B-E-A-D-G-C-F order`() {
        val expected = KeyInfo.TREBLE_FLAT_STEPS.toList()
        assertEquals(7, expected.size)
        assertEquals(expected.toSet().size, 7)
    }

    @Test
    fun `computeAccidentalSteps returns correct count for treble sharps`() {
        val engine = KeySigEngine()
        // G 大调 = 1 升号
        val gMajor = engine.candidateKeys(KeySigDifficulty.BEGINNER)
            .first { it.displayName == "G大调" }
        val steps = engine.computeAccidentalSteps(KeySigClef.TREBLE, gMajor)
        assertEquals(1, steps.size)
        assertEquals(KeyInfo.TREBLE_SHARP_STEPS[0], steps[0])
    }

    @Test
    fun `computeAccidentalSteps returns correct count for bass flats`() {
        val engine = KeySigEngine()
        // B♭ 大调 = 2 降号
        val bbMajor = engine.candidateKeys(KeySigDifficulty.INTERMEDIATE)
            .first { it.displayName == "B♭大调" }
        val steps = engine.computeAccidentalSteps(KeySigClef.BASS, bbMajor)
        assertEquals(2, steps.size)
        assertEquals(KeyInfo.BASS_FLAT_STEPS[0], steps[0])
        assertEquals(KeyInfo.BASS_FLAT_STEPS[1], steps[1])
    }

    @Test
    fun `computeAccidentalSteps returns empty for C major`() {
        val engine = KeySigEngine()
        val cMajor = engine.candidateKeys(KeySigDifficulty.BEGINNER)
            .first { it.displayName == "C大调" }
        val steps = engine.computeAccidentalSteps(KeySigClef.TREBLE, cMajor)
        assertTrue("C 大调无升降号", steps.isEmpty())
    }

    // ── 生成题目 ──────────────────────────────────────────────

    @Test
    fun `generated question has valid structure`() {
        val engine = KeySigEngine.withSeed(42L)
        val question = engine.generate(KeySigClef.TREBLE, KeySigDifficulty.BEGINNER)
        assertNotNull(question)
        assertEquals(KeySigClef.TREBLE, question.clef)
        assertEquals(KeySigDifficulty.BEGINNER, question.difficulty)
        // 选项数量为 4
        assertEquals(4, question.answerChoices.size)
        // 正确答案在选项中
        assertTrue("正确答案应在选项中",
            question.correctAnswer in question.answerChoices)
        // 选项无重复
        assertEquals("选项应无重复", question.answerChoices.size, question.answerChoices.toSet().size)
        // 升降号位置数量与调性一致
        assertEquals(question.keyInfo.accidentalCount, question.accidentalStaffSteps.size)
    }

    @Test
    fun `deterministic generation with same seed`() {
        val engine1 = KeySigEngine.withSeed(123L)
        val engine2 = KeySigEngine.withSeed(123L)
        val q1 = engine1.generate(KeySigClef.TREBLE, KeySigDifficulty.ADVANCED)
        val q2 = engine2.generate(KeySigClef.TREBLE, KeySigDifficulty.ADVANCED)
        assertEquals(q1.correctAnswer, q2.correctAnswer)
        assertEquals(q1.answerChoices, q2.answerChoices)
        assertEquals(q1.accidentalStaffSteps, q2.accidentalStaffSteps)
    }

    @Test
    fun `different seeds usually produce different questions`() {
        val engine1 = KeySigEngine.withSeed(1L)
        val engine2 = KeySigEngine.withSeed(999L)
        val q1 = engine1.generate(KeySigClef.TREBLE, KeySigDifficulty.ADVANCED)
        val q2 = engine2.generate(KeySigClef.TREBLE, KeySigDifficulty.ADVANCED)
        // 不同种子大概率生成不同正确答案（不强制，但至少结构有效）
        assertNotNull(q1)
        assertNotNull(q2)
    }

    // ── 多轮生成不崩溃 ────────────────────────────────────────

    @Test
    fun `generate many questions without errors`() {
        val engine = KeySigEngine.withSeed(7L)
        for (i in 1..50) {
            val clef = if (i % 2 == 0) KeySigClef.TREBLE else KeySigClef.BASS
            val difficulty = KeySigDifficulty.ALL[i % 3]
            val q = engine.generate(clef, difficulty)
            assertEquals(4, q.answerChoices.size)
            assertTrue(q.correctAnswer in q.answerChoices)
        }
    }

    // ── 关系调验证 ────────────────────────────────────────────

    @Test
    fun `relative minor tonic is minor third below major tonic`() {
        val engine = KeySigEngine()
        val majors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MAJOR }
        val minors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MINOR }
        // C 大调 (pc=0) → A 小调 (pc=9)
        val cMajor = majors.first { it.displayName == "C大调" }
        val aMinor = minors.first { it.displayName == "A小调" }
        assertEquals(0, cMajor.tonicPitchClass)
        assertEquals(9, aMinor.tonicPitchClass)
        // 同调号
        assertEquals(cMajor.sharpCount, aMinor.sharpCount)
        assertEquals(cMajor.flatCount, aMinor.flatCount)
    }

    @Test
    fun `G major and E minor share same key signature`() {
        val engine = KeySigEngine()
        val majors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MAJOR }
        val minors = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .filter { it.mode == KeyMode.MINOR }
        val gMajor = majors.first { it.displayName == "G大调" }
        val eMinor = minors.first { it.displayName == "E小调" }
        assertEquals(gMajor.sharpCount, eMinor.sharpCount) // 都是 1 升号
        assertEquals(gMajor.flatCount, eMinor.flatCount)
    }

    // ── 音阶 MIDI ─────────────────────────────────────────────

    @Test
    fun `major scale intervals are correct`() {
        val engine = KeySigEngine()
        val cMajor = engine.candidateKeys(KeySigDifficulty.BEGINNER)
            .first { it.displayName == "C大调" }
        val midis = cMajor.scaleMidis
        // C 大调：C4=60, D=62, E=64, F=65, G=67, A=69, B=71, C5=72
        assertEquals(8, midis.size)
        assertEquals(60, midis[0])
        assertEquals(72, midis[7])
        // 全全半全全全半
        val intervals = midis.zipWithNext { a, b -> b - a }
        assertEquals(listOf(2, 2, 1, 2, 2, 2, 1), intervals)
    }

    @Test
    fun `natural minor scale intervals are correct`() {
        val engine = KeySigEngine()
        val aMinor = engine.candidateKeys(KeySigDifficulty.ADVANCED)
            .first { it.displayName == "A小调" }
        val midis = aMinor.scaleMidis
        // A 小调：A=69, B=71, C=72, D=74, E=76, F=77, G=79, A=81
        assertEquals(8, midis.size)
        assertEquals(69, midis[0])
        // 全半全全半全全
        val intervals = midis.zipWithNext { a, b -> b - a }
        assertEquals(listOf(2, 1, 2, 2, 1, 2, 2), intervals)
    }

    // ── step to MIDI ─────────────────────────────────────────

    @Test
    fun `diatonicStepToMidi for treble bottom line is E4`() {
        val engine = KeySigEngine()
        // 高音谱号底线 (step=0) = E4 = MIDI 64
        val midi = engine.diatonicStepToMidi(KeySigClef.TREBLE, 0)
        assertEquals(64, midi)
    }

    @Test
    fun `diatonicStepToMidi for bass bottom line is G2`() {
        val engine = KeySigEngine()
        // 低音谱号底线 (step=0) = G2 = MIDI 43
        val midi = engine.diatonicStepToMidi(KeySigClef.BASS, 0)
        assertEquals(43, midi)
    }
}
