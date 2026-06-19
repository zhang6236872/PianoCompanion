package com.pianocompanion.omr

import com.pianocompanion.data.model.Score
import com.pianocompanion.data.model.ScoreNote
import com.pianocompanion.data.model.ScoreSource
import com.pianocompanion.data.model.Staff
import com.pianocompanion.omr.image.BinaryImage
import com.pianocompanion.omr.image.ConnectedComponents
import com.pianocompanion.omr.image.Notehead
import com.pianocompanion.omr.image.NoteheadDetector
import com.pianocompanion.omr.image.PitchMapper
import com.pianocompanion.omr.image.RhythmAnalyzer
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
        data class Located(val nh: Notehead, val staff: Staff, val systemIdx: Int)
        val located = ArrayList<Located>()

        systems.forEachIndexed { sysIdx, system ->
            val staff = if (systems.size > 1 && system.centerY >= binary.height / 2) Staff.BASS else Staff.TREBLE
            val band = lineSpacing * 3
            val top = system.topLine.center - band
            val bottom = system.bottomLine.center + band
            // 传入 cleaned 图像，启用"符头+符干"融合块的二次恢复扫描。
            val noteheads = NoteheadDetector.detect(blobs, system.lineSpacing, cleaned)
                .filter { it.centerY in top..bottom }
            for (nh in noteheads) {
                located += Located(nh, staff, sysIdx)
            }
        }

        // --- 节奏分析：符干/横梁/符尾 → 真实时值（不再清一色四分音符）---------
        val rhythms = RhythmAnalyzer.analyze(cleaned, located.map { it.nh }, lineSpacing)

        // --- 6. Horizontal sequencing (left→right; same-column = chord) --------
        // 按 x 排序的索引，保持与 rhythms 对齐。
        val order = located.indices.sortedBy { located[it].nh.centerX }
        val xTolerance = (lineSpacing * 0.8).toInt().coerceAtLeast(2)
        val quarterMs = 60_000L / tempo.coerceAtLeast(1)
        val measureMs = quarterMs * 4 // 默认 4/4 拍号下一个小节的时长

        val notes = ArrayList<ScoreNote>()
        var cursor = 0L
        var i = 0
        while (i < order.size) {
            val leadIdx = order[i]
            val columnX = located[leadIdx].nh.centerX
            // 同一列（和弦）共享起始时间与时值；取首成员的时值。
            val duration = rhythms[leadIdx].duration.toMillis(quarterMs)
            val startTime = cursor
            var j = i
            while (j < order.size && located[order[j]].nh.centerX - columnX <= xTolerance) {
                val ln = located[order[j]]
                val system = systems[ln.systemIdx]
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff)
                if (midi in 21..108) {
                    notes += ScoreNote(
                        midiNumber = midi,
                        noteName = MusicUtils.midiToNoteName(midi),
                        startTime = startTime,
                        duration = duration,
                        staff = ln.staff,
                        measureIndex = (startTime / measureMs).toInt()
                    )
                }
                j++
            }
            cursor += duration
            i = j
        }

        val warnings = ArrayList<String>()
        if (notes.isEmpty()) {
            warnings += "识别到五线谱但未找到音符（可能图片太小或音符不清晰）"
        }
        if (systems.size > 1) {
            warnings += "检测到 ${systems.size} 个谱表，已按高音/低音谱表分别处理"
        }
        // 节奏提示：根据是否检测到符干给出更准确的说明。
        val detectedStems = rhythms.count { it.hasStem }
        val durationTypes = rhythms.map { it.duration }.toSet()
        if (detectedStems > 0 || durationTypes.any { it != com.pianocompanion.omr.image.NoteDuration.QUARTER }) {
            val typeNames = durationTypes.joinToString("、") { it.label }
            warnings += "节奏已通过符干/横梁/符尾分析估算（$typeNames），复杂节奏需人工校对"
        } else {
            warnings += "节奏为估算值（未检测到符干，每个音符按四分音符处理），实际时值需人工校对"
        }

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
