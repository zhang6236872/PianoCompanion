package com.pianocompanion.ui.practice

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [ScoreSelectionHolder] 单元测试 — 验证跨页面乐谱传递的存入/消费/清除语义。
 */
class ScoreSelectionHolderTest {

    @After
    fun cleanup() {
        ScoreSelectionHolder.clear()
    }

    private fun sampleScore(id: String) = Score(
        id = id,
        title = "测试乐谱 $id",
        composer = "测试",
        notes = listOf(
            ScoreNote(midiNumber = 60, noteName = "C4", startTime = 0, duration = 500)
        ),
        source = ScoreSource.GENERATED
    )

    @Test
    fun `初始状态 peek 与 consume 均返回 null`() {
        assertNull(ScoreSelectionHolder.peek())
        assertNull(ScoreSelectionHolder.consume())
    }

    @Test
    fun `set 后 peek 返回同一引用且不清除`() {
        val score = sampleScore("a")
        ScoreSelectionHolder.set(score)

        val peeked = ScoreSelectionHolder.peek()
        assertSame(score, peeked)
        // peek 不消费：再次 peek 仍可取到
        assertSame(score, ScoreSelectionHolder.peek())
    }

    @Test
    fun `consume 取出后清除`() {
        val score = sampleScore("a")
        ScoreSelectionHolder.set(score)

        val consumed = ScoreSelectionHolder.consume()
        assertSame(score, consumed)
        // 消费后 peek / consume 均为 null
        assertNull(ScoreSelectionHolder.peek())
        assertNull(ScoreSelectionHolder.consume())
    }

    @Test
    fun `二次 set 覆盖前一次`() {
        val first = sampleScore("first")
        val second = sampleScore("second")

        ScoreSelectionHolder.set(first)
        ScoreSelectionHolder.set(second)

        assertSame(second, ScoreSelectionHolder.consume())
    }

    @Test
    fun `clear 后 peek 返回 null`() {
        ScoreSelectionHolder.set(sampleScore("a"))
        ScoreSelectionHolder.clear()
        assertNull(ScoreSelectionHolder.peek())
        assertNull(ScoreSelectionHolder.consume())
    }

    @Test
    fun `对空 holder 调用 clear 无副作用`() {
        ScoreSelectionHolder.clear()
        ScoreSelectionHolder.clear()
        assertNull(ScoreSelectionHolder.peek())
    }

    @Test
    fun `consume 仅消费一次，后续返回 null`() {
        ScoreSelectionHolder.set(sampleScore("once"))
        ScoreSelectionHolder.consume()
        assertNull(ScoreSelectionHolder.consume())
        assertNull(ScoreSelectionHolder.peek())
    }
}
