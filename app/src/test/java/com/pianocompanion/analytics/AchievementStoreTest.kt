package com.pianocompanion.analytics

import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AchievementStore 单元测试：成就解锁状态持久化与差分计算。
 */
class AchievementStoreTest {

    // ──────────────────────────────────────────────────────────────────────
    //  序列化 serialize
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `serialize 空集合返回空字符串`() {
        assertEquals("", AchievementStore.serialize(emptySet()))
    }

    @Test
    fun `serialize 单个 id`() {
        val result = AchievementStore.serialize(setOf("FIRST_STEPS"))
        assertEquals("FIRST_STEPS", result)
    }

    @Test
    fun `serialize 多个 id 按字母排序`() {
        val result = AchievementStore.serialize(setOf("CENTURY", "FIRST_STEPS", "MARATHON"))
        assertEquals("CENTURY,FIRST_STEPS,MARATHON", result)
    }

    @Test
    fun `serialize 结果是确定性的（相同输入相同输出）`() {
        val ids = setOf("EXPLORER", "DAILY_HABIT", "CENTURY_NOTES")
        val result1 = AchievementStore.serialize(ids)
        val result2 = AchievementStore.serialize(ids)
        assertEquals(result1, result2)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  反序列化 deserialize
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `deserialize null 返回空集合`() {
        assertEquals(emptySet<String>(), AchievementStore.deserialize(null))
    }

    @Test
    fun `deserialize 空白字符串返回空集合`() {
        assertEquals(emptySet<String>(), AchievementStore.deserialize(""))
        assertEquals(emptySet<String>(), AchievementStore.deserialize("   "))
    }

    @Test
    fun `deserialize 单个有效 id`() {
        assertEquals(setOf("FIRST_STEPS"), AchievementStore.deserialize("FIRST_STEPS"))
    }

    @Test
    fun `deserialize 多个有效 id`() {
        val result = AchievementStore.deserialize("FIRST_STEPS,CENTURY,EXPLORER")
        assertEquals(setOf("FIRST_STEPS", "CENTURY", "EXPLORER"), result)
    }

    @Test
    fun `deserialize 自动去空白`() {
        val result = AchievementStore.deserialize(" FIRST_STEPS , CENTURY , EXPLORER ")
        assertEquals(setOf("FIRST_STEPS", "CENTURY", "EXPLORER"), result)
    }

    @Test
    fun `deserialize 自动去重`() {
        val result = AchievementStore.deserialize("FIRST_STEPS,FIRST_STEPS,CENTURY")
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    @Test
    fun `deserialize 过滤掉无效 id`() {
        val result = AchievementStore.deserialize("FIRST_STEPS,INVALID_ID,CENTURY,FAKE_ACHIEVEMENT")
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    @Test
    fun `deserialize 全部无效返回空集合`() {
        val result = AchievementStore.deserialize("INVALID1,INVALID2,INVALID3")
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `deserialize 空片段被忽略`() {
        val result = AchievementStore.deserialize("FIRST_STEPS,,,CENTURY,,")
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    @Test
    fun `deserialize 纯空白片段被忽略`() {
        val result = AchievementStore.deserialize("FIRST_STEPS,   ,CENTURY")
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  往返一致性 serialize ∘ deserialize
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `serialize 后 deserialize 往返一致`() {
        val ids = setOf("FIRST_STEPS", "CENTURY", "EXPLORER", "DAILY_HABIT")
        val roundTrip = AchievementStore.deserialize(AchievementStore.serialize(ids))
        assertEquals(ids, roundTrip)
    }

    @Test
    fun `空集合往返一致`() {
        val roundTrip = AchievementStore.deserialize(AchievementStore.serialize(emptySet()))
        assertEquals(emptySet<String>(), roundTrip)
    }

    @Test
    fun `全部 22 个成就 id 往返一致`() {
        val allIds = AchievementEngine.ALL_IDS
        val roundTrip = AchievementStore.deserialize(AchievementStore.serialize(allIds))
        assertEquals(allIds, roundTrip)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  差分计算 computeNewlyUnlocked
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `computeNewlyUnlocked previous 为空时全部为新解锁`() {
        val current = setOf("FIRST_STEPS", "CENTURY")
        val newly = AchievementStore.computeNewlyUnlocked(current, emptySet())
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), newly)
    }

    @Test
    fun `computeNewlyUnlocked current 为空时无新解锁`() {
        val newly = AchievementStore.computeNewlyUnlocked(emptySet(), setOf("FIRST_STEPS"))
        assertEquals(emptySet<String>(), newly)
    }

    @Test
    fun `computeNewlyUnlocked 完全重叠时无新解锁`() {
        val ids = setOf("FIRST_STEPS", "CENTURY")
        val newly = AchievementStore.computeNewlyUnlocked(ids, ids)
        assertEquals(emptySet<String>(), newly)
    }

    @Test
    fun `computeNewlyUnlocked 部分重叠时返回差集`() {
        val current = setOf("FIRST_STEPS", "CENTURY", "EXPLORER")
        val previous = setOf("FIRST_STEPS")
        val newly = AchievementStore.computeNewlyUnlocked(current, previous)
        assertEquals(setOf("CENTURY", "EXPLORER"), newly)
    }

    @Test
    fun `computeNewlyUnlocked 过滤 current 中的无效 id`() {
        val current = setOf("FIRST_STEPS", "INVALID_ID")
        val newly = AchievementStore.computeNewlyUnlocked(current, emptySet())
        assertEquals(setOf("FIRST_STEPS"), newly)
    }

    @Test
    fun `computeNewlyUnlocked current 全部无效时返回空集合`() {
        val current = setOf("INVALID1", "INVALID2")
        val newly = AchievementStore.computeNewlyUnlocked(current, emptySet())
        assertEquals(emptySet<String>(), newly)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  合并 merge
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `merge 空集合并集`() {
        val result = AchievementStore.merge(emptySet(), setOf("FIRST_STEPS"))
        assertEquals(setOf("FIRST_STEPS"), result)
    }

    @Test
    fun `merge 双空集合返回空集合`() {
        assertEquals(emptySet<String>(), AchievementStore.merge(emptySet(), emptySet()))
    }

    @Test
    fun `merge 非重叠集合并集`() {
        val result = AchievementStore.merge(setOf("FIRST_STEPS"), setOf("CENTURY"))
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    @Test
    fun `merge 重叠集合并集去重`() {
        val result = AchievementStore.merge(setOf("FIRST_STEPS", "CENTURY"), setOf("CENTURY", "EXPLORER"))
        assertEquals(setOf("FIRST_STEPS", "CENTURY", "EXPLORER"), result)
    }

    @Test
    fun `merge 过滤 previous 中的无效 id`() {
        val result = AchievementStore.merge(setOf("FIRST_STEPS", "INVALID"), setOf("CENTURY"))
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    @Test
    fun `merge 过滤 newlyUnlocked 中的无效 id`() {
        val result = AchievementStore.merge(setOf("FIRST_STEPS"), setOf("CENTURY", "INVALID"))
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), result)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  evaluateDiff 高级 API
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `evaluateDiff previousRaw 为 null 时全部为新解锁`() {
        val current = setOf("FIRST_STEPS", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, null)
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), diff.newlyUnlockedIds)
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), diff.updatedIds)
        assertTrue(diff.hasNewlyUnlocked)
    }

    @Test
    fun `evaluateDiff previousRaw 为空字符串时全部为新解锁`() {
        val current = setOf("FIRST_STEPS")
        val diff = AchievementStore.evaluateDiff(current, "")
        assertEquals(setOf("FIRST_STEPS"), diff.newlyUnlockedIds)
        assertTrue(diff.hasNewlyUnlocked)
    }

    @Test
    fun `evaluateDiff 无新解锁时 hasNewlyUnlocked 为 false`() {
        val previous = "FIRST_STEPS,CENTURY"
        val current = setOf("FIRST_STEPS", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, previous)
        assertEquals(emptySet<String>(), diff.newlyUnlockedIds)
        assertFalse(diff.hasNewlyUnlocked)
    }

    @Test
    fun `evaluateDiff 有新解锁时 hasNewlyUnlocked 为 true`() {
        val previous = "FIRST_STEPS"
        val current = setOf("FIRST_STEPS", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, previous)
        assertEquals(setOf("CENTURY"), diff.newlyUnlockedIds)
        assertTrue(diff.hasNewlyUnlocked)
    }

    @Test
    fun `evaluateDiff updatedIds 包含并集`() {
        val previous = "FIRST_STEPS,EXPLORER"
        val current = setOf("FIRST_STEPS", "EXPLORER", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, previous)
        assertEquals(setOf("FIRST_STEPS", "EXPLORER", "CENTURY"), diff.updatedIds)
    }

    @Test
    fun `evaluateDiff updatedRaw 是序列化后的字符串`() {
        val previous = "FIRST_STEPS"
        val current = setOf("FIRST_STEPS", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, previous)
        assertEquals(AchievementStore.serialize(diff.updatedIds), diff.updatedRaw)
    }

    @Test
    fun `evaluateDiff previousRaw 含无效 id 时被过滤`() {
        val previous = "FIRST_STEPS,INVALID_ID"
        val current = setOf("FIRST_STEPS", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, previous)
        // FIRST_STEPS 在 previous 中，CENTURY 是新的
        assertEquals(setOf("CENTURY"), diff.newlyUnlockedIds)
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), diff.updatedIds)
    }

    @Test
    fun `evaluateDiff current 含无效 id 时被过滤`() {
        val current = setOf("FIRST_STEPS", "INVALID_ID", "CENTURY")
        val diff = AchievementStore.evaluateDiff(current, null)
        assertEquals(setOf("FIRST_STEPS", "CENTURY"), diff.newlyUnlockedIds)
    }

    @Test
    fun `evaluateDiff 完整流程 首次评估全部新解锁`() {
        val current = setOf("FIRST_STEPS", "GETTING_WARM", "HALF_HOUR")
        val diff = AchievementStore.evaluateDiff(current, null)

        // 全部三个都是新解锁
        assertEquals(3, diff.newlyUnlockedIds.size)
        assertTrue(diff.hasNewlyUnlocked)

        // 持久化后的字符串可以再次反序列化
        val restored = AchievementStore.deserialize(diff.updatedRaw)
        assertEquals(current, restored)
    }

    @Test
    fun `evaluateDiff 第二次评估无新解锁`() {
        // 第一次：解锁 FIRST_STEPS
        val current1 = setOf("FIRST_STEPS")
        val diff1 = AchievementStore.evaluateDiff(current1, null)

        // 第二次：同样的成就（模拟重新打开页面）
        val current2 = setOf("FIRST_STEPS")
        val diff2 = AchievementStore.evaluateDiff(current2, diff1.updatedRaw)

        assertFalse(diff2.hasNewlyUnlocked)
        assertEquals(emptySet<String>(), diff2.newlyUnlockedIds)
    }

    @Test
    fun `evaluateDiff 练习后解锁新成就`() {
        // 第一次：只有 FIRST_STEPS
        val diff1 = AchievementStore.evaluateDiff(setOf("FIRST_STEPS"), null)
        val saved = diff1.updatedRaw

        // 练习后解锁了 GETTING_WARM
        val diff2 = AchievementStore.evaluateDiff(setOf("FIRST_STEPS", "GETTING_WARM"), saved)

        assertTrue(diff2.hasNewlyUnlocked)
        assertEquals(setOf("GETTING_WARM"), diff2.newlyUnlockedIds)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  newlyUnlockedToProgress
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `newlyUnlockedToProgress 空 id 返回空列表`() {
        val profile = PracticeProfile(totalSessions = 1)
        val summary = AchievementEngine.evaluate(profile)
        val result = AchievementStore.newlyUnlockedToProgress(emptySet(), summary)
        assertEquals(emptyList<AchievementProgress>(), result)
    }

    @Test
    fun `newlyUnlockedToProgress 返回匹配的成就进度`() {
        val profile = PracticeProfile(totalSessions = 1) // 解锁 FIRST_STEPS
        val summary = AchievementEngine.evaluate(profile)
        val result = AchievementStore.newlyUnlockedToProgress(setOf("FIRST_STEPS"), summary)
        assertEquals(1, result.size)
        assertEquals("FIRST_STEPS", result[0].definition.id)
        assertTrue(result[0].isUnlocked)
    }

    @Test
    fun `newlyUnlockedToProgress 多个匹配`() {
        val profile = PracticeProfile(totalSessions = 15) // FIRST_STEPS + GETTING_WARM
        val summary = AchievementEngine.evaluate(profile)
        val result = AchievementStore.newlyUnlockedToProgress(
            setOf("FIRST_STEPS", "GETTING_WARM"), summary
        )
        assertEquals(2, result.size)
        val ids = result.map { it.definition.id }.toSet()
        assertTrue("FIRST_STEPS" in ids)
        assertTrue("GETTING_WARM" in ids)
    }

    @Test
    fun `newlyUnlockedToProgress 过滤未解锁的 id`() {
        val profile = PracticeProfile(totalSessions = 0) // 无解锁
        val summary = AchievementEngine.evaluate(profile)
        // 即使传入 FIRST_STEPS，因为它在 summary.unlocked 中不存在（未解锁），返回空
        val result = AchievementStore.newlyUnlockedToProgress(setOf("FIRST_STEPS"), summary)
        assertEquals(emptyList<AchievementProgress>(), result)
    }

    @Test
    fun `newlyUnlockedToProgress 未知 id 被忽略`() {
        val profile = PracticeProfile(totalSessions = 1)
        val summary = AchievementEngine.evaluate(profile)
        val result = AchievementStore.newlyUnlockedToProgress(
            setOf("FIRST_STEPS", "UNKNOWN_ID"), summary
        )
        assertEquals(1, result.size)
        assertEquals("FIRST_STEPS", result[0].definition.id)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  端到端集成测试
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `端到端 模拟完整的练习-解锁-持久化流程`() {
        fun session(
            totalNotes: Int = 50,
            accuracy: Float = 0.90f,
            durationMs: Long = 300_000L
        ): SessionRecord = SessionRecord(
            scoreTitle = "欢乐颂",
            startTime = System.currentTimeMillis(),
            durationMs = durationMs,
            totalNotes = totalNotes,
            correctNotes = (totalNotes * accuracy).toInt(),
            wrongNotes = 0,
            missedNotes = 0,
            extraNotes = totalNotes - (totalNotes * accuracy).toInt(),
            accuracy = accuracy
        )

        // 第一次练习：1 次，解锁 FIRST_STEPS
        val sessions1 = listOf(session())
        val profile1 = PracticeProfileBuilder.fromSessions(sessions1)
        val summary1 = AchievementEngine.evaluate(profile1)
        val currentIds1 = summary1.unlocked.map { it.definition.id }.toSet()
        val diff1 = AchievementStore.evaluateDiff(currentIds1, null)
        val saved1 = diff1.updatedRaw

        assertTrue("首次练习应解锁 FIRST_STEPS", "FIRST_STEPS" in diff1.newlyUnlockedIds)
        assertTrue(diff1.hasNewlyUnlocked)

        // 第二次练习（累计 10 次）：解锁 GETTING_WARM
        val sessions2 = (1..10).map { session() }
        val profile2 = PracticeProfileBuilder.fromSessions(sessions2)
        val summary2 = AchievementEngine.evaluate(profile2)
        val currentIds2 = summary2.unlocked.map { it.definition.id }.toSet()
        val diff2 = AchievementStore.evaluateDiff(currentIds2, saved1)
        val saved2 = diff2.updatedRaw

        assertTrue("累计 10 次应解锁 GETTING_WARM", "GETTING_WARM" in diff2.newlyUnlockedIds)
        // FIRST_STEPS 不应再次出现在新解锁中
        assertFalse("FIRST_STEPS 不应重复解锁", "FIRST_STEPS" in diff2.newlyUnlockedIds)

        // 第三次：同样的成就（无新增）
        val diff3 = AchievementStore.evaluateDiff(currentIds2, saved2)
        assertFalse(diff3.hasNewlyUnlocked)

        // 验证最终持久化集合包含所有已解锁
        val finalSet = AchievementStore.deserialize(saved2)
        assertTrue("FIRST_STEPS" in finalSet)
        assertTrue("GETTING_WARM" in finalSet)
    }

    @Test
    fun `端到端 首次打开统计页无新解锁`() {
        // 首次打开，无历史数据，无已解锁成就
        val profile = PracticeProfile() // 空画像
        val summary = AchievementEngine.evaluate(profile)
        val currentIds = summary.unlocked.map { it.definition.id }.toSet()
        val diff = AchievementStore.evaluateDiff(currentIds, null)

        assertFalse(diff.hasNewlyUnlocked)
        assertEquals(emptySet<String>(), diff.newlyUnlockedIds)
    }

    @Test
    fun `端到端 已解锁成就不会因数据减少而丢失`() {
        // 已持久化了 FIRST_STEPS + CENTURY
        val saved = "CENTURY,FIRST_STEPS"

        // 假设数据部分丢失（只有 1 次练习，CENTURY 不再解锁）
        val current = setOf("FIRST_STEPS")

        val diff = AchievementStore.evaluateDiff(current, saved)

        // FIRST_STEPS 仍然解锁，无新解锁
        assertFalse(diff.hasNewlyUnlocked)

        // updatedIds 保留了 CENTURY（因为 previous 中有它）
        // 注意：merge 保留 previous 中的成就，确保不会因数据变化而丢失
        assertTrue("CENTURY" in diff.updatedIds)
        assertTrue("FIRST_STEPS" in diff.updatedIds)
    }

    @Test
    fun `端到端 一次解锁多个成就`() {
        // 丰富的练习画像，一次解锁多个成就
        val sessions = (1..5).map {
            SessionRecord(
                scoreTitle = "曲目$it", // 5 首不同曲目 → EXPLORER
                startTime = System.currentTimeMillis(),
                durationMs = 600_000L, // 每次 10 分钟，共 50 分钟 → HALF_HOUR
                totalNotes = 30,
                correctNotes = 30,
                wrongNotes = 0,
                missedNotes = 0,
                extraNotes = 0,
                accuracy = 1.0f // 100% → PITCH_PERFECT
            )
        }
        val profile = PracticeProfileBuilder.fromSessions(sessions)
        val summary = AchievementEngine.evaluate(profile)
        val currentIds = summary.unlocked.map { it.definition.id }.toSet()

        // 首次评估（previous 为 null）
        val diff = AchievementStore.evaluateDiff(currentIds, null)

        assertTrue(diff.hasNewlyUnlocked)
        // 应该解锁了多个成就
        assertTrue(diff.newlyUnlockedIds.size >= 4)

        // 验证具体的成就
        val newlyTitles = diff.newlyUnlockedIds
        assertTrue("FIRST_STEPS" in newlyTitles)
        assertTrue("PITCH_PERFECT" in newlyTitles)
        assertTrue("EXPLORER" in newlyTitles)
        assertTrue("HALF_HOUR" in newlyTitles)

        // 转换为 Progress 列表
        val progresses = AchievementStore.newlyUnlockedToProgress(diff.newlyUnlockedIds, summary)
        assertEquals(diff.newlyUnlockedIds.size, progresses.size)
        assertTrue(progresses.all { it.isUnlocked })
    }
}
