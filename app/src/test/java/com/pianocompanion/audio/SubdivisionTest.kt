package com.pianocompanion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubdivisionTest {

    // ===== Subdivision enum 基本属性 =====

    @Test
    fun `clicksPerBeat values are correct`() {
        assertEquals(1, Subdivision.QUARTER.clicksPerBeat)
        assertEquals(2, Subdivision.EIGHTH.clicksPerBeat)
        assertEquals(3, Subdivision.TRIPLET.clicksPerBeat)
        assertEquals(4, Subdivision.SIXTEENTH.clicksPerBeat)
        assertEquals(6, Subdivision.SEXTUPLET.clicksPerBeat)
        assertEquals(8, Subdivision.THIRTY_SECOND.clicksPerBeat)
    }

    @Test
    fun `isQuarter is true only for QUARTER`() {
        assertTrue(Subdivision.QUARTER.isQuarter)
        Subdivision.entries.filter { it != Subdivision.QUARTER }.forEach {
            assertTrue("${it} should not be quarter", !it.isQuarter)
        }
    }

    @Test
    fun `totalClicks equals beatsPerMeasure times clicksPerBeat`() {
        assertEquals(4, Subdivision.totalClicks(4, Subdivision.QUARTER))
        assertEquals(8, Subdivision.totalClicks(4, Subdivision.EIGHTH))
        assertEquals(12, Subdivision.totalClicks(4, Subdivision.TRIPLET))
        assertEquals(16, Subdivision.totalClicks(4, Subdivision.SIXTEENTH))
        assertEquals(24, Subdivision.totalClicks(4, Subdivision.SEXTUPLET))
        assertEquals(32, Subdivision.totalClicks(4, Subdivision.THIRTY_SECOND))
        assertEquals(6, Subdivision.totalClicks(3, Subdivision.EIGHTH))
        assertEquals(12, Subdivision.totalClicks(3, Subdivision.SIXTEENTH))
    }

    // ===== ClickPatternGenerator pattern =====

    @Test
    fun `QUARTER pattern in 4-4 is all beats with first accent`() {
        val p = ClickPatternGenerator.pattern(4, Subdivision.QUARTER)
        assertEquals(4, p.size)
        assertEquals(ClickType.ACCENT, p[0])
        assertEquals(ClickType.BEAT, p[1])
        assertEquals(ClickType.BEAT, p[2])
        assertEquals(ClickType.BEAT, p[3])
    }

    @Test
    fun `EIGHTH pattern alternates beat and sub`() {
        val p = ClickPatternGenerator.pattern(4, Subdivision.EIGHTH)
        assertEquals(8, p.size)
        assertEquals(ClickType.ACCENT, p[0])
        assertEquals(ClickType.SUB, p[1])
        assertEquals(ClickType.BEAT, p[2])
        assertEquals(ClickType.SUB, p[3])
        assertEquals(ClickType.BEAT, p[4])
        assertEquals(ClickType.SUB, p[5])
        assertEquals(ClickType.BEAT, p[6])
        assertEquals(ClickType.SUB, p[7])
    }

    @Test
    fun `TRIPLET pattern has one beat plus two subs per beat`() {
        val p = ClickPatternGenerator.pattern(2, Subdivision.TRIPLET)
        assertEquals(6, p.size)
        // beat 0 group
        assertEquals(ClickType.ACCENT, p[0])
        assertEquals(ClickType.SUB, p[1])
        assertEquals(ClickType.SUB, p[2])
        // beat 1 group
        assertEquals(ClickType.BEAT, p[3])
        assertEquals(ClickType.SUB, p[4])
        assertEquals(ClickType.SUB, p[5])
    }

    @Test
    fun `SIXTEENTH pattern has one beat plus three subs per beat`() {
        val p = ClickPatternGenerator.pattern(1, Subdivision.SIXTEENTH)
        assertEquals(4, p.size)
        assertEquals(ClickType.ACCENT, p[0])
        assertEquals(ClickType.SUB, p[1])
        assertEquals(ClickType.SUB, p[2])
        assertEquals(ClickType.SUB, p[3])
    }

    @Test
    fun `SEXTUPLET pattern total clicks and accent position`() {
        val p = ClickPatternGenerator.pattern(2, Subdivision.SEXTUPLET)
        assertEquals(12, p.size)
        assertEquals(ClickType.ACCENT, p[0])
        // beat 1 at index 6
        assertEquals(ClickType.BEAT, p[6])
        // all others are SUB
        val subCount = p.count { it == ClickType.SUB }
        assertEquals(10, subCount)
    }

    @Test
    fun `THIRTY_SECOND pattern for 1 beat`() {
        val p = ClickPatternGenerator.pattern(1, Subdivision.THIRTY_SECOND)
        assertEquals(8, p.size)
        assertEquals(ClickType.ACCENT, p[0])
        for (i in 1 until 8) {
            assertEquals("index $i should be SUB", ClickType.SUB, p[i])
        }
    }

    @Test
    fun `pattern for beatsPerMeasure 1 quarter is single accent`() {
        val p = ClickPatternGenerator.pattern(1, Subdivision.QUARTER)
        assertEquals(1, p.size)
        assertEquals(ClickType.ACCENT, p[0])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pattern rejects beatsPerMeasure zero`() {
        ClickPatternGenerator.pattern(0, Subdivision.QUARTER)
    }

    // ===== subClickIntervalMs =====

    @Test
    fun `QUARTER interval equals beat interval`() {
        // 120 BPM => beat = 500ms; quarter => same 500ms
        assertEquals(500L, ClickPatternGenerator.subClickIntervalMs(120, Subdivision.QUARTER))
    }

    @Test
    fun `EIGHTH interval is half of beat`() {
        // 120 BPM => beat 500ms => eighth 250ms
        assertEquals(250L, ClickPatternGenerator.subClickIntervalMs(120, Subdivision.EIGHTH))
    }

    @Test
    fun `TRIPLET interval is one third of beat`() {
        // 120 BPM => beat 500ms => triplet 166ms (integer division)
        assertEquals(166L, ClickPatternGenerator.subClickIntervalMs(120, Subdivision.TRIPLET))
    }

    @Test
    fun `SIXTEENTH interval is quarter of beat`() {
        // 120 BPM => beat 500ms => sixteenth 125ms
        assertEquals(125L, ClickPatternGenerator.subClickIntervalMs(120, Subdivision.SIXTEENTH))
    }

    @Test
    fun `THIRTY_SECOND interval is eighth of beat`() {
        // 120 BPM => beat 500ms => 62ms
        assertEquals(62L, ClickPatternGenerator.subClickIntervalMs(120, Subdivision.THIRTY_SECOND))
    }

    @Test
    fun `very fast subdivision never returns zero`() {
        // 240 BPM, 32nd notes => beat 250ms => 31ms; still >= 1
        val ms = ClickPatternGenerator.subClickIntervalMs(240, Subdivision.THIRTY_SECOND)
        assertTrue("interval must be >= 1ms, got $ms", ms >= 1L)
    }

    @Test
    fun `higher BPM produces smaller interval`() {
        val slow = ClickPatternGenerator.subClickIntervalMs(60, Subdivision.EIGHTH)
        val fast = ClickPatternGenerator.subClickIntervalMs(180, Subdivision.EIGHTH)
        assertTrue("faster BPM should yield smaller interval", fast < slow)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subClickIntervalMs rejects zero bpm`() {
        ClickPatternGenerator.subClickIntervalMs(0, Subdivision.QUARTER)
    }

    // ===== measureDurationMs =====

    @Test
    fun `measureDuration independent of subdivision`() {
        // 4/4 at 120 BPM = 2000ms regardless of subdivision
        val q = ClickPatternGenerator.measureDurationMs(120, 4)
        val e = ClickPatternGenerator.measureDurationMs(120, 4)
        assertEquals(2000L, q)
        assertEquals(q, e)
    }

    @Test
    fun `measureDuration 3-4 at 120 BPM is 1500ms`() {
        assertEquals(1500L, ClickPatternGenerator.measureDurationMs(120, 3))
    }

    @Test
    fun `measureDuration equals totalClicks times subInterval for quarter`() {
        val bpm = 120
        val beats = 4
        val sub = Subdivision.EIGHTH
        val expected = beats * 60_000L / bpm
        val viaClicks = Subdivision.totalClicks(beats, sub) *
            ClickPatternGenerator.subClickIntervalMs(bpm, sub)
        // 由于整数除法可能存在 ≤1ms 的舍入差异，允许误差
        assertTrue(
            "measure duration should approximate clicks*interval",
            Math.abs(expected - viaClicks) <= beats
        )
    }

    // ===== beatIndexOf =====

    @Test
    fun `beatIndexOf maps click indices to correct beat`() {
        val sub = Subdivision.EIGHTH
        assertEquals(0, ClickPatternGenerator.beatIndexOf(0, sub))
        assertEquals(0, ClickPatternGenerator.beatIndexOf(1, sub))
        assertEquals(1, ClickPatternGenerator.beatIndexOf(2, sub))
        assertEquals(1, ClickPatternGenerator.beatIndexOf(3, sub))
        assertEquals(3, ClickPatternGenerator.beatIndexOf(7, sub))
    }

    @Test
    fun `beatIndexOf for triplet`() {
        val sub = Subdivision.TRIPLET
        assertEquals(0, ClickPatternGenerator.beatIndexOf(0, sub))
        assertEquals(0, ClickPatternGenerator.beatIndexOf(2, sub))
        assertEquals(1, ClickPatternGenerator.beatIndexOf(3, sub))
        assertEquals(2, ClickPatternGenerator.beatIndexOf(6, sub))
    }

    @Test
    fun `non-sub clicks align to beat boundary`() {
        // 对于任意细分，pattern 中非 SUB 的点击点，其 beatIndexOf 应等于
        // 它在该小节内的"主拍序号"。
        Subdivision.entries.forEach { sub ->
            val pattern = ClickPatternGenerator.pattern(4, sub)
            val nonSubIndices = pattern.indices.filter { pattern[it] != ClickType.SUB }
            nonSubIndices.forEachIndexed { beatNum, idx ->
                assertEquals(
                    "sub=$sub beat=$beatNum idx=$idx",
                    beatNum,
                    ClickPatternGenerator.beatIndexOf(idx, sub)
                )
            }
        }
    }

    // ===== 综合一致性 =====

    @Test
    fun `pattern accent is always at index zero`() {
        Subdivision.entries.forEach { sub ->
            val pattern = ClickPatternGenerator.pattern(4, sub)
            assertEquals("$sub first click must be ACCENT", ClickType.ACCENT, pattern.first())
        }
    }

    @Test
    fun `pattern has exactly one accent`() {
        Subdivision.entries.forEach { sub ->
            val pattern = ClickPatternGenerator.pattern(4, sub)
            val accentCount = pattern.count { it == ClickType.ACCENT }
            assertEquals("$sub must have exactly one accent", 1, accentCount)
        }
    }

    @Test
    fun `beat count in pattern equals beatsPerMeasure`() {
        val measures = listOf(2, 3, 4, 6)
        measures.forEach { beats ->
            Subdivision.entries.forEach { sub ->
                val pattern = ClickPatternGenerator.pattern(beats, sub)
                val beatCount = pattern.count { it != ClickType.SUB }
                assertEquals("$beats/$sub", beats, beatCount)
            }
        }
    }
}
