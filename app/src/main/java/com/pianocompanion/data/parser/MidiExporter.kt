package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import java.io.ByteArrayOutputStream

/**
 * 将 [Score] 序列化为标准 MIDI 文件 (Standard MIDI File, SMF, Format 1)。
 *
 * 这是 [MidiParser]（SMF → Score）的对称反向操作：拍照识谱 / MusicXML / 已有 MIDI
 * 导入得到的 [Score]，均可重新导出为标准 `.mid` 文件——可在任意 DAW、媒体播放器、
 * 数码钢琴中播放，或作为练习伴奏轨使用。这使「导入 ↔ 导出」形成完整闭环。
 *
 * **文件结构 (Format 1)：**
 * - Track 0（指挥轨）：tempo（Set Tempo 0x51）、拍号（Time Signature 0x58）、
 *   曲名（Track Name 0x03）等 meta 事件，全部位于 tick 0。
 * - Track 1（音符轨）：所有 [ScoreNote] 转换为 Note On / Note Off 事件，
 *   按 tick 排序后以 delta-time 编码输出。
 *
 * **时间转换：** [MidiParser] 用 `msPerTick = 60000.0 / (tempo * division)` 将 tick 转 ms，
 * 故本导出器用其逆运算 `tick = ms * tempo * division / 60000` 将 ms 转回 tick，
 * 保证导出 → 导入的 pitch 与 timing 可靠往返（round-trip）。
 *
 * 本类为纯 Kotlin 实现（无 Android 依赖），可完全单元测试。
 *
 * @param division 每四分音符的 tick 数 (PPQ 分辨率)，默认 480，与 [MidiParser] 一致。
 */
class MidiExporter(private val division: Int = 480) {

    /**
     * 将 [score] 序列化为 Format 1 Standard MIDI File 的字节数组。
     *
     * @return 合法的 SMF `.mid` 文件二进制内容。
     */
    fun export(score: Score): ByteArray {
        val tempo = if (score.tempo > 0) score.tempo else 120
        val tracks = mutableListOf<ByteArray>()

        // Track 0: 指挥轨 (tempo / 拍号 / 曲名)
        tracks.add(buildConductorTrack(score, tempo))

        // Track 1: 音符轨 (所有 Note On/Off)
        val noteEvents = score.notes.flatMap { noteToEvents(it, tempo) }
        tracks.add(buildNoteTrack(noteEvents))

        return assembleFile(format = 1, tracks = tracks)
    }

    // ----------------------------------------------------------------------
    // 公开工具函数（纯函数，便于单元测试）
    // ----------------------------------------------------------------------

    /**
     * 将毫秒时间转换为 MIDI tick。
     * `tick = ms * tempo * division / 60000`，与 [MidiParser] 的 ticksToTime 互逆。
     */
    fun msToTicks(ms: Long, tempo: Int): Long {
        if (ms <= 0) return 0L
        return (ms.toDouble() * tempo * division / 60000.0).toLong().coerceAtLeast(0L)
    }

