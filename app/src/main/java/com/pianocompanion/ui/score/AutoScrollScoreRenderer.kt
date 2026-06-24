package com.pianocompanion.ui.score

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Score renderer with horizontal auto-scroll.
 * Keeps the current note centered in the viewport during practice.
 *
 * @param notes All notes in the score
 * @param currentPosition Index of the currently playing note
 * @param notesPerView How many notes to show per viewport width
 */
@Composable
fun AutoScrollScoreRenderer(
    notes: List<ScoreNote>,
    currentPosition: Int,
    modifier: Modifier = Modifier,
    notesPerView: Int = 12
) {
    val scrollState = rememberScrollState()

    // Calculate target scroll offset to center the current note
    val noteWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    val targetScroll = (currentPosition * noteWidthPx - noteWidthPx * notesPerView / 2).coerceAtLeast(0f).toInt()

    // Animate scroll
    LaunchedEffect(currentPosition) {
        scrollState.animateScrollTo(
            value = targetScroll,
            animationSpec = tween(durationMillis = 400)
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier
                .width(with(androidx.compose.ui.platform.LocalDensity.current) {
                    (maxOf(notes.size, notesPerView) * noteWidthPx.toInt()).toDp()
                })
        ) {
            // Treble staff
            ScrollableStaffView(
                notes = notes.filter {
                    it.staff == Staff.TREBLE || it.staff == Staff.BOTH ||
                        it.staff == Staff.ALTO || it.staff == Staff.TENOR
                },
                currentPosition = currentPosition,
                isTreble = true,
                noteWidthPx = noteWidthPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Bass staff
            ScrollableStaffView(
                notes = notes.filter { it.staff == Staff.BASS || it.staff == Staff.BOTH },
                currentPosition = currentPosition,
                isTreble = false,
                noteWidthPx = noteWidthPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
        }
    }
}

@Composable
private fun ScrollableStaffView(
    notes: List<ScoreNote>,
    currentPosition: Int,
    isTreble: Boolean,
    noteWidthPx: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFFEF7))
    ) {
        val w = size.width
        val h = size.height

        val staffTop = h * 0.2f
        val staffBottom = h * 0.8f
        val lineSpacing = (staffBottom - staffTop) / 4f
        val leftMargin = noteWidthPx * 1.2f
        val staffColor = Color(0xFF333333)

        // Draw 5 staff lines
        for (i in 0..4) {
            val y = staffTop + i * lineSpacing
            drawLine(
                color = staffColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f
            )
        }

        // Start bar line
        drawLine(staffColor, Offset(leftMargin - 10f, staffTop), Offset(leftMargin - 10f, staffBottom), 2f)

        // Draw clef
        drawClef(isTreble, 10f, staffTop, lineSpacing)

        // Draw all notes
        var lastMeasure = -1

        notes.forEachIndexed { i, note ->
            if (note.staff != Staff.BOTH &&
                note.staff != Staff.ALTO &&
                note.staff != Staff.TENOR &&
                note.staff != (if (isTreble) Staff.TREBLE else Staff.BASS)
            ) return@forEachIndexed

            val x = leftMargin + i * noteWidthPx + noteWidthPx / 2

            // Draw bar line when measure changes
            if (note.measureIndex != lastMeasure && lastMeasure >= 0) {
                val barX = x - noteWidthPx / 2
                drawLine(
                    color = staffColor.copy(alpha = 0.3f),
                    start = Offset(barX, staffTop),
                    end = Offset(barX, staffBottom),
                    strokeWidth = 1f
                )
            }
            lastMeasure = note.measureIndex

            val stepFromBottom = midiToStaffSteps(note.midiNumber, isTreble)
            val y = staffBottom - stepFromBottom * (lineSpacing / 2f)
            val isCurrent = i == currentPosition
            val isPast = i < currentPosition

            drawLedgerLines(x, y, staffTop, staffBottom, lineSpacing, staffColor)

            // Accidental
            val pitchClass = note.midiNumber % 12
            if (pitchClass in setOf(1, 3, 6, 8, 10)) {
                val symbol = if (note.noteName.contains("#")) "♯" else "♭"
                drawContext.canvas.nativeCanvas.drawText(
                    symbol, x - noteWidthPx * 0.3f, y + lineSpacing * 0.35f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#333333")
                        textSize = lineSpacing * 1.8f
                        isFakeBoldText = true
                    }
                )
            }

            val noteColor = when {
                isCurrent -> Color(0xFF4CAF50)
                isPast -> Color(0xFF999999)
                else -> Color(0xFF1A1A1A)
            }
            val noteRadius = lineSpacing * 0.35f

            // Note head
            if (note.duration > 1000L) {
                drawOval(
                    color = noteColor,
                    topLeft = Offset(x - noteRadius, y - noteRadius * 0.7f),
                    size = Size(noteRadius * 2, noteRadius * 1.4f),
                    style = Stroke(width = 2f)
                )
            } else {
                drawOval(
                    color = noteColor,
                    topLeft = Offset(x - noteRadius, y - noteRadius * 0.7f),
                    size = Size(noteRadius * 2, noteRadius * 1.4f)
                )
            }

            // Stem
            val stemHeight = lineSpacing * 2.5f
            drawLine(noteColor, Offset(x + noteRadius, y), Offset(x + noteRadius, y - stemHeight), 2f)

            // Articulation marks (staccato dot / tenuto line / accent wedge)
            when (note.articulation) {
                com.pianocompanion.data.model.Articulation.STACCATO -> {
                    drawCircle(
                        color = noteColor,
                        radius = noteRadius * 0.22f,
                        center = Offset(x, y + noteRadius * 2.8f)
                    )
                }
                com.pianocompanion.data.model.Articulation.TENUTO -> {
                    val ty = y + noteRadius * 2.8f
                    drawLine(
                        color = noteColor,
                        start = Offset(x - noteRadius * 0.8f, ty),
                        end = Offset(x + noteRadius * 0.8f, ty),
                        strokeWidth = 2.5f
                    )
                }
                com.pianocompanion.data.model.Articulation.ACCENT -> {
                    val ay = y + noteRadius * 2.6f
                    val wedge = Path().apply {
                        moveTo(x - noteRadius * 0.7f, ay - noteRadius * 0.4f)
                        lineTo(x + noteRadius * 0.7f, ay)
                        lineTo(x - noteRadius * 0.7f, ay + noteRadius * 0.4f)
                    }
                    drawPath(wedge, color = noteColor, style = Stroke(width = 2.5f))
                }
                com.pianocompanion.data.model.Articulation.STACCATISSIMO -> {
                    val sy = y + noteRadius * 2.8f
                    val spade = Path().apply {
                        moveTo(x, sy - noteRadius * 0.5f)
                        lineTo(x - noteRadius * 0.3f, sy)
                        lineTo(x + noteRadius * 0.3f, sy)
                        close()
                    }
                    drawPath(spade, color = noteColor)
                }
                com.pianocompanion.data.model.Articulation.MARCATO -> {
                    val my = y + noteRadius * 2.8f
                    val caret = Path().apply {
                        moveTo(x - noteRadius * 0.55f, my)
                        lineTo(x, my - noteRadius * 0.7f)
                        lineTo(x + noteRadius * 0.55f, my)
                    }
                    drawPath(caret, color = noteColor, style = Stroke(width = 2.5f))
                }
                else -> { /* NONE: no mark */ }
            }

            // Draw fingering number above the notehead
            if (note.fingering in 1..5) {
                drawContext.canvas.nativeCanvas.drawText(
                    note.fingering.toString(),
                    x - noteRadius * 0.5f,
                    y - noteRadius * 3.0f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#1565C0")
                        textSize = noteRadius * 1.8f
                        isFakeBoldText = true
                    }
                )
            }

            // Current note highlight + label
            if (isCurrent) {
                drawOval(
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    topLeft = Offset(x - noteRadius * 2, y - noteRadius * 2),
                    size = Size(noteRadius * 4, noteRadius * 4)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    note.noteName, x - 15, staffBottom + 25,
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

// === Shared helper functions (same as ScoreRenderer) ===

private fun midiToStaffSteps(midi: Int, isTreble: Boolean): Float {
    val NOTE_TO_DIATONIC = mapOf(
        0 to 0, 1 to 0, 2 to 1, 3 to 1, 4 to 2,
        5 to 3, 6 to 3, 7 to 4, 8 to 4, 9 to 5, 10 to 5, 11 to 6
    )
    val baseMidi = if (isTreble) 64 else 43
    val baseDiatonic = NOTE_TO_DIATONIC[baseMidi % 12]!! + (baseMidi / 12) * 7
    val noteDiatonic = NOTE_TO_DIATONIC[midi % 12]!! + (midi / 12) * 7
    return (noteDiatonic - baseDiatonic).toFloat()
}

private fun DrawScope.drawLedgerLines(
    x: Float, y: Float, staffTop: Float, staffBottom: Float,
    lineSpacing: Float, color: Color
) {
    if (y < staffTop - lineSpacing / 2) {
        var ledgerY = staffTop - lineSpacing
        while (ledgerY >= y - lineSpacing / 2) {
            drawLine(color.copy(alpha = 0.5f), Offset(x - 12, ledgerY), Offset(x + 12, ledgerY), 1f)
            ledgerY -= lineSpacing
        }
    }
    if (y > staffBottom + lineSpacing / 2) {
        var ledgerY = staffBottom + lineSpacing
        while (ledgerY <= y + lineSpacing / 2) {
            drawLine(color.copy(alpha = 0.5f), Offset(x - 12, ledgerY), Offset(x + 12, ledgerY), 1f)
            ledgerY += lineSpacing
        }
    }
}

private fun DrawScope.drawClef(isTreble: Boolean, x: Float, staffTop: Float, lineSpacing: Float) {
    val symbol = if (isTreble) "𝄞" else "𝄢"
    drawContext.canvas.nativeCanvas.drawText(
        symbol, x, staffTop + lineSpacing * 3.5f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#333333")
            textSize = lineSpacing * 4.5f
            isFakeBoldText = true
        }
    )
}
