package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import java.io.InputStream

/**
 * Parses standard MIDI files (Format 0 and 1) into [Score] objects.
 *
 * Reads the binary MIDI chunk structure (MThd + MTrk), resolves running
 * status, handles meta events (tempo, time signature, track name), and
 * converts Note On/Off events into [ScoreNote]s with absolute timestamps.
 *
 * Supports Format 0 (single track) and Format 1 (multiple tracks merged
 * by absolute time).
 */
class MidiParser {

    fun parse(inputStream: InputStream): Score {
        val data = inputStream.readBytes()
        val reader = MidiDataReader(data)

        var format = 0
        var numTracks = 1
        var division = 480 // ticks per quarter note (PPQ)
        var tempo = 120    // BPM
        var beats = 4
        var beatType = 4
        var title = "Imported MIDI"
        val allNotes = mutableListOf<ScoreNote>()

        // Read header chunk
        val headerId = reader.readAscii(4)
        if (headerId != "MThd") throw IllegalArgumentException("Not a valid MIDI file")

        reader.readInt() // header length (should be 6)
        format = reader.readShort()
        numTracks = reader.readShort()
        division = reader.readShort()

        // Read each track
        for (trackIdx in 0 until numTracks) {
            val trackId = reader.readAscii(4)
            if (trackId != "MTrk") {
                // Skip unknown chunk
                val len = reader.readInt()
                reader.skip(len.toLong())
                continue
            }

            // 先读取轨道长度（readInt 会推进 position 到轨道体起始），
            // 再用推进后的 position 计算 trackEnd。此前写法 `reader.position + reader.readInt()`
            // 会用 readInt 推进前的 position（即长度字段偏移），导致 trackEnd 偏小 4 字节——
            // 单轨(Format 0)文件仅丢失末尾 EOT 故不影响音符，但多轨(Format 1)文件会使
            // 后续轨道错位、音符无法解析。此处修复以正确支持多轨 MIDI 导入与导出往返。
            val trackLength = reader.readInt()
            val trackEnd = reader.position + trackLength
            var absoluteTicks = 0L
            var runningStatus = 0
            val activeNotes = mutableMapOf<Int, MutableList<Pair<Long, Int>>>() // midi -> [(startTime, velocity)]
            var trackName = ""

            while (reader.position < trackEnd) {
                val delta = reader.readVarLen()
                absoluteTicks += delta

                var status = reader.readByte().toInt() and 0xFF

                // Running status: if high bit not set, reuse previous status
                if (status < 0x80) {
                    reader.pushBack(status)
                    status = runningStatus
                } else {
                    runningStatus = status
                }

                val highNibble = status and 0xF0

                when (highNibble) {
                    0x80 -> { // Note Off
                        val note = reader.readByte().toInt() and 0xFF
                        reader.readByte() // velocity (ignored)
                        // Find matching note-on and finalize
                        activeNotes[note]?.let { actives ->
                            if (actives.isNotEmpty()) {
                                val (startTicks, velocity) = actives.removeAt(0)
                                val (startTime, duration) = ticksToTime(startTicks, absoluteTicks, division, tempo)
                                allNotes.add(buildScoreNote(note, velocity, startTime, duration))
                            }
                        }
                    }
                    0x90 -> { // Note On
                        val note = reader.readByte().toInt() and 0xFF
                        val velocity = reader.readByte().toInt() and 0xFF
                        if (velocity == 0) {
                            // velocity 0 = note off
                            activeNotes[note]?.let { actives ->
                                if (actives.isNotEmpty()) {
                                    val (startTicks, vel) = actives.removeAt(0)
                                    val (startTime, duration) = ticksToTime(startTicks, absoluteTicks, division, tempo)
                                    allNotes.add(buildScoreNote(note, vel, startTime, duration))
                                }
                            }
                        } else {
                            activeNotes.getOrPut(note) { mutableListOf() }.add(absoluteTicks to velocity)
                        }
                    }
                    0xA0, 0xE0 -> { // Poly Aftertouch, Pitch Bend — skip 2 bytes
                        reader.readByte(); reader.readByte()
                    }
                    0xB0 -> { // CC — skip 2 bytes
                        reader.readByte(); reader.readByte()
                    }
                    0xC0, 0xD0 -> { // Program Change, Channel Pressure — skip 1 byte
                        reader.readByte()
                    }
                    0xF0 -> { // System / Meta
                        when (status) {
                            0xFF -> { // Meta event
                                val metaType = reader.readByte().toInt() and 0xFF
                                val length = reader.readVarLen().toInt()
                                when (metaType) {
                                    0x03 -> { // Track/Sequence Name
                                        trackName = reader.readAscii(length)
                                        if (title == "Imported MIDI" && trackName.isNotBlank()) title = trackName
                                    }
                                    0x51 -> { // Set Tempo (microseconds per quarter note)
                                        if (length >= 3) {
                                            val mpqn = ((reader.readByte().toInt() and 0xFF) shl 16) or
                                                    ((reader.readByte().toInt() and 0xFF) shl 8) or
                                                    (reader.readByte().toInt() and 0xFF)
                                            if (mpqn > 0) tempo = (60000000.0 / mpqn).toInt()
                                        }
                                        reader.skip((length - 3).toLong())
                                    }
                                    0x58 -> { // Time Signature
                                        if (length >= 4) {
                                            beats = reader.readByte().toInt()
                                            val denom = reader.readByte().toInt()
                                            beatType = 1 shl denom
                                            reader.readByte() // clocks per click
                                            reader.readByte() // 32nd notes per quarter
                                        }
                                        reader.skip((length - 4).toLong())
                                    }
                                    0x2F -> { // End of Track
                                        break
                                    }
                                    else -> reader.skip(length.toLong())
                                }
                            }
                            0xF0, 0xF7 -> { // SysEx
                                val length = reader.readVarLen().toInt()
                                reader.skip(length.toLong())
                            }
                            else -> {
                                // Unknown system common — skip remaining
                            }
                        }
                    }
                }
            }

            // Close any still-active notes at track end
            activeNotes.forEach { (note, actives) ->
                actives.forEach { (startTicks, velocity) ->
                    val (startTime, duration) = ticksToTime(startTicks, absoluteTicks, division, tempo)
                    allNotes.add(buildScoreNote(note, velocity, startTime, duration))
                }
            }

            reader.position = trackEnd
        }

        // Sort by start time
        allNotes.sortBy { it.startTime }

        return Score(
            id = System.currentTimeMillis().toString(),
            title = title,
            composer = "MIDI Import",
            notes = allNotes,
            tempo = tempo,
            timeSignature = "$beats/$beatType",
            source = ScoreSource.MIDI
        )
    }

