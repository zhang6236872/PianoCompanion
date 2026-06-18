package com.pianocompanion.data.parser

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parses MusicXML files into Score objects.
 *
 * Supports MusicXML 3.0+ format. Extracts notes with pitch, timing,
 * duration, and staff information.
 */
class MusicXmlParser {

    fun parse(inputStream: InputStream): Score {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val notes = mutableListOf<ScoreNote>()
        var title = "Unknown"
        var composer = "Unknown"
        var tempo = 120
        var beats = 4
        var beatType = 4

        // Parsing state
        var currentDivisions = 1  // divisions per quarter note
        var currentMeasure = 0
        var currentTimeInMeasure = 0L
        var measureStartTime = 0L

        var step = ""
        var alter = 0
        var octave = 0
        var duration = 0
        var isRest = false
        var staffNum = 1
        var velocity = 64

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "work-title" -> { parser.next(); if (parser.text != null) title = parser.text.trim() }
                        "creator" -> {
                            if (parser.getAttributeValue(null, "type") == "composer") {
                                parser.next(); if (parser.text != null) composer = parser.text.trim()
                            }
                        }
                        "divisions" -> { parser.next(); currentDivisions = parser.text.trim().toIntOrNull() ?: 1 }
                        "measure" -> {
                            currentMeasure = parser.getAttributeValue(null, "number")?.toIntOrNull() ?: currentMeasure + 1
                            currentTimeInMeasure = 0
                        }
                        "beats" -> { parser.next(); beats = parser.text.trim().toIntOrNull() ?: 4 }
                        "beat-type" -> { parser.next(); beatType = parser.text.trim().toIntOrNull() ?: 4 }
                        "sound" -> {
                            val t = parser.getAttributeValue(null, "tempo")
                            if (t != null) tempo = t.toIntOrNull() ?: 120
                        }
                        "step" -> { parser.next(); step = parser.text?.trim() ?: "" }
                        "alter" -> { parser.next(); alter = parser.text?.trim()?.toIntOrNull() ?: 0 }
                        "octave" -> { parser.next(); octave = parser.text?.trim()?.toIntOrNull() ?: 4 }
                        "duration" -> { parser.next(); duration = parser.text?.trim()?.toIntOrNull() ?: 0 }
                        "rest" -> { isRest = true }
                        "staff" -> { parser.next(); staffNum = parser.text?.trim()?.toIntOrNull() ?: 1 }
                        "velocity" -> { velocity = parser.getAttributeValue(null, "velocity")?.toIntOrNull() ?: 64 }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "note" -> {
                            if (!isRest && step.isNotEmpty()) {
                                val noteName = step.uppercase() + (if (alter == 1) "#" else "") + octave
                                val midi = MusicUtils.noteNameToMidi(noteName)
                                val msDuration = (duration * 60000L) / (currentDivisions * tempo)

                                notes.add(ScoreNote(
                                    midiNumber = midi,
                                    noteName = noteName,
                                    startTime = measureStartTime + currentTimeInMeasure,
                                    duration = msDuration,
                                    velocity = velocity,
                                    staff = if (staffNum == 1) Staff.TREBLE else Staff.BASS,
                                    measureIndex = currentMeasure
                                ))
                            }
                            currentTimeInMeasure += duration
                            // Reset for next note
                            step = ""
                            alter = 0
                            isRest = false
                            duration = 0
                        }
                        "measure" -> {
                            val measureDuration = (beats * 4 * 60000L) / (beatType * tempo)
                            measureStartTime += measureDuration
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return Score(
            id = System.currentTimeMillis().toString(),
            title = title,
            composer = composer,
            notes = notes,
            tempo = tempo,
            timeSignature = "$beats/$beatType",
            source = ScoreSource.MUSIC_XML
        )
    }
}
