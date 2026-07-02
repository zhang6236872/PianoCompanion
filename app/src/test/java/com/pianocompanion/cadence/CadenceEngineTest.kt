package com.pianocompanion.cadence

import com.pianocompanion.chord.ChordInversion
import com.pianocompanion.chord.ChordRoot
import com.pianocompanion.chord.ChordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CadenceEngine 单元测试（JUnit 4）。
 *
 * 覆盖范围：
 * - 音阶偏移表（大调/和声小调）
 * - 顺阶三和弦质量
 * - 级数→和弦根音映射
 * - 罗马数字格式化（大小写/七和弦/减/增/转位）
 * - 终止式实例化（6 种类型 × 大/小调）
 * - 移调到 12 个调性
 * - 调性名称格式化
 * - 辅助方法
 */
class CadenceEngineTest {

    // ── 音阶偏移表 ──

    @Test
    fun majorScaleOffsetsAreCorrect() {
        assertEquals(0, CadenceEngine.scaleOffset(0, CadenceMode.MAJOR))
        assertEquals(2, CadenceEngine.scaleOffset(1, CadenceMode.MAJOR))
        assertEquals(4, CadenceEngine.scaleOffset(2, CadenceMode.MAJOR))
        assertEquals(5, CadenceEngine.scaleOffset(3, CadenceMode.MAJOR))
        assertEquals(7, CadenceEngine.scaleOffset(4, CadenceMode.MAJOR))
        assertEquals(9, CadenceEngine.scaleOffset(5, CadenceMode.MAJOR))
        assertEquals(11, CadenceEngine.scaleOffset(6, CadenceMode.MAJOR))
    }

    @Test
    fun harmonicMinorScaleOffsetsAreCorrect() {
        assertEquals(0, CadenceEngine.scaleOffset(0, CadenceMode.HARMONIC_MINOR))
        assertEquals(2, CadenceEngine.scaleOffset(1, CadenceMode.HARMONIC_MINOR))
        assertEquals(3, CadenceEngine.scaleOffset(2, CadenceMode.HARMONIC_MINOR))
        assertEquals(5, CadenceEngine.scaleOffset(3, CadenceMode.HARMONIC_MINOR))
        assertEquals(7, CadenceEngine.scaleOffset(4, CadenceMode.HARMONIC_MINOR))
        assertEquals(8, CadenceEngine.scaleOffset(5, CadenceMode.HARMONIC_MINOR))
        assertEquals(11, CadenceEngine.scaleOffset(6, CadenceMode.HARMONIC_MINOR))
    }

    @Test
    fun harmonicMinorDiffersFromMajorAtDegrees2And5() {
        // III is flat3 (3 semitones) in minor vs 4 in major
        assertNotEquals(
            CadenceEngine.scaleOffset(2, CadenceMode.MAJOR),
            CadenceEngine.scaleOffset(2, CadenceMode.HARMONIC_MINOR)
        )
        // VI is flat6 (8 semitones) in minor vs 9 in major
        assertNotEquals(
            CadenceEngine.scaleOffset(5, CadenceMode.MAJOR),
            CadenceEngine.scaleOffset(5, CadenceMode.HARMONIC_MINOR)
        )
    }

    // ── 顺阶三和弦质量 ──

    @Test
    fun majorTriadQualitiesFollowStandardPattern() {
        assertEquals(ChordType.MAJOR, CadenceEngine.triadQuality(0, CadenceMode.MAJOR))
        assertEquals(ChordType.MINOR, CadenceEngine.triadQuality(1, CadenceMode.MAJOR))
        assertEquals(ChordType.MINOR, CadenceEngine.triadQuality(2, CadenceMode.MAJOR))
        assertEquals(ChordType.MAJOR, CadenceEngine.triadQuality(3, CadenceMode.MAJOR))
        assertEquals(ChordType.MAJOR, CadenceEngine.triadQuality(4, CadenceMode.MAJOR))
        assertEquals(ChordType.MINOR, CadenceEngine.triadQuality(5, CadenceMode.MAJOR))
        assertEquals(ChordType.DIMINISHED, CadenceEngine.triadQuality(6, CadenceMode.MAJOR))
    }

