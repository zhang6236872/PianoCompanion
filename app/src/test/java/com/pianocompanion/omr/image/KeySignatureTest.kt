package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调号音乐理论模型测试（纯逻辑，无图像）。
 * 字母索引约定：C=0, D=1, E=2, F=3, G=4, A=5, B=6。
 */
class KeySignatureTest {

    @Test
    fun `C major has no accidentals`() {
        val key = KeySignature.C_MAJOR_A_MINOR
        assertEquals(0, key.sharpCount)
        assertEquals(0, key.flatCount)
        assertEquals(0, key.accidentalCount)
        for (letter in 0..6) assertEquals(0, key.accidentalOffset(letter))
    }

    @Test
    fun `G major sharpens F only`() {
        val key = KeySignature.G_MAJOR_E_MINOR
        assertEquals(1, key.sharpCount)
        assertTrue(key.hasSharps)
        assertFalse(key.hasFlats)
        assertEquals(+1, key.accidentalOffset(3)) // F
        for (letter in listOf(0, 1, 2, 4, 5, 6)) assertEquals(0, key.accidentalOffset(letter))
    }

    @Test
    fun `D major sharpens F and C`() {
        val key = KeySignature.D_MAJOR
        assertEquals(+1, key.accidentalOffset(3)) // F
        assertEquals(+1, key.accidentalOffset(0)) // C
        assertEquals(0, key.accidentalOffset(4))  // G 不在 2 升号内
    }

    @Test
    fun `B major sharpens first five in order F C G D A`() {
        val key = KeySignature.B_MAJOR
        // F,C,G,D,A 升；E,B 不升
        assertEquals(+1, key.accidentalOffset(3))
        assertEquals(+1, key.accidentalOffset(0))
        assertEquals(+1, key.accidentalOffset(4))
        assertEquals(+1, key.accidentalOffset(1))
        assertEquals(+1, key.accidentalOffset(5))
        assertEquals(0, key.accidentalOffset(2))  // E
        assertEquals(0, key.accidentalOffset(6))  // B
    }

    @Test
    fun `F major flattens B only`() {
        val key = KeySignature.F_MAJOR_D_MINOR
        assertEquals(1, key.flatCount)
        assertTrue(key.hasFlats)
        assertEquals(-1, key.accidentalOffset(6)) // B
        for (letter in listOf(0, 1, 2, 3, 4, 5)) assertEquals(0, key.accidentalOffset(letter))
    }

    @Test
    fun `bE major flattens B E A`() {
        val key = KeySignature.E_FLAT_MAJOR
        assertEquals(-1, key.accidentalOffset(6)) // B
        assertEquals(-1, key.accidentalOffset(2)) // E
        assertEquals(-1, key.accidentalOffset(5)) // A
        assertEquals(0, key.accidentalOffset(1))  // D 不在 3 降号内
    }

    @Test
    fun `fromAccidentals maps counts to circle of fifths keys`() {
        assertEquals(KeySignature.C_MAJOR_A_MINOR, KeySignature.fromAccidentals(0, 0))
        assertEquals(KeySignature.G_MAJOR_E_MINOR, KeySignature.fromAccidentals(1, 0))
        assertEquals(KeySignature.A_MAJOR, KeySignature.fromAccidentals(3, 0))
        assertEquals(KeySignature.F_MAJOR_D_MINOR, KeySignature.fromAccidentals(0, 1))
        assertEquals(KeySignature.B_FLAT_MAJOR, KeySignature.fromAccidentals(0, 2))
        assertEquals(KeySignature.A_FLAT_MAJOR, KeySignature.fromAccidentals(0, 4))
    }

    @Test
    fun `fromAccidentals clamps to 0_7`() {
        assertEquals(7, KeySignature.fromAccidentals(99, 0).sharpCount)
        assertEquals(7, KeySignature.fromAccidentals(0, 99).flatCount)
    }

    @Test
    fun `sharp and flat addition orders are correct`() {
        // 升号顺序：F C G D A E B
        assertEquals(3, KeySignature.sharpLetterAt(0)) // F
        assertEquals(0, KeySignature.sharpLetterAt(1)) // C
        assertEquals(4, KeySignature.sharpLetterAt(2)) // G
        // 降号顺序：B E A D G C F
        assertEquals(6, KeySignature.flatLetterAt(0)) // B
        assertEquals(2, KeySignature.flatLetterAt(1)) // E
        assertEquals(5, KeySignature.flatLetterAt(2)) // A
    }

    @Test
    fun `isAltered reflects offset`() {
        assertTrue(KeySignature.G_MAJOR_E_MINOR.isAltered(3))  // F
        assertFalse(KeySignature.G_MAJOR_E_MINOR.isAltered(0)) // C
        assertTrue(KeySignature.F_MAJOR_D_MINOR.isAltered(6))  // B
    }

    @Test
    fun `accidentalOffset is periodic over octaves`() {
        // 字母索引对 7 取模应一致（+7 = 同字母高八度）
        val key = KeySignature.G_MAJOR_E_MINOR
        assertEquals(key.accidentalOffset(3), key.accidentalOffset(3 + 7))
        assertEquals(key.accidentalOffset(3), key.accidentalOffset(3 - 7))
    }
}
