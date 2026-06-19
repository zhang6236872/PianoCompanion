package com.pianocompanion.ui.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.Staff
import com.pianocompanion.util.MusicUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compose-based staff notation renderer.
 * Draws treble & bass clefs staves with:
 * - Notes (filled = quarter note, hollow = half note)
 * - Accidentals (sharps ♯ / flats ♭)
 * - Rests (quarter/half/whole)
 * - Bar lines
 * - Ledger lines
 * - Current position highlighting
 */
@Composable
fun ScoreRenderer(
    notes: List<ScoreNote>,
    currentPosition: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        StaffView(
            notes = notes.filter {
                it.staff == Staff.TREBLE || it.staff == Staff.BOTH ||
                    it.staff == Staff.ALTO || it.staff == Staff.TENOR
            },
            currentPosition = currentPosition,
            isTreble = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        StaffView(
            notes = notes.filter { it.staff == Staff.BASS || it.staff == Staff.BOTH },
            currentPosition = currentPosition,
            isTreble = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun StaffView(
    notes: List<ScoreNote>,
    currentPosition: Int,
    isTreble: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFEF7))
    ) {
        val w = size.width
        val h = size.height

        // Staff parameters
        val staffTop = h * 0.2f
        val staffBottom = h * 0.8f
        val lineSpacing = (staffBottom - staffTop) / 4f
        val leftMargin = 60f
        val rightMargin = 20f
        val drawableWidth = w - leftMargin - rightMargin

        // Draw 5 staff lines
        val staffColor = Color(0xFF333333)
        for (i in 0..4) {
            val y = staffTop + i * lineSpacing
            drawLine(
                color = staffColor,
                start = Offset(leftMargin, y),
                end = Offset(w - rightMargin, y),
                strokeWidth = 1.5f
            )
        }

        // Draw bar line at start and end
        drawLine(staffColor, Offset(leftMargin, staffTop), Offset(leftMargin, staffBottom), 2f)
        drawLine(staffColor, Offset(w - rightMargin, staffTop), Offset(w - rightMargin, staffBottom), 2f)

        // Draw clef
        drawClef(isTreble, leftMargin - 45f, staffTop, lineSpacing)

        // Draw notes
        if (notes.isEmpty()) return@Canvas

        val visibleCount = minOf(notes.size, 16)
        val startPos = maxOf(0, currentPosition - 4)
        val endPos = minOf(startPos + visibleCount, notes.size)
        val noteSpacing = drawableWidth / visibleCount

        var lastMeasure = -1

        for (i in startPos until endPos) {
            val note = notes[i]
            if (note.staff != Staff.BOTH &&
                note.staff != Staff.ALTO &&
                note.staff != Staff.TENOR &&
                note.staff != (if (isTreble) Staff.TREBLE else Staff.BASS)
            ) continue

            val displayIdx = i - startPos
            val x = leftMargin + displayIdx * noteSpacing + noteSpacing / 2

            // Draw bar line when measure changes
            if (note.measureIndex != lastMeasure && lastMeasure >= 0) {
                val barX = x - noteSpacing / 2
                drawLine(
                    color = staffColor.copy(alpha = 0.4f),
                    start = Offset(barX, staffTop),
                    end = Offset(barX, staffBottom),
                    strokeWidth = 1f
                )
            }
            lastMeasure = note.measureIndex

            // Convert MIDI to staff position
            val stepFromBottom = midiToStaffSteps(note.midiNumber, isTreble)
            val y = staffBottom - stepFromBottom * (lineSpacing / 2f)

            val isCurrent = i == currentPosition
            val isPast = i < currentPosition

            // Draw ledger lines if needed
            drawLedgerLines(x, y, staffTop, staffBottom, lineSpacing, staffColor)

            // Draw accidental (sharp/flat) if needed
            val pitchClass = note.midiNumber % 12
            val accidentalType = when (pitchClass) {
                1, 3, 6, 8, 10 -> getAccidentalType(note.noteName)
                else -> null
            }
            if (accidentalType != null) {
                drawAccidental(accidentalType, x - noteSpacing * 0.25f, y, lineSpacing)
            }

            // Determine note type by duration
            val isHalfNote = note.duration > 1000L
            val isWholeNote = note.duration > 2000L

            // Draw note head
            val noteColor = when {
                isCurrent -> Color(0xFF4CAF50)
                isPast -> Color(0xFF999999)
                else -> Color(0xFF1A1A1A)
            }
            val noteRadius = lineSpacing * 0.35f

            if (isWholeNote) {
                // Hollow oval for whole note, no stem
                drawOval(
                    color = noteColor,
                    topLeft = Offset(x - noteRadius, y - noteRadius * 0.7f),
                    size = Size(noteRadius * 2, noteRadius * 1.4f),
                    style = Stroke(width = 2f)
                )
            } else if (isHalfNote) {
                // Hollow oval for half note
                drawOval(
                    color = noteColor,
                    topLeft = Offset(x - noteRadius, y - noteRadius * 0.7f),
                    size = Size(noteRadius * 2, noteRadius * 1.4f),
                    style = Stroke(width = 2f)
                )
                drawStem(noteColor, x, y, noteRadius, lineSpacing, stemUp = true)
            } else {
                // Filled oval for quarter note (default)
                drawOval(
                    color = noteColor,
                    topLeft = Offset(x - noteRadius, y - noteRadius * 0.7f),
                    size = Size(noteRadius * 2, noteRadius * 1.4f)
                )
                drawStem(noteColor, x, y, noteRadius, lineSpacing, stemUp = true)
            }

            // Highlight current note with a circle
            if (isCurrent) {
                drawOval(
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    topLeft = Offset(x - noteRadius * 2, y - noteRadius * 2),
                    size = Size(noteRadius * 4, noteRadius * 4)
                )
            }

            // Draw note name below (for beginners)
            if (isCurrent) {
                drawContext.canvas.nativeCanvas.drawText(
                    note.noteName,
                    x - 15,
                    staffBottom + 25,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#4CAF50")
                        textSize = 28f
                        isFakeBoldText = true
                    }
                )
            }
        }
    }
}

