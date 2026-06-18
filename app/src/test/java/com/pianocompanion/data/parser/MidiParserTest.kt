package com.pianocompanion.data.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MidiParserTest {

    /**
     * Build a minimal MIDI file (Format 0) with the given note events.
     * Each event is (tick, note, velocity, isOn).
     */
    private fun buildMidi(
        notes: List<IntArray>,
        tempo: Int = 120,
        division: Int = 480
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()

        // --- Header chunk ---
        out.write("MThd".toByteArray())
        writeInt32(out, 6)           // header length
        writeInt16(out, 0)           // format 0
        writeInt16(out, 1)           // 1 track
        writeInt16(out, division)    // ticks per quarter note

        // --- Track chunk ---
        val trackData = java.io.ByteArrayOutputStream()

        // Set Tempo meta event
        val mpqn = 60000000 / tempo
        writeVarLen(trackData, 0) // delta=0
        trackData.write(0xFF)
        trackData.write(0x51)
        writeVarLen(trackData, 3)
        trackData.write((mpqn shr 16) and 0xFF)
        trackData.write((mpqn shr 8) and 0xFF)
        trackData.write(mpqn and 0xFF)

        // Sort notes by tick
        val sorted = notes.sortedBy { it[0] }
        var lastTick = 0
        for (event in sorted) {
            val tick = event[0]
            val note = event[1]
            val vel = event[2]
            val isOn = event[3] == 1

            writeVarLen(trackData, (tick - lastTick).toLong())
            lastTick = tick

            if (isOn) {
                trackData.write(0x90) // Note On, channel 0
                trackData.write(note)
                trackData.write(vel)
            } else {
                trackData.write(0x80) // Note Off, channel 0
                trackData.write(note)
                trackData.write(vel)
            }
        }

        // End of Track meta event
        writeVarLen(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x2F)
        writeVarLen(trackData, 0)

        val trackBytes = trackData.toByteArray()
        out.write("MTrk".toByteArray())
        writeInt32(out, trackBytes.size)
        out.write(trackBytes)

        return out.toByteArray()
    }

    private fun writeInt32(out: java.io.OutputStream, v: Int) {
        out.write((v shr 24) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeInt16(out: java.io.OutputStream, v: Int) {
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeVarLen(out: java.io.OutputStream, value: Long) {
        val buffer = mutableListOf<Int>()
        var v = value
        buffer.add(0, (v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            buffer.add(0, ((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        for (b in buffer) out.write(b)
    }

    @Test
    fun `parses single note MIDI file`() {
        // C4 (MIDI 60), Note On at tick 0, Note Off at tick 480 (1 quarter note)
        val midi = buildMidi(listOf(
            intArrayOf(0, 60, 100, 1),
            intArrayOf(480, 60, 0, 0)
        ))
        val parser = MidiParser()
        val score = parser.parse(ByteArrayInputStream(midi))

        assertEquals(1, score.notes.size)
        assertEquals(60, score.notes[0].midiNumber)
        assertEquals(120, score.tempo)
        assertEquals("C4", score.notes[0].noteName)
    }

    @Test
    fun `parses multiple sequential notes`() {
        // Three notes: C4, D4, E4 each lasting 480 ticks
        val midi = buildMidi(listOf(
            intArrayOf(0,   60, 100, 1),
            intArrayOf(480, 60, 0,   0),
            intArrayOf(480, 62, 100, 1),
            intArrayOf(960, 62, 0,   0),
            intArrayOf(960, 64, 100, 1),
            intArrayOf(1440, 64, 0,  0)
        ))
        val score = MidiParser().parse(ByteArrayInputStream(midi))

        assertEquals(3, score.notes.size)
        assertEquals(60, score.notes[0].midiNumber)
        assertEquals(62, score.notes[1].midiNumber)
        assertEquals(64, score.notes[2].midiNumber)
        // At 120 BPM, 480 ticks = 1 quarter = 500ms
        assertEquals(0L, score.notes[0].startTime)
        assertEquals(500L, score.notes[1].startTime)
        assertEquals(1000L, score.notes[2].startTime)
    }

    @Test
    fun `parses note on velocity zero as note off`() {
        val midi = buildMidi(listOf(
            intArrayOf(0,   60, 100, 1),
            intArrayOf(480, 60, 0,   1)  // velocity 0 note-on = note off
        ))
        val score = MidiParser().parse(ByteArrayInputStream(midi))
        assertEquals(1, score.notes.size)
        assertEquals(100, score.notes[0].velocity)
    }

    @Test
    fun `respects tempo change via meta event`() {
        // tempo=60 BPM → 480 ticks = 1 second = 1000ms
        val midi = buildMidi(
            notes = listOf(
                intArrayOf(0, 60, 100, 1),
                intArrayOf(480, 60, 0, 0)
            ),
            tempo = 60
        )
        val score = MidiParser().parse(ByteArrayInputStream(midi))
        assertEquals(60, score.tempo)
        assertEquals(1000L, score.notes[0].duration)
    }

    @Test
    fun `extracts title from track name meta event`() {
        val out = java.io.ByteArrayOutputStream()
        // Header
        out.write("MThd".toByteArray())
        writeInt32(out, 6)
        writeInt16(out, 0); writeInt16(out, 1); writeInt16(out, 480)

        // Track with name
        val track = java.io.ByteArrayOutputStream()
        writeVarLen(track, 0)
        track.write(0xFF); track.write(0x03) // Track Name
        val name = "Test Song"
        writeVarLen(track, name.length.toLong())
        track.write(name.toByteArray())
        // End of track
        writeVarLen(track, 0)
        track.write(0xFF); track.write(0x2F); writeVarLen(track, 0)

        val trackBytes = track.toByteArray()
        out.write("MTrk".toByteArray())
        writeInt32(out, trackBytes.size)
        out.write(trackBytes)

        val score = MidiParser().parse(ByteArrayInputStream(out.toByteArray()))
        assertEquals("Test Song", score.title)
    }

    @Test
    fun `staff assignment based on pitch`() {
        val midi = buildMidi(listOf(
            intArrayOf(0,   48, 100, 1),  // C3 → BASS
            intArrayOf(240, 48, 0,   0),
            intArrayOf(240, 72, 100, 1),  // C5 → TREBLE
            intArrayOf(720, 72, 0,   0)
        ))
        val score = MidiParser().parse(ByteArrayInputStream(midi))
        assertEquals(com.pianocompanion.data.model.Staff.BASS, score.notes[0].staff)
        assertEquals(com.pianocompanion.data.model.Staff.TREBLE, score.notes[1].staff)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-MIDI data`() {
        MidiParser().parse(ByteArrayInputStream("not midi".toByteArray()))
    }
}
