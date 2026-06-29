package com.pianocompanion.ui.library

import com.pianocompanion.data.model.Staff
import com.pianocompanion.generator.SightReadingDifficulty
import com.pianocompanion.omr.image.KeySignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SightReadingDialogState] / [SightReadingKeys] 单元测试 — 验证对话框状态到
 * 生成器配置的转换逻辑，以及常用调号子集的正确性。
 */
class SightReadingDialogStateTest {

    @Test
    fun `默认状态为 C大调 初级 8小节 4-4拍 100BPM 高音谱号`() {
        val state = SightReadingDialogState()
        assertEquals(KeySignature.C_MAJOR_A_MINOR, state.keySignature)
        assertEquals(SightReadingDifficulty.BEGINNER, state.difficulty)
        assertEquals(8, state.measures)
        assertEquals("4/4", state.timeSignature)
        assertEquals(100, state.tempo)
        assertEquals(Staff.TREBLE, state.staff)
    }

    @Test
    fun `toOptions 正确映射所有字段`() {
        val state = SightReadingDialogState(
            keySignature = KeySignature.D_MAJOR,
            difficulty = SightReadingDifficulty.INTERMEDIATE,
            measures = 16,
            timeSignature = "3/4",
            tempo = 144,
            staff = Staff.BASS
        )
        val options = state.toOptions(seed = 42L)

        assertEquals(KeySignature.D_MAJOR, options.keySignature)
        assertEquals(SightReadingDifficulty.INTERMEDIATE, options.difficulty)
        assertEquals(16, options.measures)
        assertEquals("3/4", options.timeSignature)
        assertEquals(144, options.tempo)
        assertEquals(Staff.BASS, options.staff)
        assertEquals(42L, options.seed)
    }

    @Test
    fun `toOptions 无参版本每次生成不同种子`() {
        val state = SightReadingDialogState()
        val o1 = state.toOptions()
        // 极小概率两次时间戳相同；等待一毫秒确保不同
        Thread.sleep(2)
        val o2 = state.toOptions()
        assertNotEquals("两次生成应使用不同种子", o1.seed, o2.seed)
        // 其余字段保持一致
        assertEquals(o1.keySignature, o2.keySignature)
        assertEquals(o1.difficulty, o2.difficulty)
    }

    @Test
    fun `copy 修改单个字段后其余字段不变`() {
        val base = SightReadingDialogState()
        val changed = base.copy(difficulty = SightReadingDifficulty.ADVANCED)

        assertEquals(SightReadingDifficulty.ADVANCED, changed.difficulty)
        assertEquals(base.keySignature, changed.keySignature)
        assertEquals(base.measures, changed.measures)
        assertEquals(base.tempo, changed.tempo)
        assertEquals(base.staff, changed.staff)
    }

    // ── SightReadingKeys 子集验证 ──

    @Test
    fun `common 调号包含 C G D A F bB bE 七个`() {
        val keys = SightReadingKeys.common.map { it }
        assertTrue(KeySignature.C_MAJOR_A_MINOR in keys)
        assertTrue(KeySignature.G_MAJOR_E_MINOR in keys)
        assertTrue(KeySignature.D_MAJOR in keys)
        assertTrue(KeySignature.A_MAJOR in keys)
        assertTrue(KeySignature.F_MAJOR_D_MINOR in keys)
        assertTrue(KeySignature.B_FLAT_MAJOR in keys)
        assertTrue(KeySignature.E_FLAT_MAJOR in keys)
        assertEquals(7, keys.size)
    }

    @Test
    fun `common 调号标签不重复`() {
        val labels = SightReadingKeys.common.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `timeSignatures 包含 2-4 3-4 4-4`() {
        assertEquals(listOf("4/4", "3/4", "2/4"), SightReadingKeys.timeSignatures)
    }

    @Test
    fun `measures 均为 4 的倍数`() {
        SightReadingKeys.measures.forEach { m ->
            assertEquals("小节数 $m 应为 4 的倍数", 0, m % 4)
        }
        assertEquals(listOf(4, 8, 12, 16), SightReadingKeys.measures)
    }

    @Test
    fun `tempoPresets 非空且升序`() {
        val presets = SightReadingKeys.tempoPresets
        assertTrue(presets.isNotEmpty())
        for (i in 1 until presets.size) {
            assertTrue("速度预设应升序", presets[i] >= presets[i - 1])
        }
    }

    @Test
    fun `每个 common 调号均可用 toOptions 生成有效配置`() {
        SightReadingKeys.common.forEach { key ->
            val state = SightReadingDialogState(keySignature = key)
            val options = state.toOptions(seed = 1L)
            assertEquals(key, options.keySignature)
        }
    }

    @Test
    fun `每个 difficulty 均可生成有效配置`() {
        SightReadingDifficulty.entries.forEach { diff ->
            val state = SightReadingDialogState(difficulty = diff)
            val options = state.toOptions(seed = 7L)
            assertEquals(diff, options.difficulty)
        }
    }
}
