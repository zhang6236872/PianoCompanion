package com.pianocompanion.rhythm

import org.junit.Assert.*
import org.junit.Test

/**
 * [RhythmModels] 数据模型单元测试。
 *
 * 覆盖：
 * - RhythmDuration 拍数/休止判定/显示符号
 * - RhythmDifficulty 枚举完整性
 * - RhythmEvent 代理属性
 * - RhythmPattern 时间轴转换/拍数/显示
 * - OnsetTime 数据
 */
class RhythmModelsTest {

    // ── RhythmDuration ──────────────────────────────────

    @Test
    fun `半音符等于2拍`() {
        assertEquals(2.0, RhythmDuration.HALF.beats, 0.001)
    }

    @Test
    fun `四分音符等于1拍`() {
        assertEquals(1.0, RhythmDuration.QUARTER.beats, 0.001)
    }

    @Test
    fun `八分音符等于0_5拍`() {
        assertEquals(0.5, RhythmDuration.EIGHTH.beats, 0.001)
    }

    @Test
    fun `十六分音符等于0_25拍`() {
        assertEquals(0.25, RhythmDuration.SIXTEENTH.beats, 0.001)
    }

    @Test
    fun `附点四分等于1_5拍`() {
        assertEquals(1.5, RhythmDuration.DOTTED_QUARTER.beats, 0.001)
    }

    @Test
    fun `四分休止等于1拍且为休止`() {
        assertEquals(1.0, RhythmDuration.QUARTER_REST.beats, 0.001)
        assertTrue(RhythmDuration.QUARTER_REST.isRest)
    }

    @Test
    fun `八分休止等于0_5拍且为休止`() {
        assertEquals(0.5, RhythmDuration.EIGHTH_REST.beats, 0.001)
        assertTrue(RhythmDuration.EIGHTH_REST.isRest)
    }

    @Test
    fun `非休止时值的isRest为false`() {
        assertFalse(RhythmDuration.QUARTER.isRest)
        assertFalse(RhythmDuration.HALF.isRest)
        assertFalse(RhythmDuration.EIGHTH.isRest)
        assertFalse(RhythmDuration.SIXTEENTH.isRest)
        assertFalse(RhythmDuration.DOTTED_QUARTER.isRest)
    }

    @Test
    fun `NOTES列表不含休止符`() {
        for (d in RhythmDuration.NOTES) {
            assertFalse("NOTES 不应包含休止符: $d", d.isRest)
        }
        assertEquals(5, RhythmDuration.NOTES.size)
    }

    @Test
    fun `每个时值都有显示名称和符号`() {
        for (d in RhythmDuration.values()) {
            assertTrue(d.displayName.isNotEmpty())
            assertTrue(d.displaySymbol.isNotEmpty())
        }
    }

    // ── RhythmDifficulty ────────────────────────────────

    @Test
    fun `难度有三个等级`() {
        assertEquals(3, RhythmDifficulty.ALL.size)
        assertTrue(RhythmDifficulty.ALL.contains(RhythmDifficulty.BEGINNER))
        assertTrue(RhythmDifficulty.ALL.contains(RhythmDifficulty.INTERMEDIATE))
        assertTrue(RhythmDifficulty.ALL.contains(RhythmDifficulty.ADVANCED))
    }

    @Test
    fun `难度都有显示名称`() {
        for (d in RhythmDifficulty.ALL) {
            assertTrue(d.displayName.isNotEmpty())
        }
    }

    // ── RhythmEvent ─────────────────────────────────────

    @Test
    fun `RhythmEvent代理beats和isRest`() {
        val noteEvent = RhythmEvent(RhythmDuration.QUARTER, 60)
        assertEquals(1.0, noteEvent.beats, 0.001)
        assertFalse(noteEvent.isRest)

        val restEvent = RhythmEvent(RhythmDuration.QUARTER_REST)
        assertEquals(1.0, restEvent.beats, 0.001)
        assertTrue(restEvent.isRest)
    }

    @Test
    fun `RhythmEvent默认midiNote为60`() {
        assertEquals(60, RhythmEvent(RhythmDuration.HALF).midiNote)
    }

    // ── OnsetTime ───────────────────────────────────────

    @Test
    fun `OnsetTime数据完整性`() {
        val onset = OnsetTime(onsetMs = 1000, durationMs = 500, isRest = false, midiNote = 64)
        assertEquals(1000, onset.onsetMs)
        assertEquals(500, onset.durationMs)
        assertFalse(onset.isRest)
        assertEquals(64, onset.midiNote)
    }

    // ── RhythmPattern ───────────────────────────────────

    @Test
    fun `四个四分音符pattern总拍数为4`() {
        val pattern = RhythmPattern(
            events = List(4) { RhythmEvent(RhythmDuration.QUARTER) },
            tempoBpm = 120
        )
        assertEquals(4.0, pattern.totalBeats, 0.001)
    }

