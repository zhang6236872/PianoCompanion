package com.pianocompanion.omr

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.NoteheadDetector
import com.pianocompanion.omr.image.PitchMapper
import com.pianocompanion.omr.image.StaffLineDetector
import com.pianocompanion.omr.image.StaffLineRemover
import com.pianocompanion.util.MusicUtils

/**
 * Pure-Kotlin OMR pipeline: a [BinaryImage] → [Score].
 *
 * Stages:
 *  1. Staff-line detection (horizontal projection → grouped systems).
 *  2. Staff-line removal (long, thin horizontal runs erased; glyphs preserved).
 *  3. Connected-component labeling of the cleaned image.
 *  4. Notehead selection (size/aspect filters scaled by staff spacing).
 *  5. Pitch mapping (notehead Y → MIDI via staff geometry).
 *  6. Horizontal sequencing (left→right; near-vertical stacks become chords).
 *
 * Being Android-free, the whole pipeline is verifiable in JVM unit tests with
 * synthetic scores.
 */
object OmrPipeline {

    data class Diagnostics(
        val systemCount: Int,
        val lineSpacing: Int,
        val noteheadCount: Int
    )

    data class Result(
        val score: Score,
        val warnings: List<String>,
        val diagnostics: Diagnostics
    ) {
        val isEmpty: Boolean get() = score.notes.isEmpty()
    }

    /**
     * @param binary pre-binarized score image.
     * @param title  title for the produced score.
     * @param tempo  BPM used to convert step index → millisecond timing.
     */
    fun recognize(
        binary: BinaryImage,
        title: String = "拍照识别的乐谱",
        tempo: Int = 120
    ): Result {
        // --- 1. Staff detection -------------------------------------------------
        val systems = StaffLineDetector.detect(binary)
        if (systems.isEmpty()) {
            return Result(
                score = emptyScore(title, tempo),
                warnings = listOf("未检测到五线谱，请拍摄清晰、端正的乐谱"),
                diagnostics = Diagnostics(0, 0, 0)
            )
        }

        val lineSpacing = systems.map { it.lineSpacing }.average().toInt().coerceAtLeast(1)
        val avgThickness = systems.flatMap { it.lines }.map { it.thickness }.average().toInt().coerceAtLeast(1)
        val maxLineThickness = (avgThickness + 2).coerceIn(2, 6)

        // --- 2. Staff removal ---------------------------------------------------
        val minLineRun = (binary.width * 0.5).toInt().coerceAtLeast(lineSpacing * 4)
        val cleaned = StaffLineRemover.remove(binary, minLineRun, maxLineThickness)

        // --- 3. Connected components -------------------------------------------
        val blobs = ConnectedComponents.label(cleaned, minPixels = 4)

        // --- 4 + 5. Noteheads + pitch mapping, grouped per system --------------
        data class Located(val x: Int, val y: Int, val staff: Staff, val systemIdx: Int)
        val located = ArrayList<Located>()

        systems.forEachIndexed { sysIdx, system ->
            val staff = if (systems.size > 1 && system.centerY >= binary.height / 2) Staff.BASS else Staff.TREBLE
            val band = lineSpacing * 3
            val top = system.topLine.center - band
            val bottom = system.bottomLine.center + band
            val noteheads = NoteheadDetector.detect(blobs, system.lineSpacing)
                .filter { it.centerY in top..bottom }
            for (nh in noteheads) {
                located += Located(nh.centerX, nh.centerY, staff, sysIdx)
            }
        }

        // --- 6. Horizontal sequencing (left→right; same-column = chord) --------
        located.sortBy { it.x }
        val xTolerance = (lineSpacing * 0.8).toInt().coerceAtLeast(2)
        val quarterMs = 60_000L / tempo.coerceAtLeast(1)

        val notes = ArrayList<ScoreNote>()
        val pitchesUsed = HashSet<Int>()
        var step = 0
        var i = 0
        while (i < located.size) {
            val columnX = located[i].x
            val startTime = step * quarterMs
            var j = i
            while (j < located.size && located[j].x - columnX <= xTolerance) {
                val ln = located[j]
                val system = systems[ln.systemIdx]
                val midi = PitchMapper.mapToMidi(ln.y, system, ln.staff)
                if (midi in 21..108) {
                    notes += ScoreNote(
                        midiNumber = midi,
                        noteName = MusicUtils.midiToNoteName(midi),
                        startTime = startTime,
                        duration = quarterMs,
                        staff = ln.staff,
                        measureIndex = step / 4
                    )
                    pitchesUsed += midi
                }
                j++
            }
            i = j
            step++
        }

        val warnings = ArrayList<String>()
        if (notes.isEmpty()) {
            warnings += "识别到五线谱但未找到音符（可能图片太小或音符不清晰）"
        }
        if (systems.size > 1) {
            warnings += "检测到 ${systems.size} 个谱表，已按高音/低音谱表分别处理"
        }
        // Rhythm caveat: without beam/stem analysis every note is treated as a quarter note.
        warnings += "节奏为估算值（每个音符按四分音符处理），实际时值需人工校对"

        return Result(
            score = Score(
                id = "omr_${System.currentTimeMillis()}",
                title = title,
                composer = "OMR",
                notes = notes,
                tempo = tempo,
                source = ScoreSource.OMR
            ),
            warnings = warnings,
            diagnostics = Diagnostics(systems.size, lineSpacing, notes.size)
        )
    }

    private fun emptyScore(title: String, tempo: Int): Score = Score(
        id = "omr_${System.currentTimeMillis()}",
        title = title,
        composer = "OMR",
        notes = emptyList(),
        tempo = tempo,
        source = ScoreSource.OMR
    )
}
