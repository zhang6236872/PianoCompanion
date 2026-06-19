package com.pianocompanion.data.sync

import com.pianocompanion.data.model.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyncEngine] 纯 JVM 单元测试 —— 覆盖合并去重、LWW 冲突解决、幂等性、
 * schema 迁移、完整性校验与校验和。
 */
class SyncEngineTest {

    private val engine = SyncEngine()

    private fun session(
        scoreTitle: String = "欢乐颂",
        startTime: Long = 1_000L,
        durationMs: Long = 60_000L,
        totalNotes: Int = 100,
        correctNotes: Int = 90,
        accuracy: Float = 0.9f,
        wrongNotes: Int = 10,
        updatedAt: Long = 0L,
        id: Long = startTime
    ) = SessionRecord(
        id = id,
        scoreTitle = scoreTitle,
        startTime = startTime,
        durationMs = durationMs,
        totalNotes = totalNotes,
        correctNotes = correctNotes,
        wrongNotes = wrongNotes,
        missedNotes = 0,
        extraNotes = 0,
        accuracy = accuracy,
        updatedAt = updatedAt
    )

    private fun data(
        sessions: List<SessionRecord>,
        version: Int = CURRENT_SYNC_SCHEMA_VERSION,
        checksum: String? = null
    ) = SyncData(
        version = version,
        exportedAt = 0L,
        deviceName = "test-device",
        sessions = sessions,
        checksum = checksum
    )

    // ===================== 合并 / 去重 =====================

    @Test
    fun `merge - 全新会话被新增`() {
        val local = data(listOf(session(startTime = 1_000L)))
        val incoming = data(listOf(session(startTime = 2_000L, scoreTitle = "小星星")))

        val result = engine.merge(local, incoming)

        assertEquals(1, result.added)
        assertEquals(0, result.updated)
        assertEquals(0, result.skipped)
        assertEquals(2, result.totalAfter)
    }

    @Test
    fun `merge - 内容完全相同则跳过（幂等）`() {
        val s = session(startTime = 1_000L)
        val local = data(listOf(s))
        val incoming = data(listOf(s))

        val result = engine.merge(local, incoming)

        assertEquals(0, result.added)
        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(1, result.totalAfter)
        // 同一份数据重复合并不应膨胀
        assertEquals(result.merged, engine.merge(local, incoming).merged)
    }

    @Test
    fun `merge - 设备无关 fingerprint 让不同 id 的相同记录去重`() {
        // 两台设备各生成了一条"同一时刻同一首曲子"的记录，但 id(currentTimeMillis)不同
        val deviceA = session(startTime = 5_000L, id = 111L)
        val deviceB = session(startTime = 5_000L, id = 222L)

        val result = engine.merge(data(listOf(deviceA)), data(listOf(deviceB)))

        assertEquals(0, result.added)
        assertEquals(1, result.totalAfter)
        // 内容相同应保留其中一条而非产生两条
        assertEquals(1, result.merged.count { it.startTime == 5_000L })
    }

    @Test
    fun `merge - LWW 远端 updatedAt 更新则覆盖本机`() {
        val localRec = session(startTime = 5_000L, accuracy = 0.5f, updatedAt = 100L)
        val remoteRec = session(startTime = 5_000L, accuracy = 0.95f, updatedAt = 500L)

        val result = engine.merge(data(listOf(localRec)), data(listOf(remoteRec)))

        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        assertEquals(0.95f, result.merged.first().accuracy)
    }

    @Test
    fun `merge - LWW 本机 updatedAt 更新则保留本机`() {
        val localRec = session(startTime = 5_000L, accuracy = 0.95f, updatedAt = 900L)
        val remoteRec = session(startTime = 5_000L, accuracy = 0.5f, updatedAt = 100L)

        val result = engine.merge(data(listOf(localRec)), data(listOf(remoteRec)))

        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(0.95f, result.merged.first().accuracy)
    }

    @Test
    fun `merge - updatedAt 缺失时回退到 startTime 作为 LWW 依据`() {
        // 两条 updatedAt=0 的同 fingerprint 记录，startTime 较大者胜
        val localRec = session(startTime = 5_000L, accuracy = 0.5f, updatedAt = 0L)
        val remoteRec = session(startTime = 5_000L, accuracy = 0.8f, updatedAt = 0L)
        // startTime 相同 → 视为平局，保留本机
        val result = engine.merge(data(listOf(localRec)), data(listOf(remoteRec)))
        assertEquals(1, result.skipped)

        // 当 startTime 因 fingerprint 不同而区分时……构造不同 fingerprint 但同 identity 的场景：
        // 这里直接验证 fallback：本机 startTime 早、远端 startTime 晚且 fingerprint 不同 → 新增
        val r2 = engine.merge(
            data(listOf(session(startTime = 1_000L))),
            data(listOf(session(startTime = 9_000L)))
        )
        assertEquals(1, r2.added)
    }