    @Test
    fun `msPerBeat在120BPM时为500ms`() {
        val pattern = RhythmPattern(
            events = listOf(RhythmEvent(RhythmDuration.QUARTER)),
            tempoBpm = 120
        )
        assertEquals(500L, pattern.msPerBeat)
    }

    @Test
    fun `msPerBeat在90BPM时约为667ms`() {
        val pattern = RhythmPattern(
            events = listOf(RhythmEvent(RhythmDuration.QUARTER)),
            tempoBpm = 90
        )
        assertEquals(666L, pattern.msPerBeat)
    }

    @Test
    fun `totalDurationMs等于总拍数乘每拍毫秒`() {
        val pattern = RhythmPattern(
            events = List(4) { RhythmEvent(RhythmDuration.QUARTER) },
            tempoBpm = 120
        )
        // 4拍 × 500ms = 2000ms
        assertEquals(2000L, pattern.totalDurationMs)
    }

    @Test
    fun `toOnsetTimes正确计算每个音符的起始时间`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),    // 0ms
                RhythmEvent(RhythmDuration.HALF),       // 500ms
                RhythmEvent(RhythmDuration.QUARTER)     // 1500ms
            ),
            tempoBpm = 120
        )
        val onsets = pattern.toOnsetTimes()
        assertEquals(3, onsets.size)
        assertEquals(0L, onsets[0].onsetMs)
        assertEquals(500L, onsets[1].onsetMs)
        assertEquals(1500L, onsets[2].onsetMs)
    }

    @Test
    fun `toOnsetTimes的durationMs正确`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),    // 500ms
                RhythmEvent(RhythmDuration.EIGHTH)      // 250ms
            ),
            tempoBpm = 120
        )
        val onsets = pattern.toOnsetTimes()
        assertEquals(500L, onsets[0].durationMs)
        assertEquals(250L, onsets[1].durationMs)
    }

    @Test
    fun `toTapTargets过滤休止符`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),
                RhythmEvent(RhythmDuration.QUARTER_REST),
                RhythmEvent(RhythmDuration.QUARTER)
            ),
            tempoBpm = 120
        )
        val targets = pattern.toTapTargets()
        assertEquals(2, targets.size) // 休止符被过滤
        assertFalse(targets[0].isRest)
        assertFalse(targets[1].isRest)
    }

    @Test
    fun `toTapTargets保留休止符的时间偏移`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),        // onset 0ms
                RhythmEvent(RhythmDuration.EIGHTH_REST),    // onset 500ms
                RhythmEvent(RhythmDuration.QUARTER)         // onset 750ms
            ),
            tempoBpm = 120
        )
        val targets = pattern.toTapTargets()
        assertEquals(2, targets.size)
        assertEquals(0L, targets[0].onsetMs)
        assertEquals(750L, targets[1].onsetMs) // 第三个音符在休止之后
    }

    @Test
    fun `displaySymbols拼接所有事件符号`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),
                RhythmEvent(RhythmDuration.EIGHTH)
            )
        )
        val symbols = pattern.displaySymbols
        assertTrue(symbols.contains(RhythmDuration.QUARTER.displaySymbol))
        assertTrue(symbols.contains(RhythmDuration.EIGHTH.displaySymbol))
    }

    @Test
    fun `description拼接所有事件名称`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.QUARTER),
                RhythmEvent(RhythmDuration.HALF)
            )
        )
        val desc = pattern.description
        assertTrue(desc.contains(RhythmDuration.QUARTER.displayName))
        assertTrue(desc.contains(RhythmDuration.HALF.displayName))
    }

    @Test
    fun `空pattern的totalBeats为0`() {
        val pattern = RhythmPattern(events = emptyList())
        assertEquals(0.0, pattern.totalBeats, 0.001)
        assertEquals(0L, pattern.totalDurationMs)
        assertTrue(pattern.toOnsetTimes().isEmpty())
        assertTrue(pattern.toTapTargets().isEmpty())
    }

    @Test
    fun `混合时值pattern正确累加起始时间`() {
        val pattern = RhythmPattern(
            events = listOf(
                RhythmEvent(RhythmDuration.HALF),           // 2拍, onset=0
                RhythmEvent(RhythmDuration.DOTTED_QUARTER), // 1.5拍, onset=1000
                RhythmEvent(RhythmDuration.EIGHTH)          // 0.5拍, onset=1750
            ),
            tempoBpm = 120
        )
        val onsets = pattern.toOnsetTimes()
        assertEquals(0L, onsets[0].onsetMs)
        assertEquals(1000L, onsets[1].onsetMs)
        assertEquals(1750L, onsets[2].onsetMs)
    }
}
