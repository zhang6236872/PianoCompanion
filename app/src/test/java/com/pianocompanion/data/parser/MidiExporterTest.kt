package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MidiExporter] 单元测试。
 *
 * 覆盖：SMF 文件结构（MThd/MTrk）、VLQ 编码、ms→tick 转换、tempo/拍号/曲名 meta、
 * Note On/Off 事件、velocity/音高钳制、边界（空乐谱/单音符/和弦/跨小节），以及
 * 导出 → [MidiParser] 解析的 round-trip 往返验证。
 */
class MidiExporterTest {

    private val exporter = MidiExporter(division = 480)
    private val parser = MidiParser()

    private fun note(
        midi: Int,
        startMs: Long,
        durMs: Long,
        velocity: Int = 64,
        staff: Staff = Staff.TREBLE
    ) = ScoreNote(
        midiNumber = midi,
        noteName = "n$midi",
        startTime = startMs,
        duration = durMs,
        velocity = velocity,
        staff = staff
    )

    private fun score(
        notes: List<ScoreNote>,
        tempo: Int = 120,
        timeSignature: String = "4/4",
        title: String = "测试乐谱"
    ) = Score(
        id = "test",
        title = title,
        composer = "tester",
        notes = notes,
        tempo = tempo,
        timeSignature = timeSignature,
        source = ScoreSource.OMR
    )

    // ====================================================================
    // 文件结构
    // ====================================================================

