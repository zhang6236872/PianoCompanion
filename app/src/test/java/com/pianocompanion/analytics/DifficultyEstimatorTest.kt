package com.pianocompanion.analytics

import com.pianocompanion.data.model.Accidental
import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DifficultyEstimator] 单元测试。
 *
 * 覆盖：边界情况（空/单音）、各难度因子独立验证、等级映射、内置乐谱合理性、
 * 综合场景、dominantFactors 排序、摘要生成。
 */
class DifficultyEstimatorTest {

    // ══════════════════════════════════════════════════════════════════
    //  测试辅助构建器
    // ══════════════════════════════════════════════════════════════════

    /** 构建单声部旋律音符列表。 */
    private fun melody(
        pitches: List<Int>,
        intervalMs: Long = 500L,
        durationMs: Long = 450L,
        staff: Staff = Staff.TREBLE,
        tempo: Int = 120
    ): List<ScoreNote> = pitches.mapIndexed { idx, midi ->
        ScoreNote(
            midiNumber = midi,
            noteName = "n$midi",
            startTime = idx * intervalMs,
            duration = durationMs,
            measureIndex = idx / 4,
            staff = staff
        )
    }.let { it } // tempo 仅用于说明，实际由 Score 携带

    /** 构建测试用 Score。 */
    private fun score(
        notes: List<ScoreNote>,
        tempo: Int = 120,
        timeSignature: String = "4/4"
    ): Score = Score(
        id = "test",
        title = "测试曲",
        composer = "测试",
        notes = notes,
        tempo = tempo,
        timeSignature = timeSignature
    )

    // ══════════════════════════════════════════════════════════════════
    //  边界情况
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `empty score returns zero and beginner`() {
        val result = DifficultyEstimator.estimate(score(notes = emptyList()))
        assertEquals(0, result.totalScore)
        assertEquals(DifficultyLevel.BEGINNER, result.level)
        assertTrue(result.factors.isEmpty())
        assertEquals("空乐谱", result.summary)
    }

    @Test
    fun `single note does not crash and returns beginner-ish`() {
        val notes = listOf(
            ScoreNote(midiNumber = 60, noteName = "C4", startTime = 0, duration = 500)
        )
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertTrue(result.totalScore in 0..25)
        assertEquals(DifficultyLevel.BEGINNER, result.level)
        // 音符密度因子存在
        assertNotNull(result.factor("noteDensity"))
    }

