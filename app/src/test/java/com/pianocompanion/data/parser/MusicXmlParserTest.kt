package com.pianocompanion.data.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MusicXmlParserTest {

    /** A minimal valid partwise MusicXML document used to exercise import. */
    private val sampleMusicXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <score-partwise version="3.0">
          <work>
            <work-title>测试曲</work-title>
          </work>
          <identification>
            <creator type="composer">测试作曲家</creator>
          </identification>
          <part id="P1">
            <measure number="1">
              <attributes>
                <divisions>1</divisions>
                <time><beats>4</beats><beat-type>4</beat-type></time>
                <sound tempo="120"/>
              </attributes>
              <note>
                <pitch><step>C</step><octave>4</octave></pitch>
                <duration>1</duration>
              </note>
              <note>
                <pitch><step>D</step><octave>4</octave></pitch>
                <duration>1</duration>
              </note>
              <note>
                <rest/>
                <duration>1</duration>
              </note>
              <note>
                <pitch><step>F</step><alter>1</alter><octave>5</octave></pitch>
                <duration>1</duration>
              </note>
            </measure>
          </part>
        </score-partwise>
    """.trimIndent()

    private fun parser() = MusicXmlParser()

    @Test
    fun `parses title and composer from MusicXML`() {
        val score = parser().parse(ByteArrayInputStream(sampleMusicXml.toByteArray()))
        assertEquals("测试曲", score.title)
        assertEquals("测试作曲家", score.composer)
    }

    @Test
    fun `parses pitch notes and skips rests`() {
        val score = parser().parse(ByteArrayInputStream(sampleMusicXml.toByteArray()))
        // 3 pitched notes (C4, D4, F#5) + 1 rest which must be skipped.
        assertEquals(3, score.notes.size)
    }

    @Test
    fun `parses accidental sharp note correctly`() {
        val score = parser().parse(ByteArrayInputStream(sampleMusicXml.toByteArray()))
        val sharp = score.notes.last()
        assertEquals("F#5", sharp.noteName)
        assertEquals(78, sharp.midiNumber) // F#5 == MIDI 78
    }

    @Test
    fun `parses default tempo and time signature`() {
        val score = parser().parse(ByteArrayInputStream(sampleMusicXml.toByteArray()))
        assertEquals(120, score.tempo)
        assertEquals("4/4", score.timeSignature)
    }
}