    @Test
    fun `文件头 - MThd 标识与长度`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        assertEquals("MThd", String(data, 0, 4, Charsets.US_ASCII))
        // header length = 6
        assertEquals(0, data[4].toInt())
        assertEquals(0, data[5].toInt())
        assertEquals(0, data[6].toInt())
        assertEquals(6, data[7].toInt())
    }

    @Test
    fun `文件头 - format 为 1`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        val format = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
        assertEquals(1, format)
    }

    @Test
    fun `文件头 - 轨道数为 2 (指挥轨 + 音符轨)`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        val numTracks = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
        assertEquals(2, numTracks)
    }

    @Test
    fun `文件头 - division 为 480`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        val div = ((data[12].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF)
        assertEquals(480, div)
    }

    @Test
    fun `文件头 - 至少包含两个 MTrk 轨道`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        val trackCount = countOccurrences(data, "MTrk".toByteArray(Charsets.US_ASCII))
        assertEquals(2, trackCount)
    }

    @Test
    fun `空乐谱 - 仍生成合法文件`() {
        val data = exporter.export(score(emptyList()))
        assertEquals("MThd", String(data, 0, 4, Charsets.US_ASCII))
        val numTracks = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
        assertEquals(2, numTracks)
        // round-trip: 解析不抛异常
        val parsed = parser.parse(data.inputStream())
        assertTrue(parsed.notes.isEmpty())
    }

    // ====================================================================
    // VLQ 编码
    // ====================================================================

    @Test
    fun `VLQ - 0 编码为单字节 0`() {
        assertArrayEquals(byteArrayOf(0), exporter.encodeVarLen(0))
    }

    @Test
    fun `VLQ - 127 编码为单字节`() {
        assertArrayEquals(byteArrayOf(127), exporter.encodeVarLen(127))
    }

    @Test
    fun `VLQ - 128 编码为两字节 0x81 0x00`() {
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x00), exporter.encodeVarLen(128))
    }

    @Test
    fun `VLQ - 192 编码为 0x81 0x40`() {
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x40), exporter.encodeVarLen(192))
    }

    @Test
    fun `VLQ - 480 编码为两字节 0x83 0x60`() {
        // 480 = 0b11_1100000 -> 7位分组 [3, 96(0x60)] -> 0x83 0x60
        assertArrayEquals(byteArrayOf(0x83.toByte(), 0x60), exporter.encodeVarLen(480))
    }

    @Test
    fun `VLQ - 16383 (最大两字节值) 编码为 0xFF 0x7F`() {
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x7F), exporter.encodeVarLen(16383))
    }

    @Test
    fun `VLQ - 16384 编码为三字节 0x81 0x80 0x00`() {
        // 16384 = 2^14 -> 7位分组 [1, 0, 0] -> 0x81 0x80 0x00 (中间字节 data=0 但带继续位)
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x80.toByte(), 0x00), exporter.encodeVarLen(16384))
    }

    @Test
    fun `VLQ - 负数安全降级为 0`() {
        assertArrayEquals(byteArrayOf(0), exporter.encodeVarLen(-5))
    }

    @Test
    fun `VLQ - 与 MidiParser 读取互逆`() {
        // 编码后写入文件，再用 parser 解析应得到原值
        for (v in listOf(0L, 1L, 127L, 128L, 480L, 960L, 1000L, 16383L, 16384L, 1_000_000L)) {
            val encoded = exporter.encodeVarLen(v)
            // 模拟 readVarLen：直接解码
            val decoded = decodeVlq(encoded)
            assertEquals("VLQ 往返失败 value=$v", v, decoded)
        }
    }

    private fun decodeVlq(bytes: ByteArray): Long {
        var value = 0L
        for (b in bytes) {
            val byte = b.toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F).toLong()
        }
        return value
    }

    // ====================================================================
    // ms → tick 转换
    // ====================================================================

    @Test
    fun `msToTicks - 0ms 为 0 tick`() {
        assertEquals(0L, exporter.msToTicks(0, 120))
    }

    @Test
    fun `msToTicks - 负数安全降级为 0`() {
        assertEquals(0L, exporter.msToTicks(-100, 120))
    }

    @Test
    fun `msToTicks - 四分音符 500ms@120BPM = 480 tick`() {
        // 120 BPM → 四分音符 = 500ms → 480 ticks (division=480)
        assertEquals(480L, exporter.msToTicks(500, 120))
    }

    @Test
    fun `msToTicks - 八分音符 250ms@120BPM = 240 tick`() {
        assertEquals(240L, exporter.msToTicks(250, 120))
    }

    @Test
    fun `msToTicks - 60BPM 下 1000ms = 480 tick`() {
        // 60 BPM → 四分音符 = 1000ms → 480 ticks
        assertEquals(480L, exporter.msToTicks(1000, 60))
    }

    // ====================================================================
    // Note On / Off 事件
    // ====================================================================

    @Test
    fun `单音符 - 生成 Note On 和 Note Off 两个事件`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        val noteOns = countStatusByte(data, 0x90)
        val noteOffs = countStatusByte(data, 0x80)
        assertEquals(1, noteOns)
        assertEquals(1, noteOffs)
    }

    @Test
    fun `单音符 - Note On 包含正确音高与力度`() {
        val data = exporter.export(score(listOf(note(72, 0, 500, velocity = 100))))
        // 在音符轨中查找 Note On 事件 (0x90) 后紧跟的音高与力度
        val (pitch, velocity) = findFirstNoteOn(data)!!
        assertEquals(72, pitch)
        assertEquals(100, velocity)
    }

    @Test
    fun `音高钳制 - 超出 127 钳制为 127`() {
        val data = exporter.export(score(listOf(note(200, 0, 500))))
        val (pitch, _) = findFirstNoteOn(data)!!
        assertEquals(127, pitch)
    }

    @Test
    fun `音高钳制 - 低于 0 钳制为 0`() {
        val data = exporter.export(score(listOf(note(-10, 0, 500))))
        val (pitch, _) = findFirstNoteOn(data)!!
        assertEquals(0, pitch)
    }

    @Test
    fun `力度钳制 - 0 力度默认为 64`() {
        val data = exporter.export(score(listOf(note(60, 0, 500, velocity = 0))))
        val (_, velocity) = findFirstNoteOn(data)!!
        assertEquals(64, velocity)
    }

    @Test
    fun `力度钳制 - 超过 127 钳制为 127`() {
        val data = exporter.export(score(listOf(note(60, 0, 500, velocity = 200))))
        val (_, velocity) = findFirstNoteOn(data)!!
        assertEquals(127, velocity)
    }

    @Test
    fun `和弦 - 同时发声的两个音符生成 2 个 Note On`() {
        val chord = listOf(note(60, 0, 500), note(64, 0, 500))
        val data = exporter.export(score(chord))
        assertEquals(2, countStatusByte(data, 0x90))
        assertEquals(2, countStatusByte(data, 0x80))
    }

    @Test
    fun `多音符 - 序列生成对应数量事件`() {
        val melody = listOf(
            note(60, 0, 500),
            note(62, 500, 500),
            note(64, 1000, 500),
            note(65, 1500, 500)
        )
        val data = exporter.export(score(melody))
        assertEquals(4, countStatusByte(data, 0x90))
        assertEquals(4, countStatusByte(data, 0x80))
    }

    @Test
    fun `音符最短时值 - 零时长强制为 1 tick`() {
        val data = exporter.export(score(listOf(note(60, 0, 0))))
        // 应仍能正常导出与往返
        val parsed = parser.parse(data.inputStream())
        assertEquals(1, parsed.notes.size)
        assertEquals(60, parsed.notes[0].midiNumber)
    }

    // ====================================================================
    // Meta 事件 (tempo / 拍号 / 曲名)
    // ====================================================================

    @Test
    fun `曲名 - 写入 Track Name meta`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), title = "我的曲子"))
        assertTrue("应包含曲名字节", containsAscii(data, "我的曲子") || containsText(data, "我的曲子"))
    }

    @Test
    fun `速度 - 写入 Set Tempo meta`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), tempo = 90))
        // mpqn = 60000000 / 90 = 666666
        val mpqn = findTempoMeta(data)
        assertNotNull("应包含 tempo meta", mpqn)
        assertEquals(666666L, mpqn)
    }

    @Test
    fun `速度 - 120BPM mpqn 为 500000`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), tempo = 120))
        assertEquals(500000L, findTempoMeta(data))
    }

    @Test
    fun `拍号 - 写入 4_4 Time Signature`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), timeSignature = "4/4"))
        val ts = findTimeSignatureMeta(data)
        assertNotNull(ts)
        assertEquals(4, ts!!.first) // numerator
        assertEquals(2, ts.second)  // log2(4)=2
    }

    @Test
    fun `拍号 - 写入 3_4`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), timeSignature = "3/4"))
        val ts = findTimeSignatureMeta(data)
        assertEquals(3, ts!!.first)
        assertEquals(2, ts.second)
    }

    @Test
    fun `拍号 - 写入 6_8`() {
        val data = exporter.export(score(listOf(note(60, 0, 500)), timeSignature = "6/8"))
        val ts = findTimeSignatureMeta(data)
        assertEquals(6, ts!!.first)
        assertEquals(3, ts.second) // log2(8)=3
    }

    @Test
    fun `End of Track - 每个轨道末尾包含 EOT meta`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        // EOT = 0xFF 0x2F 0x00
        val eotCount = countOccurrences(data, byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        assertEquals(2, eotCount) // 指挥轨 + 音符轨
    }

    // ====================================================================
    // Round-trip (导出 → MidiParser 解析)
    // ====================================================================

    @Test
    fun `往返 - 单音符音高一致`() {
        val original = score(listOf(note(64, 0, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(1, parsed.notes.size)
        assertEquals(64, parsed.notes[0].midiNumber)
    }

    @Test
    fun `往返 - 多音符音高一致`() {
        val original = score(listOf(
            note(60, 0, 500),
            note(62, 500, 500),
            note(64, 1000, 500),
            note(67, 1500, 500)
        ))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(4, parsed.notes.size)
        assertEquals(listOf(60, 62, 64, 67), parsed.notes.map { it.midiNumber })
    }

    @Test
    fun `往返 - 起始时间一致`() {
        // 120 BPM, 480 PPQ: msToTicks 与 ticksToTime 互逆
        val original = score(listOf(note(60, 0, 500), note(62, 500, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(0L, parsed.notes[0].startTime)
        // 500ms → 480 ticks → 回到 ms: 480 * 60000/(120*480) = 500ms
        assertEquals(500L, parsed.notes[1].startTime)
    }

    @Test
    fun `往返 - 时值一致`() {
        val original = score(listOf(note(60, 0, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        // duration 500ms → round-trip 后 parser 对 <50ms 强制 50，但 500ms 不受影响
        assertEquals(500L, parsed.notes[0].duration)
    }

    @Test
    fun `往返 - 力度一致`() {
        val original = score(listOf(note(60, 0, 500, velocity = 80)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(80, parsed.notes[0].velocity)
    }

    @Test
    fun `往返 - 黑键(升号)音高一致`() {
        val original = score(listOf(note(61, 0, 500), note(66, 500, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(listOf(61, 66), parsed.notes.map { it.midiNumber })
    }

    @Test
    fun `往返 - 和弦音高一致`() {
        val original = score(listOf(note(60, 0, 500), note(64, 0, 500), note(67, 0, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(setOf(60, 64, 67), parsed.notes.map { it.midiNumber }.toSet())
    }

    @Test
    fun `往返 - tempo 一致`() {
        val original = score(listOf(note(60, 0, 500)), tempo = 90)
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(90, parsed.tempo)
    }

    @Test
    fun `往返 - 拍号一致`() {
        val original = score(listOf(note(60, 0, 500)), timeSignature = "6/8")
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals("6/8", parsed.timeSignature)
    }

    @Test
    fun `往返 - 曲名一致`() {
        val original = score(listOf(note(60, 0, 500)), title = "Für Elise")
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals("Für Elise", parsed.title)
    }

    @Test
    fun `往返 - 低音区音符一致`() {
        val original = score(listOf(note(36, 0, 500), note(40, 500, 500)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(listOf(36, 40), parsed.notes.map { it.midiNumber })
    }

    @Test
    fun `往返 - 高音区音符一致`() {
        val original = score(listOf(note(96, 0, 250), note(100, 250, 250)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(listOf(96, 100), parsed.notes.map { it.midiNumber })
    }

    @Test
    fun `往返 - 60BPM 节奏一致`() {
        val original = score(listOf(note(60, 0, 1000), note(62, 1000, 1000)), tempo = 60)
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(0L, parsed.notes[0].startTime)
        assertEquals(1000L, parsed.notes[1].startTime)
    }

    @Test
    fun `往返 - 180BPM 节奏一致`() {
        val original = score(listOf(note(60, 0, 333), note(62, 333, 333)), tempo = 180)
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(0L, parsed.notes[0].startTime)
        // 333ms @180BPM: tick = 333*180*480/60000 = 333*864000/60000... ≈ 479.5 → 取整后往返可能有 ±1ms
        assertTrue(
            "start time 应接近 333ms, 实际=${parsed.notes[1].startTime}",
            kotlin.math.abs(parsed.notes[1].startTime - 333L) <= 2L
        )
    }

    // ====================================================================
    // 边界场景
    // ====================================================================

    @Test
    fun `最大音高 C8 (midi 108-12= midi 108) 可导出`() {
        val data = exporter.export(score(listOf(note(108, 0, 500))))
        val (pitch, _) = findFirstNoteOn(data)!!
        assertEquals(108, pitch)
    }

    @Test
    fun `最小音高 A0 (midi 21) 可导出`() {
        val data = exporter.export(score(listOf(note(21, 0, 500))))
        val (pitch, _) = findFirstNoteOn(data)!!
        assertEquals(21, pitch)
    }

    @Test
    fun `长时值音符可导出`() {
        val data = exporter.export(score(listOf(note(60, 0, 10000))))
        val parsed = parser.parse(data.inputStream())
        assertEquals(1, parsed.notes.size)
        assertEquals(60, parsed.notes[0].midiNumber)
    }

    @Test
    fun `BASS 谱表音符可导出`() {
        val original = score(listOf(note(40, 0, 500, staff = Staff.BASS)))
        val parsed = parser.parse(exporter.export(original).inputStream())
        assertEquals(40, parsed.notes[0].midiNumber)
    }

    @Test
    fun `导出文件可被标准库重读且 MThd 完整`() {
        val data = exporter.export(score(listOf(note(60, 0, 500))))
        // 再次解析确保结构完整无截断
        val parsed = parser.parse(data.inputStream())
        assertNotNull(parsed)
        assertTrue(parsed.notes.isNotEmpty())
    }

    // ====================================================================
    // 辅助函数
    // ====================================================================

    private fun countOccurrences(data: ByteArray, pattern: ByteArray): Int {
        var count = 0
        var i = 0
        while (i <= data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) { match = false; break }
            }
            if (match) { count++; i += pattern.size } else i++
        }
        return count
    }

    private fun readBeInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)

    /**
     * 严格解析 SMF 中的 Note On / Note Off 事件（按 chunk 边界 + delta VLQ + 状态字
     * 逐事件推进），避免与 UTF-8 曲名字节（如 "乐" = E4 B9 90）误匹配。
     * 返回 (isNoteOn, pitch, velocity) 列表。本导出器不使用 running status，故按显式状态解析。
     */
    private fun parseNoteEvents(data: ByteArray): List<Triple<Boolean, Int, Int>> {
        val events = mutableListOf<Triple<Boolean, Int, Int>>()
        var pos = 14 // 跳过 MThd 头
        while (pos + 8 <= data.size) {
            val id = String(data, pos, 4, Charsets.US_ASCII)
            val len = readBeInt(data, pos + 4)
            val bodyStart = pos + 8
            val bodyEnd = minOf(bodyStart + len, data.size)
            if (id == "MTrk") {
                var i = bodyStart
                while (i < bodyEnd) {
                    // 跳过 delta-time VLQ
                    while (i < bodyEnd && (data[i].toInt() and 0x80) != 0) i++
                    i++ // 消费最后一个高位为 0 的 delta 字节
                    if (i >= bodyEnd) break
                    val status = data[i].toInt() and 0xFF
                    val high = status and 0xF0
                    when (high) {
                        0x80 -> {
                            events.add(Triple(false, data[i + 1].toInt() and 0xFF, 0))
                            i += 3
                        }
                        0x90 -> {
                            events.add(Triple(true, data[i + 1].toInt() and 0xFF, data[i + 2].toInt() and 0xFF))
                            i += 3
                        }
                        0xA0, 0xB0, 0xE0 -> i += 3
                        0xC0, 0xD0 -> i += 2
                        0xF0 -> {
                            if (status == 0xFF) {
                                // meta: type + VLQ length + data
                                var j = i + 2
                                var metaLen = 0
                                do {
                                    val b = data[j].toInt() and 0xFF
                                    metaLen = (metaLen shl 7) or (b and 0x7F)
                                    j++
                                } while ((data[j - 1].toInt() and 0x80) != 0)
                                i = j + metaLen
                            } else {
                                // sysex: VLQ length + data
                                var j = i + 1
                                var sysexLen = 0
                                do {
                                    val b = data[j].toInt() and 0xFF
                                    sysexLen = (sysexLen shl 7) or (b and 0x7F)
                                    j++
                                } while ((data[j - 1].toInt() and 0x80) != 0)
                                i = j + sysexLen
                            }
                        }
                        else -> i++
                    }
                }
            }
            pos = bodyEnd
        }
        return events
    }

    private fun countStatusByte(data: ByteArray, status: Int): Int {
        val isOn = (status and 0xF0) == 0x90
        return parseNoteEvents(data).count { it.first == isOn }
    }

    private fun findFirstNoteOn(data: ByteArray): Pair<Int, Int>? =
        parseNoteEvents(data).firstOrNull { it.first }?.let { it.second to it.third }

    private fun findTempoMeta(data: ByteArray): Long? {
        for (i in 14 until data.size - 5) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0x51) {
                val mpqn = ((data[i + 3].toInt() and 0xFF) shl 16) or
                        ((data[i + 4].toInt() and 0xFF) shl 8) or
                        (data[i + 5].toInt() and 0xFF)
                return mpqn.toLong()
            }
        }
        return null
    }

    private fun findTimeSignatureMeta(data: ByteArray): Pair<Int, Int>? {
        for (i in 14 until data.size - 5) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0x58) {
                val num = data[i + 3].toInt()
                val denomPower = data[i + 4].toInt()
                return num to denomPower
            }
        }
        return null
    }

    private fun containsAscii(data: ByteArray, text: String): Boolean {
        val target = text.toByteArray(Charsets.US_ASCII)
        return countOccurrences(data, target) > 0
    }

    private fun containsText(data: ByteArray, text: String): Boolean {
        return countOccurrences(data, text.toByteArray(Charsets.UTF_8)) > 0
    }
}