/** Determine if the accidental is a sharp or flat from the note name */
private fun getAccidentalType(noteName: String): AccidentalType {
    return if (noteName.contains("#")) AccidentalType.SHARP
    else if (noteName.contains("b") || noteName.contains("♭")) AccidentalType.FLAT
    else AccidentalType.SHARP
}

enum class AccidentalType { SHARP, FLAT, NATURAL }

/**
 * Draw an accidental symbol (sharp ♯ / flat ♭ / natural ♮)
 */
private fun DrawScope.drawAccidental(
    type: AccidentalType,
    x: Float,
    y: Float,
    lineSpacing: Float
) {
    val symbol = when (type) {
        AccidentalType.SHARP -> "♯"
        AccidentalType.FLAT -> "♭"
        AccidentalType.NATURAL -> "♮"
    }
    drawContext.canvas.nativeCanvas.drawText(
        symbol,
        x,
        y + lineSpacing * 0.35f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#333333")
            textSize = lineSpacing * 1.8f
            isFakeBoldText = true
        }
    )
}

/**
 * Draw a note stem (up or down)
 */
private fun DrawScope.drawStem(
    color: Color,
    x: Float,
    y: Float,
    noteRadius: Float,
    lineSpacing: Float,
    stemUp: Boolean = true
) {
    val stemHeight = lineSpacing * 2.5f
    val stemX = if (stemUp) x + noteRadius * 0.8f else x - noteRadius * 0.8f
    drawLine(
        color = color,
        start = Offset(stemX, y),
        end = Offset(stemX, if (stemUp) y - stemHeight else y + stemHeight),
        strokeWidth = 2f
    )
}

/**
 * Draw a rest symbol (quarter/half/whole)
 */
@Suppress("unused")
private fun DrawScope.drawRest(
    duration: Long,
    x: Float,
    staffTop: Float,
    lineSpacing: Float
) {
    val symbol = when {
        duration > 2000L -> "𝄻" // whole rest
        duration > 1000L -> "𝄼" // half rest
        else -> "𝄽"              // quarter rest
    }
    drawContext.canvas.nativeCanvas.drawText(
        symbol,
        x,
        staffTop + lineSpacing * 3f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1A1A1A")
            textSize = lineSpacing * 2.5f
            isFakeBoldText = true
        }
    )
}

/**
 * Convert MIDI note number to staff position (steps from bottom line).
 */
private fun midiToStaffSteps(midi: Int, isTreble: Boolean): Float {
    val NOTE_TO_DIATONIC = mapOf(
        0 to 0, 1 to 0,
        2 to 1, 3 to 1,
        4 to 2,
        5 to 3, 6 to 3,
        7 to 4, 8 to 4,
        9 to 5, 10 to 5,
        11 to 6
    )

    val baseMidi = if (isTreble) 64 else 43
    val basePitchClass = baseMidi % 12
    val baseOctave = baseMidi / 12
    val baseDiatonic = NOTE_TO_DIATONIC[basePitchClass]!! + baseOctave * 7

    val pitchClass = midi % 12
    val octave = midi / 12
    val noteDiatonic = NOTE_TO_DIATONIC[pitchClass]!! + octave * 7

    return (noteDiatonic - baseDiatonic).toFloat()
}

/**
 * Draw ledger lines for notes above or below the staff.
 */
private fun DrawScope.drawLedgerLines(
    x: Float,
    y: Float,
    staffTop: Float,
    staffBottom: Float,
    lineSpacing: Float,
    color: Color
) {
    if (y < staffTop - lineSpacing / 2) {
        var ledgerY = staffTop - lineSpacing
        while (ledgerY >= y - lineSpacing / 2) {
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(x - 12, ledgerY),
                end = Offset(x + 12, ledgerY),
                strokeWidth = 1f
            )
            ledgerY -= lineSpacing
        }
    }
    if (y > staffBottom + lineSpacing / 2) {
        var ledgerY = staffBottom + lineSpacing
        while (ledgerY <= y + lineSpacing / 2) {
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(x - 12, ledgerY),
                end = Offset(x + 12, ledgerY),
                strokeWidth = 1f
            )
            ledgerY += lineSpacing
        }
    }
}

/**
 * Draw a simple clef symbol (treble or bass) using text.
 */
private fun DrawScope.drawClef(
    isTreble: Boolean,
    x: Float,
    staffTop: Float,
    lineSpacing: Float
) {
    val symbol = if (isTreble) "𝄞" else "𝄢"
    val fontSize = lineSpacing * 4.5f
    drawContext.canvas.nativeCanvas.drawText(
        symbol,
        x,
        staffTop + lineSpacing * 3.5f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#333333")
            textSize = fontSize
            isFakeBoldText = true
        }
    )
}
