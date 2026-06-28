package com.pianocompanion.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GoalEditor] 单元测试。
 *
 * 覆盖：目标校验、序列化/反序列化往返、增删改操作、预设应用、建议值、格式化/解析。
 */
class GoalEditorTest {

    // ══════════════════════════════════════════════════════════════════
    //  校验 (validateTarget / isValidGoal)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `accuracy target valid in range 0 to 1`() {
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 0.0) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 0.5) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 0.85) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 1.0) is GoalValidation.Valid)
    }

    @Test
    fun `accuracy target invalid outside range`() {
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, -0.1) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 1.01) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, 1.5) is GoalValidation.Invalid)
    }

    @Test
    fun `non-accuracy target valid when positive`() {
        assertTrue(GoalEditor.validateTarget(GoalMetric.PRACTICE_TIME, 30.0) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.SESSION_COUNT, 1.0) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.NOTES_PLAYED, 500.0) is GoalValidation.Valid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.UNIQUE_PIECES, 3.0) is GoalValidation.Valid)
    }

    @Test
    fun `non-accuracy target invalid when zero or negative`() {
        assertTrue(GoalEditor.validateTarget(GoalMetric.PRACTICE_TIME, 0.0) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.PRACTICE_TIME, -5.0) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.SESSION_COUNT, -1.0) is GoalValidation.Invalid)
    }

    @Test
    fun `NaN and infinite targets are invalid`() {
        assertTrue(GoalEditor.validateTarget(GoalMetric.PRACTICE_TIME, Double.NaN) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.ACCURACY, Double.POSITIVE_INFINITY) is GoalValidation.Invalid)
        assertTrue(GoalEditor.validateTarget(GoalMetric.NOTES_PLAYED, Double.NEGATIVE_INFINITY) is GoalValidation.Invalid)
    }

    @Test
    fun `invalid validation contains reason message`() {
        val result = GoalEditor.validateTarget(GoalMetric.ACCURACY, 1.5)
        assertTrue(result is GoalValidation.Invalid)
        assertTrue((result as GoalValidation.Invalid).reason.isNotEmpty())
    }

    @Test
    fun `isValidGoal combines validation`() {
        assertTrue(GoalEditor.isValidGoal(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85))
        assertFalse(GoalEditor.isValidGoal(GoalMetric.ACCURACY, GoalPeriod.DAILY, 1.5))
        assertFalse(GoalEditor.isValidGoal(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 0.0))
        assertTrue(GoalEditor.isValidGoal(GoalMetric.SESSION_COUNT, GoalPeriod.WEEKLY, 5.0))
    }

    // ══════════════════════════════════════════════════════════════════
    //  序列化 / 反序列化 (serialize / deserialize)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `serialize single goal produces correct format`() {
        val goal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        assertEquals("DAILY_PRACTICE_TIME:30.0", GoalEditor.serializeGoals(listOf(goal)))
    }

    @Test
    fun `serialize multiple goals comma-separated`() {
        val goals = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0),
            GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85),
            GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.WEEKLY, 2000.0)
        )
        assertEquals(
            "DAILY_PRACTICE_TIME:30.0,DAILY_ACCURACY:0.85,WEEKLY_NOTES_PLAYED:2000.0",
            GoalEditor.serializeGoals(goals)
        )
    }

    @Test
    fun `serialize empty list returns empty string`() {
        assertEquals("", GoalEditor.serializeGoals(emptyList()))
    }

    @Test
    fun `deserialize null returns empty list`() {
        assertEquals(emptyList<GoalDefinition>(), GoalEditor.deserializeGoals(null))
    }

    @Test
    fun `deserialize blank string returns empty list`() {
        assertEquals(emptyList<GoalDefinition>(), GoalEditor.deserializeGoals(""))
        assertEquals(emptyList<GoalDefinition>(), GoalEditor.deserializeGoals("   "))
    }

    @Test
    fun `deserialize single goal`() {
        val goals = GoalEditor.deserializeGoals("DAILY_PRACTICE_TIME:30.0")
        assertEquals(1, goals.size)
        assertEquals(GoalMetric.PRACTICE_TIME, goals[0].metric)
        assertEquals(GoalPeriod.DAILY, goals[0].period)
        assertEquals(30.0, goals[0].target, 0.001)
    }

    @Test
    fun `deserialize multiple goals`() {
        val goals = GoalEditor.deserializeGoals("DAILY_PRACTICE_TIME:30.0,DAILY_ACCURACY:0.85,WEEKLY_NOTES_PLAYED:2000.0")
        assertEquals(3, goals.size)
        assertEquals(GoalMetric.ACCURACY, goals[1].metric)
        assertEquals(0.85, goals[1].target, 0.001)
        assertEquals(GoalPeriod.WEEKLY, goals[2].period)
    }

    @Test
    fun `round trip serialize then deserialize preserves goals`() {
        val original = listOf(
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0),
            GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0),
            GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85),
            GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.WEEKLY, 150.0),
            GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.WEEKLY, 2000.0),
            GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.WEEKLY, 5.0)
        )
        val serialized = GoalEditor.serializeGoals(original)
        val deserialized = GoalEditor.deserializeGoals(serialized)
        assertEquals(original.size, deserialized.size)
        original.zip(deserialized).forEach { (expected, actual) ->
            assertEquals(expected.key, actual.key)
            assertEquals(expected.target, actual.target, 0.001)
        }
    }

    @Test
    fun `deserialize skips malformed entries`() {
        // 完全无冒号的条目被跳过
        val goals = GoalEditor.deserializeGoals("DAILY_PRACTICE_TIME:30.0,GARBAGE,DAILY_ACCURACY:0.85")
        assertEquals(2, goals.size)
    }

    @Test
    fun `deserialize skips entries with invalid target`() {
        val goals = GoalEditor.deserializeGoals("DAILY_PRACTICE_TIME:abc,DAILY_ACCURACY:0.85")
        assertEquals(1, goals.size)
        assertEquals(GoalMetric.ACCURACY, goals[0].metric)
    }

    @Test
    fun `deserialize skips entries with unknown period`() {
        val goals = GoalEditor.deserializeGoals("YEARLY_PRACTICE_TIME:30.0,DAILY_ACCURACY:0.85")
        assertEquals(1, goals.size)
    }

    @Test
    fun `deserialize skips entries with unknown metric`() {
        val goals = GoalEditor.deserializeGoals("DAILY_UNKNOWN:30.0,DAILY_ACCURACY:0.85")
        assertEquals(1, goals.size)
    }

    @Test
    fun `deserialize handles whitespace in entries`() {
        val goals = GoalEditor.deserializeGoals(" DAILY_PRACTICE_TIME : 30.0 , DAILY_ACCURACY : 0.85 ")
        assertEquals(2, goals.size)
    }

    // ══════════════════════════════════════════════════════════════════
    //  增删改 (addOrUpdateGoal / removeGoal / toggleGoal / isGoalEnabled)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `addOrUpdateGoal appends new goal`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        val newGoal = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val result = GoalEditor.addOrUpdateGoal(goals, newGoal)
        assertEquals(2, result.size)
        assertTrue(result.any { it.key == newGoal.key })
    }

    @Test
    fun `addOrUpdateGoal replaces existing goal by key`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        val updatedGoal = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 60.0)
        val result = GoalEditor.addOrUpdateGoal(goals, updatedGoal)
        assertEquals(1, result.size)
        assertEquals(60.0, result[0].target, 0.001)
    }

    @Test
    fun `addOrUpdateGoal on empty list returns single goal`() {
        val result = GoalEditor.addOrUpdateGoal(emptyList(), GoalDefinition(GoalMetric.SESSION_COUNT, GoalPeriod.DAILY, 2.0))
        assertEquals(1, result.size)
    }

    @Test
    fun `addOrUpdateGoal preserves other goals when updating`() {
        val g1 = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val g2 = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val g3 = GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.WEEKLY, 500.0)
        val goals = listOf(g1, g2, g3)
        val updated = GoalEditor.addOrUpdateGoal(goals, g2.copy(target = 0.90))
        assertEquals(3, result_size_check(updated))
        val acc = updated.find { it.key == g2.key }!!
        assertEquals(0.90, acc.target, 0.001)
    }

    private fun result_size_check(list: List<GoalDefinition>): Int = list.size

    @Test
    fun `removeGoal removes matching key`() {
        val g1 = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val g2 = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val goals = listOf(g1, g2)
        val result = GoalEditor.removeGoal(goals, g1.key)
        assertEquals(1, result.size)
        assertEquals(g2.key, result[0].key)
    }

    @Test
    fun `removeGoal on non-existent key returns same list`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        val result = GoalEditor.removeGoal(goals, "NON_EXISTENT")
        assertEquals(1, result.size)
    }

    @Test
    fun `removeGoal on empty list returns empty list`() {
        val result = GoalEditor.removeGoal(emptyList(), "DAILY_PRACTICE_TIME")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toggleGoal enables by adding when true`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        val newGoal = GoalDefinition(GoalMetric.ACCURACY, GoalPeriod.DAILY, 0.85)
        val result = GoalEditor.toggleGoal(goals, newGoal, enabled = true)
        assertEquals(2, result.size)
    }

    @Test
    fun `toggleGoal disables by removing when false`() {
        val g1 = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val goals = listOf(g1)
        val result = GoalEditor.toggleGoal(goals, g1, enabled = false)
        assertEquals(0, result.size)
    }

    @Test
    fun `toggleGoal enabling existing goal updates target`() {
        val existing = GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0)
        val goals = listOf(existing)
        val updated = existing.copy(target = 45.0)
        val result = GoalEditor.toggleGoal(goals, updated, enabled = true)
        assertEquals(1, result.size)
        assertEquals(45.0, result[0].target, 0.001)
    }

    @Test
    fun `isGoalEnabled returns true for existing goal`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        assertTrue(GoalEditor.isGoalEnabled(goals, "DAILY_PRACTICE_TIME"))
    }

    @Test
    fun `isGoalEnabled returns false for missing goal`() {
        val goals = listOf(GoalDefinition(GoalMetric.PRACTICE_TIME, GoalPeriod.DAILY, 30.0))
        assertFalse(GoalEditor.isGoalEnabled(goals, "DAILY_ACCURACY"))
        assertFalse(GoalEditor.isGoalEnabled(emptyList(), "DAILY_PRACTICE_TIME"))
    }

    // ══════════════════════════════════════════════════════════════════
    //  预设 (applyPreset)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `applyPreset returns preset goals for known name`() {
        val easy = GoalEditor.applyPreset("轻松")
        assertEquals(2, easy.size)
        assertTrue(easy.any { it.metric == GoalMetric.PRACTICE_TIME && it.period == GoalPeriod.DAILY })

        val moderate = GoalEditor.applyPreset("适中")
        assertEquals(4, moderate.size)

        val challenge = GoalEditor.applyPreset("挑战")
        assertEquals(5, challenge.size)
    }

    @Test
    fun `applyPreset returns default goals for unknown name`() {
        val result = GoalEditor.applyPreset("不存在的预设")
        val defaults = GoalTracker.defaultGoals()
        assertEquals(defaults.size, result.size)
    }

    @Test
    fun `applyPreset matches GoalTracker presets exactly`() {
        GoalTracker.presets().forEach { (name, expectedGoals) ->
            val actual = GoalEditor.applyPreset(name)
            assertEquals(expectedGoals.size, actual.size)
            expectedGoals.zip(actual).forEach { (expected, actualGoal) ->
                assertEquals(expected.key, actualGoal.key)
                assertEquals(expected.target, actualGoal.target, 0.001)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  建议值 (suggestedTargets / allAvailableGoals)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `suggestedTargets returns non-empty ascending list for each metric`() {
        for (metric in GoalMetric.values()) {
            val targets = GoalEditor.suggestedTargets(metric)
            assertTrue("$metric should have suggested targets", targets.isNotEmpty())
            // 验证递增
            for (i in 1 until targets.size) {
                assertTrue(
                    "$metric suggestions should be ascending at index $i",
                    targets[i] >= targets[i - 1]
                )
            }
        }
    }

    @Test
    fun `suggestedTargets for accuracy is in valid range`() {
        val targets = GoalEditor.suggestedTargets(GoalMetric.ACCURACY)
        targets.forEach { target ->
            assertTrue("Accuracy suggestion $target should be 0..1", target in 0.0..1.0)
        }
    }

    @Test
    fun `suggestedTargets for non-accuracy metrics are positive`() {
        for (metric in GoalMetric.values()) {
            if (metric == GoalMetric.ACCURACY) continue
            val targets = GoalEditor.suggestedTargets(metric)
            targets.forEach { target ->
                assertTrue("$metric suggestion $target should be positive", target > 0)
            }
        }
    }

    @Test
    fun `allAvailableGoals returns period times metric combinations`() {
        val all = GoalEditor.allAvailableGoals()
        val expectedCount = GoalPeriod.values().size * GoalMetric.values().size
        assertEquals(expectedCount, all.size)
    }

    @Test
    fun `allAvailableGoals covers every combination`() {
        val all = GoalEditor.allAvailableGoals()
        for (period in GoalPeriod.values()) {
            for (metric in GoalMetric.values()) {
                val key = "${period.name}_${metric.name}"
                assertTrue("Missing combination $key", all.any { it.key == key })
            }
        }
    }

    @Test
    fun `allAvailableGoals produces valid targets`() {
        val all = GoalEditor.allAvailableGoals()
        all.forEach { goal ->
            assertTrue(
                "Goal ${goal.key} should be valid",
                GoalEditor.validateTarget(goal.metric, goal.target) is GoalValidation.Valid
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  格式化 / 解析 (formatTargetForInput / parseInputToTarget)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `formatTargetForInput converts accuracy to percentage`() {
        assertEquals("85", GoalEditor.formatTargetForInput(GoalMetric.ACCURACY, 0.85))
        assertEquals("0", GoalEditor.formatTargetForInput(GoalMetric.ACCURACY, 0.0))
        assertEquals("100", GoalEditor.formatTargetForInput(GoalMetric.ACCURACY, 1.0))
    }

    @Test
    fun `formatTargetForInput shows integer for non-accuracy metrics`() {
        assertEquals("30", GoalEditor.formatTargetForInput(GoalMetric.PRACTICE_TIME, 30.0))
        assertEquals("2", GoalEditor.formatTargetForInput(GoalMetric.SESSION_COUNT, 2.0))
        assertEquals("500", GoalEditor.formatTargetForInput(GoalMetric.NOTES_PLAYED, 500.0))
        assertEquals("3", GoalEditor.formatTargetForInput(GoalMetric.UNIQUE_PIECES, 3.0))
    }

    @Test
    fun `parseInputToTarget converts accuracy percentage to fraction`() {
        assertEquals(0.85, GoalEditor.parseInputToTarget(GoalMetric.ACCURACY, "85")!!, 0.001)
        assertEquals(0.5, GoalEditor.parseInputToTarget(GoalMetric.ACCURACY, "50")!!, 0.001)
        assertEquals(1.0, GoalEditor.parseInputToTarget(GoalMetric.ACCURACY, "100")!!, 0.001)
    }

    @Test
    fun `parseInputToTarget returns direct value for non-accuracy metrics`() {
        assertEquals(30.0, GoalEditor.parseInputToTarget(GoalMetric.PRACTICE_TIME, "30")!!, 0.001)
        assertEquals(2.0, GoalEditor.parseInputToTarget(GoalMetric.SESSION_COUNT, "2")!!, 0.001)
        assertEquals(500.0, GoalEditor.parseInputToTarget(GoalMetric.NOTES_PLAYED, "500")!!, 0.001)
    }

    @Test
    fun `parseInputToTarget returns null for invalid input`() {
        assertNull(GoalEditor.parseInputToTarget(GoalMetric.PRACTICE_TIME, "abc"))
        assertNull(GoalEditor.parseInputToTarget(GoalMetric.ACCURACY, "xyz"))
    }

    @Test
    fun `parseInputToTarget handles whitespace`() {
        assertEquals(30.0, GoalEditor.parseInputToTarget(GoalMetric.PRACTICE_TIME, "  30  ")!!, 0.001)
    }

    @Test
    fun `format then parse round trip for accuracy`() {
        val original = 0.85
        val formatted = GoalEditor.formatTargetForInput(GoalMetric.ACCURACY, original)
        val parsed = GoalEditor.parseInputToTarget(GoalMetric.ACCURACY, formatted)
        assertEquals(original, parsed!!, 0.001)
    }

    @Test
    fun `format then parse round trip for non-accuracy metric`() {
        val original = 45.0
        val formatted = GoalEditor.formatTargetForInput(GoalMetric.PRACTICE_TIME, original)
        val parsed = GoalEditor.parseInputToTarget(GoalMetric.PRACTICE_TIME, formatted)
        assertEquals(original, parsed!!, 0.001)
    }

    // ══════════════════════════════════════════════════════════════════
    //  端到端集成
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `end to end edit workflow`() {
        // 1. 从默认目标开始
        var goals = GoalTracker.defaultGoals()
        val initialCount = goals.size
        assertTrue(initialCount > 0)

        // 2. 添加一个新目标
        val newGoal = GoalDefinition(GoalMetric.UNIQUE_PIECES, GoalPeriod.DAILY, 3.0)
        goals = GoalEditor.addOrUpdateGoal(goals, newGoal)
        assertEquals(initialCount + 1, goals.size)
        assertTrue(GoalEditor.isGoalEnabled(goals, newGoal.key))

        // 3. 更新一个已有目标的值
        val existingKey = goals.first().key
        val existingMetric = goals.first().metric
        val existingPeriod = goals.first().period
        goals = GoalEditor.addOrUpdateGoal(goals, GoalDefinition(existingMetric, existingPeriod, 999.0))
        val updated = goals.find { it.key == existingKey }!!
        assertEquals(999.0, updated.target, 0.001)

        // 4. 移除一个目标
        goals = GoalEditor.removeGoal(goals, newGoal.key)
        assertFalse(GoalEditor.isGoalEnabled(goals, newGoal.key))

        // 5. 序列化/反序列化后状态保持
        val serialized = GoalEditor.serializeGoals(goals)
        val restored = GoalEditor.deserializeGoals(serialized)
        assertEquals(goals.size, restored.size)
    }

    @Test
    fun `end to end preset apply then customize`() {
        // 应用预设
        var goals = GoalEditor.applyPreset("挑战")
        assertEquals(5, goals.size)

        // 自定义修改其中一个目标的值
        val toModify = goals.first()
        goals = GoalEditor.addOrUpdateGoal(goals, toModify.copy(target = 999.0))
        assertEquals(5, goals.size) // 数量不变
        val modified = goals.find { it.key == toModify.key }!!
        assertEquals(999.0, modified.target, 0.001)

        // 添加额外目标
        goals = GoalEditor.addOrUpdateGoal(
            goals,
            GoalDefinition(GoalMetric.NOTES_PLAYED, GoalPeriod.DAILY, 500.0)
        )
        assertEquals(6, goals.size)
    }

    @Test
    fun `empty goals serialize deserialize round trip`() {
        val serialized = GoalEditor.serializeGoals(emptyList())
        assertEquals("", serialized)
        val restored = GoalEditor.deserializeGoals(serialized)
        assertTrue(restored.isEmpty())
    }
}
