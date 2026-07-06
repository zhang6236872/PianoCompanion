package com.pianocompanion.musicalterms

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * 音乐术语库（MusicalTermsLibrary）单元测试。
 *
 * 验证术语库的完整性、去重、分类、难度筛选等核心特性。
 */
class MusicalTermsLibraryTest {

    @Test
    fun `术语库非空`() {
        assertTrue(MusicalTermsLibrary.ALL.isNotEmpty())
        assertTrue(MusicalTermsLibrary.size > 50)
    }

    @Test
    fun `术语无重复`() {
        val lowercased = MusicalTermsLibrary.ALL.map { it.term.lowercase() }
        val unique = lowercased.toSet()
        assertEquals(lowercased.size, unique.size)
    }

    @Test
    fun `所有类别都有术语`() {
        for (category in TermCategory.ALL) {
            val terms = MusicalTermsLibrary.byCategory(category)
            assertTrue("类别 ${category.displayName} 应有术语", terms.isNotEmpty())
        }
    }

    @Test
    fun `所有难度都有术语`() {
        for (difficulty in TermDifficulty.ALL) {
            val terms = MusicalTermsLibrary.upToDifficulty(difficulty)
            assertTrue("难度 ${difficulty.displayName} 应有术语", terms.isNotEmpty())
        }
    }

    @Test
    fun `初级术语是中级术语的子集`() {
        val beginner = MusicalTermsLibrary.upToDifficulty(TermDifficulty.BEGINNER).toSet()
        val intermediate = MusicalTermsLibrary.upToDifficulty(TermDifficulty.INTERMEDIATE).toSet()
        assertTrue(intermediate.containsAll(beginner))
    }

    @Test
    fun `中级术语是高级术语的子集`() {
        val intermediate = MusicalTermsLibrary.upToDifficulty(TermDifficulty.INTERMEDIATE).toSet()
        val advanced = MusicalTermsLibrary.upToDifficulty(TermDifficulty.ADVANCED).toSet()
        assertTrue(advanced.containsAll(intermediate))
    }

    @Test
    fun `高级包含全部术语`() {
        val advanced = MusicalTermsLibrary.upToDifficulty(TermDifficulty.ADVANCED)
        assertEquals(MusicalTermsLibrary.size, advanced.size)
    }

    @Test
    fun `byCategory 只返回对应类别`() {
        for (category in TermCategory.ALL) {
            val terms = MusicalTermsLibrary.byCategory(category)
            assertTrue(terms.all { it.category == category })
        }
    }

    @Test
    fun `filter category null 返回所有该难度术语`() {
        val all = MusicalTermsLibrary.filter(null, TermDifficulty.ADVANCED)
        assertEquals(MusicalTermsLibrary.size, all.size)
    }

    @Test
    fun `filter 正确组合类别和难度`() {
        val result = MusicalTermsLibrary.filter(TermCategory.TEMPO, TermDifficulty.BEGINNER)
        assertTrue(result.all { it.category == TermCategory.TEMPO })
        assertTrue(result.all { it.difficulty == TermDifficulty.BEGINNER })
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `filter 初级只含 BEGINNER 难度`() {
        for (category in TermCategory.ALL) {
            val result = MusicalTermsLibrary.filter(category, TermDifficulty.BEGINNER)
            assertTrue(result.all { it.difficulty == TermDifficulty.BEGINNER })
        }
    }

    @Test
    fun `filter 中级含 BEGINNER 和 INTERMEDIATE`() {
        for (category in TermCategory.ALL) {
            val result = MusicalTermsLibrary.filter(category, TermDifficulty.INTERMEDIATE)
            val validDifficulties = setOf(TermDifficulty.BEGINNER, TermDifficulty.INTERMEDIATE)
            assertTrue(result.all { it.difficulty in validDifficulties })
        }
    }

    @Test
    fun `速度术语有 BPM 范围`() {
        val tempoTerms = MusicalTermsLibrary.byCategory(TermCategory.TEMPO)
            .filter { it.bpmRange != null }
        assertTrue("至少有部分速度术语有 BPM 范围", tempoTerms.isNotEmpty())
    }

    @Test
    fun `力度术语有缩写`() {
        val dynamicsWithAbbr = MusicalTermsLibrary.byCategory(TermCategory.DYNAMICS)
            .filter { it.abbreviation != null }
        assertTrue("至少有部分力度术语有缩写", dynamicsWithAbbr.isNotEmpty())
    }

    @Test
    fun `displayLabel 有缩写时包含缩写`() {
        val term = MusicalTerm("test", "测试", TermCategory.TEMPO, "1-2", TermDifficulty.BEGINNER, "tst")
        assertEquals("test (tst)", term.displayLabel)
    }

    @Test
    fun `displayLabel 无缩写时只有术语`() {
        val term = MusicalTerm("test", "测试", TermCategory.TEMPO, null, TermDifficulty.BEGINNER)
        assertEquals("test", term.displayLabel)
    }

    @Test
    fun `countByCategoryAndDifficulty 返回所有组合`() {
        val counts = MusicalTermsLibrary.countByCategoryAndDifficulty()
        assertEquals(TermCategory.ALL.size * TermDifficulty.ALL.size, counts.size)
        // 每个组合应 >= 0
        counts.values.forEach { assertTrue(it >= 0) }
    }

    @Test
    fun `每个术语的 meaning 非空`() {
        MusicalTermsLibrary.ALL.forEach { term ->
            assertTrue("术语 ${term.term} 的 meaning 应非空", term.meaning.isNotBlank())
        }
    }

    @Test
    fun `每个术语的 term 非空`() {
        MusicalTermsLibrary.ALL.forEach { term ->
            assertTrue("术语文本应非空", term.term.isNotBlank())
        }
    }

    @Test
    fun `常见的 Allegro 存在`() {
        val allegro = MusicalTermsLibrary.ALL.find { it.term == "Allegro" }
        assertNotNull(allegro)
        assertEquals(TermCategory.TEMPO, allegro!!.category)
        assertTrue(allegro.meaning.contains("快板"))
    }

    @Test
    fun `常见的 forte 存在且有缩写 f`() {
        val forte = MusicalTermsLibrary.ALL.find { it.term == "forte" }
        assertNotNull(forte)
        assertEquals("f", forte!!.abbreviation)
    }

    @Test
    fun `常见的 legato 存在`() {
        val legato = MusicalTermsLibrary.ALL.find { it.term == "legato" }
        assertNotNull(legato)
        assertEquals(TermCategory.ARTICULATION, legato!!.category)
    }

    @Test
    fun `常见的 crescendo 存在且有缩写`() {
        val cresc = MusicalTermsLibrary.ALL.find { it.term == "crescendo" }
        assertNotNull(cresc)
        assertEquals("cresc.", cresc!!.abbreviation)
    }
}
