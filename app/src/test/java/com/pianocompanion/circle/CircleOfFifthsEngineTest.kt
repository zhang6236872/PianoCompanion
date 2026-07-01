package com.pianocompanion.circle

import org.junit.Assert.*
import org.junit.Test

/**
 * 五度圈引擎 [CircleOfFifthsEngine] 单元测试。
 *
 * 验证圆环位置换算、调号计算、等音记谱（diatonic spelling）、
 * 调内顺阶三和弦罗马数字分析、关系调与近关系调等核心音乐理论逻辑。
 */
class CircleOfFifthsEngineTest {

    private val cm = CircleKey(0, CircleMode.MAJOR)
    private val gm = CircleKey(7, CircleMode.MAJOR)
    private val dm = CircleKey(2, CircleMode.MAJOR)
    private val fm = CircleKey(5, CircleMode.MAJOR)
    private val am = CircleKey(9, CircleMode.MINOR)
    private val em = CircleKey(4, CircleMode.MINOR)

    // ════════════════════════════════════════
    //  圆环位置换算
    // ════════════════════════════════════════

    @Test
    fun `C大调在位置0`() {
        assertEquals(0, CircleOfFifthsEngine.positionOf(cm))
    }

    @Test
    fun `G大调在位置1（升号方向第一格）`() {
        assertEquals(1, CircleOfFifthsEngine.positionOf(gm))
    }

    @Test
    fun `D大调在位置2`() {
        assertEquals(2, CircleOfFifthsEngine.positionOf(dm))
    }

    @Test
    fun `F大调在位置11（降号方向最后一格）`() {
        assertEquals(11, CircleOfFifthsEngine.positionOf(fm))
    }

    @Test
    fun `a小调与C大调共享位置0`() {
        assertEquals(0, CircleOfFifthsEngine.positionOf(am))
    }