    private fun buildScoreNote(midiNumber: Int, velocity: Int, startTime: Long, duration: Long): ScoreNote {
        return ScoreNote(
            midiNumber = midiNumber,
            noteName = MusicUtils.midiToNoteName(midiNumber),
            startTime = startTime,
            duration = duration,
            velocity = velocity,
            staff = if (midiNumber >= 60) Staff.TREBLE else Staff.BASS,
            measureIndex = 0
        )
    }

    /**
     * Convert ticks to milliseconds.
     * ticksPerQuarter = division (PPQ mode)
     * microsecondsPerQuarter = 60000000 / tempo
     */
    private fun ticksToTime(
        startTicks: Long,
        endTicks: Long,
        division: Int,
        tempo: Int
    ): Pair<Long, Long> {
        val msPerTick = 60000.0 / (tempo * division)
        val startTime = (startTicks * msPerTick).toLong()
        val duration = ((endTicks - startTicks) * msPerTick).toLong()
        return startTime to maxOf(duration, 50L) // min 50ms to avoid zero-length
    }
}

/**
 * Helper class for reading MIDI binary data with push-back support.
 */
private class MidiDataReader(private val data: ByteArray) {
    var position: Int = 0
        internal set

    private var pushedBack: Int = -1

    fun readByte(): Byte {
        if (pushedBack >= 0) {
            val b = pushedBack.toByte()
            pushedBack = -1
            return b
        }
        return data[position++]
    }

    fun pushBack(value: Int) {
        pushedBack = value
    }

    fun readShort(): Int {
        val high = readByte().toInt() and 0xFF
        val low = readByte().toInt() and 0xFF
        return (high shl 8) or low
    }

    fun readInt(): Int {
        val b0 = readByte().toInt() and 0xFF
        val b1 = readByte().toInt() and 0xFF
        val b2 = readByte().toInt() and 0xFF
        val b3 = readByte().toInt() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readAscii(length: Int): String {
        // 按 UTF-8 解码（与 ASCII 完全兼容）。此前按字节逐个 toChar() 实为 Latin-1 解码，
        // 会导致非 ASCII 曲名（如 "Für Elise"、"测试乐谱"）在导入时乱码。
        // chunk 标识 "MThd"/"MTrk" 为纯 ASCII，UTF-8 解码结果不变。
        val bytes = ByteArray(length)
        for (k in 0 until length) bytes[k] = readByte()
        return String(bytes, Charsets.UTF_8)
    }

    fun readVarLen(): Long {
        var value: Long = 0
        var byte: Int
        do {
            byte = readByte().toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F).toLong()
        } while ((byte and 0x80) != 0)
        return value
    }

    fun skip(n: Long) {
        if (pushedBack >= 0) {
            pushedBack = -1
            if (n <= 1) return
            position += (n - 1).toInt()
        } else {
            position += n.toInt()
        }
    }
}
