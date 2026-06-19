package com.pianocompanion.omr.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拍号模型测试（纯逻辑）。 */
class TimeSignatureTest {

    @Test
    fun `quartersPerMeasure for common meters`() {
        assertEquals(4.0, TimeSignature.FOUR_FOUR.quartersPerMeasure, 1e-9)
        assertEquals(3.0, TimeSignature.THREE_FOUR.quartersPerMeasure, 1e-9)
        assertEquals(2.0, TimeSignature.TWO_FOUR.quartersPerMeasure, 1e-9)
        // 6/8 = 6 个八分 = 3 个四分
        assertEquals(3.0, TimeSignature.SIX_EIGHT.quartersPerMeasure, 1e-9)
        assertEquals(1.5, TimeSignature(3, 8).quartersPerMeasure, 1e-9)
    }

    @Test
    fun `isValid accepts power-of-two denominators`() {
        assertTrue(TimeSignature(4, 4).isValid)
        assertTrue(TimeSignature(3, 8).isValid)
        assertTrue(TimeSignature(2, 2).isValid)
        assertTrue(TimeSignature(4, 16).isValid)
    }

    @Test
    fun `isValid rejects bad meters`() {
        assertFalse(TimeSignature(0, 4).isValid)    // 分子为 0
        assertFalse(TimeSignature(4, 0).isValid)    // 分母为 0
        assertFalse(TimeSignature(4, 5).isValid)    // 分母非 2 的幂
        assertFalse(TimeSignature(4, 3).isValid)    // 分母非 2 的幂
        assertFalse(TimeSignature(-1, 4).isValid)   // 负分子
    }

    @Test
    fun `fromDigits builds valid signature or null`() {
        assertEquals(TimeSignature(3, 4), TimeSignature.fromDigits(3, 4))
        assertEquals(TimeSignature(6, 8), TimeSignature.fromDigits(6, 8))
        assertNull(TimeSignature.fromDigits(4, 5)) // 非法分母
    }

    @Test
    fun `toString renders numerator_slash_denominator`() {
        assertEquals("4/4", TimeSignature.FOUR_FOUR.toString())
        assertEquals("6/8", TimeSignature.SIX_EIGHT.toString())
    }
}