    @Test
    fun harmonicMinorTriadQualitiesAreCorrect() {
        assertEquals(ChordType.MINOR, CadenceEngine.triadQuality(0, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.DIMINISHED, CadenceEngine.triadQuality(1, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.AUGMENTED, CadenceEngine.triadQuality(2, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.MINOR, CadenceEngine.triadQuality(3, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.MAJOR, CadenceEngine.triadQuality(4, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.MAJOR, CadenceEngine.triadQuality(5, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordType.DIMINISHED, CadenceEngine.triadQuality(6, CadenceMode.HARMONIC_MINOR))
    }

    // ── 级数→和弦根音 ──

    @Test
    fun chordRootForMajorKeyC() {
        assertEquals(ChordRoot.C, CadenceEngine.chordRootForDegree(ChordRoot.C, 0, CadenceMode.MAJOR))
        assertEquals(ChordRoot.D, CadenceEngine.chordRootForDegree(ChordRoot.C, 1, CadenceMode.MAJOR))
        assertEquals(ChordRoot.E, CadenceEngine.chordRootForDegree(ChordRoot.C, 2, CadenceMode.MAJOR))
        assertEquals(ChordRoot.F, CadenceEngine.chordRootForDegree(ChordRoot.C, 3, CadenceMode.MAJOR))
        assertEquals(ChordRoot.G, CadenceEngine.chordRootForDegree(ChordRoot.C, 4, CadenceMode.MAJOR))
        assertEquals(ChordRoot.A, CadenceEngine.chordRootForDegree(ChordRoot.C, 5, CadenceMode.MAJOR))
        assertEquals(ChordRoot.B, CadenceEngine.chordRootForDegree(ChordRoot.C, 6, CadenceMode.MAJOR))
    }

    @Test
    fun chordRootForMajorKeyG() {
        assertEquals(ChordRoot.G, CadenceEngine.chordRootForDegree(ChordRoot.G, 0, CadenceMode.MAJOR))
        assertEquals(ChordRoot.A, CadenceEngine.chordRootForDegree(ChordRoot.G, 1, CadenceMode.MAJOR))
        assertEquals(ChordRoot.D, CadenceEngine.chordRootForDegree(ChordRoot.G, 4, CadenceMode.MAJOR))
        assertEquals(ChordRoot.E, CadenceEngine.chordRootForDegree(ChordRoot.G, 5, CadenceMode.MAJOR))
    }

    @Test
    fun chordRootForHarmonicMinorKeyA() {
        // a harmonic minor: a, b, c, d, e, f, g#
        assertEquals(ChordRoot.A, CadenceEngine.chordRootForDegree(ChordRoot.A, 0, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordRoot.B, CadenceEngine.chordRootForDegree(ChordRoot.A, 1, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordRoot.C, CadenceEngine.chordRootForDegree(ChordRoot.A, 2, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordRoot.D, CadenceEngine.chordRootForDegree(ChordRoot.A, 3, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordRoot.E, CadenceEngine.chordRootForDegree(ChordRoot.A, 4, CadenceMode.HARMONIC_MINOR))
        assertEquals(ChordRoot.F, CadenceEngine.chordRootForDegree(ChordRoot.A, 5, CadenceMode.HARMONIC_MINOR))
    }

    // ── 罗马数字格式化 ──

    @Test
    fun romanNumeralUppercaseForMajorTriad() {
        assertEquals("I", CadenceEngine.romanNumeral(0, ChordType.MAJOR, ChordInversion.ROOT_POSITION))
        assertEquals("IV", CadenceEngine.romanNumeral(3, ChordType.MAJOR, ChordInversion.ROOT_POSITION))
        assertEquals("V", CadenceEngine.romanNumeral(4, ChordType.MAJOR, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralLowercaseForMinorTriad() {
        assertEquals("ii", CadenceEngine.romanNumeral(1, ChordType.MINOR, ChordInversion.ROOT_POSITION))
        assertEquals("vi", CadenceEngine.romanNumeral(5, ChordType.MINOR, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralDiminishedHasDegreeSymbol() {
        assertEquals("vii°", CadenceEngine.romanNumeral(6, ChordType.DIMINISHED, ChordInversion.ROOT_POSITION))
        assertEquals("ii°", CadenceEngine.romanNumeral(1, ChordType.DIMINISHED, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralAugmentedHasPlusSymbol() {
        assertEquals("III+", CadenceEngine.romanNumeral(2, ChordType.AUGMENTED, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralDominantSeventhHasSuperscript7() {
        assertEquals("V⁷", CadenceEngine.romanNumeral(4, ChordType.DOMINANT_7, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralMajorSeventhHasMSuperscript() {
        assertEquals("Iᴹ⁷", CadenceEngine.romanNumeral(0, ChordType.MAJOR_7, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralMinorSeventhHasSuperscript7() {
        assertEquals("ii⁷", CadenceEngine.romanNumeral(1, ChordType.MINOR_7, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralHalfDiminishedHasOESymbol() {
        assertEquals("viiø⁷", CadenceEngine.romanNumeral(6, ChordType.HALF_DIMINISHED_7, ChordInversion.ROOT_POSITION))
    }

    @Test
    fun romanNumeralFirstInversionHasSuperscript6() {
        assertEquals("V⁶", CadenceEngine.romanNumeral(4, ChordType.MAJOR, ChordInversion.FIRST_INVERSION))
    }

    @Test
    fun romanNumeralSecondInversionHas64() {
        assertEquals("IV⁶₄", CadenceEngine.romanNumeral(3, ChordType.MAJOR, ChordInversion.SECOND_INVERSION))
    }

    @Test
    fun romanNumeralThirdInversionAppends42AfterSeventh() {
        // V⁷ with third inversion: the code appends ⁷ first, then ⁴₂ → "V⁷⁴₂"
        assertEquals("V⁷⁴₂", CadenceEngine.romanNumeral(4, ChordType.DOMINANT_7, ChordInversion.THIRD_INVERSION))
    }

    // ── buildDiatonicStep ──

    @Test
    fun buildDiatonicStepMajorIInCMajor() {
        val step = CadenceEngine.buildDiatonicStep(ChordRoot.C, 0, CadenceMode.MAJOR)
        assertEquals(ChordType.MAJOR, step.chordType)
        assertEquals("I", step.romanNumeral)
        assertEquals(ChordInversion.ROOT_POSITION, step.inversion)
        // C major triad: C4, E4, G4 → MIDI 60, 64, 67
        assertEquals(listOf(60, 64, 67), step.voicing.midiNotes)
    }

    @Test
    fun buildDiatonicStepDominantSeventhV7InCMajor() {
        val step = CadenceEngine.buildDiatonicStep(
            ChordRoot.C, 4, CadenceMode.MAJOR, useDominantSeventh = true
        )
        assertEquals(ChordType.DOMINANT_7, step.chordType)
        assertEquals("V⁷", step.romanNumeral)
        // G dominant 7: G4, B4, D5, F5 → MIDI 67, 71, 74, 77
        assertEquals(listOf(67, 71, 74, 77), step.voicing.midiNotes)
    }

    @Test
    fun buildDiatonicStepMinorIInAHarmonicMinor() {
        val step = CadenceEngine.buildDiatonicStep(ChordRoot.A, 0, CadenceMode.HARMONIC_MINOR)
        assertEquals(ChordType.MINOR, step.chordType)
        assertEquals("i", step.romanNumeral)
        // a minor triad: A4, C5, E5 → MIDI 69, 72, 76
        assertEquals(listOf(69, 72, 76), step.voicing.midiNotes)
    }

    @Test
    fun buildDiatonicStepVInAHarmonicMinorIsMajor() {
        val step = CadenceEngine.buildDiatonicStep(ChordRoot.A, 4, CadenceMode.HARMONIC_MINOR)
        assertEquals(ChordType.MAJOR, step.chordType)
        assertEquals("V", step.romanNumeral)
        // E major triad: E4, G#4, B4 → MIDI 64, 68, 71
        assertEquals(listOf(64, 68, 71), step.voicing.midiNotes)
    }

    @Test
    fun buildDiatonicStepIv6InAHarmonicMinor() {
        val step = CadenceEngine.buildDiatonicStep(
            ChordRoot.A, 3, CadenceMode.HARMONIC_MINOR, inversion = ChordInversion.FIRST_INVERSION
        )
        assertEquals(ChordType.MINOR, step.chordType)
        assertEquals("iv⁶", step.romanNumeral)
    }

    // ── 终止式实例化 ──

    @Test
    fun perfectAuthenticCadenceInCMajorIsV7ToI() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertEquals(2, inst.steps.size)
        assertEquals("V⁷", inst.steps[0].romanNumeral)
        assertEquals("I", inst.steps[1].romanNumeral)
        assertEquals("V⁷ → I", inst.romanNumeralSummary)
    }

    @Test
    fun perfectAuthenticCadenceHasDominantSeventhOnV() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertEquals(ChordType.DOMINANT_7, inst.steps[0].chordType)
        assertEquals(ChordType.MAJOR, inst.steps[1].chordType)
    }

    @Test
    fun perfectAuthenticCadenceBothChordsInRootPosition() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertTrue(inst.steps.all { it.inversion == ChordInversion.ROOT_POSITION })
    }

    @Test
    fun imperfectAuthenticCadenceUsesV6FirstInversion() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.IMPERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertEquals(2, inst.steps.size)
        assertEquals("V⁶", inst.steps[0].romanNumeral)
        assertEquals("I", inst.steps[1].romanNumeral)
        assertEquals(ChordInversion.FIRST_INVERSION, inst.steps[0].inversion)
    }

    @Test
    fun plagalCadenceInCMajorIsIVToI() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PLAGAL, CadenceMode.MAJOR)
        assertEquals(2, inst.steps.size)
        assertEquals("IV", inst.steps[0].romanNumeral)
        assertEquals("I", inst.steps[1].romanNumeral)
        assertEquals(ChordType.MAJOR, inst.steps[0].chordType)
    }

    @Test
    fun plagalCadenceInAMinorIsIvToI() {
        val inst = CadenceEngine.instantiate(ChordRoot.A, CadenceType.PLAGAL, CadenceMode.HARMONIC_MINOR)
        assertEquals(2, inst.steps.size)
        assertEquals("iv", inst.steps[0].romanNumeral)
        assertEquals("i", inst.steps[1].romanNumeral)
        assertEquals(ChordType.MINOR, inst.steps[0].chordType)
        assertEquals(ChordType.MINOR, inst.steps[1].chordType)
    }

    @Test
    fun deceptiveCadenceInCMajorIsVToVi() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.DECEPTIVE, CadenceMode.MAJOR)
        assertEquals(2, inst.steps.size)
        assertEquals("V", inst.steps[0].romanNumeral)
        assertEquals("vi", inst.steps[1].romanNumeral)
        assertEquals(ChordType.MINOR, inst.steps[1].chordType)
    }

    @Test
    fun deceptiveCadenceInAMinorIsVToVI() {
        val inst = CadenceEngine.instantiate(ChordRoot.A, CadenceType.DECEPTIVE, CadenceMode.HARMONIC_MINOR)
        assertEquals("V", inst.steps[0].romanNumeral)
        assertEquals("VI", inst.steps[1].romanNumeral)
        assertEquals(ChordType.MAJOR, inst.steps[1].chordType)
    }

    @Test
    fun halfCadenceInCMajorIsIVToV() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.HALF, CadenceMode.MAJOR)
        assertEquals(2, inst.steps.size)
        assertEquals("IV", inst.steps[0].romanNumeral)
        assertEquals("V", inst.steps[1].romanNumeral)
    }

    @Test
    fun phrygianHalfCadenceInAMinorIsIv6ToV() {
        val inst = CadenceEngine.instantiate(ChordRoot.A, CadenceType.PHRYGIAN_HALF, CadenceMode.HARMONIC_MINOR)
        assertEquals(2, inst.steps.size)
        assertEquals("iv⁶", inst.steps[0].romanNumeral)
        assertEquals("V", inst.steps[1].romanNumeral)
        assertEquals(ChordInversion.FIRST_INVERSION, inst.steps[0].inversion)
    }

    // ── 移调到 12 个调性 ──

    @Test
    fun perfectAuthenticCadenceTransposedToAll12MajorKeys() {
        for (key in ChordRoot.entries) {
            val inst = CadenceEngine.instantiate(key, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
            assertEquals(2, inst.steps.size)
            assertEquals("V⁷ → I", inst.romanNumeralSummary)
            // V is tonic + 7 semitones
            val tonicPc = key.pitchClass
            val dominantPc = CadenceEngine.chordRootForDegree(key, 4, CadenceMode.MAJOR).pitchClass
            assertEquals((tonicPc + 7) % 12, dominantPc)
        }
    }

    @Test
    fun transpositionToGMajorProducesCorrectRoots() {
        val inst = CadenceEngine.instantiate(ChordRoot.G, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        // V7 of G major = D7
        assertEquals(ChordRoot.D, inst.steps[0].voicing.root)
        // I = G major
        assertEquals(ChordRoot.G, inst.steps[1].voicing.root)
    }

    @Test
    fun transpositionToFMajorUsesFlats() {
        val inst = CadenceEngine.instantiate(ChordRoot.F, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertTrue(inst.preferFlats)
        assertEquals(ChordRoot.C, inst.steps[0].voicing.root)
        assertEquals(ChordRoot.F, inst.steps[1].voicing.root)
    }

    @Test
    fun transpositionToBFlatMajorUsesFlats() {
        val inst = CadenceEngine.instantiate(ChordRoot.B_FLAT, CadenceType.PLAGAL, CadenceMode.MAJOR)
        assertTrue(inst.preferFlats)
        assertTrue(inst.romanNumeralSummary.contains("IV"))
    }

    @Test
    fun deceptiveCadenceTransposedToAll12MajorKeys() {
        for (key in ChordRoot.entries) {
            val inst = CadenceEngine.instantiate(key, CadenceType.DECEPTIVE, CadenceMode.MAJOR)
            assertEquals("V → vi", inst.romanNumeralSummary)
        }
    }

    @Test
    fun phrygianHalfCadenceOnlyInMinor() {
        assertFalse(CadenceType.PHRYGIAN_HALF.supportsMajor)
        assertTrue(CadenceType.PHRYGIAN_HALF.supportsMinor)
    }

    @Test
    fun phrygianHalfCadenceTransposedToAll12MinorKeys() {
        for (key in ChordRoot.entries) {
            val inst = CadenceEngine.instantiate(key, CadenceType.PHRYGIAN_HALF, CadenceMode.HARMONIC_MINOR)
            assertEquals("iv⁶ → V", inst.romanNumeralSummary)
        }
    }

    // ── 调性名称格式化 ──

    @Test
    fun formatKeyNameCMajor() {
        assertEquals("C大调", CadenceEngine.formatKeyName(ChordRoot.C, CadenceMode.MAJOR, false))
    }

    @Test
    fun formatKeyNameAHarmonicMinor() {
        assertEquals("a和声小调", CadenceEngine.formatKeyName(ChordRoot.A, CadenceMode.HARMONIC_MINOR, false))
    }

    @Test
    fun formatKeyNameFMajorWithFlats() {
        assertEquals("F大调", CadenceEngine.formatKeyName(ChordRoot.F, CadenceMode.MAJOR, true))
    }

    @Test
    fun formatKeyNameBFlatMinor() {
        assertEquals("b♭和声小调", CadenceEngine.formatKeyName(ChordRoot.B_FLAT, CadenceMode.HARMONIC_MINOR, true))
    }

    // ── 辅助方法 ──

    @Test
    fun allCadencesByCategoryReturnsAllTypes() {
        val byCategory = CadenceEngine.allCadencesByCategory()
        assertEquals(4, byCategory.size)
        assertTrue(byCategory[CadenceCategory.AUTHENTIC]!!.contains(CadenceType.PERFECT_AUTHENTIC))
        assertTrue(byCategory[CadenceCategory.AUTHENTIC]!!.contains(CadenceType.IMPERFECT_AUTHENTIC))
        assertTrue(byCategory[CadenceCategory.PLAGAL]!!.contains(CadenceType.PLAGAL))
        assertTrue(byCategory[CadenceCategory.DECEPTIVE]!!.contains(CadenceType.DECEPTIVE))
        assertTrue(byCategory[CadenceCategory.HALF]!!.contains(CadenceType.HALF))
        assertTrue(byCategory[CadenceCategory.HALF]!!.contains(CadenceType.PHRYGIAN_HALF))
    }

    @Test
    fun supportedModesForEachCadenceType() {
        assertEquals(
            listOf(CadenceMode.MAJOR, CadenceMode.HARMONIC_MINOR),
            CadenceEngine.supportedModes(CadenceType.PERFECT_AUTHENTIC)
        )
        assertEquals(
            listOf(CadenceMode.MAJOR, CadenceMode.HARMONIC_MINOR),
            CadenceEngine.supportedModes(CadenceType.PLAGAL)
        )
        assertEquals(
            listOf(CadenceMode.MAJOR, CadenceMode.HARMONIC_MINOR),
            CadenceEngine.supportedModes(CadenceType.DECEPTIVE)
        )
        assertEquals(
            listOf(CadenceMode.MAJOR, CadenceMode.HARMONIC_MINOR),
            CadenceEngine.supportedModes(CadenceType.HALF)
        )
        assertEquals(
            listOf(CadenceMode.HARMONIC_MINOR),
            CadenceEngine.supportedModes(CadenceType.PHRYGIAN_HALF)
        )
    }

    @Test
    fun resolutionRootOfPerfectAuthenticCadenceIsTonic() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        val resRoot = CadenceEngine.resolutionRoot(inst)
        assertNotNull(resRoot)
        assertEquals(ChordRoot.C, resRoot)
    }

    @Test
    fun resolutionRootOfHalfCadenceIsDominant() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.HALF, CadenceMode.MAJOR)
        val resRoot = CadenceEngine.resolutionRoot(inst)
        assertNotNull(resRoot)
        assertEquals(ChordRoot.G, resRoot)
    }

    @Test
    fun preferFlatsForFlatKeys() {
        assertTrue(CadenceEngine.preferFlatsFor(ChordRoot.F))
        assertTrue(CadenceEngine.preferFlatsFor(ChordRoot.B_FLAT))
        assertTrue(CadenceEngine.preferFlatsFor(ChordRoot.E_FLAT))
        assertTrue(CadenceEngine.preferFlatsFor(ChordRoot.A_FLAT))
        assertFalse(CadenceEngine.preferFlatsFor(ChordRoot.C))
        assertFalse(CadenceEngine.preferFlatsFor(ChordRoot.G))
        assertFalse(CadenceEngine.preferFlatsFor(ChordRoot.D))
    }

    // ── CadenceInstance 属性 ──

    @Test
    fun instanceChordCountIs2ForAllCadenceTypes() {
        for (type in CadenceType.entries) {
            val mode = CadenceEngine.supportedModes(type).first()
            val inst = CadenceEngine.instantiate(ChordRoot.C, type, mode)
            assertEquals(2, inst.chordCount)
        }
    }

    @Test
    fun instanceFinalChordIsLastStep() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        assertEquals(inst.steps.last(), inst.finalChord)
    }

    @Test
    fun instanceAllMidiNotesAreDistinctAndSorted() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        val notes = inst.allMidiNotes
        assertEquals(notes, notes.distinct().sorted())
    }

    @Test
    fun instanceKeyNameMatchesInstantiation() {
        val inst = CadenceEngine.instantiate(ChordRoot.D, CadenceType.HALF, CadenceMode.MAJOR)
        assertEquals("D大调", inst.keyName)
    }

    // ── MIDI 音符验证 ──

    @Test
    fun pacInCMajorProducesG7ThenCMajor() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PERFECT_AUTHENTIC, CadenceMode.MAJOR)
        // G7: G4(67) B4(71) D5(74) F5(77)
        assertEquals(listOf(67, 71, 74, 77), inst.steps[0].voicing.midiNotes)
        // C major: C4(60) E4(64) G4(67)
        assertEquals(listOf(60, 64, 67), inst.steps[1].voicing.midiNotes)
    }

    @Test
    fun plagalCadenceInCMajorProducesFMajorThenCMajor() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.PLAGAL, CadenceMode.MAJOR)
        // F major: F4(65) A4(69) C5(72)
        assertEquals(listOf(65, 69, 72), inst.steps[0].voicing.midiNotes)
        // C major: C4(60) E4(64) G4(67)
        assertEquals(listOf(60, 64, 67), inst.steps[1].voicing.midiNotes)
    }

    @Test
    fun deceptiveCadenceInCMajorProducesGMajorThenAMinor() {
        val inst = CadenceEngine.instantiate(ChordRoot.C, CadenceType.DECEPTIVE, CadenceMode.MAJOR)
        // G major: G4(67) B4(71) D5(74)
        assertEquals(listOf(67, 71, 74), inst.steps[0].voicing.midiNotes)
        // A minor: A4(69) C5(72) E5(76)
        assertEquals(listOf(69, 72, 76), inst.steps[1].voicing.midiNotes)
    }

    @Test
    fun phrygianHalfCadenceInAMinorProducesDm6ThenEMajor() {
        val inst = CadenceEngine.instantiate(ChordRoot.A, CadenceType.PHRYGIAN_HALF, CadenceMode.HARMONIC_MINOR)
        // d minor first inversion: root D4(62), F4(65), A4(69), first inv → F4(65), A4(69), D5(74)
        val step0 = inst.steps[0]
        assertEquals(ChordInversion.FIRST_INVERSION, step0.inversion)
        assertEquals(ChordType.MINOR, step0.chordType)
        assertEquals(listOf(65, 69, 74), step0.voicing.midiNotes)
        // E major: E4(64) G#4(68) B4(71)
        assertEquals(listOf(64, 68, 71), inst.steps[1].voicing.midiNotes)
    }

    // ── 完整性 ──

    @Test
    fun allCadenceTypesHaveNonEmptySteps() {
        for (type in CadenceType.entries) {
            val mode = CadenceEngine.supportedModes(type).first()
            val inst = CadenceEngine.instantiate(ChordRoot.C, type, mode)
            assertTrue(inst.steps.isNotEmpty())
            assertTrue(inst.steps.all { it.voicing.midiNotes.isNotEmpty() })
            assertTrue(inst.steps.all { it.romanNumeral.isNotEmpty() })
        }
    }

    @Test
    fun allCadenceTypesHaveDescriptions() {
        for (type in CadenceType.entries) {
            assertTrue(type.description.isNotEmpty())
            assertTrue(type.displayName.isNotEmpty())
            assertTrue(type.abbreviation.isNotEmpty())
        }
    }
}