    /**
     * 将非负整数编码为 MIDI 变长字节序列 (Variable Length Quantity, VLQ)。
     *
     * 每字节低 7 位为数据，最高位 (0x80) 为「后续仍有字节」标志；最高有效组在前。
     * 与 [MidiParser] 的 `MidiDataReader.readVarLen` 互逆。最大 4 字节 (28 位)。
     */
    fun encodeVarLen(value: Long): ByteArray {
        if (value < 0) return byteArrayOf(0)
        var buffer = (value and 0x7F).toInt()
        var v = value ushr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F or 0x80L).toInt())
            v = v ushr 7
        }
        val out = ByteArrayOutputStream()
        while (true) {
            out.write(buffer and 0xFF)
            if ((buffer and 0x80) == 0) break
            buffer = buffer ushr 8
        }
        return out.toByteArray()
    }

    // ----------------------------------------------------------------------
    // 内部构建逻辑
    // ----------------------------------------------------------------------

    /** 构建指挥轨：tempo、拍号、曲名，全部位于 tick 0。 */
    private fun buildConductorTrack(score: Score, tempo: Int): ByteArray {
        val events = mutableListOf<MidiEvent>()

        // 曲名 (Track Name, 0x03)
        events += MidiEvent(0L, nameMeta(score.title))

        // 速度 (Set Tempo, 0x51): microseconds-per-quarter-note, 3 字节
        events += MidiEvent(0L, tempoMeta(tempo))

        // 拍号 (Time Signature, 0x58): 4 字节
        events += MidiEvent(0L, timeSignatureMeta(score.timeSignature))

        return buildTrack(events)
    }

    /** 构建音符轨：所有 Note On/Off 按 tick 排序后 delta-time 编码。 */
    private fun buildNoteTrack(events: List<MidiEvent>): ByteArray {
        return buildTrack(events)
    }

    /**
     * 将一组 [MidiEvent] 按 tick 排序，转换为带 MTrk 头的字节块。
     * 末尾追加 End of Track (0xFF 0x2F 0x00) meta 事件。
     */
    private fun buildTrack(events: List<MidiEvent>): ByteArray {
        val sorted = events.sortedBy { it.tick }
        val out = ByteArrayOutputStream()
        var lastTick = 0L
        for (e in sorted) {
            val delta = e.tick - lastTick
            out.write(encodeVarLen(delta))
            out.write(e.data)
            lastTick = e.tick
        }
        // End of Track (delta 0，紧跟最后一个事件)
        out.write(encodeVarLen(0L))
        out.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

        val body = out.toByteArray()
        val header = ByteArrayOutputStream()
        header.write(MTHD_TRACK_ID.toByteArray(Charsets.US_ASCII))
        header.write(intToBytes(body.size))
        header.write(body)
        return header.toByteArray()
    }

    /** 将单个音符转换为 Note On / Note Off 两个事件。 */
    private fun noteToEvents(note: ScoreNote, tempo: Int): List<MidiEvent> {
        val pitch = note.midiNumber.coerceIn(0, 127)
        // velocity: 0 视为默认力度；MIDI Note On velocity 0 等价于 Note Off，故下限为 1。
        val velocity = when {
            note.velocity <= 0 -> 64
            note.velocity > 127 -> 127
            else -> note.velocity
        }
        val startTick = msToTicks(note.startTime, tempo)
        val endTick = msToTicks(note.endTime, tempo)
        val durationTicks = (endTick - startTick).coerceAtLeast(1L)
        val noteChannel = 0x90.toByte() // Note On, channel 0
        val offChannel = 0x80.toByte()  // Note Off, channel 0
        val onEvent = MidiEvent(startTick, byteArrayOf(noteChannel, pitch.toByte(), velocity.toByte()))
        val offEvent = MidiEvent(startTick + durationTicks, byteArrayOf(offChannel, pitch.toByte(), 0))
        return listOf(onEvent, offEvent)
    }

    /** 组装完整 SMF：MThd 头 + 各 MTrk 轨道。 */
    private fun assembleFile(format: Int, tracks: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MTHD_HEADER_ID.toByteArray(Charsets.US_ASCII))
        out.write(intToBytes(6)) // header length 固定 6
        out.write(shortToBytes(format))
        out.write(shortToBytes(tracks.size))
        out.write(shortToBytes(division))
        for (track in tracks) out.write(track)
        return out.toByteArray()
    }

    // ----------------------------------------------------------------------
    // Meta 事件构造
    // ----------------------------------------------------------------------

    /** Track Name (0x03) meta 事件。 */
    private fun nameMeta(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(0xFF.toByte().toInt())
        out.write(0x03)
        out.write(encodeVarLen(bytes.size.toLong()))
        out.write(bytes)
        return out.toByteArray()
    }

    /** Set Tempo (0x51) meta 事件：microseconds-per-quarter-note，3 字节大端。 */
    private fun tempoMeta(tempo: Int): ByteArray {
        val mpqn = (60000000.0 / tempo).toLong().coerceIn(0L, 0xFFFFFFL).toInt()
        return byteArrayOf(
            0xFF.toByte(),
            0x51,
            0x03,
            ((mpqn ushr 16) and 0xFF).toByte(),
            ((mpqn ushr 8) and 0xFF).toByte(),
            (mpqn and 0xFF).toByte()
        )
    }

    /** Time Signature (0x58) meta 事件：分子、分母(2的幂)、clocks/click、32分音符/四分音符。 */
    private fun timeSignatureMeta(signature: String): ByteArray {
        val (numerator, denomPower) = parseTimeSignature(signature)
        return byteArrayOf(
            0xFF.toByte(),
            0x58,
            0x04,
            numerator.coerceIn(1, 255).toByte(),
            denomPower.coerceIn(0, 255).toByte(),
            24, // clocks per metronome click (标准值)
            8   // 32nd notes per quarter (标准值)
        )
    }

    /** 解析 "beats/beatType" 字符串为 (numerator, log2(beatType))。默认 4/4。 */
    private fun parseTimeSignature(signature: String): Pair<Int, Int> {
        val parts = signature.split("/")
        val beats = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 4
        val beatType = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 4
        val denomPower = if (beatType > 0) 31 - Integer.numberOfLeadingZeros(beatType) else 2
        return beats to denomPower
    }

    // ----------------------------------------------------------------------
    // 字节序工具
    // ----------------------------------------------------------------------

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    /** 一个带绝对 tick 时间戳的 MIDI 事件（channel/meta 皆可）。 */
    private data class MidiEvent(val tick: Long, val data: ByteArray)

    companion object {
        private const val MTHD_HEADER_ID = "MThd"
        private const val MTHD_TRACK_ID = "MTrk"
    }
}