    // ══════════════════════════════════════════════════════════════════
    //  等级映射 (DifficultyLevel.fromScore)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `fromScore maps boundaries correctly`() {
        assertEquals(DifficultyLevel.BEGINNER, DifficultyLevel.fromScore(0))
        assertEquals(DifficultyLevel.BEGINNER, DifficultyLevel.fromScore(20))
        assertEquals(DifficultyLevel.EASY, DifficultyLevel.fromScore(21))
        assertEquals(DifficultyLevel.EASY, DifficultyLevel.fromScore(40))
        assertEquals(DifficultyLevel.INTERMEDIATE, DifficultyLevel.fromScore(41))
        assertEquals(DifficultyLevel.INTERMEDIATE, DifficultyLevel.fromScore(60))
        assertEquals(DifficultyLevel.ADVANCED, DifficultyLevel.fromScore(61))
        assertEquals(DifficultyLevel.ADVANCED, DifficultyLevel.fromScore(80))
        assertEquals(DifficultyLevel.EXPERT, DifficultyLevel.fromScore(81))
        assertEquals(DifficultyLevel.EXPERT, DifficultyLevel.fromScore(100))
    }

    @Test
    fun `fromScore clamps out of range`() {
        assertEquals(DifficultyLevel.BEGINNER, DifficultyLevel.fromScore(-5))
        assertEquals(DifficultyLevel.EXPERT, DifficultyLevel.fromScore(150))
    }

    @Test
    fun `level stars and labels are non-empty`() {
        for (level in DifficultyLevel.values()) {
            assertTrue("stars empty for $level", level.stars.isNotEmpty())
            assertTrue("label empty for $level", level.label.isNotEmpty())
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  音符密度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `sparse melody has low density score`() {
        // 4 个音符，每个间隔 1 秒 → 3-4 notes/sec 低密度
        val notes = melody(listOf(60, 62, 64, 65), intervalMs = 1000)
        val result = DifficultyEstimator.estimate(score(notes = notes))
        val density = result.factor("noteDensity")!!
        assertTrue("density score ${density.score} should be low", density.score < 40)
    }

    @Test
    fun `dense melody has higher density score`() {
        // 10 个音符挤在 1 秒内 → 高密度
        val notes = (0 until 10).map { i ->
            ScoreNote(midiNumber = 60 + i, noteName = "n", startTime = (i * 100).toLong(), duration = 90)
        }
        val result = DifficultyEstimator.estimate(score(notes = notes))
        val dense = result.factor("noteDensity")!!
        assertTrue("density score ${dense.score} should be high", dense.score > 70)
    }

    @Test
    fun `denser melody scores higher than sparser`() {
        val sparse = DifficultyEstimator.estimate(
            score(notes = melody((0 until 8).toList(), intervalMs = 1000))
        )
        val dense = DifficultyEstimator.estimate(
            score(notes = melody((0 until 8).toList(), intervalMs = 150))
        )
        assertTrue(
            "dense (${dense.factor("noteDensity")!!.score}) > sparse (${sparse.factor("noteDensity")!!.score})",
            dense.factor("noteDensity")!!.score > sparse.factor("noteDensity")!!.score
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  节奏复杂度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `short notes increase rhythmic complexity`() {
        // 全四分音符（长）vs 全十六分音符（短），同 tempo
        val longNotes = melody(listOf(60, 62, 64, 65), intervalMs = 500, durationMs = 450)
        val shortNotes = melody(listOf(60, 62, 64, 65), intervalMs = 125, durationMs = 110)
        val long = DifficultyEstimator.estimate(score(notes = longNotes))
        val short = DifficultyEstimator.estimate(score(notes = shortNotes))
        assertTrue(
            "short (${short.factor("rhythmicComplexity")!!.score}) >= long (${long.factor("rhythmicComplexity")!!.score})",
            short.factor("rhythmicComplexity")!!.score >= long.factor("rhythmicComplexity")!!.score
        )
    }

    @Test
    fun `tuplets contribute to rhythmic complexity`() {
        val plain = melody(listOf(60, 62, 64, 65), intervalMs = 500, durationMs = 450)
        val withTuplets = plain.map { it.copy(tuplet = 3) }
        val plainResult = DifficultyEstimator.estimate(score(notes = plain))
        val tupletResult = DifficultyEstimator.estimate(score(notes = withTuplets))
        assertTrue(
            tupletResult.factor("rhythmicComplexity")!!.score >
                plainResult.factor("rhythmicComplexity")!!.score
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  复音密度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `monophonic melody has zero polyphony`() {
        val notes = melody(listOf(60, 62, 64, 65), intervalMs = 500)
        val result = DifficultyEstimator.estimate(score(notes = notes))
        val poly = result.factor("polyphony")!!
        assertEquals(0.0, poly.rawValue, 0.001)
    }

    @Test
    fun `chords increase polyphony score`() {
        // 3 组三音和弦
        val chordNotes = mutableListOf<ScoreNote>()
        for (m in 0 until 3) {
            listOf(60, 64, 67).forEachIndexed { idx, midi ->
                chordNotes.add(
                    ScoreNote(
                        midiNumber = midi + m * 4, noteName = "n",
                        startTime = m * 500L, duration = 450, staff = Staff.TREBLE
                    )
                )
            }
        }
        val result = DifficultyEstimator.estimate(score(notes = chordNotes))
        assertTrue(
            "polyphony score ${result.factor("polyphony")!!.score} should be > 0",
            result.factor("polyphony")!!.score > 0
        )
    }

    @Test
    fun `polyphony detects simultaneous onsets within tolerance`() {
        // 两个音符起始时间差 10ms（< 15ms 容差）→ 视为和弦
        val notes = listOf(
            ScoreNote(midiNumber = 60, noteName = "C", startTime = 0, duration = 400),
            ScoreNote(midiNumber = 64, noteName = "E", startTime = 10, duration = 400)
        )
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertTrue(result.factor("polyphony")!!.rawValue > 0.0)
    }

    // ══════════════════════════════════════════════════════════════════
    //  速度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `slower tempo scores lower than faster`() {
        val notes = melody(listOf(60, 62, 64, 65), intervalMs = 500)
        val slow = DifficultyEstimator.estimate(score(notes = notes, tempo = 60))
        val fast = DifficultyEstimator.estimate(score(notes = notes, tempo = 200))
        assertTrue(slow.factor("tempo")!!.score < fast.factor("tempo")!!.score)
    }

    @Test
    fun `tempo 60 maps to zero and 200 maps to 100`() {
        val notes = melody(listOf(60, 62, 64, 65), intervalMs = 500)
        assertEquals(0, DifficultyEstimator.estimate(score(notes = notes, tempo = 60)).factor("tempo")!!.score)
        assertEquals(100, DifficultyEstimator.estimate(score(notes = notes, tempo = 200)).factor("tempo")!!.score)
    }

    // ══════════════════════════════════════════════════════════════════
    //  音域跨度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `narrow range scores low and wide range scores high`() {
        val narrow = DifficultyEstimator.estimate(
            score(notes = melody(listOf(60, 60, 60, 60)))
        )
        val wide = DifficultyEstimator.estimate(
            score(notes = melody(listOf(48, 60, 72, 84)))
        )
        assertTrue(narrow.factor("pitchRange")!!.score < wide.factor("pitchRange")!!.score)
    }

    @Test
    fun `single pitch range is zero semitones`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 60))))
        assertEquals(0, result.factor("pitchRange")!!.rawValue.toInt())
    }

    // ══════════════════════════════════════════════════════════════════
    //  旋律跳跃因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `stepwise melody scores lower than leaping melody`() {
        val stepwise = DifficultyEstimator.estimate(
            score(notes = melody(listOf(60, 62, 64, 65, 67))) // 二度级进
        )
        val leaping = DifficultyEstimator.estimate(
            score(notes = melody(listOf(60, 72, 55, 79, 50))) // 大跳
        )
        assertTrue(
            "leap ${leaping.factor("leaps")!!.score} > stepwise ${stepwise.factor("leaps")!!.score}",
            leaping.factor("leaps")!!.score > stepwise.factor("leaps")!!.score
        )
    }

    @Test
    fun `single note has zero leap score`() {
        val result = DifficultyEstimator.estimate(
            score(notes = listOf(ScoreNote(midiNumber = 60, noteName = "C", startTime = 0, duration = 400)))
        )
        assertEquals(0, result.factor("leaps")!!.score)
    }

    // ══════════════════════════════════════════════════════════════════
    //  半音化因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `no accidentals scores zero chromaticism`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64))))
        assertEquals(0, result.factor("chromaticism")!!.score)
    }

    @Test
    fun `accidentals increase chromaticism`() {
        val withSharps = melody(listOf(60, 61, 63, 64)).mapIndexed { i, n ->
            if (i % 2 == 0) n.copy(accidental = Accidental.SHARP) else n
        }
        val result = DifficultyEstimator.estimate(score(notes = withSharps))
        assertTrue(result.factor("chromaticism")!!.score > 0)
    }

    @Test
    fun `natural accidental does not count as chromatic`() {
        // 还原号是「纠正」性质的，不增加半音化难度
        val withNaturals = melody(listOf(60, 62, 64)).map { it.copy(accidental = Accidental.NATURAL) }
        val result = DifficultyEstimator.estimate(score(notes = withNaturals))
        assertEquals(0, result.factor("chromaticism")!!.score)
    }

    // ══════════════════════════════════════════════════════════════════
    //  装饰音因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `plain notes have zero ornamentation`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64))))
        assertEquals(0, result.factor("ornamentation")!!.score)
    }

    @Test
    fun `grace notes increase ornamentation`() {
        val notes = melody(listOf(60, 62, 64)).map { it.copy(isGraceNote = true) }
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertTrue(result.factor("ornamentation")!!.score > 0)
    }

    @Test
    fun `tremolo slashes increase ornamentation`() {
        val notes = melody(listOf(60, 62, 64)).map { it.copy(tremoloSlashCount = 2) }
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertTrue(result.factor("ornamentation")!!.score > 0)
    }

    @Test
    fun `arpeggiated notes increase ornamentation`() {
        val notes = melody(listOf(60, 64, 67)).map { it.copy(isArpeggiated = true) }
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertTrue(result.factor("ornamentation")!!.score > 0)
    }

    // ══════════════════════════════════════════════════════════════════
    //  双手独立性因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `single staff has zero hand independence`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64))))
        assertEquals(0, result.factor("handIndependence")!!.score)
    }

    @Test
    fun `treble and bass together score highest`() {
        val notes = melody(listOf(60, 62, 64), staff = Staff.TREBLE) +
            melody(listOf(48, 43, 41), staff = Staff.BASS)
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertEquals(80, result.factor("handIndependence")!!.score)
    }

    @Test
    fun `two non treble-bass staves score moderate`() {
        val notes = melody(listOf(60, 62, 64), staff = Staff.ALTO) +
            melody(listOf(48, 43, 41), staff = Staff.TENOR)
        val result = DifficultyEstimator.estimate(score(notes = notes))
        assertEquals(50, result.factor("handIndependence")!!.score)
    }

    // ══════════════════════════════════════════════════════════════════
    //  曲目长度因子
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `short piece scores lower than long piece`() {
        val short = DifficultyEstimator.estimate(
            score(notes = melody(listOf(60, 62, 64), intervalMs = 500))
        )
        // 长曲：100 个音符，间隔 2 秒 → ~200 秒
        val longNotes = (0 until 100).map {
            ScoreNote(midiNumber = 60, noteName = "C", startTime = it * 2000L, duration = 1500)
        }
        val long = DifficultyEstimator.estimate(score(notes = longNotes))
        assertTrue(short.factor("length")!!.score < long.factor("length")!!.score)
    }

    // ══════════════════════════════════════════════════════════════════
    //  因子结构完整性
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `result contains all ten factors for non-empty score`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65))))
        val expectedKeys = listOf(
            "noteDensity", "rhythmicComplexity", "polyphony", "tempo",
            "pitchRange", "leaps", "chromaticism", "ornamentation",
            "handIndependence", "length"
        )
        assertEquals(expectedKeys.size, result.factors.size)
        expectedKeys.forEach { key ->
            assertNotNull("missing factor $key", result.factor(key))
        }
    }

    @Test
    fun `factor scores are within 0 to 100`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65))))
        for (f in result.factors) {
            assertTrue("factor ${f.key} score ${f.score} out of range", f.score in 0..100)
        }
    }

    @Test
    fun `weights sum to one`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65))))
        val sum = result.factors.sumOf { it.weight }
        assertEquals(1.0, sum, 0.001)
    }

    @Test
    fun `total score within 0 to 100`() {
        // 各种乐谱都应在范围内
        val samples = listOf(
            score(notes = emptyList()),
            score(notes = melody(listOf(60))),
            score(notes = melody((48..96).toList(), intervalMs = 50, durationMs = 40, tempo = 200)),
            score(notes = melody(List(50) { 60 }, intervalMs = 100))
        )
        for (s in samples) {
            val r = DifficultyEstimator.estimate(s)
            assertTrue("score ${r.totalScore} out of range for ${s.notes.size} notes", r.totalScore in 0..100)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  dominantFactors 与 summary
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `dominant factors sorted by weighted score descending`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65, 67))))
        val weighted = result.dominantFactors.map { it.weightedScore }
        for (i in 1 until weighted.size) {
            assertTrue("not sorted at $i", weighted[i - 1] >= weighted[i])
        }
    }

    @Test
    fun `summary contains level label and score`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65))))
        assertTrue(result.summary.contains(result.level.label))
        assertTrue(result.summary.contains("${result.totalScore}分"))
    }

    @Test
    fun `summary mentions main difficulty for non-empty score`() {
        val result = DifficultyEstimator.estimate(score(notes = melody(listOf(60, 62, 64, 65))))
        // 非空乐谱摘要应包含「主要难点」
        assertTrue("summary=${result.summary}", result.summary.contains("主要难点"))
    }

    // ══════════════════════════════════════════════════════════════════
    //  内置乐谱合理性（简单儿歌应为入门/初级）
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `simple children song melody is beginner or easy`() {
        // 小星星式：C C G G A A G，单声部级进+小跳，tempo 120
        val notes = melody(listOf(60, 60, 67, 67, 69, 69, 67), intervalMs = 400)
        val result = DifficultyEstimator.estimate(score(notes = notes, tempo = 120))
        assertTrue(
            "simple song level ${result.level} (score ${result.totalScore}) should be BEGINNER or EASY",
            result.level == DifficultyLevel.BEGINNER || result.level == DifficultyLevel.EASY
        )
    }

    @Test
    fun `virtuoso run scores much higher than children song`() {
        val easy = DifficultyEstimator.estimate(
            score(notes = melody(listOf(60, 60, 67, 67, 69, 69, 67), intervalMs = 400))
        )
        // 大师级：超密音符 + 大跨度 + 高速 + 和弦 + 装饰音
        val hardNotes = mutableListOf<ScoreNote>()
        for (i in 0 until 30) {
            val t = i * 80L
            // 三音和弦
            listOf(48 + (i * 5) % 48, 52 + (i * 5) % 48, 55 + (i * 5) % 48).forEach { midi ->
                hardNotes.add(
                    ScoreNote(
                        midiNumber = midi, noteName = "n", startTime = t, duration = 70,
                        staff = if (i % 2 == 0) Staff.TREBLE else Staff.BASS,
                        accidental = if (i % 3 == 0) Accidental.SHARP else Accidental.NONE,
                        isGraceNote = (i % 5 == 0)
                    )
                )
            }
        }
        val hard = DifficultyEstimator.estimate(score(notes = hardNotes, tempo = 200))
        assertTrue(
            "hard (${hard.totalScore}) should be >> easy (${easy.totalScore})",
            hard.totalScore > easy.totalScore + 30
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  确定性
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `estimation is deterministic`() {
        val notes = melody(listOf(60, 62, 64, 65, 67, 69), intervalMs = 350, durationMs = 300)
        val s = score(notes = notes, tempo = 140)
        val r1 = DifficultyEstimator.estimate(s)
        val r2 = DifficultyEstimator.estimate(s)
        assertEquals(r1.totalScore, r2.totalScore)
        assertEquals(r1.level, r2.level)
    }

    @Test
    fun `note order independence for same onset grouping`() {
        // 打乱顺序的相同音符集合应得到相同结果
        val ordered = melody(listOf(60, 62, 64, 65), intervalMs = 500)
        val shuffled = ordered.shuffled()
        val r1 = DifficultyEstimator.estimate(score(notes = ordered))
        val r2 = DifficultyEstimator.estimate(score(notes = shuffled))
        assertEquals(r1.totalScore, r2.totalScore)
    }
}
