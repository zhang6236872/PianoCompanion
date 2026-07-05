package com.pianocompanion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [FavoriteStore] 单元测试 — 纯 Kotlin，无 Android 依赖。
 */
class FavoriteStoreTest {

    private lateinit var store: FavoriteStore

    @Before
    fun setUp() {
        store = FavoriteStore()
    }

    // ───────────────── 基本增删改查 ─────────────────

    @Test
    fun `empty store has zero favorites`() {
        assertEquals(0, store.size)
        assertTrue(store.isEmpty())
        assertFalse(store.isNotEmpty())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `add returns true for new key`() {
        assertTrue(store.add("ode_to_joy"))
        assertEquals(1, store.size)
        assertTrue(store.isFavorite("ode_to_joy"))
    }

    @Test
    fun `add returns false for duplicate key`() {
        store.add("ode_to_joy")
        assertFalse(store.add("ode_to_joy"))
        assertEquals(1, store.size)
    }

    @Test
    fun `remove returns true for existing key`() {
        store.add("ode_to_joy")
        assertTrue(store.remove("ode_to_joy"))
        assertEquals(0, store.size)
        assertFalse(store.isFavorite("ode_to_joy"))
    }

    @Test
    fun `remove returns false for missing key`() {
        assertFalse(store.remove("ode_to_joy"))
        assertEquals(0, store.size)
    }

    @Test
    fun `isFavorite returns false for unknown key`() {
        assertFalse(store.isFavorite("unknown"))
    }

    @Test
    fun `multiple distinct keys can be added`() {
        store.add("a")
        store.add("b")
        store.add("c")
        assertEquals(3, store.size)
        assertTrue(store.isFavorite("a"))
        assertTrue(store.isFavorite("b"))
        assertTrue(store.isFavorite("c"))
    }

    @Test
    fun `list returns insertion-order snapshot`() {
        store.add("c")
        store.add("a")
        store.add("b")
        assertEquals(listOf("c", "a", "b"), store.list())
    }

    @Test
    fun `list snapshot is decoupled from store mutations`() {
        store.add("a")
        val snapshot = store.list()
        store.add("b")
        assertEquals(listOf("a"), snapshot)
        assertEquals(listOf("a", "b"), store.list())
    }

    // ───────────────── toggle ─────────────────

    @Test
    fun `toggle on adds favorite and returns true`() {
        assertTrue(store.toggle("ode_to_joy"))
        assertTrue(store.isFavorite("ode_to_joy"))
    }

    @Test
    fun `toggle off removes favorite and returns false`() {
        store.add("ode_to_joy")
        assertFalse(store.toggle("ode_to_joy"))
        assertFalse(store.isFavorite("ode_to_joy"))
    }

    @Test
    fun `toggle twice returns to original state`() {
        val first = store.toggle("x")
        val second = store.toggle("x")
        assertTrue(first)
        assertFalse(second)
        assertFalse(store.isFavorite("x"))
        assertEquals(0, store.size)
    }

    // ───────────────── clear & replaceAll ─────────────────

    @Test
    fun `clear removes all favorites`() {
        store.add("a")
        store.add("b")
        store.clear()
        assertEquals(0, store.size)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `replaceAll sets all favorites`() {
        store.replaceAll(listOf("a", "b", "c"))
        assertEquals(3, store.size)
        assertTrue(store.isFavorite("b"))
    }

    @Test
    fun `replaceAll replaces previous content`() {
        store.add("old")
        store.replaceAll(listOf("new"))
        assertFalse(store.isFavorite("old"))
        assertTrue(store.isFavorite("new"))
        assertEquals(1, store.size)
    }

    @Test
    fun `replaceAll with empty clears store`() {
        store.add("a")
        store.replaceAll(emptyList())
        assertTrue(store.isEmpty())
    }

    // ───────────────── sortByFavorites ─────────────────

    @Test
    fun `sortByFavorites puts favorites first preserving order`() {
        store.add("c")
        store.add("a")
        val sorted = store.sortByFavorites(listOf("a", "b", "c", "d"))
        // favorites c,a first (in input order: a appears before c → a, c), then b,d
        assertEquals(listOf("a", "c", "b", "d"), sorted)
    }

    @Test
    fun `sortByFavorites with no favorites preserves original order`() {
        val input = listOf("x", "y", "z")
        assertEquals(input, store.sortByFavorites(input))
    }

    @Test
    fun `sortByFavorites with all favorites preserves original order`() {
        store.add("x")
        store.add("y")
        store.add("z")
        val input = listOf("x", "y", "z")
        assertEquals(input, store.sortByFavorites(input))
    }

    @Test
    fun `sortByFavorites is stable for non-favorites`() {
        store.add("fav")
        val sorted = store.sortByFavorites(listOf("n3", "fav", "n1", "n2"))
        assertEquals(listOf("fav", "n3", "n1", "n2"), sorted)
    }

    @Test
    fun `sortByFavorites deduplicates keys`() {
        store.add("fav")
        val sorted = store.sortByFavorites(listOf("fav", "x", "fav", "y"))
        assertEquals(listOf("fav", "x", "y"), sorted)
    }

    @Test
    fun `sortByFavorites empty input returns empty`() {
        store.add("fav")
        assertTrue(store.sortByFavorites(emptyList()).isEmpty())
    }

    // ───────────────── filterToFavorites ─────────────────

    @Test
    fun `filterToFavorites keeps only favorites in order`() {
        store.add("b")
        store.add("d")
        val filtered = store.filterToFavorites(listOf("a", "b", "c", "d", "e"))
        assertEquals(listOf("b", "d"), filtered)
    }

    @Test
    fun `filterToFavorites returns empty when no favorites match`() {
        store.add("z")
        assertTrue(store.filterToFavorites(listOf("a", "b")).isEmpty())
    }

    @Test
    fun `filterToFavorites deduplicates`() {
        store.add("a")
        val filtered = store.filterToFavorites(listOf("a", "a", "b", "a"))
        assertEquals(listOf("a"), filtered)
    }

    // ───────────────── key helpers ─────────────────

    @Test
    fun `keyForImported adds prefix`() {
        assertEquals("imported:欢乐颂.xml", FavoriteStore.keyForImported("欢乐颂.xml"))
    }

    @Test
    fun `keyForBuiltIn uses id directly`() {
        assertEquals("ode_to_joy", FavoriteStore.keyForBuiltIn("ode_to_joy"))
    }

    @Test
    fun `imported key does not collide with built-in id`() {
        val builtInKey = FavoriteStore.keyForBuiltIn("ode_to_joy")
        val importedKey = FavoriteStore.keyForImported("ode_to_joy.xml")
        store.add(builtInKey)
        store.add(importedKey)
        assertEquals(2, store.size)
        assertTrue(store.isFavorite(builtInKey))
        assertTrue(store.isFavorite(importedKey))
    }

    // ───────────────── JSON 序列化 ─────────────────

    @Test
    fun `toJson empty store returns empty array`() {
        assertEquals("[]", store.toJson())
    }

    @Test
    fun `toJson single element`() {
        store.add("ode_to_joy")
        assertEquals("[\"ode_to_joy\"]", store.toJson())
    }

    @Test
    fun `toJson multiple elements`() {
        store.add("a")
        store.add("b")
        assertEquals("[\"a\",\"b\"]", store.toJson())
    }

    @Test
    fun `toJson escapes special characters`() {
        store.add("he said \"hi\"\\done")
        val json = store.toJson()
        assertEquals("[\"he said \\\"hi\\\"\\\\done\"]", json)
        // round-trip
        store.clear()
        store.fromJson(json)
        assertEquals(listOf("he said \"hi\"\\done"), store.list())
    }

    @Test
    fun `fromJson null returns zero and empty`() {
        assertEquals(0, store.fromJson(null))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `fromJson blank returns zero`() {
        assertEquals(0, store.fromJson("   "))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `fromJson empty array returns zero`() {
        assertEquals(0, store.fromJson("[]"))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `fromJson null literal returns zero`() {
        assertEquals(0, store.fromJson("null"))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `fromJson round-trip multiple elements`() {
        store.add("ode_to_joy")
        store.add("imported:小夜曲.xml")
        store.add("twinkle")
        val json = store.toJson()

        val restored = FavoriteStore()
        val count = restored.fromJson(json)
        assertEquals(3, count)
        assertEquals(store.list(), restored.list())
    }

    @Test
    fun `fromJson with unicode keys`() {
        val json = "[\"欢乐颂\",\"贝多芬第九\"]"
        val count = store.fromJson(json)
        assertEquals(2, count)
        assertTrue(store.isFavorite("欢乐颂"))
        assertTrue(store.isFavorite("贝多芬第九"))
    }

    @Test
    fun `fromJson replaces previous content`() {
        store.add("old")
        store.fromJson("[\"new1\",\"new2\"]")
        assertFalse(store.isFavorite("old"))
        assertTrue(store.isFavorite("new1"))
        assertEquals(2, store.size)
    }

    @Test
    fun `fromJson invalid json clears store`() {
        store.add("keep")
        val count = store.fromJson("not json at all")
        assertEquals(0, count)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `fromJson handles escaped characters`() {
        val json = "[\"a\\\"b\",\"c\\\\d\",\"e\\nf\"]"
        store.fromJson(json)
        assertTrue(store.isFavorite("a\"b"))
        assertTrue(store.isFavorite("c\\d"))
        assertTrue(store.isFavorite("e\nf"))
    }

    @Test
    fun `fromJson handles whitespace between elements`() {
        store.fromJson("[ \"a\" , \"b\" , \"c\" ]")
        assertEquals(3, store.size)
        assertTrue(store.isFavorite("b"))
    }

    @Test
    fun `fromJson handles forward slash escape`() {
        store.fromJson("[\"a/b\"]")
        assertTrue(store.isFavorite("a/b"))
    }

    @Test
    fun `full round trip preserves insertion order`() {
        store.add("zzz")
        store.add("aaa")
        store.add("mmm")
        val json = store.toJson()
        val restored = FavoriteStore()
        restored.fromJson(json)
        assertEquals(listOf("zzz", "aaa", "mmm"), restored.list())
    }
}