    @Test
    fun `12个大调位置覆盖0到11`() {
        val positions = CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).map { CircleOfFifthsEngine.positionOf(it) }
        assertEquals((0..11).toList(), positions.sorted())
    }

    @Test
    fun `大调与小调在相同位置（关系调）`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { majorKey ->
            val minorKey = CircleOfFifthsEngine.relativeKey(majorKey)
            assertEquals(
                "大调 ${CircleOfFifthsEngine.tonicName(majorKey)} 与其关系小调应在同一位置",
                CircleOfFifthsEngine.positionOf(majorKey),
                CircleOfFifthsEngine.positionOf(minorKey)
            )
        }
    }

    // ════════════════════════════════════════
    //  调号计算
    // ════════════════════════════════════════

    @Test
    fun `C大调无升降号`() {
        val sig = CircleOfFifthsEngine.keySignature(cm)
        assertEquals(0, sig.sharpsCount)
        assertEquals(0, sig.flatsCount)
        assertTrue(sig.isNaturalKey)
    }

    @Test
    fun `G大调有1个升号F`() {
        val sig = CircleOfFifthsEngine.keySignature(gm)
        assertEquals(1, sig.sharpsCount)
        assertEquals(0, sig.flatsCount)
        assertTrue('F' in sig.sharpenedLetters)
    }

    @Test
    fun `D大调有2个升号F和C`() {
        val sig = CircleOfFifthsEngine.keySignature(dm)
        assertEquals(2, sig.sharpsCount)
        assertTrue('F' in sig.sharpenedLetters)
        assertTrue('C' in sig.sharpenedLetters)
    }

    @Test
    fun `F大调有1个降号B`() {
        val sig = CircleOfFifthsEngine.keySignature(fm)
        assertEquals(1, sig.flatsCount)
        assertTrue('B' in sig.flattenedLetters)
    }

    @Test
    fun `升号总数不超过6，降号不超过5`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            val sig = CircleOfFifthsEngine.keySignature(key)
            assertTrue(
                "${CircleOfFifthsEngine.tonicName(key)}的升号数应≤6",
                sig.sharpsCount <= 6
            )
            assertTrue(
                "${CircleOfFifthsEngine.tonicName(key)}的降号数应≤5",
                sig.flatsCount <= 5
            )
        }
    }

    @Test
    fun `升号与降号不会同时出现`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            val sig = CircleOfFifthsEngine.keySignature(key)
            assertTrue(
                "${CircleOfFifthsEngine.tonicName(key)}不应同时有升号和降号",
                sig.sharpsCount == 0 || sig.flatsCount == 0
            )
        }
    }

    @Test
    fun `升号顺序遵循F-C-G-D-A-E-B`() {
        val eMajor = CircleKey(4, CircleMode.MAJOR) // E大调，4个升号
        val sig = CircleOfFifthsEngine.keySignature(eMajor)
        assertEquals(setOf('F', 'C', 'G', 'D'), sig.sharpenedLetters)
    }

    @Test
    fun `降号顺序遵循B-E-A-D-G-C-F`() {
        val abMajor = CircleKey(8, CircleMode.MAJOR) // A♭大调，4个降号
        val sig = CircleOfFifthsEngine.keySignature(abMajor)
        assertEquals(setOf('B', 'E', 'A', 'D'), sig.flattenedLetters)
    }

    // ════════════════════════════════════════
    //  主音名与等音记谱
    // ════════════════════════════════════════

    @Test
    fun `C大调主音名是C`() {
        assertEquals("C", CircleOfFifthsEngine.tonicName(cm))
    }

    @Test
    fun `F大调主音名是F（不降号）`() {
        assertEquals("F", CircleOfFifthsEngine.tonicName(fm))
    }

    @Test
    fun `D♭大调主音名是D♭（降号侧）`() {
        // D♭大调 = pc 1, 位置7（降号侧）
        val dbMajor = CircleKey(1, CircleMode.MAJOR)
        assertEquals("D♭", CircleOfFifthsEngine.tonicName(dbMajor))
    }

    @Test
    fun `F♯大调主音名是F♯（升号侧）`() {
        // F♯大调 = pc 6, 位置6（升号侧）
        val fsMajor = CircleKey(6, CircleMode.MAJOR)
        assertEquals("F♯", CircleOfFifthsEngine.tonicName(fsMajor))
    }

    @Test
    fun `a小调主音名是A`() {
        assertEquals("A", CircleOfFifthsEngine.tonicName(am))
    }

    @Test
    fun `C大调音阶拼写 = C D E F G A B`() {
        assertEquals(listOf("C", "D", "E", "F", "G", "A", "B"), CircleOfFifthsEngine.scaleNoteNames(cm))
    }

    @Test
    fun `G大调音阶拼写 = G A B C D E F♯`() {
        assertEquals(listOf("G", "A", "B", "C", "D", "E", "F♯"), CircleOfFifthsEngine.scaleNoteNames(gm))
    }

    @Test
    fun `D大调音阶拼写 = D E F♯ G A B C♯`() {
        assertEquals(listOf("D", "E", "F♯", "G", "A", "B", "C♯"), CircleOfFifthsEngine.scaleNoteNames(dm))
    }

    @Test
    fun `F大调音阶拼写 = F G A B♭ C D E`() {
        assertEquals(listOf("F", "G", "A", "B♭", "C", "D", "E"), CircleOfFifthsEngine.scaleNoteNames(fm))
    }

    @Test
    fun `音阶拼写中每个字母唯一（无重复字母）`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            val names = CircleOfFifthsEngine.scaleNoteNames(key)
            val letters = names.map { it.first() }
            assertEquals(
                "${CircleOfFifthsEngine.tonicName(key)}大调的音阶字母应覆盖7个不重复字母",
                7,
                letters.toSet().size
            )
        }
    }

    // ════════════════════════════════════════
    //  音阶级类与MIDI
    // ════════════════════════════════════════

    @Test
    fun `C大调音阶级类 = 0 2 4 5 7 9 11`() {
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), CircleOfFifthsEngine.scalePcs(cm))
    }

    @Test
    fun `a小调音阶级类 = 9 11 0 2 4 5 7`() {
        assertEquals(listOf(9, 11, 0, 2, 4, 5, 7), CircleOfFifthsEngine.scalePcs(am))
    }

    @Test
    fun `音阶MIDI音符全部在钢琴范围21-108内`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            CircleOfFifthsEngine.scaleMidiNotes(key).forEach { midi ->
                assertTrue("MIDI $midi 超出钢琴范围", midi in 21..108)
            }
        }
    }

    @Test
    fun `C大调音阶MIDI = 60 62 64 65 67 69 71`() {
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71), CircleOfFifthsEngine.scaleMidiNotes(cm))
    }

    // ════════════════════════════════════════
    //  关系调
    // ════════════════════════════════════════

    @Test
    fun `C大调的关系小调是a小调`() {
        assertEquals(am, CircleOfFifthsEngine.relativeKey(cm))
    }

    @Test
    fun `a小调的关系大调是C大调`() {
        assertEquals(cm, CircleOfFifthsEngine.relativeKey(am))
    }

    @Test
    fun `关系调运算可逆`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            val rel = CircleOfFifthsEngine.relativeKey(key)
            assertEquals(key, CircleOfFifthsEngine.relativeKey(rel))
        }
    }

    @Test
    fun `大调的关系调一定是小调，反之亦然`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            assertNotEquals(key.mode, CircleOfFifthsEngine.relativeKey(key).mode)
        }
    }

    // ════════════════════════════════════════
    //  调内顺阶三和弦（罗马数字分析）
    // ════════════════════════════════════════

    @Test
    fun `C大调有7个顺阶三和弦`() {
        val chords = CircleOfFifthsEngine.diatonicChords(cm)
        assertEquals(7, chords.size)
        assertEquals((1..7).toList(), chords.map { it.degree })
    }

    @Test
    fun `C大调顺阶和弦罗马数字 = I ii iii IV V vi vii°`() {
        val romans = CircleOfFifthsEngine.diatonicChords(cm).map { it.romanNumeral }
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), romans)
    }

    @Test
    fun `C大调顺阶和弦性质 = 大三 小三 小三 大三 大三 小三 减三`() {
        val qualities = CircleOfFifthsEngine.diatonicChords(cm).map { it.quality }
        assertEquals(
            listOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.MINOR,
                   ChordQuality.MAJOR, ChordQuality.MAJOR, ChordQuality.MINOR,
                   ChordQuality.DIMINISHED),
            qualities
        )
    }

    @Test
    fun `C大调V和弦是G大三 = G-B-D`() {
        val v = CircleOfFifthsEngine.diatonicChords(cm)[4]
        assertEquals("V", v.romanNumeral)
        assertEquals(ChordQuality.MAJOR, v.quality)
        assertEquals(listOf("G", "B", "D"), v.noteNames)
    }

    @Test
    fun `C大调vii°和弦是B减三 = B-D-F`() {
        val vii = CircleOfFifthsEngine.diatonicChords(cm)[6]
        assertEquals("vii°", vii.romanNumeral)
        assertEquals(ChordQuality.DIMINISHED, vii.quality)
        assertEquals(listOf("B", "D", "F"), vii.noteNames)
    }

    @Test
    fun `a小调顺阶和弦罗马数字 = i ii° III iv v VI VII`() {
        val romans = CircleOfFifthsEngine.diatonicChords(am).map { it.romanNumeral }
        assertEquals(listOf("i", "ii°", "III", "iv", "v", "VI", "VII"), romans)
    }

    @Test
    fun `每个顺阶和弦有3个音`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            CircleOfFifthsEngine.diatonicChords(key).forEach { chord ->
                assertEquals(3, chord.noteNames.size)
                assertEquals(3, chord.midiNotes.size)
            }
        }
    }

    @Test
    fun `顺阶和弦的MIDI音符按升序排列`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            CircleOfFifthsEngine.diatonicChords(key).forEach { chord ->
                val sorted = chord.midiNotes.sorted()
                assertEquals(sorted, chord.midiNotes)
            }
        }
    }

    // ════════════════════════════════════════
    //  近关系调
    // ════════════════════════════════════════

    @Test
    fun `C大调的近关系调包含a小调（关系调）`() {
        val related = CircleOfFifthsEngine.closelyRelatedKeys(cm)
        assertTrue("关系调a小调应在近关系调中", am in related)
    }

    @Test
    fun `C大调的近关系调包含G大调（属调）`() {
        val related = CircleOfFifthsEngine.closelyRelatedKeys(cm)
        assertTrue("属调G大调应在近关系调中", gm in related)
    }

    @Test
    fun `C大调的近关系调包含F大调（下属调）`() {
        val related = CircleOfFifthsEngine.closelyRelatedKeys(cm)
        assertTrue("下属调F大调应在近关系调中", fm in related)
    }

    @Test
    fun `近关系调有3到5个（去重后）`() {
        CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).forEach { key ->
            val related = CircleOfFifthsEngine.closelyRelatedKeys(key)
            assertTrue(
                "${CircleOfFifthsEngine.tonicName(key)}的近关系调数量应在3~5个",
                related.size in 3..5
            )
        }
    }

    // ════════════════════════════════════════
    //  完整调性信息
    // ════════════════════════════════════════

    @Test
    fun `keyInfo返回完整的调性信息`() {
        val info = CircleOfFifthsEngine.keyInfo(cm)
        assertEquals("C大调", info.displayName)
        assertEquals("C", info.tonicName)
        assertEquals(0, info.position)
        assertFalse(info.preferFlats)
        assertEquals(am, info.relativeKey)
        assertEquals("a小调", info.relativeDisplayName)
    }

    @Test
    fun `keyInfo降号侧调性的preferFlats为true`() {
        val dbInfo = CircleOfFifthsEngine.keyInfo(CircleKey(8, CircleMode.MAJOR)) // A♭大调, 位置8
        assertTrue(dbInfo.preferFlats)
    }

    @Test
    fun `allKeys返回12个调`() {
        assertEquals(12, CircleOfFifthsEngine.allKeys(CircleMode.MAJOR).size)
        assertEquals(12, CircleOfFifthsEngine.allKeys(CircleMode.MINOR).size)
    }

    @Test
    fun `majorPcAt反演正确 - 位置0对应C`() {
        assertEquals(0, CircleOfFifthsEngine.majorPcAt(0, CircleMode.MAJOR))
    }

    @Test
    fun `majorPcAt位置1对应G`() {
        assertEquals(7, CircleOfFifthsEngine.majorPcAt(1, CircleMode.MAJOR))
    }

    @Test
    fun `频率列表与MIDI音符数量一致`() {
        val freqs = CircleOfFifthsEngine.scaleFrequencies(cm)
        val midis = CircleOfFifthsEngine.scaleMidiNotes(cm)
        assertEquals(midis.size, freqs.size)
        freqs.forEach { f -> assertTrue("频率应为正值", f > 0) }
    }

    @Test
    fun `angleDegrees位置0是0度（顶部）`() {
        assertEquals(0.0, CircleOfFifthsEngine.angleDegrees(0), 0.001)
    }

    @Test
    fun `angleDegrees位置3是90度（右侧）`() {
        assertEquals(90.0, CircleOfFifthsEngine.angleDegrees(3), 0.001)
    }
}
