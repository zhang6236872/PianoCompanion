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
import com.pianocompanion.omr.image.SignatureDetector
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

        // --- 4. 每系统检测符头 -------------------------------------------------
        // 分两阶段：先用"紧凑主扫描"得到不含谱号/拍号等高大字形的干净符头，用于确定
        // 签名区右界（避免谱号曲线被符头恢复扫描误判为符头、进而污染签名区边界）；
        // 再用完整扫描（含符头+符干恢复、连梁组切分）得到最终符头，并排除签名区。
        data class Located(val nh: Notehead, val staff: Staff, val systemIdx: Int)
        val fullNoteheadsBySystem = ArrayList<List<Notehead>>(systems.size)
        val noteheadsForSignature = ArrayList<List<Notehead>>(systems.size)
        systems.forEach { system ->
            val band = lineSpacing * 3
            val top = system.topLine.center - band
            val bottom = system.bottomLine.center + band
            val compact = NoteheadDetector.detect(blobs, system.lineSpacing) // 仅紧凑主扫描
                .filter { it.centerY in top..bottom }
            // 传入 cleaned 图像，启用"符头+符干"融合块的二次恢复扫描与连梁组切分。
            val full = NoteheadDetector.detect(blobs, system.lineSpacing, cleaned)
                .filter { it.centerY in top..bottom }
            fullNoteheadsBySystem += full
            // 签名区右界优先用干净符头；若该系统全是带符干/连梁音符（紧凑扫描为空），
            // 则回退到完整扫描，保证边界不为空。
            noteheadsForSignature += if (compact.isNotEmpty()) compact else full
        }

        // --- 5. 签名识别：谱号 / 调号 / 拍号（替代旧的"按竖直位置推断谱表"）---
        val signatures = SignatureDetector.detect(cleaned, systems, blobs, noteheadsForSignature)
        val keysBySystem = signatures.perSystem.map { it.keySignature }
        val detectedTimeSig = signatures.timeSignature

        // 排除签名区内的符头：谱号曲线、拍号数字等高大字形会被符头恢复扫描误判为
        // 符头，它们位于第一个真实音符左侧的签名区内，按 signatureEndX 过滤掉。
        val noteheadsBySystem = fullNoteheadsBySystem.mapIndexed { idx, nhs ->
            val endX = signatures.perSystem.getOrElse(idx) { null }?.signatureEndX ?: 0
            if (endX <= 0) nhs else nhs.filter { it.centerX > endX }
        }

        // --- 6. 用识别到的谱号 + 调号映射音高 ----------------------------------
        val located = ArrayList<Located>()
        systems.forEachIndexed { sysIdx, system ->
            val staff = resolveStaff(signatures.perSystem[sysIdx].clef, systems, system, binary)
            for (nh in noteheadsBySystem[sysIdx]) {
                located += Located(nh, staff, sysIdx)
            }
        }

        // --- 节奏分析：符干/横梁/符尾 → 真实时值（不再清一色四分音符）---------
        val rhythms = RhythmAnalyzer.analyze(cleaned, located.map { it.nh }, lineSpacing)

        // --- 7. Horizontal sequencing (left→right; same-column = chord) --------
        // 按 x 排序的索引，保持与 rhythms 对齐。
        val order = located.indices.sortedBy { located[it].nh.centerX }
        val xTolerance = (lineSpacing * 0.8).toInt().coerceAtLeast(2)
        val quarterMs = 60_000L / tempo.coerceAtLeast(1)
        // 拍号决定一个小节的时长；未识别到时默认 4/4。
        val quartersPerMeasure = detectedTimeSig?.quartersPerMeasure ?: 4.0
        val measureMs = (quarterMs * quartersPerMeasure).toLong().coerceAtLeast(1L)

        val notes = ArrayList<ScoreNote>()
        var cursor = 0L
        var i = 0
        while (i < order.size) {
            val leadIdx = order[i]
            val columnX = located[leadIdx].nh.centerX
            // 同一列（和弦）共享起始时间与时值；取首成员的时值（含附点倍率）。
            val duration = rhythms[leadIdx].effectiveMillis(quarterMs)
            val startTime = cursor
            var j = i
            while (j < order.size && located[order[j]].nh.centerX - columnX <= xTolerance) {
                val ln = located[order[j]]
                val system = systems[ln.systemIdx]
                val midi = PitchMapper.mapToMidi(ln.nh.centerY, system, ln.staff, keysBySystem[ln.systemIdx])
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
            warnings += "检测到 ${systems.size} 个谱表，已分别识别谱号后处理"
        }
        // 签名识别提示。
        warnings += signatures.warnings
        val detectedClefs = signatures.perSystem.map { it.clef }
        if (detectedClefs.any {
                it == SignatureDetector.ClefType.TREBLE ||
                    it == SignatureDetector.ClefType.BASS ||
                    it == SignatureDetector.ClefType.ALTO ||
                    it == SignatureDetector.ClefType.TENOR
            }
        ) {
            val names = detectedClefs.joinToString("、") {
                when (it) {
                    SignatureDetector.ClefType.TREBLE -> "高音谱号"
                    SignatureDetector.ClefType.BASS -> "低音谱号"
                    SignatureDetector.ClefType.ALTO -> "中音谱号"
                    SignatureDetector.ClefType.TENOR -> "次中音谱号"
                    else -> "未知"
                }
            }
            warnings += "谱号识别：$names"
        }
        if (detectedTimeSig != null) {
            warnings += "拍号识别：${detectedTimeSig.numerator}/${detectedTimeSig.denominator}"
        }
        // 节奏提示：根据是否检测到符干给出更准确的说明。
        val detectedStems = rhythms.count { it.hasStem }
        val durationTypes = rhythms.map { it.duration }.toSet()
        val dottedCount = rhythms.count { it.dotted }
        val flaggedCount = rhythms.count { it.flagCount > 0 }
        if (detectedStems > 0 || durationTypes.any { it != com.pianocompanion.omr.image.NoteDuration.QUARTER }) {
            val typeNames = durationTypes.joinToString("、") { it.label }
            val dotHint = if (dottedCount > 0) "，含 $dottedCount 个附点音符" else ""
            val flagHint = if (flaggedCount > 0) "，含 $flaggedCount 个带符尾音符" else ""
            warnings += "节奏已通过符干/横梁/符尾分析估算（$typeNames$dotHint$flagHint），复杂节奏需人工校对"
        } else {
            val dotHint = if (dottedCount > 0) "，含 $dottedCount 个附点音符" else ""
            warnings += "节奏为估算值（未检测到符干，每个音符按四分音符处理$dotHint），实际时值需人工校对"
        }

        return Result(
            score = Score(
                id = "omr_${System.currentTimeMillis()}",
                title = title,
                composer = "OMR",
                notes = notes,
                tempo = tempo,
                timeSignature = detectedTimeSig?.toString() ?: "4/4",
                source = ScoreSource.OMR
            ),
            warnings = warnings,
            diagnostics = Diagnostics(systems.size, lineSpacing, notes.size)
        )
    }

    /**
     * 把识别到的谱号转为 [Staff]；识别失败(UNKNOWN)时回退到旧的竖直位置启发式，
     * 保证对没有绘制谱号的合成图仍保持原有行为。
     */
    private fun resolveStaff(
        clef: SignatureDetector.ClefType,
        systems: List<com.pianocompanion.omr.image.StaffSystem>,
        system: com.pianocompanion.omr.image.StaffSystem,
        binary: BinaryImage
    ): Staff = when (clef) {
        SignatureDetector.ClefType.TREBLE -> Staff.TREBLE
        SignatureDetector.ClefType.BASS -> Staff.BASS
        SignatureDetector.ClefType.ALTO -> Staff.ALTO
        SignatureDetector.ClefType.TENOR -> Staff.TENOR
        SignatureDetector.ClefType.UNKNOWN ->
            if (systems.size > 1 && system.centerY >= binary.height / 2) Staff.BASS else Staff.TREBLE
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
