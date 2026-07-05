package com.pianocompanion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [MetronomePresetStore] 单元测试 — 纯 Kotlin，无 Android 依赖。
 */
class MetronomePresetStoreTest {

    private lateinit var store: MetronomePresetStore

    @Before
    fun setUp() {
        store = MetronomePresetStore()
    }

    // ───────────────── 基本增删改查 ─────────────────

    @Test
    fun `empty store has zero presets`() {
        assertEquals(0, store.size)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `save adds preset to store`() {
        store.save(MetronomePreset("测试", 120, 4, Subdivision.QUARTER))
        assertEquals(1, store.size)
        val preset = store.find("测试")
        assertNotNull(preset)
        assertEquals(120, preset!!.bpm)
        assertEquals(4, preset.beatsPerMeasure)
        assertEquals(Subdivision.QUARTER, preset.subdivision)
    }

    @Test
    fun `find returns null for non-existent name`() {
        assertNull(store.find("不存在"))
    }

    @Test
    fun `exists returns true for saved preset`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.EIGHTH))
        assertTrue(store.exists("A"))
        assertFalse(store.exists("B"))
    }

    @Test
    fun `delete removes preset and returns true`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        assertTrue(store.delete("A"))
        assertEquals(0, store.size)
        assertFalse(store.exists("A"))
    }

    @Test
    fun `delete returns false for non-existent preset`() {
        assertFalse(store.delete("不存在"))
    }

    @Test
    fun `save with duplicate name overwrites existing`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("A", 140, 3, Subdivision.SIXTEENTH))
        assertEquals(1, store.size)
        val preset = store.find("A")
        assertEquals(140, preset!!.bpm)
        assertEquals(3, preset.beatsPerMeasure)
        assertEquals(Subdivision.SIXTEENTH, preset.subdivision)
    }

    // ───────────────── 排序 ─────────────────

    @Test
    fun `list returns presets sorted by name`() {
        store.save(MetronomePreset("Charlie", 80, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("Alpha", 100, 4, Subdivision.EIGHTH))
        store.save(MetronomePreset("Bravo", 120, 4, Subdivision.TRIPLET))
        val names = store.list().map { it.name }
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), names)
    }

    @Test
    fun `list with Chinese names sorts by Unicode order`() {
        store.save(MetronomePreset("中板", 100, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("慢板", 60, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("快板", 140, 4, Subdivision.QUARTER))
        val names = store.list().map { it.name }
        // Unicode codepoint: 中(U+4E2D) < 快(U+5FEB) < 慢(U+6162)
        assertEquals(listOf("中板", "快板", "慢板"), names)
    }

    // ───────────────── 验证 ─────────────────

    @Test
    fun `validate rejects blank name`() {
        val result = store.validate("", 120, 4)
        assertEquals(PresetValidationResult.NameBlank, result)
    }

    @Test
    fun `validate rejects whitespace-only name`() {
        val result = store.validate("   ", 120, 4)
        assertEquals(PresetValidationResult.NameBlank, result)
    }

    @Test
    fun `validate rejects overly long name`() {
        val longName = "A".repeat(MetronomePreset.MAX_NAME_LENGTH + 1)
        val result = store.validate(longName, 120, 4)
        assertEquals(PresetValidationResult.NameTooLong, result)
    }

    @Test
    fun `validate accepts name at max length`() {
        val name = "B".repeat(MetronomePreset.MAX_NAME_LENGTH)
        val result = store.validate(name, 120, 4)
        assertEquals(PresetValidationResult.Ok, result)
    }

    @Test
    fun `validate rejects duplicate name`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val result = store.validate("A", 120, 4)
        assertTrue(result is PresetValidationResult.NameTaken)
    }

    @Test
    fun `validate accepts same name with ignoreExisting`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val result = store.validate("A", 120, 4, ignoreExisting = "A")
        assertEquals(PresetValidationResult.Ok, result)
    }

    @Test
    fun `validate rejects bpm below minimum`() {
        val result = store.validate("X", MetronomePreset.MIN_BPM - 1, 4)
        assertEquals(PresetValidationResult.BpmOutOfRange, result)
    }

    @Test
    fun `validate rejects bpm above maximum`() {
        val result = store.validate("X", MetronomePreset.MAX_BPM + 1, 4)
        assertEquals(PresetValidationResult.BpmOutOfRange, result)
    }

    @Test
    fun `validate accepts bpm at boundaries`() {
        assertEquals(PresetValidationResult.Ok,
            store.validate("X", MetronomePreset.MIN_BPM, 4))
        assertEquals(PresetValidationResult.Ok,
            store.validate("Y", MetronomePreset.MAX_BPM, 4))
    }

    @Test
    fun `validate rejects beats below minimum`() {
        val result = store.validate("X", 120, MetronomePreset.MIN_BEATS - 1)
        assertEquals(PresetValidationResult.BeatsOutOfRange, result)
    }

    @Test
    fun `validate rejects beats above maximum`() {
        val result = store.validate("X", 120, MetronomePreset.MAX_BEATS + 1)
        assertEquals(PresetValidationResult.BeatsOutOfRange, result)
    }

    @Test
    fun `validate rejects beats at zero`() {
        val result = store.validate("X", 120, 0)
        assertEquals(PresetValidationResult.BeatsOutOfRange, result)
    }

    @Test
    fun `validate with null ignoreExisting does not allow same name`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val result = store.validate("A", 120, 4, ignoreExisting = null)
        assertTrue(result is PresetValidationResult.NameTaken)
    }

    // ───────────────── 重命名 ─────────────────

    @Test
    fun `rename changes preset name while keeping config`() {
        store.save(MetronomePreset("旧名", 100, 4, Subdivision.EIGHTH))
        val result = store.rename("旧名", "新名")
        assertEquals(PresetValidationResult.Ok, result)
        assertNull(store.find("旧名"))
        val preset = store.find("新名")
        assertNotNull(preset)
        assertEquals(100, preset!!.bpm)
        assertEquals(4, preset.beatsPerMeasure)
        assertEquals(Subdivision.EIGHTH, preset.subdivision)
    }

    @Test
    fun `rename to existing other name fails`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("B", 120, 3, Subdivision.TRIPLET))
        val result = store.rename("A", "B")
        assertTrue(result is PresetValidationResult.NameTaken)
        // Originals unchanged
        assertNotNull(store.find("A"))
        assertEquals(120, store.find("B")!!.bpm)
    }

    @Test
    fun `rename non-existent returns NameBlank`() {
        val result = store.rename("不存在", "新名")
        assertEquals(PresetValidationResult.NameBlank, result)
    }

    @Test
    fun `rename to blank new name fails`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val result = store.rename("A", "")
        assertEquals(PresetValidationResult.NameBlank, result)
        assertNotNull(store.find("A"))
    }

    @Test
    fun `rename to same name is allowed (no-op essentially)`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val result = store.rename("A", "A")
        assertEquals(PresetValidationResult.Ok, result)
        assertEquals(1, store.size)
    }

    // ───────────────── 批量操作 ─────────────────

    @Test
    fun `clear empties the store`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("B", 120, 3, Subdivision.EIGHTH))
        store.clear()
        assertEquals(0, store.size)
    }

    @Test
    fun `replaceAll overwrites all presets`() {
        store.save(MetronomePreset("旧", 60, 4, Subdivision.QUARTER))
        store.replaceAll(listOf(
            MetronomePreset("新1", 100, 4, Subdivision.EIGHTH),
            MetronomePreset("新2", 140, 3, Subdivision.TRIPLET),
        ))
        assertEquals(2, store.size)
        assertFalse(store.exists("旧"))
        assertTrue(store.exists("新1"))
        assertTrue(store.exists("新2"))
    }

    // ───────────────── summary 属性 ─────────────────

    @Test
    fun `summary produces readable config string`() {
        val preset = MetronomePreset("测试", 120, 4, Subdivision.EIGHTH)
        assertEquals("120 · 4/4 · 八分音符", preset.summary)
    }

    @Test
    fun `summary with triplet subdivision`() {
        val preset = MetronomePreset("三连", 90, 3, Subdivision.TRIPLET)
        assertEquals("90 · 3/4 · 三连音", preset.summary)
    }

    // ───────────────── defaults 工厂 ─────────────────

    @Test
    fun `defaults returns non-empty list of valid presets`() {
        val defaults = MetronomePreset.defaults()
        assertTrue(defaults.isNotEmpty())
        defaults.forEach { p ->
            assertTrue(p.bpm in MetronomePreset.MIN_BPM..MetronomePreset.MAX_BPM)
            assertTrue(p.beatsPerMeasure in MetronomePreset.MIN_BEATS..MetronomePreset.MAX_BEATS)
            assertTrue(p.name.isNotBlank())
        }
    }

    @Test
    fun `defaults have unique names`() {
        val defaults = MetronomePreset.defaults()
        val names = defaults.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    // ───────────────── JSON 序列化往返 ─────────────────

    @Test
    fun `empty store serializes to empty array`() {
        assertEquals("[]", store.toJson())
    }

    @Test
    fun `json round-trip preserves all presets`() {
        store.save(MetronomePreset("哈农", 80, 4, Subdivision.QUARTER))
        store.save(MetronomePreset("音阶", 100, 4, Subdivision.EIGHTH))
        store.save(MetronomePreset("三连", 90, 3, Subdivision.TRIPLET))
        val json = store.toJson()

        val store2 = MetronomePresetStore()
        val count = store2.fromJson(json)
        assertEquals(3, count)
        assertEquals(3, store2.size)

        val preset = store2.find("音阶")
        assertNotNull(preset)
        assertEquals(100, preset!!.bpm)
        assertEquals(4, preset.beatsPerMeasure)
        assertEquals(Subdivision.EIGHTH, preset.subdivision)
    }

    @Test
    fun `json round-trip preserves all six subdivisions`() {
        Subdivision.entries.forEachIndexed { i, sub ->
            store.save(MetronomePreset("P$i", 100, 4, sub))
        }
        val json = store.toJson()

        val store2 = MetronomePresetStore()
        store2.fromJson(json)
        Subdivision.entries.forEachIndexed { i, sub ->
            assertEquals(sub, store2.find("P$i")?.subdivision)
        }
    }

    @Test
    fun `fromJson replaces existing content`() {
        store.save(MetronomePreset("旧", 60, 4, Subdivision.QUARTER))
        store.fromJson("""[{"n":"新","b":120,"m":4,"s":"EIGHTH"}]""")
        assertEquals(1, store.size)
        assertFalse(store.exists("旧"))
        assertTrue(store.exists("新"))
    }

    @Test
    fun `fromJson with null returns zero`() {
        assertEquals(0, store.fromJson(null))
        assertEquals(0, store.size)
    }

    @Test
    fun `fromJson with blank string returns zero`() {
        assertEquals(0, store.fromJson("   "))
        assertEquals(0, store.size)
    }

    @Test
    fun `fromJson with empty array returns zero`() {
        assertEquals(0, store.fromJson("[]"))
        assertEquals(0, store.size)
    }

    @Test
    fun `fromJson with malformed json does not crash and clears store`() {
        store.save(MetronomePreset("A", 80, 4, Subdivision.QUARTER))
        val count = store.fromJson("not valid json {{{")
        // 不崩溃；清空旧内容（替换语义）
        assertEquals(0, store.size)
    }

    @Test
    fun `fromJson skips presets with invalid subdivision name`() {
        val json = """[
            {"n":"好","b":120,"m":4,"s":"QUARTER"},
            {"n":"坏","b":120,"m":4,"s":"NONEXISTENT"}
        ]"""
        val count = store.fromJson(json)
        assertEquals(1, count)
        assertTrue(store.exists("好"))
        assertFalse(store.exists("坏"))
    }

    @Test
    fun `fromJson skips presets with missing fields`() {
        val json = """[
            {"n":"好","b":120,"m":4,"s":"QUARTER"},
            {"b":120,"m":4,"s":"EIGHTH"},
            {"n":"无bpm","m":4,"s":"TRIPLET"}
        ]"""
        val count = store.fromJson(json)
        assertEquals(1, count)
        assertTrue(store.exists("好"))
    }

    @Test
    fun `fromJson skips presets with out-of-range bpm`() {
        val json = """[
            {"n":"好","b":120,"m":4,"s":"QUARTER"},
            {"n":"超界","b":9999,"m":4,"s":"EIGHTH"}
        ]"""
        val count = store.fromJson(json)
        assertEquals(1, count)
        assertTrue(store.exists("好"))
        assertFalse(store.exists("超界"))
    }

    @Test
    fun `fromJson skips duplicate names keeping first`() {
        val json = """[
            {"n":"重复","b":80,"m":4,"s":"QUARTER"},
            {"n":"重复","b":120,"m":4,"s":"EIGHTH"}
        ]"""
        val count = store.fromJson(json)
        assertEquals(1, count)
        assertEquals(80, store.find("重复")?.bpm)
    }

    // ───────────────── JSON 特殊字符 ─────────────────

    @Test
    fun `json handles preset name with quotes`() {
        store.save(MetronomePreset("含\"引号\"", 80, 4, Subdivision.QUARTER))
        val json = store.toJson()
        val store2 = MetronomePresetStore()
        store2.fromJson(json)
        assertEquals("含\"引号\"", store2.find("含\"引号\"")?.name)
    }

    @Test
    fun `json handles preset name with backslash`() {
        store.save(MetronomePreset("反\\斜杠", 80, 4, Subdivision.QUARTER))
        val json = store.toJson()
        val store2 = MetronomePresetStore()
        store2.fromJson(json)
        assertEquals("反\\斜杠", store2.find("反\\斜杠")?.name)
    }

    @Test
    fun `json handles preset name with newline`() {
        store.save(MetronomePreset("换\n行", 80, 4, Subdivision.QUARTER))
        val json = store.toJson()
        val store2 = MetronomePresetStore()
        store2.fromJson(json)
        assertEquals("换\n行", store2.find("换\n行")?.name)
    }

    @Test
    fun `json handles preset name with special chars`() {
        val name = "节拍器/2.0 ✨ 🎹"
        store.save(MetronomePreset(name, 120, 6, Subdivision.SEXTUPLET))
        val json = store.toJson()
        val store2 = MetronomePresetStore()
        store2.fromJson(json)
        assertEquals(name, store2.find(name)?.name)
        assertEquals(120, store2.find(name)?.bpm)
        assertEquals(6, store2.find(name)?.beatsPerMeasure)
        assertEquals(Subdivision.SEXTUPLET, store2.find(name)?.subdivision)
    }

    @Test
    fun `json output contains expected field keys`() {
        store.save(MetronomePreset("A", 120, 4, Subdivision.EIGHTH))
        val json = store.toJson()
        assertTrue(json.contains("\"n\""))
        assertTrue(json.contains("\"b\""))
        assertTrue(json.contains("\"m\""))
        assertTrue(json.contains("\"s\""))
    }

    // ───────────────── data class 不变式 ─────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `preset rejects bpm below minimum`() {
        MetronomePreset("X", MetronomePreset.MIN_BPM - 1, 4, Subdivision.QUARTER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preset rejects bpm above maximum`() {
        MetronomePreset("X", MetronomePreset.MAX_BPM + 1, 4, Subdivision.QUARTER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preset rejects beats below minimum`() {
        MetronomePreset("X", 120, 0, Subdivision.QUARTER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preset rejects beats above maximum`() {
        MetronomePreset("X", 120, MetronomePreset.MAX_BEATS + 1, Subdivision.QUARTER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `preset rejects blank name`() {
        MetronomePreset("", 120, 4, Subdivision.QUARTER)
    }
}