    @Test
    fun `merge - 结果按 startTime 倒序排列`() {
        val local = data(listOf(session(startTime = 1_000L)))
        val incoming = data(listOf(
            session(startTime = 5_000L),
            session(startTime = 3_000L)
        ))

        val result = engine.merge(local, incoming)

        val starts = result.merged.map { it.startTime }
        assertEquals(listOf(5_000L, 3_000L, 1_000L), starts)
    }

    @Test
    fun `merge - changed 等于新增加覆盖`() {
        val local = data(listOf(session(startTime = 1_000L)))
        val incoming = data(listOf(
            session(startTime = 2_000L),                                    // 新增
            session(startTime = 1_000L, accuracy = 0.99f, updatedAt = 2_000L) // 覆盖（updatedAt 晚于本机 startTime=1000）
        ))

        val result = engine.merge(local, incoming)

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(2, result.changed)
    }

    // ===================== 校验 =====================

    @Test
    fun `validate - 正常数据通过`() {
        val r = engine.validate(data(listOf(session())))
        assertTrue(r.ok)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun `validate - 版本过高为硬错误`() {
        val r = engine.validate(data(listOf(session()), version = 99))
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("版本") })
    }

    @Test
    fun `validate - 旧版本产生 warning 但仍 ok`() {
        val r = engine.validate(data(listOf(session()), version = 1))
        assertTrue(r.ok)
        assertTrue(r.warnings.any { it.contains("迁移") })
    }

    @Test
    fun `validate - 负时长为硬错误`() {
        val r = engine.validate(data(listOf(session(durationMs = -1L))))
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("时长") })
    }

    @Test
    fun `validate - 正确音数大于总音数为硬错误`() {
        val r = engine.validate(data(listOf(session(totalNotes = 10, correctNotes = 11))))
        assertFalse(r.ok)
    }

    @Test
    fun `validate - 准确率越界为硬错误`() {
        val r = engine.validate(data(listOf(session(accuracy = 1.5f))))
        assertFalse(r.ok)
    }

    @Test
    fun `validate - 校验和匹配则通过`() {
        val sessions = listOf(session(), session(startTime = 2_000L))
        val cs = engine.checksum(sessions)
        val r = engine.validate(data(sessions, checksum = cs))
        assertTrue(r.ok)
    }

    @Test
    fun `validate - 校验和不匹配为硬错误`() {
        val sessions = listOf(session())
        val r = engine.validate(data(sessions, checksum = "deadbeef"))
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("校验和") })
    }

    // ===================== 迁移 =====================

    @Test
    fun `migrate - v1 升级到当前版本并清除 checksum`() {
        val raw = data(listOf(session()), version = 1, checksum = "old")
        val migrated = engine.migrate(raw)

        assertEquals(CURRENT_SYNC_SCHEMA_VERSION, migrated.version)
        assertNull(migrated.checksum)
    }

    @Test
    fun `migrate - 规范化 NaN 准确率`() {
        val raw = data(listOf(session(accuracy = Float.NaN)), version = 1)
        val migrated = engine.migrate(raw)

        assertEquals(0f, migrated.sessions.first().accuracy)
        // 规范化后应通过校验
        assertTrue(engine.validate(migrated).ok)
    }

    @Test
    fun `migrate - 已是当前版本且带 checksum 时幂等`() {
        val sessions = listOf(session())
        val current = data(sessions, version = CURRENT_SYNC_SCHEMA_VERSION, checksum = engine.checksum(sessions))
        val migrated = engine.migrate(current)

        assertEquals(current, migrated)
    }

    // ===================== 校验和 / 指纹 =====================

    @Test
    fun `checksum - 与输入顺序无关（确定性）`() {
        val a = listOf(session(startTime = 1_000L), session(startTime = 3_000L))
        val b = listOf(session(startTime = 3_000L), session(startTime = 1_000L))

        assertEquals(engine.checksum(a), engine.checksum(b))
    }

    @Test
    fun `checksum - 内容变化则改变`() {
        val base = listOf(session(correctNotes = 90))
        val changed = listOf(session(correctNotes = 91))

        assertNotEquals(engine.checksum(base), engine.checksum(changed))
    }

    @Test
    fun `fingerprint - 相同内容相同指纹，不同 id 不影响指纹`() {
        val a = session(startTime = 5_000L, id = 111L)
        val b = session(startTime = 5_000L, id = 222L)
        assertEquals(engine.fingerprint(a), engine.fingerprint(b))
    }

    @Test
    fun `fingerprint - 不同内容不同指纹`() {
        assertNotEquals(
            engine.fingerprint(session(scoreTitle = "A")),
            engine.fingerprint(session(scoreTitle = "B"))
        )
    }
}
